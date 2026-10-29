package com.shamashel.workout.engine

import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.max

internal data class WorkingUpdate(
    val state: SlotState,
    val decision: Decision,
    val reason: String,
    val loadIncreased: Boolean,
    val secondConsecutiveMiss: Boolean,
    val prompts: List<Prompt>,
)

internal fun evaluateWorking(request: EvalRequest, target: RirTarget): WorkingUpdate {
    val slot = request.slot
    return if (slot.isCarry) {
        evaluateCarry(request, target)
    } else {
        evaluateReps(request, target)
    }
}

private fun evaluateReps(request: EvalRequest, target: RirTarget): WorkingUpdate {
    val slot = request.slot
    val log = request.log
    val performed = performedWeight(request)
    val state = request.state.copy(weight = performed, exerciseName = request.state.exerciseName)
    val prompts = mutableListOf<Prompt>()
    val sets = log.sets
    val avg = averageRir(sets)
    val allLo = sets.isNotEmpty() && sets.all { it.reps >= slot.repLo }
    val allHi = sets.size >= slot.nSets && sets.all { it.reps >= slot.repHi }
    val incomplete = sets.size < slot.nSets
    val tooHard = sets.any { it.rir == 0 } || (avg != null && avg < target.gate - 1)
    val tooEasy = avg != null && avg >= target.gate + 2 && allLo && !allHi && !incomplete

    val update = when {
        incomplete || tooHard || !allLo -> missUpdate(request, state, tooHard, tenPercentBecauseFailure(sets))
        allHi && avg != null && avg >= max(1, target.gate - 1) ->
            earnedUpdate(request, state, target, avg, prompts)
        tooEasy -> tooEasyUpdate(request, state, target)
        else -> inRangeUpdate(request, state, target, someAtTop = sets.any { it.reps >= slot.repHi })
    }

    return finishWorking(request, update)
}

private fun evaluateCarry(request: EvalRequest, target: RirTarget): WorkingUpdate {
    val slot = request.slot
    val log = request.log
    val performed = performedWeight(request)
    val state = request.state.copy(weight = performed)
    val sets = log.sets
    val avg = averageRir(sets)
    val allLo = sets.isNotEmpty() && sets.all { it.reps >= slot.repLo }
    val allHi = sets.size >= slot.nSets && sets.all { it.reps >= slot.repHi }
    val incomplete = sets.size < slot.nSets
    val tooHard = sets.any { it.rir == 0 } || (avg != null && avg < target.gate - 1)
    val postureOk = log.postureClean == true
    val gripOk = sets.isNotEmpty() && sets.all { it.rir >= 1 }

    val update = when {
        incomplete || !postureOk || tooHard || !allLo ->
            missUpdate(request, state, tooHard || !postureOk, tenPercentBecauseFailure(sets))
        allHi && postureOk && gripOk && avg != null && avg >= max(1, target.gate - 1) ->
            earnedUpdate(request, state, target, avg, mutableListOf())
        else -> carryTimeUpdate(request, state, target)
    }
    return finishWorking(request, update)
}

private fun earnedUpdate(
    request: EvalRequest,
    state: SlotState,
    target: RirTarget,
    avg: Double,
    prompts: MutableList<Prompt>,
): WorkingUpdate {
    val slot = request.slot
    val settings = request.settings
    val increment = effectiveIncrement(slot, state, settings)
    val available = availableFor(slot, settings)
    val atMax = atMaxImplement(state.weight, available)
    val fraction = stepFraction(state.weight, increment)
    val eff = extensionCap(slot)
    val alreadyExtendedTop = request.log.sets.isNotEmpty() && request.log.sets.all { it.reps >= eff }

    if (atMax && isGoblet(slot)) {
        return ceilingHold(request, state, prompts)
    }

    val wantWeight = fraction <= 0.10 + 1e-6 || alreadyExtendedTop
    if (!wantWeight) {
        val extended = state.copy(extensionUsed = true)
        val targets = climb(slot, request.log, eff)
        val reason = if (slot.isCarry) {
            "Jump of ${formatSignedLb(increment)} is over 10% at ${formatLb(state.weight)}, so add time before load. Next ${formatReps(targets)} s."
        } else {
            "Jump of ${formatSignedLb(increment)} is over 10% at ${formatLb(state.weight)}, so add reps before load. Next ${formatReps(targets)}."
        }
        return plain(extended.copy(targetReps = targets), Decision.EXTEND_REPS, reason, false, prompts)
    }

    if (request.suppressCarryIncrease && slot.isCarry) {
        val held = state.copy(targetReps = List(slot.nSets) { slot.repHi })
        val reason = "Trap-bar load went up today, so this carry stays at ${formatLb(state.weight)}."
        return plain(held, Decision.CARRY_HELD_FOR_DEADLIFT, reason, false, prompts)
    }

    val stepped = addOneStep(state.weight, increment, available)
    if (stepped == null) {
        val reason = "All sets at ${slot.repHi}, but ${formatLb(snapLb(state.weight + increment))} is not in your available weights. Hold ${formatLb(state.weight)}."
        return plain(state.copy(targetReps = List(slot.nSets) { slot.repHi }), Decision.IN_RANGE, reason, false, prompts)
    }
    val increased = state.copy(
        weight = stepped,
        targetReps = List(slot.nSets) { slot.repLo },
        extensionUsed = false,
        lastIncreaseDate = request.date,
        sessionsAtWeight = 0,
        preferMicro = false,
        ceilingStreak = 0,
    )
    val reason = "All sets at ${slot.repHi} and avg RIR ${formatAvg(avg)} ≥ ${max(1, target.gate - 1)}, so ${formatSignedLb(stepped - state.weight)}"
    return plain(increased, Decision.EARNED_INCREASE, reason, true, prompts)
}

private fun ceilingHold(
    request: EvalRequest,
    state: SlotState,
    prompts: MutableList<Prompt>,
): WorkingUpdate {
    val slot = request.slot
    val eff = minOf(15, max(slot.repHi, 15))
    val atTop = request.log.sets.all { it.reps >= slot.repHi }
    val streak = if (atTop) state.ceilingStreak + 1 else 0
    val extended = streak > 0 && slot.repHi < 15
    val cap = if (extended) eff else slot.repHi
    val targets = if (request.log.sets.all { it.reps >= cap }) {
        List(slot.nSets) { cap }
    } else {
        climb(slot, request.log, cap)
    }
    if (streak >= 3) {
        prompts += Prompt(
            PromptKind.GOBLET_CEILING,
            "Goblet squat is on the heaviest dumbbell you have, with every set at the top, for 3 sessions. " +
                "Extend reps to 15, then add a tempo second or a pause second, " +
                "then switch this slot to a DB split squat or a DB box squat.",
        )
    }
    val next = state.copy(
        ceilingStreak = streak,
        extensionUsed = extended || state.extensionUsed,
        targetReps = targets,
    )
    val reason = "At the heaviest dumbbell available. Hold ${formatLb(state.weight)}. Next ${formatReps(targets)}."
    return plain(next, Decision.IN_RANGE, reason, false, prompts)
}

private fun tooEasyUpdate(request: EvalRequest, state: SlotState, target: RirTarget): WorkingUpdate {
    val slot = request.slot
    val increment = effectiveIncrement(slot, state, request.settings)
    val days = state.lastIncreaseDate?.let { ChronoUnit.DAYS.between(it, request.date) }
    val cooledDown = days == null || days >= 14
    val fraction = stepFraction(state.weight, increment)
    val jumpOk = fraction <= 0.10 + 1e-6
    val repsNow = request.log.sets.map { it.reps }
    val thirdUnchanged = state.lastLoggedReps == repsNow &&
        weightsEqual(state.lastLoggedWeight, performedWeight(request)) &&
        state.unchangedStreak >= 2
    if (cooledDown && jumpOk && !thirdUnchanged) {
        val stepped = addOneStep(state.weight, increment, availableFor(slot, request.settings))
        if (stepped != null) {
            val targets = List(slot.nSets) { slot.repLo + 1 }
            val next = state.copy(
                weight = stepped,
                targetReps = targets,
                lastIncreaseDate = request.date,
                sessionsAtWeight = 0,
                preferMicro = false,
                extensionUsed = false,
            )
            val reason = "Easier than the RIR target by 2 or more, so one step to ${formatLb(stepped)}. Next target ${formatReps(targets)}."
            return plain(next, Decision.TOO_EASY_INCREASE, reason, true, emptyList())
        }
    }
    return inRangeUpdate(request, state, target, someAtTop = request.log.sets.any { it.reps >= slot.repHi })
}

private fun inRangeUpdate(
    request: EvalRequest,
    state: SlotState,
    target: RirTarget,
    someAtTop: Boolean,
): WorkingUpdate {
    val slot = request.slot
    val cap = repCap(slot, state.extensionUsed)
    val targets = climb(slot, request.log, cap)
    val formatted = formatReps(targets)
    val reason = if (someAtTop) {
        "Not all at ${slot.repHi}, hold. Next $formatted"
    } else if (slot.isCarry) {
        "In range, hold weight. Next target $formatted s."
    } else {
        "In range, hold weight. Next target $formatted"
    }
    return plain(state.copy(targetReps = targets), Decision.IN_RANGE, reason, false, emptyList())
}

private fun carryTimeUpdate(request: EvalRequest, state: SlotState, target: RirTarget): WorkingUpdate {
    return inRangeUpdate(request, state, target, someAtTop = request.log.sets.any { it.reps >= request.slot.repHi })
}

private fun missUpdate(
    request: EvalRequest,
    state: SlotState,
    tooHard: Boolean,
    failure: Boolean,
): WorkingUpdate {
    val slot = request.slot
    val nextCount = state.missCount + 1
    if (nextCount == 1) {
        val kept = state.copy(missCount = 1, targetReps = state.targetReps.ifEmpty { List(slot.nSets) { slot.repLo } })
        val reason = if (tooHard) {
            "Too hard: miss 1, repeat ${formatLb(state.weight)}"
        } else {
            "Short of the range: miss 1, repeat ${formatLb(state.weight)}"
        }
        return plain(kept, Decision.MISS_REPEAT, reason, false, emptyList(), secondMiss = false)
    }
    val drop = if (failure) 0.10 else 0.05
    val raw = state.weight * (1.0 - drop)
    val dropped = roundDownToAvailable(raw, availableFor(slot, request.settings))
    val actual = if (dropped + 0.05 < state.weight) dropped else state.weight
    val pctLabel = if (failure) "10" else "5"
    val because = if (failure) " because RIR 0" else ""
    val reason = if (weightsEqual(actual, state.weight)) {
        "Miss 2: already at the lightest available weight, hold ${formatLb(actual)}. Miss count reset."
    } else {
        "Miss 2: weight → ${formatLb(actual)} (−$pctLabel%$because), miss count reset"
    }
    val reset = state.copy(
        weight = actual,
        missCount = 0,
        sessionsAtWeight = 0,
        extensionUsed = false,
        preferMicro = false,
    )
    return plain(reset, Decision.MISS_RESET, reason, false, emptyList(), secondMiss = true)
}

private fun plain(
    state: SlotState,
    decision: Decision,
    reason: String,
    loadIncreased: Boolean,
    prompts: List<Prompt>,
    secondMiss: Boolean = false,
): WorkingUpdate {
    return WorkingUpdate(
        state = state,
        decision = decision,
        reason = reason,
        loadIncreased = loadIncreased,
        secondConsecutiveMiss = secondMiss,
        prompts = prompts,
    )
}

private fun climb(slot: SlotDefinition, log: SessionLog, cap: Int): List<Int> {
    return List(slot.nSets) { index ->
        val last = log.sets.getOrNull(index)?.reps ?: slot.repLo
        minOf(last + slot.repStep, cap)
    }
}

private fun rememberSession(state: SlotState, request: EvalRequest, loadIncreased: Boolean): SlotState {
    val reps = request.log.sets.map { it.reps }
    val performed = performedWeight(request)
    val sameReps = state.lastLoggedReps == reps
    val sameWeight = weightsEqual(state.lastLoggedWeight, performed)
    val streak = if (state.lastLoggedReps == null) {
        1
    } else if (sameReps && sameWeight) {
        state.unchangedStreak + 1
    } else {
        1
    }
    val total = reps.sum()
    val progressed = loadIncreased || (state.lastLoggedTotalReps != null && total > state.lastLoggedTotalReps)
    val stall = if (progressed) 0 else state.stallSessions + 1
    val sessions = if (loadIncreased) 0 else state.sessionsAtWeight + 1
    val stillAtGobletCeiling = isGoblet(request.slot) &&
        atMaxImplement(performed, availableFor(request.slot, request.settings)) &&
        request.log.sets.size >= request.slot.nSets &&
        request.log.sets.all { it.reps >= request.slot.repHi }
    return state.copy(
        unchangedStreak = streak,
        stallSessions = stall,
        sessionsAtWeight = sessions,
        lastLoggedReps = reps,
        lastLoggedWeight = performed,
        lastLoggedTotalReps = total,
        ceilingStreak = if (stillAtGobletCeiling) state.ceilingStreak else 0,
    )
}

private data class PlateauOutcome(val state: SlotState, val prompts: List<Prompt>)

private fun finishWorking(request: EvalRequest, update: WorkingUpdate): WorkingUpdate {
    val decided = if (update.decision == Decision.MISS_REPEAT || update.decision == Decision.MISS_RESET) {
        update
    } else {
        update.copy(state = update.state.copy(missCount = 0))
    }
    val withMemory = rememberSession(decided.state, request, decided.loadIncreased)
    val plateau = plateauPrompts(request.slot, withMemory, request.settings, decided.loadIncreased)
    return decided.copy(state = plateau.state, prompts = decided.prompts + plateau.prompts)
}

private fun plateauPrompts(
    slot: SlotDefinition,
    state: SlotState,
    settings: EngineSettings,
    loadIncreased: Boolean,
): PlateauOutcome {
    if (!slot.tracksLoad || loadIncreased) return PlateauOutcome(state, emptyList())
    val prompts = mutableListOf<Prompt>()
    var prefer = state.preferMicro
    if (state.unchangedStreak >= 3) {
        prompts += Prompt(
            PromptKind.PLATEAU_RECOVERY,
            "Reps and weight have not moved for 3 sessions. Check sleep and fatigue before you add load. Weight stays.",
        )
        val configured = baseIncrement(slot, settings)
        val fullStep = if (settings.microPlates) {
            when (slot.equipment) {
                Equipment.DUMBBELL -> maxOf(configured, 5.0)
                Equipment.BARBELL -> maxOf(configured, 10.0)
                Equipment.MACHINE -> maxOf(configured, 10.0)
                Equipment.BODYWEIGHT -> configured
            }
        } else {
            configured
        }
        if (stepFraction(state.weight, fullStep) > 0.05 && settings.microPlates && configured + WEIGHT_EPS < fullStep) {
            prefer = true
            prompts += Prompt(
                PromptKind.PLATEAU_MICRO,
                "The next full step is over 5%. Micro-plates are on, so the next increase uses a micro-step.",
            )
        }
    }
    if (state.stallSessions >= 6) {
        val sisters = if (slot.substitutes.isEmpty()) {
            "a close variation you can actually set up"
        } else {
            slot.substitutes.joinToString(", ")
        }
        prompts += Prompt(
            PromptKind.PLATEAU_SWAP,
            "Two blocks on ${slot.name} without a heavier weight or more reps. A swap to $sisters is worth trying.",
        )
    }
    return PlateauOutcome(state.copy(preferMicro = prefer), prompts)
}

internal fun painEarlyReasons(request: EvalRequest): List<String> {
    val pain = request.log.painFlag
    if (pain >= 2) return listOf("Pain flag $pain on ${request.slot.id}")
    return emptyList()
}

internal fun applyBlockedIncrease(request: EvalRequest, update: WorkingUpdate): WorkingUpdate {
    val pain = request.log.painFlag
    var block = update.state.loadBlockSessions
    if (pain == 1) block = max(block, 1)
    if (pain >= 2) block = max(block, 2)
    if (pain == 0 && block == 0) return update

    var state = update.state
    var decision = update.decision
    var reason = update.reason
    var loadIncreased = update.loadIncreased
    if (loadIncreased && block > 0) {
        val heldTargets = if (decision == Decision.TOO_EASY_INCREASE) {
            climb(request.slot, request.log, repCap(request.slot, request.state.extensionUsed))
        } else {
            List(request.slot.nSets) { repCap(request.slot, request.state.extensionUsed) }
        }
        state = request.state.copy(
            weight = performedWeight(request),
            targetReps = heldTargets,
            missCount = update.state.missCount,
            extensionUsed = update.state.extensionUsed,
            loadBlockSessions = block - 1,
            unchangedStreak = update.state.unchangedStreak,
            stallSessions = update.state.stallSessions,
            ceilingStreak = update.state.ceilingStreak,
            preferMicro = request.state.preferMicro,
            lastLoggedReps = update.state.lastLoggedReps,
            lastLoggedWeight = update.state.lastLoggedWeight,
            lastLoggedTotalReps = update.state.lastLoggedTotalReps,
            sessionsAtWeight = update.state.sessionsAtWeight,
            exerciseName = update.state.exerciseName,
        )
        // Recompute memory against the held weight so a suppressed increase does not count as progress.
        val remembered = rememberSession(
            state.copy(lastLoggedReps = request.state.lastLoggedReps, lastLoggedWeight = request.state.lastLoggedWeight, lastLoggedTotalReps = request.state.lastLoggedTotalReps, stallSessions = request.state.stallSessions, unchangedStreak = request.state.unchangedStreak, sessionsAtWeight = request.state.sessionsAtWeight),
            request,
            loadIncreased = false,
        )
        state = remembered.copy(loadBlockSessions = (block - 1).coerceAtLeast(0), targetReps = heldTargets, weight = performedWeight(request))
        loadIncreased = false
        decision = Decision.PAIN_HOLD
        reason = "Pain flag $pain: no load increase. Hold ${formatLb(state.weight)}."
    } else if (block > 0) {
        state = state.copy(loadBlockSessions = block - 1)
    }
    return update.copy(state = state, decision = decision, reason = reason, loadIncreased = loadIncreased)
}

internal fun decoratePain(request: EvalRequest, update: WorkingUpdate): WorkingUpdate {
    if (request.log.painFlag < 2) return update
    val prompts = update.prompts.toMutableList()
    val slot = request.slot
    val baseWeight = update.state.weight
    if (slot.substitutes.isNotEmpty()) {
        val at = formatLb(roundDownToAvailable(baseWeight * 0.85, availableFor(slot, request.settings)))
        prompts += Prompt(
            PromptKind.SUBSTITUTE,
            "Stop ${update.state.exerciseName}. Substitute at about 85% ($at), same slot: " +
                slot.substitutes.joinToString(", ") + ". No load increase for 2 sessions.",
        )
    }
    prompts += Prompt(PromptKind.EARLY_DELOAD, "Pain flag ${request.log.painFlag} starts an early deload.")
    val recent = request.recentModeratePainDates.count { date ->
        val days = ChronoUnit.DAYS.between(date, request.date)
        days in 0..14
    }
    if (recent + 1 >= 2) {
        prompts += Prompt(PromptKind.DOCTOR, DOCTOR_PROMPT)
    }
    return update.copy(prompts = prompts)
}

internal fun performedWeight(request: EvalRequest): Double {
    val logged = request.log.sets.filter { it.weight > 0.0 }
    if (logged.isEmpty()) return request.state.weight
    return snapLb(logged.minOf { it.weight })
}

private fun averageRir(sets: List<SetLog>): Double? {
    if (sets.isEmpty()) return null
    return sets.map { it.rir.coerceIn(0, 5) }.average()
}

private fun tenPercentBecauseFailure(sets: List<SetLog>): Boolean = sets.any { it.rir == 0 }

private fun atMaxImplement(weight: Double, available: List<Double>): Boolean {
    val max = available.maxOrNull() ?: return false
    return weight + 0.05 >= max
}

private fun isGoblet(slot: SlotDefinition): Boolean {
    return slot.kind == SlotKind.GOBLET_SQUAT || slot.kind == SlotKind.PAUSE_GOBLET_SQUAT
}

internal fun deloadSuggestion(slot: SlotDefinition, state: SlotState, settings: EngineSettings): Suggestion {
    val sets = if (slot.isCarry) {
        minOf(2, slot.nSets).coerceAtLeast(1)
    } else {
        ceil(slot.nSets * 0.6).toInt().coerceAtLeast(1)
    }
    val weight = if (!slot.tracksLoad || slot.equipment == Equipment.BODYWEIGHT) {
        0.0
    } else if (slot.isCarry) {
        state.weight
    } else {
        roundNearestAvailable(state.weight * 0.90, availableFor(slot, settings))
    }
    val targets = if (slot.isCarry) {
        val seconds = roundToStep(slot.repHi * 0.70, 5).coerceAtLeast(slot.repStep)
        List(sets) { seconds }
    } else if (!slot.tracksLoad) {
        List(sets) { slot.repLo }
    } else {
        List(sets) { slot.repLo }
    }
    val reason = if (slot.isCarry) {
        "Deload: $sets sets, same load ${formatLb(state.weight)}, about 70% time (${formatReps(targets)} s), RIR 4+."
    } else if (!slot.tracksLoad) {
        "Deload: easy practice only. This slot is not loaded."
    } else {
        "Deload: $sets sets at 90% (${formatLb(weight)}), reps ${slot.repLo}–${slot.repLo + 2}, RIR 4+."
    }
    return Suggestion(
        weight = weight,
        targetReps = targets,
        sets = sets,
        targetRirLabel = "4+",
        targetRirGate = 4,
        reason = reason,
        deload = true,
    )
}

private fun roundToStep(value: Double, step: Int): Int {
    if (step <= 0) return kotlin.math.round(value).toInt()
    val nearest = kotlin.math.round(value / step).toInt()
    return nearest * step
}

internal fun workingSuggestion(slot: SlotDefinition, state: SlotState, target: RirTarget): Suggestion {
    val reps = if (state.targetReps.size == slot.nSets) state.targetReps else List(slot.nSets) { slot.repLo }
    return Suggestion(
        weight = state.weight,
        targetReps = reps,
        sets = slot.nSets,
        targetRirLabel = target.label,
        targetRirGate = target.gate,
        reason = "Planned weight ${formatLb(state.weight)}, target ${formatReps(reps)}, RIR ${target.label}.",
        deload = false,
    )
}

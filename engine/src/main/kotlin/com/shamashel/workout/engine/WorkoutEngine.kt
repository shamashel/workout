package com.shamashel.workout.engine

object WorkoutEngine {
    fun initialState(slot: SlotDefinition, weight: Double): SlotState {
        return SlotState(
            slotId = slot.id,
            exerciseName = slot.name,
            weight = snapLb(weight),
            targetReps = List(slot.nSets) { slot.repLo },
        )
    }

    fun prescription(slot: SlotDefinition, state: SlotState, clock: ProgramClock, settings: EngineSettings): Suggestion {
        val target = rirTarget(slot, clock.weekOfBlock, clock.deloadActive)
        return if (target.deload) {
            deloadSuggestion(slot, state, settings)
        } else {
            workingSuggestion(slot, state, target)
        }
    }

    fun evaluate(request: EvalRequest): SessionResult {
        val slot = request.slot
        val target = rirTarget(slot, request.weekOfBlock, request.deloadActive)
        if (request.log.painFlag >= 3) {
            return medicalStop(request, target)
        }
        if (target.deload) {
            return deloadLogged(request, target)
        }
        if (!slot.tracksLoad) {
            return bodyweight(request, target)
        }

        val worked = evaluateWorking(request, target)
        val pained = decoratePain(request, applyBlockedIncrease(request, worked))
        val suggestion = workingSuggestion(slot, pained.state, rirTarget(slot, request.weekOfBlock, false))
            .copy(reason = pained.reason, targetRirLabel = target.label, targetRirGate = target.gate)
        val following = if (request.weekOfBlock == 3 && !request.deloadActive) {
            deloadSuggestion(slot, pained.state, request.settings)
        } else {
            null
        }
        return SessionResult(
            state = pained.state,
            decision = pained.decision,
            reason = pained.reason,
            suggestion = suggestion.copy(reason = pained.reason),
            prompts = pained.prompts,
            earlyDeloadReasons = painEarlyReasons(request),
            secondConsecutiveMiss = pained.secondConsecutiveMiss,
            loadIncreased = pained.loadIncreased,
            stopSession = false,
            medicalMessage = null,
            targetRirLabel = target.label,
            nextRirLabel = nextSessionRir(slot, request.weekOfBlock, request.deloadActive).label,
            followingDeload = following,
        )
    }

    fun evaluateDay(requests: List<EvalRequest>): List<SessionResult> {
        val sorted = requests.sortedBy { it.slot.sortOrder }
        val results = mutableListOf<SessionResult>()
        var hingeIncreased = false
        for (request in sorted) {
            val flagged = if (hingeIncreased && request.slot.isCarry) {
                request.copy(suppressCarryIncrease = true)
            } else {
                request
            }
            val result = evaluate(flagged)
            if (request.slot.usesTrapBarRir && result.loadIncreased) {
                hingeIncreased = true
            }
            results += result
        }
        return results
    }

    fun applySubstitute(
        slot: SlotDefinition,
        state: SlotState,
        settings: EngineSettings,
        substituteName: String,
    ): SlotState {
        val available = availableFor(slot, settings)
        val reduced = if (state.weight <= 0.0 || available.isEmpty()) {
            state.weight
        } else {
            roundDownToAvailable(state.weight * 0.85, available)
        }
        return state.copy(
            exerciseName = substituteName,
            weight = reduced,
            targetReps = List(slot.nSets) { slot.repLo },
            missCount = 0,
            sessionsAtWeight = 0,
            extensionUsed = false,
            loadBlockSessions = maxOf(state.loadBlockSessions, 2),
            unchangedStreak = 0,
            stallSessions = 0,
            ceilingStreak = 0,
            preferMicro = false,
            lastLoggedReps = null,
            lastLoggedWeight = null,
            lastLoggedTotalReps = null,
        )
    }

    fun resetMisses(state: SlotState): SlotState = state.copy(missCount = 0)
}

private fun medicalStop(request: EvalRequest, target: RirTarget): SessionResult {
    return SessionResult(
        state = request.state,
        decision = Decision.NO_COACHING,
        reason = MEDICAL_STOP_MESSAGE,
        suggestion = null,
        prompts = emptyList(),
        earlyDeloadReasons = listOf("Pain flag 3 on ${request.slot.id}. Session stopped."),
        secondConsecutiveMiss = false,
        loadIncreased = false,
        stopSession = true,
        medicalMessage = MEDICAL_STOP_MESSAGE,
        targetRirLabel = target.label,
        nextRirLabel = "",
    )
}

private fun deloadLogged(request: EvalRequest, target: RirTarget): SessionResult {
    val suggestion = workingSuggestion(request.slot, request.state, target).copy(
        reason = "Deload logged. Working weight stays ${formatLb(request.state.weight)} for the next block.",
        deload = true,
    )
    val pained = if (request.log.painFlag >= 2) {
        decoratePain(
            request,
            WorkingUpdate(
                state = request.state,
                decision = Decision.DELOAD_LOGGED,
                reason = suggestion.reason,
                loadIncreased = false,
                secondConsecutiveMiss = false,
                prompts = emptyList(),
            ),
        )
    } else {
        null
    }
    return SessionResult(
        state = request.state,
        decision = Decision.DELOAD_LOGGED,
        reason = suggestion.reason,
        suggestion = suggestion,
        prompts = pained?.prompts ?: emptyList(),
        earlyDeloadReasons = painEarlyReasons(request),
        secondConsecutiveMiss = false,
        loadIncreased = false,
        stopSession = false,
        medicalMessage = null,
        targetRirLabel = "4+",
        nextRirLabel = nextSessionRir(request.slot, request.weekOfBlock, true).label,
    )
}

private fun bodyweight(request: EvalRequest, target: RirTarget): SessionResult {
    val base = WorkingUpdate(
        state = request.state,
        decision = Decision.BODYWEIGHT_LOGGED,
        reason = "Logged. ${request.slot.name} is not progressed by load.",
        loadIncreased = false,
        secondConsecutiveMiss = false,
        prompts = emptyList(),
    )
    val decorated = decoratePain(request, base)
    return SessionResult(
        state = request.state.copy(loadBlockSessions = maxOf(request.state.loadBlockSessions, if (request.log.painFlag >= 2) 2 else 0)),
        decision = Decision.BODYWEIGHT_LOGGED,
        reason = decorated.reason,
        suggestion = workingSuggestion(request.slot, request.state, target).copy(reason = decorated.reason),
        prompts = decorated.prompts,
        earlyDeloadReasons = painEarlyReasons(request),
        secondConsecutiveMiss = false,
        loadIncreased = false,
        stopSession = false,
        medicalMessage = null,
        targetRirLabel = target.label,
        nextRirLabel = nextSessionRir(request.slot, request.weekOfBlock, request.deloadActive).label,
    )
}

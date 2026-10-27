package com.shamashel.workout.data

import androidx.room.withTransaction
import com.shamashel.workout.engine.Decision
import com.shamashel.workout.engine.EngineSettings
import com.shamashel.workout.engine.EvalRequest
import com.shamashel.workout.engine.PainEvent
import com.shamashel.workout.engine.ProgramClock
import com.shamashel.workout.engine.SAFETY_COPY
import com.shamashel.workout.engine.SessionLog
import com.shamashel.workout.engine.SetLog
import com.shamashel.workout.engine.SlotDefinition
import com.shamashel.workout.engine.SlotNumberOverride
import com.shamashel.workout.engine.WeekSignals
import com.shamashel.workout.engine.WeeklyScore
import com.shamashel.workout.engine.WorkoutEngine
import com.shamashel.workout.engine.Year1Plan
import com.shamashel.workout.engine.YearOutline
import com.shamashel.workout.engine.activateEarlyDeload
import com.shamashel.workout.engine.applySlotOverrides
import com.shamashel.workout.engine.formatLb
import com.shamashel.workout.engine.formatReps
import com.shamashel.workout.engine.immediateDeloadReasons
import com.shamashel.workout.engine.onCheckIn
import com.shamashel.workout.engine.withMicroPlates
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class WorkoutRepository(private val db: WorkoutDatabase) {
    private val dao = db.dao()
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val dateFormat = DateTimeFormatter.ofPattern("MMM d", Locale.US)

    suspend fun bootstrap() {
        if (dao.program() != null) return
        val today = LocalDate.now().toEpochDay()
        db.withTransaction {
            dao.saveSlots(Year1Plan.slots.map { it.toEntity() })
            dao.saveStates(Year1Plan.slots.map { WorkoutEngine.initialState(it, 0.0).toEntity() })
            dao.saveProgram(
                ProgramEntity(
                    blockIndex = 1,
                    weekOfBlock = 1,
                    deloadActive = false,
                    deloadReason = null,
                    year = 1,
                    safetyAcknowledged = false,
                    week0Passes = 0,
                    secondMissSlots = "",
                    sessionReadiness = "",
                    weekStartedEpochDay = today,
                ),
            )
            dao.saveSettings(EngineSettings().toEntity(writeHealthConnect = false))
            Year1Plan.outlines.forEach { outline ->
                dao.savePhase(PhaseNoteEntity(outline.year, outline.title, outline.body))
            }
        }
    }

    suspend fun acknowledgeSafety() {
        val program = requireProgram()
        dao.saveProgram(program.copy(safetyAcknowledged = true))
    }

    suspend fun home(selectedDay: Char?): HomeSnapshot {
        val today = LocalDate.now()
        val program = requireProgram()
        val clock = program.toClock()
        val settings = engineSettings()
        val slots = definitions()
        val states = dao.states().associate { it.slotId to it.toState() }
        val drafts = dao.drafts().map { it.slotId }.toSet()
        val loggedToday = dao.sessionsSince(today.toEpochDay()).map { it.slotId }.toSet()
        val day = selectedDay ?: scheduledDay(today)
        val daySlots = slots.filter { it.day == day }
        val cards = daySlots.map { slot ->
            val state = states.getValue(slot.id)
            val rx = WorkoutEngine.prescription(slot, state, clock, settings)
            TodaySlot(
                id = slot.id,
                name = state.exerciseName,
                optional = slot.isOptional,
                perSide = slot.perSide,
                isCarry = slot.isCarry,
                tracksLoad = slot.tracksLoad,
                notes = slot.notes,
                prescription = if (slot.tracksLoad && state.weight > 0.0) {
                    val effort = if (slot.isCarry) "sec" else "reps"
                    "${rx.sets} sets · ${formatLb(rx.weight)} · ${formatReps(rx.targetReps).replace(" / ", ", ")} $effort · RIR ${rx.targetRirLabel}"
                } else if (!slot.tracksLoad) {
                    val effort = if (slot.repStep > 1) "seconds" else "reps"
                    "${slot.nSets} sets · ${slot.repLo} $effort · bodyweight"
                } else {
                    "Starting weight not set"
                },
                rirLabel = rx.targetRirLabel,
                sets = rx.sets,
                weight = if (rx.deload) rx.weight else state.weight,
                targetReps = rx.targetReps,
                drafted = slot.id in drafts,
                loggedToday = slot.id in loggedToday,
            )
        }
        val needsWeights = slots.filter { it.tracksLoad && !it.isOptional }.any { (states[it.id]?.weight ?: 0.0) <= 0.0 }
        val weekSessions = dao.sessionsSince(program.weekStartedEpochDay)
        val daysLogged = weekSessions.map { it.day }.toSet()
        val checkIn = dao.latestCheckIn()
        val checkInDue = daysLogged.containsAll(setOf("A", "B", "C")) &&
            (checkIn == null || checkIn.epochDay < program.weekStartedEpochDay)
        val rir = if (daySlots.isEmpty()) "" else cards.firstOrNull { it.tracksLoad }?.rirLabel.orEmpty()
        return HomeSnapshot(
            day = day,
            dateLabel = today.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.US)),
            headline = dayTitle(day),
            weekLabel = "Block ${clock.blockIndex} · week ${clock.weekOfBlock.coerceAtMost(4)} of 4",
            rirNote = if (clock.deloadActive || clock.weekOfBlock >= 4) {
                "Deload week. Easy sets, target RIR 4+."
            } else {
                "Target RIR this week: $rir. If you're unsure, pick the higher RIR."
            },
            deloadBanner = if (clock.deloadActive || clock.weekOfBlock >= 4) {
                clock.deloadReason ?: "Scheduled deload, week 4 of the block."
            } else {
                null
            },
            slots = cards,
            needsWeights = needsWeights,
            checkInDue = checkInDue,
            showSafety = !program.safetyAcknowledged,
            week0Passes = program.week0Passes,
            isRestDay = day !in listOf('A', 'B', 'C'),
            restNote = restNote(day),
        )
    }

    suspend fun editorSeed(slotId: String): TodaySlot {
        return home(definitions().first { it.id == slotId }.day).slots.first { it.id == slotId }
    }

    suspend fun saveDraft(slotId: String, sets: List<DraftSet>, pain: Int, note: String, posture: Boolean) {
        val payload = json.encodeToString(sets.map { DraftSetDto(it.weight, it.reps, it.rir) })
        dao.saveDraft(DraftEntity(slotId, payload, pain, note, posture))
    }

    suspend fun loadDraft(slotId: String): DraftEntity? = dao.drafts().firstOrNull { it.slotId == slotId }

    fun decodeDraft(raw: String): List<DraftSetDto> = json.decodeFromString(raw)

    suspend fun finishDay(day: Char, durationMin: Int, readiness: Int?): FinishOutcome {
        val today = LocalDate.now()
        return db.withTransaction {
            finishInside(day, today, durationMin, readiness)
        }
    }

    private suspend fun finishInside(day: Char, today: LocalDate, durationMin: Int, readiness: Int?): FinishOutcome {
        val program = requireProgram()
        var clock = program.toClock()
        val settings = engineSettings()
        val slots = definitions().filter { it.day == day }.associateBy { it.id }
        val states = dao.states().associate { it.slotId to it.toState() }.toMutableMap()
        val drafts = dao.drafts().filter { slots.containsKey(it.slotId) }.sortedBy { slots.getValue(it.slotId).sortOrder }
        val rows = mutableListOf<SummaryRow>()
        var hingeUp = false
        var medical: String? = null
        val processed = mutableListOf<String>()
        var signals = signalsOf(program).copy(
            painEvents = dao.sessionsSince(today.minusDays(7).toEpochDay())
                .filter { it.painFlag >= 2 }
                .map { PainEvent(it.slotId, it.painFlag, LocalDate.ofEpochDay(it.epochDay)) },
        )
        for (draft in drafts) {
            val slot = slots.getValue(draft.slotId)
            val sets = decodeDraft(draft.setsJson).map { SetLog(it.reps, it.rir, it.weight) }
            val log = SessionLog(sets, draft.painFlag, if (slot.isCarry) draft.postureClean else null, draft.note)
            if (draft.painFlag >= 3) {
                val sessionId = dao.insertSession(
                    SessionEntity(
                        epochDay = today.toEpochDay(),
                        slotId = slot.id,
                        day = day.toString(),
                        painFlag = 3,
                        note = draft.note,
                        postureClean = log.postureClean,
                        deload = clock.deloadActive || clock.weekOfBlock >= 4,
                        decision = Decision.NO_COACHING.name,
                        reason = com.shamashel.workout.engine.MEDICAL_STOP_MESSAGE,
                        loadIncreased = false,
                        performedWeight = sets.minOfOrNull { it.weight } ?: 0.0,
                    ),
                )
                dao.insertSets(sets.mapIndexed { index, set -> set.toEntity(sessionId, index + 1) })
                signals = signals.copy(painEvents = signals.painEvents + PainEvent(slot.id, 3, today))
                clock = activateEarlyDeload(clock, "Pain flag 3 on ${slot.id}. Session stopped.")
                medical = com.shamashel.workout.engine.MEDICAL_STOP_MESSAGE
                processed += slot.id
                rows += SummaryRow(
                    slotId = slot.id,
                    name = states.getValue(slot.id).exerciseName,
                    reason = medical,
                    weightLabel = "",
                    repsLabel = "",
                    rirLabel = "",
                    prompts = emptyList(),
                    suggestedWeight = 0.0,
                    suggestedReps = emptyList(),
                    coaching = false,
                )
                break
            }
            val priorPain = dao.moderatePainBefore(slot.id, today.minusDays(14).toEpochDay(), today.toEpochDay())
                .map { LocalDate.ofEpochDay(it.epochDay) }
            val result = WorkoutEngine.evaluate(
                EvalRequest(
                    slot = slot,
                    state = states.getValue(slot.id),
                    log = log,
                    settings = settings,
                    date = today,
                    weekOfBlock = clock.weekOfBlock,
                    deloadActive = clock.deloadActive,
                    suppressCarryIncrease = hingeUp && slot.isCarry,
                    recentModeratePainDates = priorPain,
                ),
            )
            val sessionId = dao.insertSession(
                SessionEntity(
                    epochDay = today.toEpochDay(),
                    slotId = slot.id,
                    day = day.toString(),
                    painFlag = draft.painFlag,
                    note = draft.note,
                    postureClean = log.postureClean,
                    deload = result.decision == Decision.DELOAD_LOGGED || clock.deloadActive,
                    decision = result.decision.name,
                    reason = result.reason,
                    loadIncreased = result.loadIncreased,
                    performedWeight = sets.minOfOrNull { it.weight } ?: states.getValue(slot.id).weight,
                ),
            )
            dao.insertSets(sets.mapIndexed { index, set -> set.toEntity(sessionId, index + 1) })
            dao.saveState(result.state.toEntity())
            states[slot.id] = result.state
            if (slot.usesTrapBarRir && result.loadIncreased) hingeUp = true
            if (result.secondConsecutiveMiss) {
                signals = signals.copy(secondMissSlots = signals.secondMissSlots + slot.id)
            }
            if (draft.painFlag > 0) {
                signals = signals.copy(painEvents = signals.painEvents + PainEvent(slot.id, draft.painFlag, today))
            }
            result.earlyDeloadReasons.forEach { reason ->
                clock = activateEarlyDeload(clock, reason)
            }
            val deloadNote = result.followingDeload?.let { listOf("Next week is a deload: ${it.reason}") }.orEmpty()
            rows += SummaryRow(
                slotId = slot.id,
                name = result.state.exerciseName,
                reason = result.reason,
                weightLabel = formatLb(result.state.weight),
                repsLabel = formatReps(result.state.targetReps),
                rirLabel = result.nextRirLabel,
                prompts = result.prompts.map { it.message } + deloadNote,
                suggestedWeight = result.state.weight,
                suggestedReps = result.state.targetReps,
                coaching = true,
            )
            processed += slot.id
        }
        if (readiness != null) {
            signals = signals.copy(sessionReadiness = signals.sessionReadiness + readiness)
        }
        val immediate = immediateDeloadReasons(signals, today)
        immediate.forEach { clock = activateEarlyDeload(clock, it) }
        dao.deleteDrafts(processed)
        dao.insertDay(
            WorkoutDayEntity(
                epochDay = today.toEpochDay(),
                day = day.toString(),
                durationMin = durationMin,
                readiness = readiness,
                wroteHealthConnect = false,
                deload = clock.deloadActive,
            ),
        )
        dao.saveProgram(program.copy(
            blockIndex = clock.blockIndex,
            weekOfBlock = clock.weekOfBlock,
            deloadActive = clock.deloadActive,
            deloadReason = clock.deloadReason,
            secondMissSlots = signals.secondMissSlots.joinToString(","),
            sessionReadiness = signals.sessionReadiness.joinToString(","),
        ))
        return FinishOutcome(
            rows = rows,
            medicalMessage = medical,
            deloadBanner = clock.deloadReason,
            healthNote = null,
        )
    }

    suspend fun overrideNext(slotId: String, weight: Double, reps: List<Int>) {
        val state = dao.states().first { it.slotId == slotId }.toState()
        dao.insertOverride(
            OverrideEntity(
                epochMillis = System.currentTimeMillis(),
                slotId = slotId,
                suggestedWeight = state.weight,
                chosenWeight = weight,
                suggestedReps = state.targetReps.joinToString(","),
                chosenReps = reps.joinToString(","),
            ),
        )
        dao.saveState(state.copy(weight = weight, targetReps = reps).toEntity())
    }

    suspend fun applySubstitute(slotId: String, name: String) {
        val slot = definitions().first { it.id == slotId }
        val state = dao.states().first { it.slotId == slotId }.toState()
        val next = WorkoutEngine.applySubstitute(slot, state, engineSettings(), name)
        dao.saveState(next.toEntity())
    }

    suspend fun startingSlots(): List<StartingSlot> {
        val states = dao.states().associateBy { it.slotId }
        return definitions().filter { it.tracksLoad }.map { slot ->
            StartingSlot(
                id = slot.id,
                name = slot.name,
                hint = "${slot.repLo}–${slot.repHi}${if (slot.isCarry) " s" else " reps"} · ${slot.nSets} sets",
                weight = states[slot.id]?.weight ?: 0.0,
            )
        }
    }

    suspend fun saveStartingWeights(weights: Map<String, Double>) {
        val slots = definitions().associateBy { it.id }
        weights.forEach { (id, weight) ->
            val slot = slots[id] ?: return@forEach
            val current = dao.states().first { it.slotId == id }.toState()
            val fresh = if (current.sessionsAtWeight == 0 && current.lastLoggedReps == null) {
                WorkoutEngine.initialState(slot, weight)
            } else {
                current.copy(weight = weight)
            }
            dao.saveState(fresh.toEntity())
        }
    }

    suspend fun recordPracticePass() {
        val program = requireProgram()
        dao.saveProgram(program.copy(week0Passes = (program.week0Passes + 1).coerceAtMost(2)))
    }

    suspend fun submitCheckIn(soreness: Int, performance: Int, readiness: Int, skipChoice: Boolean?): CheckInOutcome {
        val today = LocalDate.now()
        return db.withTransaction {
            val program = requireProgram()
            val clock = program.toClock()
            val score = WeeklyScore(soreness, performance, readiness)
            val signals = signalsOf(program)
            val previous = dao.checkInsForBlock(clock.blockIndex)
                .lastOrNull { it.weekOfBlock == clock.weekOfBlock - 1 }
                ?.let { WeeklyScore(it.soreness, it.performance, it.readiness) }
            val pain = dao.sessionsSince(today.minusDays(14).toEpochDay()).any { it.painFlag > 0 }
            val advance = onCheckIn(
                clock = clock,
                score = score,
                signals = signals,
                settings = engineSettings(),
                today = today,
                previousWeekScore = previous,
                anyPainLastTwoWeeks = pain,
                skipChoice = skipChoice,
            )
            if (advance.offerSkip) {
                CheckInOutcome(offerSkip = true, messages = advance.messages)
            } else {
                dao.insertCheckIn(
                    CheckInEntity(
                        epochDay = today.toEpochDay(),
                        blockIndex = clock.blockIndex,
                        weekOfBlock = clock.weekOfBlock,
                        soreness = soreness,
                        performance = performance,
                        readiness = readiness,
                        skippedDeload = skipChoice == true,
                    ),
                )
                if (advance.resetMissCounts) {
                    dao.states().forEach { entity ->
                        dao.saveState(WorkoutEngine.resetMisses(entity.toState()).toEntity())
                    }
                }
                dao.saveProgram(
                    program.copy(
                        blockIndex = advance.clock.blockIndex,
                        weekOfBlock = advance.clock.weekOfBlock,
                        deloadActive = advance.clock.deloadActive,
                        deloadReason = advance.clock.deloadReason,
                        secondMissSlots = "",
                        sessionReadiness = "",
                        weekStartedEpochDay = today.toEpochDay(),
                    ),
                )
                CheckInOutcome(offerSkip = false, messages = advance.messages)
            }
        }
    }

    suspend fun history(slotId: String): HistorySnapshot {
        val name = dao.states().firstOrNull { it.slotId == slotId }?.exerciseName ?: slotId
        val points = dao.sessionsForSlot(slotId).map { session ->
            val sets = dao.setsForSession(session.id)
            val top = sets.maxByOrNull { it.reps } ?: sets.firstOrNull()
            val reps = top?.reps ?: 0
            val rir = top?.rir ?: 0
            HistoryPoint(
                dateLabel = LocalDate.ofEpochDay(session.epochDay).format(dateFormat),
                weight = session.performedWeight,
                reps = reps,
                estimatedReps = reps + rir,
                rirLabel = if (rir >= 5) "5+" else rir.toString(),
                deload = session.deload,
                pain = session.painFlag,
                reason = session.reason,
            )
        }
        return HistorySnapshot(slotId, name, points)
    }

    suspend fun slotChoices(): List<Pair<String, String>> {
        return dao.states().map { it.slotId to it.exerciseName }.sortedBy { it.first }
    }

    suspend fun engineSettings(): EngineSettings = (dao.settings() ?: EngineSettings().toEntity(false)).toEngine()

    suspend fun settingsEntity(): SettingsEntity = dao.settings() ?: EngineSettings().toEntity(false)

    suspend fun saveSettings(entity: SettingsEntity) {
        dao.saveSettings(entity)
    }

    suspend fun phases(): List<YearOutline> {
        val stored = dao.phases()
        return if (stored.isEmpty()) {
            Year1Plan.outlines
        } else {
            stored.map { YearOutline(it.year, it.title, it.body) }
        }
    }

    suspend fun savePhase(year: Int, title: String, body: String) {
        dao.savePhase(PhaseNoteEntity(year, title, body))
    }

    suspend fun saveYear1Override(slotId: String, repLo: Int, repHi: Int, sets: Int, name: String) {
        dao.saveSlotOverride(SlotOverrideEntity(slotId, repLo, repHi, sets, name))
        val base = Year1Plan.slot(slotId)
        val updated = applySlotOverrides(
            listOf(base),
            listOf(SlotNumberOverride(slotId, repLo, repHi, sets, name.ifBlank { null })),
        ).first()
        val existing = dao.slots().filter { it.id != slotId }
        dao.saveSlots(existing + updated.toEntity())
    }

    suspend fun logSwim(durationMin: Int, pool: Boolean, wrote: Boolean) {
        dao.insertSwim(
            SwimEntity(
                epochDay = LocalDate.now().toEpochDay(),
                durationMin = durationMin,
                pool = pool,
                wroteHealthConnect = wrote,
            ),
        )
    }

    suspend fun exportJson(): String {
        val bundle = BackupBundle(
            version = 1,
            program = dao.program(),
            settings = dao.settings(),
            slots = dao.slots(),
            states = dao.states(),
            sessions = dao.allSessions(),
            sets = dao.allSets(),
            checkIns = dao.allCheckIns(),
            overrides = dao.allOverrides(),
            days = dao.allDays(),
            swims = dao.allSwims(),
            phases = dao.phases(),
            slotOverrides = dao.slotOverrides(),
            drafts = dao.drafts(),
        )
        return json.encodeToString(bundle)
    }

    suspend fun importJson(raw: String) {
        val bundle = json.decodeFromString<BackupBundle>(raw)
        require(bundle.version == 1) { "This backup is version ${bundle.version}. This app reads version 1." }
        db.withTransaction {
            dao.clearSlots()
            dao.clearStates()
            dao.clearDrafts()
            dao.clearSessions()
            dao.clearSets()
            dao.clearCheckIns()
            dao.clearOverrides()
            dao.clearDays()
            dao.clearSwims()
            dao.clearPhases()
            dao.clearSlotOverrides()
            bundle.program?.let { dao.saveProgram(it) }
            bundle.settings?.let { dao.saveSettings(it) }
            dao.saveSlots(bundle.slots)
            dao.saveStates(bundle.states)
            dao.insertSessions(bundle.sessions)
            dao.insertSets(bundle.sets)
            dao.insertCheckIns(bundle.checkIns)
            dao.insertOverrides(bundle.overrides)
            dao.insertDays(bundle.days)
            dao.insertSwims(bundle.swims)
            bundle.phases.forEach { dao.savePhase(it) }
            bundle.slotOverrides.forEach { dao.saveSlotOverride(it) }
            bundle.drafts.forEach { dao.saveDraft(it) }
        }
    }

    private suspend fun definitions(): List<SlotDefinition> {
        val stored = dao.slots()
        val base = if (stored.isEmpty()) Year1Plan.slots else stored.map { it.toDefinition() }
        val overrides = dao.slotOverrides().map {
            SlotNumberOverride(it.slotId, it.repLo, it.repHi, it.nSets, it.name)
        }
        return applySlotOverrides(base, overrides)
    }

    private suspend fun requireProgram(): ProgramEntity {
        return dao.program() ?: error("Workout plan is not seeded")
    }

    private fun signalsOf(program: ProgramEntity): WeekSignals {
        return WeekSignals(
            secondMissSlots = parseIdSet(program.secondMissSlots),
            sessionReadiness = parseReadiness(program.sessionReadiness),
        )
    }

    private fun SetLog.toEntity(sessionId: Long, number: Int): SetEntity {
        return SetEntity(
            sessionId = sessionId,
            setNumber = number,
            weight = weight,
            reps = reps,
            rir = rir,
        )
    }
}

private fun scheduledDay(date: LocalDate): Char {
    return when (date.dayOfWeek) {
        java.time.DayOfWeek.MONDAY -> 'A'
        java.time.DayOfWeek.WEDNESDAY -> 'B'
        java.time.DayOfWeek.FRIDAY -> 'C'
        java.time.DayOfWeek.TUESDAY, java.time.DayOfWeek.THURSDAY -> 'S'
        java.time.DayOfWeek.SATURDAY -> 'W'
        java.time.DayOfWeek.SUNDAY -> 'R'
    }
}

private fun dayTitle(day: Char): String {
    return when (day) {
        'A' -> "Day A · Squat, push, pull, carry"
        'B' -> "Day B · Hinge, push, pull, carry"
        'C' -> "Day C · Pause squat, hinge, pull"
        'S' -> "Swim day"
        'W' -> "Long easy walk"
        'R' -> "Rest"
        else -> "Day $day"
    }
}

private fun restNote(day: Char): String? {
    return when (day) {
        'S' -> "Easy swim. Log the minutes if you want them in Health Connect. Strength is tomorrow."
        'W' -> "Long easy walk. No lifting."
        'R' -> "Rest. The next strength day is Monday, Day A."
        else -> null
    }
}

@Serializable
data class DraftSetDto(val weight: Double, val reps: Int, val rir: Int)

@Serializable
data class BackupBundle(
    val version: Int,
    val program: ProgramEntity? = null,
    val settings: SettingsEntity? = null,
    val slots: List<SlotEntity> = emptyList(),
    val states: List<SlotStateEntity> = emptyList(),
    val sessions: List<SessionEntity> = emptyList(),
    val sets: List<SetEntity> = emptyList(),
    val checkIns: List<CheckInEntity> = emptyList(),
    val overrides: List<OverrideEntity> = emptyList(),
    val days: List<WorkoutDayEntity> = emptyList(),
    val swims: List<SwimEntity> = emptyList(),
    val phases: List<PhaseNoteEntity> = emptyList(),
    val slotOverrides: List<SlotOverrideEntity> = emptyList(),
    val drafts: List<DraftEntity> = emptyList(),
)

const val SAFETY_LINE = SAFETY_COPY

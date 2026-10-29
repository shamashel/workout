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
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val backupJson = Json { ignoreUnknownKeys = true; prettyPrint = true }

const val BACKUP_SCHEMA_VERSION = 1

class WorkoutRepository(
    private val db: WorkoutDatabase,
    private val onDataChanged: () -> Unit = {},
    private val clock: () -> LocalDate = { LocalDate.now() },
) {
    private val dao = db.dao()
    private val json = backupJson
    private val dateFormat = DateTimeFormatter.ofPattern("MMM d", Locale.US)

    suspend fun bootstrap() {
        if (dao.program() == null) {
            val today = clock().toEpochDay()
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
        ensureCurrentPlan()
    }

    /**
     * Moves logs off slot ids whose exercise changed, then writes the current
     * Year 1 definitions. Progression rows are left in place.
     */
    internal suspend fun ensureCurrentPlan() {
        db.withTransaction {
            remapLegacySlots()
            for (plan in Year1Plan.slots) {
                dao.saveSlots(listOf(plan.toEntity()))
                if (dao.states().none { it.slotId == plan.id }) {
                    dao.saveState(WorkoutEngine.initialState(plan, 0.0).toEntity())
                }
            }
            val deadlift = dao.states().firstOrNull { it.slotId == "B1" }
            if (deadlift != null && deadlift.exerciseName == "Trap-bar deadlift") {
                dao.saveState(deadlift.copy(exerciseName = Year1Plan.slot("B1").name))
            }
            val year2 = dao.phases().firstOrNull { it.year == 2 }
            val outline = Year1Plan.outlines.first { it.year == 2 }
            if (year2 != null && year2.body.contains("step-up")) {
                dao.savePhase(PhaseNoteEntity(outline.year, outline.title, outline.body))
            }
        }
    }

    private suspend fun remapLegacySlots() {
        val stepUp = dao.slot("C4")
        if (stepUp != null && stepUp.kind == "STEP_UP" && dao.slot("C4s") == null) {
            renameSlot("C4", "C4s")
            dao.setSlotDay("C4s", "H")
        }
        val hold = dao.slot("C5")
        if (hold != null && hold.kind == "DEEP_SQUAT_HOLD" && dao.slot("C4") == null) {
            renameSlot("C5", "C4")
        }
        val plank = dao.slot("B5")
        if (plank != null && plank.kind == "PLANK" && dao.slot("B6") == null) {
            renameSlot("B5", "B6")
        }
    }

    private suspend fun renameSlot(from: String, to: String) {
        dao.renameSlotRow(from, to)
        dao.renameState(from, to)
        dao.renameDraft(from, to)
        dao.renameSessions(from, to)
        dao.renameOverrides(from, to)
        dao.renameSlotOverride(from, to)
        val program = dao.program() ?: return
        val misses = remapSlotToken(program.secondMissSlots, from, to)
        if (misses != program.secondMissSlots) {
            dao.saveProgram(program.copy(secondMissSlots = misses))
        }
    }

    suspend fun acknowledgeSafety() {
        val program = requireProgram()
        dao.saveProgram(program.copy(safetyAcknowledged = true))
    }

    suspend fun home(): HomeSnapshot {
        val today = clock()
        val stored = settingsEntity()
        val schedule = WeekSchedule.decode(stored.weekSchedule)
        val usual = schedule.on(today).code
        val showing = effectiveDay(stored, today)
        return buildHome(showing, today, usual, showing != usual)
    }

    suspend fun useDayToday(day: Char) {
        val today = clock()
        val current = settingsEntity()
        val usual = WeekSchedule.decode(current.weekSchedule).on(today).code
        if (day == usual) {
            saveSettings(current.copy(dayOverrideEpoch = null, dayOverride = null))
        } else {
            saveSettings(current.copy(dayOverrideEpoch = today.toEpochDay(), dayOverride = day.toString()))
        }
    }

    suspend fun clearTodayOverride() {
        val current = settingsEntity()
        saveSettings(current.copy(dayOverrideEpoch = null, dayOverride = null))
    }

    suspend fun startFreshFromToday() {
        val today = clock()
        val current = settingsEntity()
        saveSettings(
            current.copy(
                weekSchedule = WeekSchedule.year1.reanchored(today).encode(),
                dayOverrideEpoch = null,
                dayOverride = null,
            ),
        )
    }

    private suspend fun buildHome(
        day: Char,
        today: LocalDate,
        usualDay: Char,
        todayOnly: Boolean,
    ): HomeSnapshot {
        val program = requireProgram()
        val programClock = program.toClock()
        val settings = engineSettings()
        val schedule = WeekSchedule.decode(settingsEntity().weekSchedule)
        val slots = definitions()
        val states = dao.states().associate { it.slotId to it.toState() }
        val drafts = dao.drafts().map { it.slotId }.toSet()
        val loggedToday = dao.sessionsSince(today.toEpochDay()).map { it.slotId }.toSet()
        val showing = WeekdayAssignment.fromCode(day) ?: WeekdayAssignment.REST
        val daySlots = slots.filter { it.day == showing.code }
        val cards = daySlots.map { slot ->
            val state = states.getValue(slot.id)
            val rx = WorkoutEngine.prescription(slot, state, programClock, settings)
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
            day = showing.code,
            dateLabel = today.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.US)),
            headline = headlineFor(showing),
            weekLabel = "Block ${programClock.blockIndex} · week ${programClock.weekOfBlock.coerceAtMost(4)} of 4",
            rirNote = if (programClock.deloadActive || programClock.weekOfBlock >= 4) {
                "Deload week. Easy sets, target RIR 4+."
            } else {
                "Target RIR this week: $rir. If you're unsure, pick the higher RIR."
            },
            deloadBanner = if (programClock.deloadActive || programClock.weekOfBlock >= 4) {
                programClock.deloadReason ?: "Scheduled deload, week 4 of the block."
            } else {
                null
            },
            slots = cards,
            needsWeights = needsWeights,
            checkInDue = checkInDue,
            showSafety = !program.safetyAcknowledged,
            week0Passes = program.week0Passes,
            isRestDay = showing == WeekdayAssignment.REST,
            restNote = restNoteFor(showing, schedule, today),
            usualDay = usualDay,
            todayOnly = todayOnly,
        )
    }

    suspend fun editorSeed(slotId: String): TodaySlot {
        val day = definitions().first { it.id == slotId }.day
        return buildHome(day, clock(), day, false).slots.first { it.id == slotId }
    }

    suspend fun saveDraft(slotId: String, sets: List<DraftSet>, pain: Int, note: String, posture: Boolean) {
        val payload = json.encodeToString(sets.map { DraftSetDto(it.weight, it.reps, it.rir) })
        dao.saveDraft(DraftEntity(slotId, payload, pain, note, posture))
    }

    suspend fun loadDraft(slotId: String): DraftEntity? = dao.drafts().firstOrNull { it.slotId == slotId }

    fun decodeDraft(raw: String): List<DraftSetDto> = json.decodeFromString(raw)

    suspend fun finishDay(day: Char, durationMin: Int, readiness: Int?): FinishOutcome {
        val today = clock()
        val outcome = db.withTransaction {
            finishInside(day, today, durationMin, readiness)
        }
        onDataChanged()
        return outcome
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
            val loggedWeight = sets.map { it.weight }.filter { it > 0.0 }.minOrNull()
            val stored = states.getValue(slot.id)
            if (stored.weight <= 0.0 && loggedWeight != null) {
                val seeded = stored.copy(weight = loggedWeight)
                states[slot.id] = seeded
            }
            if (draft.painFlag >= 3) {
                val held = states.getValue(slot.id)
                if (stored.weight <= 0.0 && loggedWeight != null) {
                    dao.saveState(held.toEntity())
                }
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
                        performedWeight = loggedWeight ?: held.weight,
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
                    performedWeight = loggedWeight ?: states.getValue(slot.id).weight,
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
        val today = clock()
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
        onDataChanged()
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
                epochDay = clock().toEpochDay(),
                durationMin = durationMin,
                pool = pool,
                wroteHealthConnect = wrote,
            ),
        )
    }

    suspend fun exportJson(): String {
        val bundle = BackupBundle(
            version = BACKUP_SCHEMA_VERSION,
            program = dao.program(),
            settings = dao.settings(),
            slots = dao.slots(),
            states = dao.states().sortedBy { it.slotId },
            sessions = dao.allSessions(),
            sets = dao.allSets(),
            checkIns = dao.allCheckIns(),
            overrides = dao.allOverrides(),
            days = dao.allDays(),
            swims = dao.allSwims(),
            phases = dao.phases().sortedBy { it.year },
            slotOverrides = dao.slotOverrides().sortedBy { it.slotId },
            drafts = dao.drafts().sortedBy { it.slotId },
        )
        return json.encodeToString(bundle)
    }

    suspend fun importJson(raw: String) {
        val bundle = decodeBackup(raw)
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
        ensureCurrentPlan()
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

private fun remapSlotToken(raw: String, from: String, to: String): String {
    return raw.split(",")
        .map { token -> if (token.trim() == from) to else token.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(",")
}

private fun effectiveDay(settings: SettingsEntity, date: LocalDate): Char {
    val schedule = WeekSchedule.decode(settings.weekSchedule)
    val usual = schedule.on(date).code
    if (settings.dayOverrideEpoch != date.toEpochDay()) return usual
    val override = settings.dayOverride?.firstOrNull() ?: return usual
    return if (override == 'A' || override == 'B' || override == 'C') override else usual
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

data class BackupPreview(
    val version: Int,
    val sessionCount: Int,
    val firstDay: LocalDate?,
    val lastDay: LocalDate?,
)

fun decodeBackup(raw: String): BackupBundle {
    if (raw.isBlank()) {
        throw IllegalArgumentException("That file isn't a Trades Prep backup.")
    }
    val bundle = try {
        backupJson.decodeFromString<BackupBundle>(raw)
    } catch (_: SerializationException) {
        throw IllegalArgumentException("That file isn't a Trades Prep backup.")
    }
    if (bundle.version != BACKUP_SCHEMA_VERSION) {
        throw IllegalArgumentException(
            "This backup is version ${bundle.version}. This app reads version $BACKUP_SCHEMA_VERSION.",
        )
    }
    val sessionIds = bundle.sessions.map { it.id }.toSet()
    val keptSets = bundle.sets.filter { it.sessionId in sessionIds }
    return if (keptSets.size == bundle.sets.size) bundle else bundle.copy(sets = keptSets)
}

fun previewBackup(raw: String): BackupPreview {
    val bundle = decodeBackup(raw)
    val days = bundle.sessions.map { it.epochDay }
    return BackupPreview(
        version = bundle.version,
        sessionCount = bundle.sessions.size,
        firstDay = days.minOrNull()?.let(LocalDate::ofEpochDay),
        lastDay = days.maxOrNull()?.let(LocalDate::ofEpochDay),
    )
}

private val backupDayFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

fun restoreSummary(preview: BackupPreview): String {
    val count = when (preview.sessionCount) {
        1 -> "1 session"
        else -> "${preview.sessionCount} sessions"
    }
    val first = preview.firstDay
    val last = preview.lastDay
    val range = when {
        first == null || last == null -> ""
        first == last -> " on ${first.format(backupDayFormat)}"
        else -> " from ${first.format(backupDayFormat)} to ${last.format(backupDayFormat)}"
    }
    return "This file has $count$range. Restoring replaces the sessions, settings, and progression on this phone."
}

fun backupFileName(now: LocalDateTime = LocalDateTime.now()): String {
    val stamp = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm"))
    return "trades-prep-$stamp.json"
}

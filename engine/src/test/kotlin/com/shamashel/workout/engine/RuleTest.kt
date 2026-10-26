package com.shamashel.workout.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleTest {
    @Test
    fun tenPercentRule_extendsRepsThenLoadsAtTheCap() {
        val slot = Year1Plan.slot("A2")
        var state = WorkoutEngine.initialState(slot, 20.0)
        val first = run(slot, state, log(20.0, 12, 3, 12, 3, 12, 3), week = 1)
        assertEquals(Decision.EXTEND_REPS, first.decision)
        assertEquals(20.0, first.state.weight, 0.01)
        assertEquals(listOf(13, 13, 13), first.state.targetReps)
        assertTrue(first.state.extensionUsed)
        assertTrue(first.reason.contains("over 10%"))
        state = first.state

        state = run(slot, state, log(20.0, 13, 3, 13, 3, 13, 3), week = 1).state
        assertEquals(listOf(14, 14, 14), state.targetReps)
        state = run(slot, state, log(20.0, 14, 3, 14, 3, 14, 3), week = 1).state
        assertEquals(listOf(15, 15, 15), state.targetReps)

        val loaded = run(slot, state, log(20.0, 15, 3, 15, 3, 15, 2), week = 1)
        assertEquals(Decision.EARNED_INCREASE, loaded.decision)
        assertEquals(25.0, loaded.state.weight, 0.01)
        assertEquals(listOf(8, 8, 8), loaded.state.targetReps)
        assertFalse(loaded.state.extensionUsed)
    }

    @Test
    fun tenPercentRule_doesNotExtendTwice() {
        val slot = Year1Plan.slot("A1")
        val extended = WorkoutEngine.initialState(slot, 20.0).copy(extensionUsed = true, targetReps = listOf(15, 15, 15))
        val again = run(slot, extended, log(20.0, 15, 3, 15, 3, 15, 3), week = 1)
        assertEquals(25.0, again.state.weight, 0.01)
        assertEquals(listOf(8, 8, 8), again.state.targetReps)
    }

    @Test
    fun repHiAlreadyAt15_loadsDespiteABigJump() {
        val slot = Year1Plan.slot("C1")
        val state = WorkoutEngine.initialState(slot, 20.0)
        val result = run(slot, state, log(20.0, 15, 3, 15, 3, 15, 3), week = 1)
        assertEquals(Decision.EARNED_INCREASE, result.decision)
        assertEquals(25.0, result.state.weight, 0.01)
        assertEquals(listOf(10, 10, 10), result.state.targetReps)
    }

    @Test
    fun exactlyTenPercent_isAllowed() {
        val slot = Year1Plan.slot("A1")
        val state = WorkoutEngine.initialState(slot, 50.0)
        val result = run(slot, state, log(50.0, 12, 3, 12, 3, 12, 3), week = 1)
        assertEquals(55.0, result.state.weight, 0.01)
        assertEquals(Decision.EARNED_INCREASE, result.decision)
    }

    @Test
    fun missWithoutFailure_dropsFivePercent() {
        val slot = Year1Plan.slot("B1")
        val once = WorkoutEngine.initialState(slot, 135.0).copy(missCount = 1)
        val result = run(slot, once, log(135.0, 5, 2, 5, 2, 4, 1), week = 2)
        assertEquals(Decision.MISS_RESET, result.decision)
        assertEquals(125.0, result.state.weight, 0.01)
        assertTrue(result.reason.contains("−5%"))
        assertFalse(result.reason.contains("RIR 0"))
    }

    @Test
    fun week2_belowTheTwoToThreeBand_isAMiss() {
        val slot = Year1Plan.slot("A1")
        val state = WorkoutEngine.initialState(slot, 50.0)
        val result = run(slot, state, log(50.0, 10, 2, 10, 2, 9, 1), week = 2)
        assertEquals(Decision.MISS_REPEAT, result.decision)
        assertEquals(50.0, result.state.weight, 0.01)
    }

    @Test
    fun tooEasy_addsOneStepThenWaitsTwoWeeks_andRespectsTenPercent() {
        val slot = Year1Plan.slot("A2")
        val start = WorkoutEngine.initialState(slot, 80.0)
        val first = run(slot, start, log(80.0, 8, 5, 8, 5, 8, 5), week = 1, date = day0)
        assertEquals(Decision.TOO_EASY_INCREASE, first.decision)
        assertEquals(85.0, first.state.weight, 0.01)
        assertEquals(listOf(9, 9, 9), first.state.targetReps)

        val soon = run(slot, first.state, log(85.0, 8, 5, 8, 5, 8, 5), week = 1, date = day0.plusDays(7))
        assertEquals(Decision.IN_RANGE, soon.decision)
        assertEquals(85.0, soon.state.weight, 0.01)

        val later = run(slot, soon.state, log(85.0, 9, 5, 9, 5, 9, 5), week = 2, date = day0.plusDays(14))
        assertEquals(Decision.TOO_EASY_INCREASE, later.decision)
        assertEquals(90.0, later.state.weight, 0.01)

        val small = WorkoutEngine.initialState(slot, 20.0)
        val blocked = run(slot, small, log(20.0, 8, 5, 8, 5, 8, 5), week = 1)
        assertEquals(Decision.IN_RANGE, blocked.decision)
        assertEquals(20.0, blocked.state.weight, 0.01)
        assertEquals(listOf(9, 9, 9), blocked.state.targetReps)
    }

    @Test
    fun oneIncrementOnly_andNoMissingWeight() {
        val slot = Year1Plan.slot("B1")
        val state = WorkoutEngine.initialState(slot, 135.0)
        val result = run(slot, state, log(135.0, 10, 3, 10, 3, 10, 3), week = 1)
        assertEquals(145.0, result.state.weight, 0.01)
        assertEquals(listOf(6, 6, 6), result.state.targetReps)

        val db = Year1Plan.slot("A1")
        val no55 = settings(db = listOf(5.0, 10.0, 20.0, 25.0, 30.0, 35.0, 40.0, 45.0, 50.0, 60.0))
        val held = run(db, WorkoutEngine.initialState(db, 50.0), log(50.0, 12, 3, 12, 3, 12, 3), week = 1, settings = no55)
        assertEquals(50.0, held.state.weight, 0.01)
        assertTrue(held.reason.contains("not in your available"))
    }

    @Test
    fun carry_timeThenLoad_tenPercent_andPostureMiss() {
        val slot = Year1Plan.slot("A4")
        val heavyEnough = run(
            slot,
            WorkoutEngine.initialState(slot, 50.0),
            log(50.0, 45, 2, 45, 2, 45, 2, posture = true),
            week = 1,
        )
        assertEquals(Decision.EARNED_INCREASE, heavyEnough.decision)
        assertEquals(55.0, heavyEnough.state.weight, 0.01)
        assertEquals(listOf(30, 30, 30), heavyEnough.state.targetReps)

        val bigJump = run(
            slot,
            WorkoutEngine.initialState(slot, 40.0),
            log(40.0, 45, 2, 45, 2, 45, 2, posture = true),
            week = 1,
        )
        assertEquals(Decision.EXTEND_REPS, bigJump.decision)
        assertEquals(40.0, bigJump.state.weight, 0.01)
        assertEquals(listOf(50, 50, 50), bigJump.state.targetReps)

        val building = run(
            slot,
            WorkoutEngine.initialState(slot, 50.0),
            log(50.0, 30, 2, 35, 2, 32, 2, posture = true),
            week = 1,
        )
        assertEquals(Decision.IN_RANGE, building.decision)
        assertEquals(listOf(35, 40, 37), building.state.targetReps)
        assertEquals(50.0, building.state.weight, 0.01)

        val sloppy = run(
            slot,
            WorkoutEngine.initialState(slot, 50.0),
            log(50.0, 45, 2, 45, 2, 45, 2, posture = false),
            week = 1,
        )
        assertEquals(Decision.MISS_REPEAT, sloppy.decision)
        assertEquals(50.0, sloppy.state.weight, 0.01)
    }

    @Test
    fun carry_notHeavierOnTheSameDayAsATrapBarIncrease() {
        val deadlift = Year1Plan.slot("B1")
        val carry = Year1Plan.slot("B4")
        val results = WorkoutEngine.evaluateDay(
            listOf(
                request(deadlift, WorkoutEngine.initialState(deadlift, 200.0), log(200.0, 10, 3, 10, 3, 10, 3), week = 1),
                request(carry, WorkoutEngine.initialState(carry, 50.0), log(50.0, 30, 2, 30, 2, 30, 2, posture = true), week = 1),
            ),
        )
        val hinge = results.first { it.state.slotId == "B1" }
        val suitcase = results.first { it.state.slotId == "B4" }
        assertEquals(210.0, hinge.state.weight, 0.01)
        assertTrue(hinge.loadIncreased)
        assertEquals(Decision.CARRY_HELD_FOR_DEADLIFT, suitcase.decision)
        assertEquals(50.0, suitcase.state.weight, 0.01)
        assertTrue(suitcase.reason.contains("Trap-bar"))
    }

    @Test
    fun deload_doesNotChangeWorkingWeight_andCarryKeepsLoad() {
        val squat = Year1Plan.slot("A1")
        val state = WorkoutEngine.initialState(squat, 50.0).copy(targetReps = listOf(12, 12, 12))
        val logged = run(squat, state, log(45.0, 8, 5, 8, 4), week = 4, deload = true)
        assertEquals(50.0, logged.state.weight, 0.01)
        assertEquals(listOf(12, 12, 12), logged.state.targetReps)

        val carry = Year1Plan.slot("A4")
        val rx = deloadSuggestion(carry, WorkoutEngine.initialState(carry, 40.0), settings())
        assertEquals(40.0, rx.weight, 0.01)
        assertEquals(2, rx.sets)
        assertEquals(listOf(30, 30), rx.targetReps)
    }

    @Test
    fun painFlag1_blocksTheIncreaseOnly() {
        val slot = Year1Plan.slot("A1")
        val state = WorkoutEngine.initialState(slot, 50.0)
        val held = run(slot, state, log(50.0, 12, 3, 12, 2, 12, 2, pain = 1), week = 1)
        assertEquals(Decision.PAIN_HOLD, held.decision)
        assertEquals(50.0, held.state.weight, 0.01)
        assertEquals(0, held.state.loadBlockSessions)
        assertTrue(held.reason.contains("no load increase"))
        assertTrue(held.earlyDeloadReasons.isEmpty())
    }

    @Test
    fun painFlag2_substituteEarlyDeloadAndATwoSessionBlock() {
        val slot = Year1Plan.slot("C4")
        val state = WorkoutEngine.initialState(slot, 40.0)
        val first = run(
            slot,
            state,
            log(40.0, 8, 3, 8, 3, pain = 2),
            week = 1,
            date = day0,
        )
        assertEquals(40.0, first.state.weight, 0.01)
        assertEquals(1, first.state.loadBlockSessions)
        assertTrue(first.earlyDeloadReasons.any { it.contains("Pain flag 2") })
        assertTrue(first.prompts.any { it.kind == PromptKind.SUBSTITUTE && it.message.contains("DB split squat") })
        assertTrue(first.prompts.any { it.kind == PromptKind.EARLY_DELOAD })
        assertTrue(first.prompts.none { it.kind == PromptKind.DOCTOR })

        val repeat = run(
            slot,
            first.state,
            log(40.0, 8, 3, 8, 3, pain = 2),
            week = 2,
            date = day0.plusDays(10),
            recentPain = listOf(day0),
        )
        assertTrue(repeat.prompts.any { it.kind == PromptKind.DOCTOR && it.message == DOCTOR_PROMPT })

        val earned = WorkoutEngine.initialState(slot, 50.0).copy(loadBlockSessions = 1, targetReps = listOf(10, 10))
        val blocked = run(slot, earned, log(50.0, 10, 3, 10, 3), week = 1)
        assertEquals(Decision.PAIN_HOLD, blocked.decision)
        assertEquals(50.0, blocked.state.weight, 0.01)
        assertEquals(0, blocked.state.loadBlockSessions)
    }

    @Test
    fun painFlag3_stopsWithNoCoaching() {
        val slot = Year1Plan.slot("A1")
        val state = WorkoutEngine.initialState(slot, 50.0).copy(targetReps = listOf(10, 9, 9), missCount = 1)
        val stopped = run(slot, state, log(50.0, 12, 3, 12, 3, 12, 3, pain = 3), week = 1)
        assertEquals(Decision.NO_COACHING, stopped.decision)
        assertTrue(stopped.stopSession)
        assertNull(stopped.suggestion)
        assertEquals(state, stopped.state)
        assertNotNull(stopped.medicalMessage)
        assertTrue(stopped.medicalMessage!!.contains("seek medical care"))
        assertTrue(stopped.prompts.isEmpty())
        assertTrue(stopped.reason.contains("seek medical care"))
    }

    @Test
    fun substitute_restartsAt85PercentRoundedDown() {
        val slot = Year1Plan.slot("C4")
        val state = WorkoutEngine.initialState(slot, 50.0).copy(targetReps = listOf(10, 10), missCount = 2)
        val next = WorkoutEngine.applySubstitute(slot, state, settings(), "DB split squat")
        assertEquals("DB split squat", next.exerciseName)
        assertEquals(40.0, next.weight, 0.01)
        assertEquals(listOf(8, 8), next.targetReps)
        assertEquals(0, next.missCount)
        assertTrue(next.loadBlockSessions >= 2)
    }

    @Test
    fun plateau_recoveryMicroAndSwap() {
        val slot = Year1Plan.slot("A2")
        var state = WorkoutEngine.initialState(slot, 80.0)
        repeat(2) {
            state = run(slot, state, log(80.0, 9, 3, 9, 3, 9, 3), week = 1).state
        }
        val third = run(slot, state, log(80.0, 9, 3, 9, 3, 9, 3), week = 1, settings = settings(micro = true))
        assertTrue(third.prompts.any { it.kind == PromptKind.PLATEAU_RECOVERY })
        assertTrue(third.prompts.any { it.kind == PromptKind.PLATEAU_MICRO })
        assertTrue(third.state.preferMicro)
        assertEquals(80.0, third.state.weight, 0.01)

        val earned = third.state.copy(targetReps = listOf(12, 12, 12))
        val micro = run(slot, earned, log(80.0, 12, 3, 12, 3, 12, 3), week = 2, settings = settings(micro = true))
        assertEquals(Decision.EARNED_INCREASE, micro.decision)
        assertEquals(82.5, micro.state.weight, 0.01)

        var stalled = WorkoutEngine.initialState(slot, 80.0)
        repeat(5) {
            stalled = run(slot, stalled, log(80.0, 9, 3, 9, 3, 9, 2), week = 1).state
        }
        val sixth = run(slot, stalled, log(80.0, 9, 3, 9, 3, 9, 2), week = 1)
        assertTrue(sixth.prompts.any { it.kind == PromptKind.PLATEAU_SWAP && it.message.contains("DB incline press") })
    }

    @Test
    fun gobletCeiling_promptsAfterThreeSessionsAtTheHeaviestDumbbell() {
        val slot = Year1Plan.slot("A1")
        val capped = settings(db = (1..10).map { it * 5.0 })
        var state = WorkoutEngine.initialState(slot, 50.0)
        repeat(2) {
            val step = run(slot, state, log(50.0, 12, 3, 12, 3, 12, 3), week = 1, settings = capped)
            assertEquals(50.0, step.state.weight, 0.01)
            assertTrue(step.prompts.none { it.kind == PromptKind.GOBLET_CEILING })
            state = step.state
        }
        val third = run(slot, state, log(50.0, 12, 3, 12, 3, 12, 3), week = 1, settings = capped)
        assertTrue(third.prompts.any { it.kind == PromptKind.GOBLET_CEILING && it.message.contains("split squat") })
        assertEquals(50.0, third.state.weight, 0.01)
    }

    @Test
    fun rounding_nearestTieGoesDown_andDownGoesToTheGrid() {
        assertEquals(20.0, roundNearestAvailable(22.5, listOf(20.0, 25.0)), 0.01)
        assertEquals(120.0, roundDownToAvailable(121.5, defaultBarbellWeights()), 0.01)
        assertEquals(120.0, roundNearestAvailable(121.5, defaultBarbellWeights()), 0.01)
        assertEquals(45.0, roundNearestAvailable(50.0 * 0.9, defaultDbWeights(false)), 0.01)
        assertEquals(40.0, roundDownToAvailable(42.5, defaultDbWeights(false)), 0.01)
        val micro = EngineSettings().withMicroPlates(true)
        assertEquals(2.5, micro.dbIncrement, 0.01)
        assertEquals(5.0, micro.barbellIncrement, 0.01)
        assertTrue(micro.availableDb.contains(52.5))
        assertEquals(5.0, EngineSettings().dbIncrement, 0.01)
        assertEquals(10.0, EngineSettings().barbellIncrement, 0.01)
    }

    @Test
    fun rirTargets_neverRiseInsideTheWorkingBlock_andStayAboveTheFloor() {
        Year1Plan.slots.filter { it.tracksLoad }.forEach { slot ->
            val weeks = (1..3).map { rirTarget(slot, it, false) }
            assertTrue(weeks[0].gate >= weeks[1].gate)
            assertTrue(weeks[1].gate >= weeks[2].gate)
            weeks.forEach { assertTrue(it.gate >= slot.rirFloor) }
            assertFalse(weeks.any { it.deload })
        }
        val squat = Year1Plan.slot("A1")
        assertEquals("2–3", rirTarget(squat, 2, false).label)
        assertEquals("3", rirTarget(Year1Plan.slot("B1"), 2, false).label)
        assertEquals("4+", rirTarget(squat, 4, false).label)
        assertEquals(4, rirTarget(squat, 1, true).gate)
    }

    @Test
    fun earlyDeloadAndSkipRules() {
        val today = day0
        val twoMisses = WeekSignals(secondMissSlots = setOf("A1", "B2"))
        assertTrue(immediateDeloadReasons(twoMisses, today).isNotEmpty())
        assertTrue(immediateDeloadReasons(WeekSignals(secondMissSlots = setOf("A1")), today).isEmpty())

        val tired = WeekSignals(sessionReadiness = listOf(2, 3, 2))
        assertTrue(immediateDeloadReasons(tired, today).any { it.contains("Readiness") })

        val pain = WeekSignals(painEvents = listOf(PainEvent("C4", 2, today.minusDays(3))))
        assertTrue(immediateDeloadReasons(pain, today).any { it.contains("pain flag") })
        val oldPain = WeekSignals(painEvents = listOf(PainEvent("C4", 2, today.minusDays(8))))
        assertTrue(immediateDeloadReasons(oldPain, today).isEmpty())

        val badWeek = checkInDeloadReasons(WeeklyScore(2, 4, 3), WeekSignals(), today)
        assertTrue(badWeek.any { it.contains("Performance") })
        val lowReady = checkInDeloadReasons(WeeklyScore(2, 2, 2), WeekSignals(), today)
        assertTrue(lowReady.any { it.contains("readiness") })

        val fresh = WeeklyScore(2, 2, 3)
        val sore = WeeklyScore(3, 2, 3)
        assertFalse(skipDeloadEligible(settings(skip = false), fresh, fresh, false))
        assertFalse(skipDeloadEligible(settings(skip = true), fresh, fresh, true))
        assertFalse(skipDeloadEligible(settings(skip = true), fresh, sore, false))
        assertTrue(skipDeloadEligible(settings(skip = true), fresh, fresh, false))

        val clock = ProgramClock(blockIndex = 2, weekOfBlock = 3)
        val offer = onCheckIn(clock, fresh, WeekSignals(), settings(skip = true), today, fresh, false, null)
        assertTrue(offer.offerSkip)
        assertEquals(3, offer.clock.weekOfBlock)

        val skipped = onCheckIn(clock, fresh, WeekSignals(), settings(skip = true), today, fresh, false, true)
        assertEquals(1, skipped.clock.weekOfBlock)
        assertEquals(3, skipped.clock.blockIndex)
        assertTrue(skipped.resetMissCounts)
        assertFalse(skipped.clock.deloadActive)

        val scheduled = onCheckIn(clock, fresh, WeekSignals(), settings(), today, fresh, false, false)
        assertTrue(scheduled.clock.deloadActive)
        assertEquals(4, scheduled.clock.weekOfBlock)
        assertFalse(scheduled.resetMissCounts)

        val done = onCheckIn(scheduled.clock, fresh, WeekSignals(), settings(), today, null, false, null)
        assertEquals(1, done.clock.weekOfBlock)
        assertEquals(3, done.clock.blockIndex)
        assertTrue(done.resetMissCounts)
        assertFalse(done.clock.deloadActive)

        val early = onCheckIn(
            ProgramClock(weekOfBlock = 2),
            WeeklyScore(1, 4, 3),
            WeekSignals(),
            settings(),
            today,
            null,
            false,
            null,
        )
        assertTrue(early.clock.deloadActive)
        assertEquals(4, early.clock.weekOfBlock)
    }

    @Test
    fun year1Plan_hasTheLockedSlots() {
        assertEquals(15, Year1Plan.slots.size)
        val a1 = Year1Plan.slot("A1")
        assertEquals(8, a1.repLo)
        assertEquals(12, a1.repHi)
        assertEquals(3, a1.nSets)
        assertEquals(1, a1.rirFloor)
        assertFalse(a1.isOptional)
        val b1 = Year1Plan.slot("B1")
        assertEquals(6, b1.repLo)
        assertEquals(10, b1.repHi)
        assertTrue(b1.usesTrapBarRir)
        assertEquals(2, b1.rirFloor)
        val a4 = Year1Plan.slot("A4")
        assertTrue(a4.isCarry)
        assertEquals(30, a4.repLo)
        assertEquals(45, a4.repHi)
        assertEquals(5, a4.repStep)
        val c4 = Year1Plan.slot("C4")
        assertTrue(c4.isOptional)
        assertEquals(2, c4.nSets)
        assertEquals(8, c4.repLo)
        assertEquals(10, c4.repHi)
        val c5 = Year1Plan.slot("C5")
        assertFalse(c5.tracksLoad)
        assertEquals(4, Year1Plan.outlines.size)
        val relocked = applySlotOverrides(Year1Plan.slots, listOf(SlotNumberOverride("A1", repLo = 6, repHi = 10, nSets = 4)))
        assertEquals(6, relocked.first { it.id == "A1" }.repLo)
        assertEquals(8, relocked.first { it.id == "A2" }.repLo)
        assertEquals(SAFETY_COPY, "Not medical advice. Check with your doctor before ramping up.")
    }

    @Test
    fun fewerSetsThanPrescribed_isAMiss() {
        val slot = Year1Plan.slot("A1")
        val result = run(slot, WorkoutEngine.initialState(slot, 50.0), log(50.0, 12, 3, 12, 3), week = 1)
        assertEquals(Decision.MISS_REPEAT, result.decision)
        assertEquals(50.0, result.state.weight, 0.01)
    }
}

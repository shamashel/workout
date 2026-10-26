package com.shamashel.workout.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkedExampleTest {
    @Test
    fun gobletSquat_weekByWeek_matchesSection3() {
        val slot = Year1Plan.slot("A1")
        var state = WorkoutEngine.initialState(slot, 50.0)

        val week1 = run(slot, state, log(50.0, 9, 3, 8, 3, 8, 2), week = 1)
        assertEquals(Decision.IN_RANGE, week1.decision)
        assertEquals(50.0, week1.state.weight, 0.01)
        assertEquals(listOf(10, 9, 9), week1.state.targetReps)
        assertEquals("3", week1.targetRirLabel)
        assertEquals("In range, hold weight. Next target 10 / 9 / 9", week1.reason)
        state = week1.state

        val week2 = run(slot, state, log(50.0, 10, 3, 10, 2, 9, 2), week = 2)
        assertEquals(Decision.IN_RANGE, week2.decision)
        assertEquals(50.0, week2.state.weight, 0.01)
        assertEquals(listOf(11, 11, 10), week2.state.targetReps)
        assertEquals("2–3", week2.targetRirLabel)
        assertEquals("In range, hold weight. Next target 11 / 11 / 10", week2.reason)
        state = week2.state

        val week3 = run(slot, state, log(50.0, 12, 2, 11, 2, 11, 1), week = 3)
        assertEquals(Decision.IN_RANGE, week3.decision)
        assertEquals(50.0, week3.state.weight, 0.01)
        assertEquals(listOf(12, 12, 12), week3.state.targetReps)
        assertEquals("2", week3.targetRirLabel)
        assertEquals("Not all at 12, hold. Next 12 / 12 / 12", week3.reason)
        val deload = requireNotNull(week3.followingDeload)
        assertEquals(45.0, deload.weight, 0.01)
        assertEquals(2, deload.sets)
        assertEquals(listOf(8, 8), deload.targetReps)
        assertEquals("4+", deload.targetRirLabel)
        state = week3.state

        val loggedDeload = run(slot, state, log(45.0, 8, 5, 8, 4), week = 4, deload = true)
        assertEquals(Decision.DELOAD_LOGGED, loggedDeload.decision)
        assertEquals(50.0, loggedDeload.state.weight, 0.01)
        assertEquals(listOf(12, 12, 12), loggedDeload.state.targetReps)
        state = WorkoutEngine.resetMisses(loggedDeload.state)

        val week5 = run(slot, state, log(50.0, 12, 3, 12, 2, 12, 2), week = 1)
        assertEquals(Decision.EARNED_INCREASE, week5.decision)
        assertEquals(55.0, week5.state.weight, 0.01)
        assertEquals(listOf(8, 8, 8), week5.state.targetReps)
        assertEquals("3", week5.targetRirLabel)
        assertEquals("All sets at 12 and avg RIR 2.3 ≥ 2, so +5 lb", week5.reason)
        assertEquals(0, week5.state.missCount)
    }

    @Test
    fun trapBarDeadlift_workingWeeks_matchSection3() {
        val slot = Year1Plan.slot("B1")
        var state = WorkoutEngine.initialState(slot, 135.0)

        val week1 = run(slot, state, log(135.0, 8, 3, 7, 3, 7, 3), week = 1)
        assertEquals(Decision.IN_RANGE, week1.decision)
        assertEquals(135.0, week1.state.weight, 0.01)
        assertEquals(listOf(9, 8, 8), week1.state.targetReps)
        assertEquals("3", week1.targetRirLabel)
        assertEquals("In range, hold weight. Next target 9 / 8 / 8", week1.reason)
        state = week1.state

        val week2 = run(slot, state, log(135.0, 9, 3, 8, 3, 8, 2), week = 2)
        assertEquals(Decision.IN_RANGE, week2.decision)
        assertEquals(listOf(10, 9, 9), week2.state.targetReps)
        assertEquals("3", week2.targetRirLabel)
        assertEquals("In range, hold weight. Next target 10 / 9 / 9", week2.reason)
        state = week2.state

        val week3 = run(slot, state, log(135.0, 10, 2, 10, 2, 9, 2), week = 3)
        assertEquals(Decision.IN_RANGE, week3.decision)
        assertEquals(135.0, week3.state.weight, 0.01)
        assertEquals(listOf(10, 10, 10), week3.state.targetReps)
        assertEquals("Not all at 10, hold. Next 10 / 10 / 10", week3.reason)

        val deload = WorkoutEngine.prescription(
            slot,
            week3.state,
            ProgramClock(weekOfBlock = 4, deloadActive = true),
            settings(),
        )
        assertEquals(120.0, deload.weight, 0.01)
        assertEquals(2, deload.sets)
        assertEquals("4+", deload.targetRirLabel)
        assertEquals(listOf(6, 6), deload.targetReps)
    }

    @Test
    fun trapBarDeadlift_twoMisses_dropTenPercentTo120() {
        val slot = Year1Plan.slot("B1")
        val aiming = WorkoutEngine.initialState(slot, 135.0).copy(targetReps = listOf(10, 10, 10))

        val first = run(slot, aiming, log(135.0, 7, 1, 6, 0, 5, 0), week = 3)
        assertEquals(Decision.MISS_REPEAT, first.decision)
        assertEquals(135.0, first.state.weight, 0.01)
        assertEquals(listOf(10, 10, 10), first.state.targetReps)
        assertEquals(1, first.state.missCount)
        assertEquals("Too hard: miss 1, repeat 135 lb", first.reason)

        val second = run(slot, first.state, log(135.0, 6, 1, 6, 0, 5, 0), week = 3)
        assertEquals(Decision.MISS_RESET, second.decision)
        assertEquals(120.0, second.state.weight, 0.01)
        assertEquals(0, second.state.missCount)
        assertEquals(listOf(10, 10, 10), second.state.targetReps)
        assertEquals("Miss 2: weight → 120 lb (−10% because RIR 0), miss count reset", second.reason)
    }

    @Test
    fun successfulSession_clearsMissCount() {
        val slot = Year1Plan.slot("A1")
        val missed = WorkoutEngine.initialState(slot, 50.0).copy(missCount = 1, targetReps = listOf(10, 9, 9))
        val next = run(slot, missed, log(50.0, 10, 3, 9, 3, 9, 2), week = 1)
        assertEquals(Decision.IN_RANGE, next.decision)
        assertEquals(0, next.state.missCount)
        assertNull(next.medicalMessage)
    }
}

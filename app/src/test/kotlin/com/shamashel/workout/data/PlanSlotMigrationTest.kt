package com.shamashel.workout.data

import com.shamashel.workout.engine.WorkoutEngine
import com.shamashel.workout.engine.Year1Plan
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlanSlotMigrationTest {
    @Test
    fun oldSlotIdsKeepTheExerciseTheyWereLoggedUnder() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val database = WorkoutDatabase.create(context, "legacy-slots.db")
        try {
            val repository = WorkoutRepository(database)
            repository.bootstrap()
            database.openHelper.writableDatabase.apply {
                execSQL("DELETE FROM slots WHERE id = 'B6'")
                execSQL("DELETE FROM slot_state WHERE slotId = 'B6'")
            }
            val dao = database.dao()
            dao.saveSlots(listOf(stepUp(), hold(), plank()))
            dao.saveState(state("C4", "DB step-up", 25.0))
            dao.saveState(state("C5", "Deep-squat hold", 0.0))
            dao.saveState(state("B5", "Plank", 0.0))
            dao.saveState(state("A5", "Dead bug", 0.0))
            dao.saveState(state("A1", "Goblet squat", 50.0))
            dao.saveState(state("B1", "Trap-bar deadlift", 135.0))
            dao.saveDraft(DraftEntity("C4", """[{"weight":25.0,"reps":8,"rir":3}]""", 0, "step draft", false))
            dao.insertSessions(
                listOf(
                    session(1, "C4", "step session"),
                    session(2, "C5", "hold session"),
                    session(3, "B5", "plank session"),
                    session(4, "A5", "bug session"),
                ),
            )
            val program = dao.program()!!
            dao.saveProgram(program.copy(secondMissSlots = "C4,B5"))

            repository.ensureCurrentPlan()

            assertEquals("C4s", dao.allSessions().first { it.note == "step session" }.slotId)
            assertEquals("step draft", dao.drafts().single { it.slotId == "C4s" }.note)
            assertEquals(25.0, dao.states().first { it.slotId == "C4s" }.weight, 0.001)
            assertEquals("DB step-up", dao.states().first { it.slotId == "C4s" }.exerciseName)
            assertEquals("H", dao.slot("C4s")?.day)

            assertEquals("C4", dao.allSessions().first { it.note == "hold session" }.slotId)
            assertEquals("Deep-squat hold", dao.slot("C4")?.name)
            assertEquals("DEEP_SQUAT_HOLD", dao.slot("C4")?.kind)
            assertFalse(dao.states().any { it.slotId == "C5" })

            assertEquals("B6", dao.allSessions().first { it.note == "plank session" }.slotId)
            assertEquals("Plank", dao.slot("B6")?.name)
            assertEquals("Leg curl", dao.slot("B5")?.name)
            assertEquals(0.0, dao.states().first { it.slotId == "B5" }.weight, 0.001)

            assertEquals("A5", dao.allSessions().first { it.note == "bug session" }.slotId)
            assertEquals("Dead bug", dao.slot("A5")?.name)
            assertEquals(50.0, dao.states().first { it.slotId == "A1" }.weight, 0.001)
            assertEquals(135.0, dao.states().first { it.slotId == "B1" }.weight, 0.001)
            assertEquals("Trap-bar deadlift, low handles", dao.states().first { it.slotId == "B1" }.exerciseName)
            assertEquals("C4s,B6", dao.program()?.secondMissSlots)
            assertTrue(dao.slots().none { it.kind == "STEP_UP" && it.day == "C" })
            assertEquals("Leg curl", Year1Plan.slot("B5").name)
        } finally {
            database.close()
        }
    }
}

private fun stepUp(): SlotEntity {
    return SlotEntity(
        id = "C4",
        day = "C",
        sortOrder = 4,
        name = "DB step-up",
        kind = "STEP_UP",
        repLo = 8,
        repHi = 10,
        nSets = 2,
        rirFloor = 1,
        substitutes = "DB split squat",
        isCarry = false,
        isOptional = true,
        tracksLoad = true,
        equipment = "DUMBBELL",
        perSide = true,
        usesTrapBarRir = false,
        repStep = 1,
        notes = "Low box",
    )
}

private fun hold(): SlotEntity {
    return stepUp().copy(
        id = "C5",
        sortOrder = 5,
        name = "Deep-squat hold",
        kind = "DEEP_SQUAT_HOLD",
        repLo = 30,
        repHi = 30,
        tracksLoad = false,
        equipment = "BODYWEIGHT",
        perSide = false,
        notes = "Hold",
    )
}

private fun plank(): SlotEntity {
    return stepUp().copy(
        id = "B5",
        day = "B",
        sortOrder = 5,
        name = "Plank",
        kind = "PLANK",
        repLo = 20,
        repHi = 40,
        tracksLoad = false,
        equipment = "BODYWEIGHT",
        perSide = false,
        notes = "Plank",
    )
}

private fun state(slotId: String, name: String, weight: Double): SlotStateEntity {
    val plan = Year1Plan.slots.firstOrNull { it.id == slotId } ?: Year1Plan.slot("A5")
    return WorkoutEngine.initialState(plan, weight).copy(slotId = slotId, exerciseName = name).toEntity()
}

private fun session(id: Long, slotId: String, note: String): SessionEntity {
    return SessionEntity(
        id = id,
        epochDay = 20753,
        slotId = slotId,
        day = "C",
        painFlag = 0,
        note = note,
        postureClean = null,
        deload = false,
        decision = "IN_RANGE",
        reason = "",
        loadIncreased = false,
        performedWeight = 0.0,
    )
}

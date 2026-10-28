package com.shamashel.workout.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

class HealthSaveStatusTest {
    @Test
    fun mapsOffWrittenAndSkipped() {
        assertEquals(HealthSaveStatus.OFF, healthSaveStatus(writingEnabled = false, writeError = null))
        assertEquals(HealthSaveStatus.OFF, healthSaveStatus(writingEnabled = false, writeError = "ignored"))
        assertEquals(HealthSaveStatus.WRITTEN, healthSaveStatus(writingEnabled = true, writeError = null))
        assertEquals(
            HealthSaveStatus.SKIPPED,
            healthSaveStatus(
                writingEnabled = true,
                writeError = "Health Connect write permission isn't granted. The session stays in the app.",
            ),
        )
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FinishWorkoutTest {
    @Test
    fun draftWeightsFinishWhenStartingWeightIsUnset() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "finish-draft.db"
        context.deleteDatabase(name)
        val created = WorkoutDatabase.create(context, name)
        val repository = WorkoutRepository(created)
        repository.bootstrap()
        assertEquals(0.0, created.dao().states().first { it.slotId == "A1" }.weight, 0.001)
        repository.saveDraft(
            slotId = "A1",
            sets = listOf(DraftSet(50.0, 10, 2), DraftSet(50.0, 10, 2), DraftSet(50.0, 9, 2)),
            pain = 0,
            note = "first real day",
            posture = false,
        )
        created.close()

        val reopened = WorkoutDatabase.create(context, name)
        try {
            val again = WorkoutRepository(reopened)
            assertEquals("first real day", again.loadDraft("A1")?.note)
            val outcome = again.finishDay('A', 42, 4)
            assertNull(outcome.medicalMessage)
            val session = reopened.dao().allSessions().single()
            assertEquals("A1", session.slotId)
            assertEquals("A", session.day)
            assertEquals(50.0, session.performedWeight, 0.001)
            assertEquals("first real day", session.note)
            assertEquals(3, reopened.dao().setsForSession(session.id).size)
            assertEquals(50.0, reopened.dao().states().first { it.slotId == "A1" }.weight, 0.001)
            assertTrue(reopened.dao().drafts().none { it.slotId == "A1" })
        } finally {
            reopened.close()
        }
    }
}

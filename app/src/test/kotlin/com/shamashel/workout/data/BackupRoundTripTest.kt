package com.shamashel.workout.data

import android.content.Context
import com.shamashel.workout.R
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupRoundTripTest {
    @Test
    fun exportImport_keepsHistorySettingsAndAnOpenDraft() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val source = WorkoutDatabase.create(context, "backup-source.db")
        val destination = WorkoutDatabase.create(context, "backup-dest.db")
        try {
            val origin = WorkoutRepository(source)
            origin.bootstrap()
            origin.saveStartingWeights(mapOf("A1" to 50.0))
            origin.saveSettings(origin.settingsEntity().copy(allowSkipDeload = true))
            origin.saveDraft(
                slotId = "A1",
                sets = listOf(DraftSet(50.0, 10, 3), DraftSet(50.0, 10, 2), DraftSet(50.0, 9, 2)),
                pain = 0,
                note = "logged through the app",
                posture = false,
            )
            val finished = origin.finishDay('A', 42, 4)
            assertTrue(finished.rows.any { it.slotId == "A1" })
            origin.saveDraft(
                slotId = "B1",
                sets = listOf(DraftSet(135.0, 8, 3)),
                pain = 0,
                note = "still open",
                posture = false,
            )

            val raw = origin.exportJson()
            assertTrue(raw.contains("still open"))
            val legacyFields = Json.parseToJsonElement(raw).jsonObject.toMutableMap()
            legacyFields.remove("drafts")
            val withoutDrafts = JsonObject(legacyFields).toString()
            WorkoutRepository(destination).importJson(withoutDrafts)
            val legacy = destination.dao().drafts()
            assertTrue(legacy.isEmpty())

            destination.close()
            val restoredDb = WorkoutDatabase.create(context, "backup-dest.db")
            try {
                val restored = WorkoutRepository(restoredDb)
                restored.importJson(raw)
                assertEquals(true, restored.settingsEntity().allowSkipDeload)
                val sessions = restoredDb.dao().allSessions()
                assertEquals(listOf("A1"), sessions.map { it.slotId })
                assertEquals("logged through the app", sessions.single().note)
                assertEquals(3, restoredDb.dao().setsForSession(sessions.single().id).size)
                assertEquals("still open", restoredDb.dao().drafts().single().note)
                assertEquals(50.0, restoredDb.dao().states().first { it.slotId == "A1" }.weight, 0.001)
            } finally {
                restoredDb.close()
            }
        } finally {
            source.close()
            if (destination.isOpen) destination.close()
        }
    }

    @Test
    fun checkpoint_foldsTheWriteAheadLog() = runBlocking {
        val context: Context = RuntimeEnvironment.getApplication()
        val database = WorkoutDatabase.create(context, "checkpoint.db")
        try {
            val repository = WorkoutRepository(database)
            repository.bootstrap()
            repository.saveSettings(repository.settingsEntity().copy(allowSkipDeload = true))
            database.checkpoint()
            val cursor = database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint")
            cursor.use {
                assertTrue(it.moveToFirst())
                assertEquals(0, it.getInt(0))
                assertEquals(0, it.getInt(1))
            }
            assertEquals(1, database.openHelper.writableDatabase.version)
        } finally {
            database.close()
        }
    }

    @Test
    fun backupRules_nameTheRoomFiles() {
        val context = RuntimeEnvironment.getApplication()
        assertTrue(xmlText(context, R.xml.backup_rules).contains("workout.db-wal"))
        val rules = xmlText(context, R.xml.data_extraction_rules)
        assertTrue(rules.contains("workout.db"))
        assertTrue(rules.contains("cloud-backup"))
        assertTrue(rules.contains("device-transfer"))
        assertTrue(rules.contains("sharedpref"))
    }
}

private fun xmlText(context: Context, resId: Int): String {
    val parser = context.resources.getXml(resId)
    val text = StringBuilder()
    while (parser.next() != XmlPullParser.END_DOCUMENT) {
        text.append(parser.name ?: "")
        text.append(' ')
        for (index in 0 until parser.attributeCount) {
            text.append(parser.getAttributeValue(index))
            text.append(' ')
        }
    }
    return text.toString()
}

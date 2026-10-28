package com.shamashel.workout.data

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate

class WeekScheduleTest {
    private val wednesday = LocalDate.of(2026, 10, 28)
    private val monday = LocalDate.of(2026, 10, 26)
    private val sunday = LocalDate.of(2026, 11, 1)

    @Test
    fun year1MatchesTheCurrentWeek() {
        assertEquals(DayOfWeek.WEDNESDAY, wednesday.dayOfWeek)
        assertEquals(DayOfWeek.MONDAY, monday.dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, sunday.dayOfWeek)
        val week = WeekSchedule.year1
        assertEquals(WeekdayAssignment.A, week.on(DayOfWeek.MONDAY))
        assertEquals(WeekdayAssignment.REST, week.on(DayOfWeek.TUESDAY))
        assertEquals(WeekdayAssignment.B, week.on(wednesday))
        assertEquals(WeekdayAssignment.REST, week.on(DayOfWeek.THURSDAY))
        assertEquals(WeekdayAssignment.C, week.on(DayOfWeek.FRIDAY))
        assertEquals(WeekdayAssignment.REST, week.on(DayOfWeek.SATURDAY))
        assertEquals(WeekdayAssignment.REST, week.on(sunday))
        assertEquals(YEAR1_WEEK, week.encode())
    }

    @Test
    fun reanchorMakesTodayDayAAndKeepsThePattern() {
        val rotated = WeekSchedule.year1.reanchored(wednesday)
        assertEquals(WeekdayAssignment.A, rotated.on(DayOfWeek.WEDNESDAY))
        assertEquals(WeekdayAssignment.REST, rotated.on(DayOfWeek.THURSDAY))
        assertEquals(WeekdayAssignment.B, rotated.on(DayOfWeek.FRIDAY))
        assertEquals(WeekdayAssignment.REST, rotated.on(DayOfWeek.SATURDAY))
        assertEquals(WeekdayAssignment.C, rotated.on(DayOfWeek.SUNDAY))
        assertEquals(WeekdayAssignment.REST, rotated.on(DayOfWeek.MONDAY))
        assertEquals(WeekdayAssignment.REST, rotated.on(DayOfWeek.TUESDAY))
        assertEquals("RRARBRC", rotated.encode())
    }

    @Test
    fun reanchorOnMondayIsTheUsualWeek() {
        assertEquals(WeekSchedule.year1, WeekSchedule.year1.reanchored(monday))
    }

    @Test
    fun reanchorOnSundayWrapsTheWeek() {
        val rotated = WeekSchedule.year1.reanchored(sunday)
        assertEquals(WeekdayAssignment.A, rotated.on(DayOfWeek.SUNDAY))
        assertEquals(WeekdayAssignment.REST, rotated.on(DayOfWeek.MONDAY))
        assertEquals(WeekdayAssignment.B, rotated.on(DayOfWeek.TUESDAY))
        assertEquals(WeekdayAssignment.REST, rotated.on(DayOfWeek.WEDNESDAY))
        assertEquals(WeekdayAssignment.C, rotated.on(DayOfWeek.THURSDAY))
        assertEquals(WeekdayAssignment.REST, rotated.on(DayOfWeek.FRIDAY))
        assertEquals(WeekdayAssignment.REST, rotated.on(DayOfWeek.SATURDAY))
    }

    @Test
    fun yearBoundaryDatesStayOnTheirOwnWeekdays() {
        val newYearsEve = LocalDate.of(2026, 12, 31)
        val newYear = newYearsEve.plusDays(1)
        assertEquals(LocalDate.of(2027, 1, 1), newYear)
        assertEquals(DayOfWeek.THURSDAY, newYearsEve.dayOfWeek)
        assertEquals(DayOfWeek.FRIDAY, newYear.dayOfWeek)
        assertEquals(WeekdayAssignment.REST, WeekSchedule.year1.on(newYearsEve))
        assertEquals(WeekdayAssignment.C, WeekSchedule.year1.on(newYear))
    }

    @Test
    fun garbageScheduleFallsBackToYear1() {
        assertEquals(WeekSchedule.year1, WeekSchedule.decode(null))
        assertEquals(WeekSchedule.year1, WeekSchedule.decode("ABC"))
        assertEquals(WeekSchedule.year1, WeekSchedule.decode("ARBRCRX"))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeekPlanPersistenceTest {
    @Test
    fun scheduleOverrideAndReanchor_leaveLoggedDaysAlone() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val database = WorkoutDatabase.create(context, "week-plan.db")
        var date = LocalDate.of(2026, 10, 28)
        try {
            val repository = WorkoutRepository(database, clock = { date })
            repository.bootstrap()
            assertEquals('B', repository.home().day)
            assertFalse(repository.home().todayOnly)

            repository.saveSettings(repository.settingsEntity().copy(weekSchedule = "RRARRRR"))
            assertEquals('A', repository.home().day)
            assertEquals("RRARRRR", repository.settingsEntity().weekSchedule)

            repository.saveSettings(repository.settingsEntity().copy(weekSchedule = YEAR1_WEEK))
            repository.saveStartingWeights(mapOf("B1" to 135.0))
            repository.saveDraft(
                slotId = "B1",
                sets = listOf(DraftSet(135.0, 8, 3)),
                pain = 0,
                note = "wednesday hinge",
                posture = true,
            )
            repository.finishDay('B', 40, 4)
            val logged = database.dao().allSessions().single()
            assertEquals("B", logged.day)
            assertEquals("wednesday hinge", logged.note)
            val states = database.dao().states().sortedBy { it.slotId }

            repository.useDayToday('A')
            assertEquals(YEAR1_WEEK, repository.settingsEntity().weekSchedule)
            assertEquals('A', repository.home().day)
            assertTrue(repository.home().todayOnly)
            assertEquals('B', repository.home().usualDay)
            assertEquals("B", database.dao().allSessions().single().day)
            assertEquals(states, database.dao().states().sortedBy { it.slotId })

            repository.clearTodayOverride()
            assertEquals('B', repository.home().day)
            assertFalse(repository.home().todayOnly)
            assertNull(repository.settingsEntity().dayOverrideEpoch)

            repository.startFreshFromToday()
            assertEquals('A', repository.home().day)
            assertFalse(repository.home().todayOnly)
            assertEquals("RRARBRC", repository.settingsEntity().weekSchedule)
            assertEquals("B", database.dao().allSessions().single().day)
            assertEquals("wednesday hinge", database.dao().allSessions().single().note)
            assertEquals(states, database.dao().states().sortedBy { it.slotId })
        } finally {
            database.close()
        }
    }

    @Test
    fun todayOverrideExpiresOnTheNextDate() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val database = WorkoutDatabase.create(context, "week-rollover.db")
        var date = LocalDate.of(2026, 12, 31)
        try {
            val repository = WorkoutRepository(database, clock = { date })
            repository.bootstrap()
            assertEquals('R', repository.home().day)
            repository.useDayToday('A')
            assertEquals('A', repository.home().day)
            assertTrue(repository.home().todayOnly)
            date = date.plusDays(1)
            assertEquals(LocalDate.of(2027, 1, 1), date)
            val home = repository.home()
            assertEquals('C', home.day)
            assertFalse(home.todayOnly)
            assertEquals(LocalDate.of(2026, 12, 31).toEpochDay(), database.dao().settings()?.dayOverrideEpoch)
        } finally {
            database.close()
        }
    }

    @Test
    fun olderBackupWithoutAScheduleStillImports() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val source = WorkoutDatabase.create(context, "week-export.db")
        val destination = WorkoutDatabase.create(context, "week-import.db")
        try {
            val origin = WorkoutRepository(source)
            origin.bootstrap()
            origin.saveSettings(origin.settingsEntity().copy(weekSchedule = "AAARRRR", allowSkipDeload = true))
            val raw = origin.exportJson()
            val fields = Json.parseToJsonElement(raw).jsonObject.toMutableMap()
            val settings = fields.getValue("settings").jsonObject.toMutableMap()
            settings.remove("weekSchedule")
            settings.remove("dayOverrideEpoch")
            settings.remove("dayOverride")
            fields["settings"] = JsonObject(settings)
            WorkoutRepository(destination).importJson(JsonObject(fields).toString())
            assertEquals(YEAR1_WEEK, destination.dao().settings()?.weekSchedule)
            assertEquals(true, destination.dao().settings()?.allowSkipDeload)
        } finally {
            source.close()
            destination.close()
        }
    }

    @Test
    fun migrationFromVersion1KeepsSettings() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "v1-week.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        val sqlite = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file, null)
        sqlite.use { db ->
            V1_SCHEMA.forEach { db.execSQL(it) }
            db.execSQL(
                """
                INSERT INTO settings (
                    id, microPlates, allowSkipDeload, dbIncrement, barbellIncrement, latIncrement,
                    availableDb, availableBarbell, availableLat, writeHealthConnect
                ) VALUES (1, 0, 1, 5.0, 10.0, 17.0, '5,10', '45,135', '10,15', 0)
                """.trimIndent(),
            )
            db.version = 1
        }
        val database = WorkoutDatabase.create(context, name)
        try {
            val settings = database.dao().settings()
            assertEquals(true, settings?.allowSkipDeload)
            assertEquals(17.0, settings?.latIncrement ?: 0.0, 0.001)
            assertEquals(YEAR1_WEEK, settings?.weekSchedule)
            assertNull(settings?.dayOverrideEpoch)
            assertEquals(2, database.openHelper.writableDatabase.version)
        } finally {
            database.close()
        }
    }
}

private val V1_SCHEMA = listOf(
    "CREATE TABLE IF NOT EXISTS `slots` (`id` TEXT NOT NULL, `day` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, `name` TEXT NOT NULL, `kind` TEXT NOT NULL, `repLo` INTEGER NOT NULL, `repHi` INTEGER NOT NULL, `nSets` INTEGER NOT NULL, `rirFloor` INTEGER NOT NULL, `substitutes` TEXT NOT NULL, `isCarry` INTEGER NOT NULL, `isOptional` INTEGER NOT NULL, `tracksLoad` INTEGER NOT NULL, `equipment` TEXT NOT NULL, `perSide` INTEGER NOT NULL, `usesTrapBarRir` INTEGER NOT NULL, `repStep` INTEGER NOT NULL, `notes` TEXT NOT NULL, PRIMARY KEY(`id`))",
    "CREATE TABLE IF NOT EXISTS `slot_state` (`slotId` TEXT NOT NULL, `exerciseName` TEXT NOT NULL, `weight` REAL NOT NULL, `targetReps` TEXT NOT NULL, `missCount` INTEGER NOT NULL, `sessionsAtWeight` INTEGER NOT NULL, `lastIncreaseEpochDay` INTEGER, `extensionUsed` INTEGER NOT NULL, `loadBlockSessions` INTEGER NOT NULL, `unchangedStreak` INTEGER NOT NULL, `stallSessions` INTEGER NOT NULL, `ceilingStreak` INTEGER NOT NULL, `preferMicro` INTEGER NOT NULL, `lastLoggedReps` TEXT, `lastLoggedWeight` REAL, `lastLoggedTotalReps` INTEGER, PRIMARY KEY(`slotId`))",
    "CREATE TABLE IF NOT EXISTS `drafts` (`slotId` TEXT NOT NULL, `setsJson` TEXT NOT NULL, `painFlag` INTEGER NOT NULL, `note` TEXT NOT NULL, `postureClean` INTEGER NOT NULL, PRIMARY KEY(`slotId`))",
    "CREATE TABLE IF NOT EXISTS `sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `epochDay` INTEGER NOT NULL, `slotId` TEXT NOT NULL, `day` TEXT NOT NULL, `painFlag` INTEGER NOT NULL, `note` TEXT NOT NULL, `postureClean` INTEGER, `deload` INTEGER NOT NULL, `decision` TEXT NOT NULL, `reason` TEXT NOT NULL, `loadIncreased` INTEGER NOT NULL, `performedWeight` REAL NOT NULL)",
    "CREATE TABLE IF NOT EXISTS `sets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` INTEGER NOT NULL, `setNumber` INTEGER NOT NULL, `weight` REAL NOT NULL, `reps` INTEGER NOT NULL, `rir` INTEGER NOT NULL)",
    "CREATE TABLE IF NOT EXISTS `checkins` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `epochDay` INTEGER NOT NULL, `blockIndex` INTEGER NOT NULL, `weekOfBlock` INTEGER NOT NULL, `soreness` INTEGER NOT NULL, `performance` INTEGER NOT NULL, `readiness` INTEGER NOT NULL, `skippedDeload` INTEGER NOT NULL)",
    "CREATE TABLE IF NOT EXISTS `overrides` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `epochMillis` INTEGER NOT NULL, `slotId` TEXT NOT NULL, `suggestedWeight` REAL NOT NULL, `chosenWeight` REAL NOT NULL, `suggestedReps` TEXT NOT NULL, `chosenReps` TEXT NOT NULL)",
    "CREATE TABLE IF NOT EXISTS `program` (`id` INTEGER NOT NULL, `blockIndex` INTEGER NOT NULL, `weekOfBlock` INTEGER NOT NULL, `deloadActive` INTEGER NOT NULL, `deloadReason` TEXT, `year` INTEGER NOT NULL, `safetyAcknowledged` INTEGER NOT NULL, `week0Passes` INTEGER NOT NULL, `secondMissSlots` TEXT NOT NULL, `sessionReadiness` TEXT NOT NULL, `weekStartedEpochDay` INTEGER NOT NULL, PRIMARY KEY(`id`))",
    "CREATE TABLE IF NOT EXISTS `settings` (`id` INTEGER NOT NULL, `microPlates` INTEGER NOT NULL, `allowSkipDeload` INTEGER NOT NULL, `dbIncrement` REAL NOT NULL, `barbellIncrement` REAL NOT NULL, `latIncrement` REAL NOT NULL, `availableDb` TEXT NOT NULL, `availableBarbell` TEXT NOT NULL, `availableLat` TEXT NOT NULL, `writeHealthConnect` INTEGER NOT NULL, PRIMARY KEY(`id`))",
    "CREATE TABLE IF NOT EXISTS `workout_days` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `epochDay` INTEGER NOT NULL, `day` TEXT NOT NULL, `durationMin` INTEGER NOT NULL, `readiness` INTEGER, `wroteHealthConnect` INTEGER NOT NULL, `deload` INTEGER NOT NULL)",
    "CREATE TABLE IF NOT EXISTS `swims` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `epochDay` INTEGER NOT NULL, `durationMin` INTEGER NOT NULL, `pool` INTEGER NOT NULL, `wroteHealthConnect` INTEGER NOT NULL)",
    "CREATE TABLE IF NOT EXISTS `phase_notes` (`year` INTEGER NOT NULL, `title` TEXT NOT NULL, `body` TEXT NOT NULL, PRIMARY KEY(`year`))",
    "CREATE TABLE IF NOT EXISTS `slot_overrides` (`slotId` TEXT NOT NULL, `repLo` INTEGER, `repHi` INTEGER, `nSets` INTEGER, `name` TEXT, PRIMARY KEY(`slotId`))",
    "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
    "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '3bc67919ef61b33f8d2280c34cecf152')",
)

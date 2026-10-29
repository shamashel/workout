package com.shamashel.workout.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM program WHERE id = 1")
    suspend fun program(): ProgramEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgram(entity: ProgramEntity)

    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun settings(): SettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(entity: SettingsEntity)

    @Query("SELECT * FROM slots ORDER BY day, sortOrder")
    suspend fun slots(): List<SlotEntity>

    @Query("SELECT * FROM slots WHERE id = :id")
    suspend fun slot(id: String): SlotEntity?

    @Query("UPDATE slots SET id = :to WHERE id = :from")
    suspend fun renameSlotRow(from: String, to: String)

    @Query("UPDATE slots SET day = :day WHERE id = :id")
    suspend fun setSlotDay(id: String, day: String)

    @Query("UPDATE slot_state SET slotId = :to WHERE slotId = :from")
    suspend fun renameState(from: String, to: String)

    @Query("UPDATE drafts SET slotId = :to WHERE slotId = :from")
    suspend fun renameDraft(from: String, to: String)

    @Query("UPDATE sessions SET slotId = :to WHERE slotId = :from")
    suspend fun renameSessions(from: String, to: String)

    @Query("UPDATE overrides SET slotId = :to WHERE slotId = :from")
    suspend fun renameOverrides(from: String, to: String)

    @Query("UPDATE slot_overrides SET slotId = :to WHERE slotId = :from")
    suspend fun renameSlotOverride(from: String, to: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSlots(entities: List<SlotEntity>)

    @Query("SELECT * FROM slot_state")
    suspend fun states(): List<SlotStateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveState(entity: SlotStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveStates(entities: List<SlotStateEntity>)

    @Query("SELECT * FROM drafts")
    suspend fun drafts(): List<DraftEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDraft(entity: DraftEntity)

    @Query("DELETE FROM drafts WHERE slotId = :slotId")
    suspend fun deleteDraft(slotId: String)

    @Query("DELETE FROM drafts WHERE slotId IN (:ids)")
    suspend fun deleteDrafts(ids: List<String>)

    @Insert
    suspend fun insertSession(entity: SessionEntity): Long

    @Insert
    suspend fun insertSets(entities: List<SetEntity>)

    @Query("SELECT * FROM sessions WHERE slotId = :slotId ORDER BY epochDay, id")
    suspend fun sessionsForSlot(slotId: String): List<SessionEntity>

    @Query("SELECT * FROM sets WHERE sessionId = :sessionId ORDER BY setNumber")
    suspend fun setsForSession(sessionId: Long): List<SetEntity>

    @Query("SELECT * FROM sessions WHERE epochDay >= :from ORDER BY epochDay, id")
    suspend fun sessionsSince(from: Long): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE slotId = :slotId AND painFlag >= 2 AND epochDay >= :from AND epochDay < :before")
    suspend fun moderatePainBefore(slotId: String, from: Long, before: Long): List<SessionEntity>

    @Insert
    suspend fun insertCheckIn(entity: CheckInEntity)

    @Query("SELECT * FROM checkins WHERE blockIndex = :block ORDER BY id")
    suspend fun checkInsForBlock(block: Int): List<CheckInEntity>

    @Query("SELECT * FROM checkins ORDER BY epochDay DESC LIMIT 1")
    suspend fun latestCheckIn(): CheckInEntity?

    @Insert
    suspend fun insertOverride(entity: OverrideEntity)

    @Insert
    suspend fun insertDay(entity: WorkoutDayEntity)

    @Insert
    suspend fun insertSwim(entity: SwimEntity)

    @Query("SELECT * FROM phase_notes ORDER BY year")
    suspend fun phases(): List<PhaseNoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePhase(entity: PhaseNoteEntity)

    @Query("SELECT * FROM slot_overrides")
    suspend fun slotOverrides(): List<SlotOverrideEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSlotOverride(entity: SlotOverrideEntity)

    @Query("DELETE FROM slots")
    suspend fun clearSlots()

    @Query("DELETE FROM slot_state")
    suspend fun clearStates()

    @Query("DELETE FROM drafts")
    suspend fun clearDrafts()

    @Query("DELETE FROM sessions")
    suspend fun clearSessions()

    @Query("DELETE FROM sets")
    suspend fun clearSets()

    @Query("DELETE FROM checkins")
    suspend fun clearCheckIns()

    @Query("DELETE FROM overrides")
    suspend fun clearOverrides()

    @Query("DELETE FROM workout_days")
    suspend fun clearDays()

    @Query("DELETE FROM swims")
    suspend fun clearSwims()

    @Query("DELETE FROM phase_notes")
    suspend fun clearPhases()

    @Query("DELETE FROM slot_overrides")
    suspend fun clearSlotOverrides()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(entities: List<SessionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCheckIns(entities: List<CheckInEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOverrides(entities: List<OverrideEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDays(entities: List<WorkoutDayEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSwims(entities: List<SwimEntity>)

    @Query("SELECT * FROM sessions ORDER BY id")
    suspend fun allSessions(): List<SessionEntity>

    @Query("SELECT * FROM sets ORDER BY id")
    suspend fun allSets(): List<SetEntity>

    @Query("SELECT * FROM checkins ORDER BY id")
    suspend fun allCheckIns(): List<CheckInEntity>

    @Query("SELECT * FROM overrides ORDER BY id")
    suspend fun allOverrides(): List<OverrideEntity>

    @Query("SELECT * FROM workout_days ORDER BY id")
    suspend fun allDays(): List<WorkoutDayEntity>

    @Query("SELECT * FROM swims ORDER BY id")
    suspend fun allSwims(): List<SwimEntity>
}

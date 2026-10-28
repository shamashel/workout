package com.shamashel.workout.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "slots")
data class SlotEntity(
    @PrimaryKey val id: String,
    val day: String,
    val sortOrder: Int,
    val name: String,
    val kind: String,
    val repLo: Int,
    val repHi: Int,
    val nSets: Int,
    val rirFloor: Int,
    val substitutes: String,
    val isCarry: Boolean,
    val isOptional: Boolean,
    val tracksLoad: Boolean,
    val equipment: String,
    val perSide: Boolean,
    val usesTrapBarRir: Boolean,
    val repStep: Int,
    val notes: String,
)

@Serializable
@Entity(tableName = "slot_state")
data class SlotStateEntity(
    @PrimaryKey val slotId: String,
    val exerciseName: String,
    val weight: Double,
    val targetReps: String,
    val missCount: Int,
    val sessionsAtWeight: Int,
    val lastIncreaseEpochDay: Long?,
    val extensionUsed: Boolean,
    val loadBlockSessions: Int,
    val unchangedStreak: Int,
    val stallSessions: Int,
    val ceilingStreak: Int,
    val preferMicro: Boolean,
    val lastLoggedReps: String?,
    val lastLoggedWeight: Double?,
    val lastLoggedTotalReps: Int?,
)

@Serializable
@Entity(tableName = "drafts")
data class DraftEntity(
    @PrimaryKey val slotId: String,
    val setsJson: String,
    val painFlag: Int,
    val note: String,
    val postureClean: Boolean,
)

@Serializable
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val slotId: String,
    val day: String,
    val painFlag: Int,
    val note: String,
    val postureClean: Boolean?,
    val deload: Boolean,
    val decision: String,
    val reason: String,
    val loadIncreased: Boolean,
    val performedWeight: Double,
)

@Serializable
@Entity(tableName = "sets")
data class SetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val setNumber: Int,
    val weight: Double,
    val reps: Int,
    val rir: Int,
)

@Serializable
@Entity(tableName = "checkins")
data class CheckInEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val blockIndex: Int,
    val weekOfBlock: Int,
    val soreness: Int,
    val performance: Int,
    val readiness: Int,
    val skippedDeload: Boolean,
)

@Serializable
@Entity(tableName = "overrides")
data class OverrideEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochMillis: Long,
    val slotId: String,
    val suggestedWeight: Double,
    val chosenWeight: Double,
    val suggestedReps: String,
    val chosenReps: String,
)

@Serializable
@Entity(tableName = "program")
data class ProgramEntity(
    @PrimaryKey val id: Int = 1,
    val blockIndex: Int,
    val weekOfBlock: Int,
    val deloadActive: Boolean,
    val deloadReason: String?,
    val year: Int,
    val safetyAcknowledged: Boolean,
    val week0Passes: Int,
    val secondMissSlots: String,
    val sessionReadiness: String,
    val weekStartedEpochDay: Long,
)

@Serializable
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    val microPlates: Boolean,
    val allowSkipDeload: Boolean,
    val dbIncrement: Double,
    val barbellIncrement: Double,
    val latIncrement: Double,
    val availableDb: String,
    val availableBarbell: String,
    val availableLat: String,
    val writeHealthConnect: Boolean,
    val weekSchedule: String = YEAR1_WEEK,
    val dayOverrideEpoch: Long? = null,
    val dayOverride: String? = null,
)

@Serializable
@Entity(tableName = "workout_days")
data class WorkoutDayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val day: String,
    val durationMin: Int,
    val readiness: Int?,
    val wroteHealthConnect: Boolean,
    val deload: Boolean,
)

@Serializable
@Entity(tableName = "swims")
data class SwimEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val durationMin: Int,
    val pool: Boolean,
    val wroteHealthConnect: Boolean,
)

@Serializable
@Entity(tableName = "phase_notes")
data class PhaseNoteEntity(
    @PrimaryKey val year: Int,
    val title: String,
    val body: String,
)

@Serializable
@Entity(tableName = "slot_overrides")
data class SlotOverrideEntity(
    @PrimaryKey val slotId: String,
    val repLo: Int?,
    val repHi: Int?,
    val nSets: Int?,
    val name: String?,
)

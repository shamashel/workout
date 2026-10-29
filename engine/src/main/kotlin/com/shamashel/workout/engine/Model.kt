package com.shamashel.workout.engine

import java.time.LocalDate

enum class Equipment {
    DUMBBELL,
    BARBELL,
    MACHINE,
    BODYWEIGHT,
}

enum class SlotKind {
    GOBLET_SQUAT,
    DB_PRESS,
    DB_ROW,
    FARMER_CARRY,
    DEAD_BUG,
    TRAP_BAR_DEADLIFT,
    DB_BENCH,
    LAT_PULLDOWN,
    SUITCASE_CARRY,
    PLANK,
    PAUSE_GOBLET_SQUAT,
    BARBELL_RDL,
    LEG_CURL,
    STEP_UP,
    DEEP_SQUAT_HOLD,
}

/**
 * One exercise slot in the locked plan. Progression state lives in [SlotState],
 * not here, so a definition can be re-locked without wiping logs.
 */
data class SlotDefinition(
    val id: String,
    val day: Char,
    val sortOrder: Int,
    val name: String,
    val kind: SlotKind,
    val repLo: Int,
    val repHi: Int,
    val nSets: Int,
    val rirFloor: Int,
    val substitutes: List<String>,
    val isCarry: Boolean,
    val isOptional: Boolean,
    val tracksLoad: Boolean,
    val equipment: Equipment,
    val perSide: Boolean,
    val usesTrapBarRir: Boolean,
    val repStep: Int,
    val notes: String,
)

data class SetLog(
    val reps: Int,
    val rir: Int,
    val weight: Double,
)

data class SessionLog(
    val sets: List<SetLog>,
    val painFlag: Int,
    val postureClean: Boolean?,
    val note: String = "",
)

data class SlotState(
    val slotId: String,
    val exerciseName: String,
    val weight: Double,
    val targetReps: List<Int>,
    val missCount: Int = 0,
    val sessionsAtWeight: Int = 0,
    val lastIncreaseDate: LocalDate? = null,
    val extensionUsed: Boolean = false,
    val loadBlockSessions: Int = 0,
    val unchangedStreak: Int = 0,
    val stallSessions: Int = 0,
    val ceilingStreak: Int = 0,
    val preferMicro: Boolean = false,
    val lastLoggedReps: List<Int>? = null,
    val lastLoggedWeight: Double? = null,
    val lastLoggedTotalReps: Int? = null,
)

data class EngineSettings(
    val microPlates: Boolean = false,
    val allowSkipDeload: Boolean = false,
    val dbIncrement: Double = 5.0,
    val barbellIncrement: Double = 10.0,
    val latIncrement: Double = 5.0,
    val availableDb: List<Double> = defaultDbWeights(micro = false),
    val availableBarbell: List<Double> = defaultBarbellWeights(),
    val availableLat: List<Double> = defaultLatWeights(),
)

data class EvalRequest(
    val slot: SlotDefinition,
    val state: SlotState,
    val log: SessionLog,
    val settings: EngineSettings,
    val date: LocalDate,
    val weekOfBlock: Int,
    val deloadActive: Boolean = false,
    val suppressCarryIncrease: Boolean = false,
    val recentModeratePainDates: List<LocalDate> = emptyList(),
)

enum class Decision {
    IN_RANGE,
    EARNED_INCREASE,
    EXTEND_REPS,
    TOO_EASY_INCREASE,
    MISS_REPEAT,
    MISS_RESET,
    PAIN_HOLD,
    CARRY_HELD_FOR_DEADLIFT,
    DELOAD_LOGGED,
    BODYWEIGHT_LOGGED,
    NO_COACHING,
}

enum class PromptKind {
    PLATEAU_RECOVERY,
    PLATEAU_MICRO,
    PLATEAU_SWAP,
    GOBLET_CEILING,
    SUBSTITUTE,
    DOCTOR,
    EARLY_DELOAD,
}

data class Prompt(
    val kind: PromptKind,
    val message: String,
)

data class Suggestion(
    val weight: Double,
    val targetReps: List<Int>,
    val sets: Int,
    val targetRirLabel: String,
    val targetRirGate: Int,
    val reason: String,
    val deload: Boolean,
)

data class SessionResult(
    val state: SlotState,
    val decision: Decision,
    val reason: String,
    val suggestion: Suggestion?,
    val prompts: List<Prompt>,
    val earlyDeloadReasons: List<String>,
    val secondConsecutiveMiss: Boolean,
    val loadIncreased: Boolean,
    val stopSession: Boolean,
    val medicalMessage: String?,
    val targetRirLabel: String,
    val nextRirLabel: String,
    val followingDeload: Suggestion? = null,
)

data class RirTarget(
    val gate: Int,
    val label: String,
    val deload: Boolean,
)

const val MEDICAL_STOP_MESSAGE =
    "Stop the session and seek medical care. Numbness, tingling, chest pain, dizziness, or sudden sharp pain needs a clinician, not a training plan."

const val SAFETY_COPY = "Not medical advice. Check with your doctor before ramping up."

const val DOCTOR_PROMPT =
    "This slot has been moderately painful more than once in two weeks. Talk to your doctor or a physio before you keep loading it."

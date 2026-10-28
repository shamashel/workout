package com.shamashel.workout.data

data class TodaySlot(
    val id: String,
    val name: String,
    val optional: Boolean,
    val perSide: Boolean,
    val isCarry: Boolean,
    val tracksLoad: Boolean,
    val notes: String,
    val prescription: String,
    val rirLabel: String,
    val sets: Int,
    val weight: Double,
    val targetReps: List<Int>,
    val drafted: Boolean,
    val loggedToday: Boolean,
)

data class HomeSnapshot(
    val day: Char,
    val dateLabel: String,
    val headline: String,
    val weekLabel: String,
    val rirNote: String,
    val deloadBanner: String?,
    val slots: List<TodaySlot>,
    val needsWeights: Boolean,
    val checkInDue: Boolean,
    val showSafety: Boolean,
    val week0Passes: Int,
    val isRestDay: Boolean,
    val restNote: String?,
    val usualDay: Char = 'A',
    val todayOnly: Boolean = false,
)

data class DraftSet(
    val weight: Double,
    val reps: Int,
    val rir: Int,
)

data class SummaryRow(
    val slotId: String,
    val name: String,
    val reason: String,
    val weightLabel: String,
    val repsLabel: String,
    val rirLabel: String,
    val prompts: List<String>,
    val suggestedWeight: Double,
    val suggestedReps: List<Int>,
    val coaching: Boolean,
)

enum class HealthSaveStatus {
    OFF,
    WRITTEN,
    SKIPPED,
}

fun healthSaveStatus(writingEnabled: Boolean, writeError: String?): HealthSaveStatus = when {
    !writingEnabled -> HealthSaveStatus.OFF
    writeError == null -> HealthSaveStatus.WRITTEN
    else -> HealthSaveStatus.SKIPPED
}

data class FinishOutcome(
    val rows: List<SummaryRow>,
    val medicalMessage: String?,
    val deloadBanner: String?,
    val healthNote: String?,
    val healthStatus: HealthSaveStatus = HealthSaveStatus.OFF,
)

data class HistoryPoint(
    val dateLabel: String,
    val weight: Double,
    val reps: Int,
    val estimatedReps: Int,
    val rirLabel: String,
    val deload: Boolean,
    val pain: Int,
    val reason: String,
)

data class HistorySnapshot(
    val slotId: String,
    val name: String,
    val points: List<HistoryPoint>,
)

data class CheckInOutcome(
    val offerSkip: Boolean,
    val messages: List<String>,
)

data class StartingSlot(
    val id: String,
    val name: String,
    val hint: String,
    val weight: Double,
)

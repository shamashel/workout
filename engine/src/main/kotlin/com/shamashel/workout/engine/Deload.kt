package com.shamashel.workout.engine

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class PainEvent(
    val slotId: String,
    val flag: Int,
    val date: LocalDate,
)

data class WeekSignals(
    val secondMissSlots: Set<String> = emptySet(),
    val sessionReadiness: List<Int> = emptyList(),
    val painEvents: List<PainEvent> = emptyList(),
)

data class WeeklyScore(
    val soreness: Int,
    val performance: Int,
    val readiness: Int,
)

data class ProgramClock(
    val blockIndex: Int = 1,
    val weekOfBlock: Int = 1,
    val deloadActive: Boolean = false,
    val deloadReason: String? = null,
    val year: Int = 1,
)

data class AdvanceResult(
    val clock: ProgramClock,
    val resetMissCounts: Boolean,
    val clearWeekSignals: Boolean,
    val offerSkip: Boolean,
    val messages: List<String>,
)

fun immediateDeloadReasons(signals: WeekSignals, today: LocalDate): List<String> {
    val reasons = mutableListOf<String>()
    if (signals.secondMissSlots.size >= 2) {
        reasons += "Two slots each missed twice in a row this week."
    }
    if (signals.sessionReadiness.count { it <= 2 } >= 2) {
        reasons += "Readiness was 2/5 or lower on two sessions this week."
    }
    val recentPain = signals.painEvents.any { event ->
        event.flag >= 2 && ChronoUnit.DAYS.between(event.date, today) in 0..7
    }
    if (recentPain) {
        reasons += "A pain flag of 2 or 3 was logged in the last 7 days."
    }
    return reasons
}

fun checkInDeloadReasons(score: WeeklyScore, signals: WeekSignals, today: LocalDate): List<String> {
    val reasons = immediateDeloadReasons(signals, today).toMutableList()
    if (score.performance >= 4) {
        reasons += "Performance score was 4: this week did not match last week."
    }
    if (score.readiness <= 2) {
        reasons += "Weekly readiness was ${score.readiness}/5."
    }
    return reasons.distinct()
}

/**
 * Last two weekly scores must each be at or under 2, with no pain.
 * Requiring each week (not the average) is the stricter reading, so a 1 and a 3 do not average their way into a skip.
 */
fun skipDeloadEligible(
    settings: EngineSettings,
    olderWeek: WeeklyScore?,
    newerWeek: WeeklyScore?,
    anyPain: Boolean,
): Boolean {
    if (!settings.allowSkipDeload) return false
    if (anyPain) return false
    if (olderWeek == null || newerWeek == null) return false
    return olderWeek.performance <= 2 && olderWeek.soreness <= 2 &&
        newerWeek.performance <= 2 && newerWeek.soreness <= 2
}

fun activateEarlyDeload(clock: ProgramClock, reason: String): ProgramClock {
    if (clock.deloadActive || clock.weekOfBlock >= 4) {
        return clock.copy(deloadReason = clock.deloadReason ?: reason)
    }
    return clock.copy(deloadActive = true, weekOfBlock = 4, deloadReason = reason)
}

fun onCheckIn(
    clock: ProgramClock,
    score: WeeklyScore,
    signals: WeekSignals,
    settings: EngineSettings,
    today: LocalDate,
    previousWeekScore: WeeklyScore?,
    anyPainLastTwoWeeks: Boolean,
    skipChoice: Boolean?,
): AdvanceResult {
    val triggers = checkInDeloadReasons(score, signals, today)
    val inDeload = clock.deloadActive || clock.weekOfBlock >= 4

    if (inDeload) {
        if (triggers.isNotEmpty()) {
            val reason = triggers.joinToString(" ")
            return AdvanceResult(
                clock = clock.copy(deloadActive = true, weekOfBlock = 4, deloadReason = reason),
                resetMissCounts = true,
                clearWeekSignals = true,
                offerSkip = false,
                messages = listOf("Deload week stays in place. $reason"),
            )
        }
        return AdvanceResult(
            clock = ProgramClock(
                blockIndex = clock.blockIndex + 1,
                weekOfBlock = 1,
                deloadActive = false,
                deloadReason = null,
                year = clock.year,
            ),
            resetMissCounts = true,
            clearWeekSignals = true,
            offerSkip = false,
            messages = listOf("Deload done. Next block starts at the pre-deload working weights."),
        )
    }

    if (triggers.isNotEmpty()) {
        val reason = triggers.joinToString(" ")
        return AdvanceResult(
            clock = activateEarlyDeload(clock, reason),
            resetMissCounts = false,
            clearWeekSignals = true,
            offerSkip = false,
            messages = listOf("Early deload. $reason"),
        )
    }

    if (clock.weekOfBlock >= 3) {
        val eligible = skipDeloadEligible(settings, previousWeekScore, score, anyPainLastTwoWeeks)
        if (eligible && skipChoice == null) {
            return AdvanceResult(
                clock = clock,
                resetMissCounts = false,
                clearWeekSignals = false,
                offerSkip = true,
                messages = listOf("The last two weeks were fresh and pain-free. Skip this deload?"),
            )
        }
        if (eligible && skipChoice == true) {
            return AdvanceResult(
                clock = ProgramClock(
                    blockIndex = clock.blockIndex + 1,
                    weekOfBlock = 1,
                    deloadActive = false,
                    deloadReason = null,
                    year = clock.year,
                ),
                resetMissCounts = true,
                clearWeekSignals = true,
                offerSkip = false,
                messages = listOf("Deload skipped. The choice is logged. Next block starts at the week-3 weights."),
            )
        }
        return AdvanceResult(
            clock = clock.copy(
                weekOfBlock = 4,
                deloadActive = true,
                deloadReason = "Scheduled deload, week 4 of the block.",
            ),
            resetMissCounts = false,
            clearWeekSignals = true,
            offerSkip = false,
            messages = listOf("Week 4 is a deload: fewer sets, about 90% load, RIR 4+."),
        )
    }

    return AdvanceResult(
        clock = clock.copy(weekOfBlock = clock.weekOfBlock + 1, deloadActive = false, deloadReason = null),
        resetMissCounts = false,
        clearWeekSignals = true,
        offerSkip = false,
        messages = emptyList(),
    )
}

fun noteSecondMiss(signals: WeekSignals, slotId: String): WeekSignals {
    return signals.copy(secondMissSlots = signals.secondMissSlots + slotId)
}

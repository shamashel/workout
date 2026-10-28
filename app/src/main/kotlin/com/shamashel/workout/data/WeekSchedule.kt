package com.shamashel.workout.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Year 1 lifting week, Monday through Sunday.
 * Tuesday and Thursday were swim days, Saturday a long walk, Sunday rest.
 * Those four are rest here: the schedule only chooses a lifting day or rest.
 * The sequence is Day A, rest, Day B, rest, Day C, rest, rest.
 */
const val YEAR1_WEEK = "ARBRCRR"

const val START_FRESH_BODY =
    "Today becomes Day A. Tomorrow follows the usual pattern: rest, Day B, rest, Day C, then two rest days. Sessions already logged keep their day."

enum class WeekdayAssignment {
    A,
    B,
    C,
    REST,
    ;

    val code: Char
        get() = when (this) {
            A -> 'A'
            B -> 'B'
            C -> 'C'
            REST -> 'R'
        }

    val chip: String
        get() = when (this) {
            A -> "A"
            B -> "B"
            C -> "C"
            REST -> "Rest"
        }

    val spoken: String
        get() = when (this) {
            A -> "Day A"
            B -> "Day B"
            C -> "Day C"
            REST -> "Rest"
        }

    companion object {
        fun fromCode(code: Char): WeekdayAssignment? = when (code) {
            'A' -> A
            'B' -> B
            'C' -> C
            'R' -> REST
            else -> null
        }
    }
}

data class WeekSchedule(val days: List<WeekdayAssignment>) {
    init {
        require(days.size == 7) { "A week has 7 days." }
    }

    fun on(date: LocalDate): WeekdayAssignment = on(date.dayOfWeek)

    fun on(weekday: DayOfWeek): WeekdayAssignment = days[weekday.value - 1]

    fun with(weekday: DayOfWeek, assignment: WeekdayAssignment): WeekSchedule {
        val updated = days.toMutableList()
        updated[weekday.value - 1] = assignment
        return WeekSchedule(updated)
    }

    fun encode(): String = days.joinToString(separator = "") { it.code.toString() }

    /** The next lifting day after [from], wrapping into the following week. */
    fun nextStrength(from: LocalDate): Pair<LocalDate, WeekdayAssignment>? {
        for (offset in 1..7) {
            val date = from.plusDays(offset.toLong())
            val assignment = on(date)
            if (assignment != WeekdayAssignment.REST) return date to assignment
        }
        return null
    }

    /**
     * Rotate the Year 1 pattern so [today] is Day A and the following weekdays
     * take the next slots of that pattern.
     */
    fun reanchored(today: LocalDate): WeekSchedule {
        val todayIndex = today.dayOfWeek.value - 1
        val rotated = List(7) { index -> days[(index - todayIndex).mod(7)] }
        return WeekSchedule(rotated)
    }

    companion object {
        val year1: WeekSchedule = decode(YEAR1_WEEK)

        fun decode(raw: String?): WeekSchedule {
            val parsed = raw?.map { WeekdayAssignment.fromCode(it) }
            if (parsed == null || parsed.size != 7 || parsed.any { it == null }) return year1Fallback()
            return WeekSchedule(parsed.filterNotNull())
        }

        private fun year1Fallback(): WeekSchedule {
            return WeekSchedule(listOf(
                WeekdayAssignment.A,
                WeekdayAssignment.REST,
                WeekdayAssignment.B,
                WeekdayAssignment.REST,
                WeekdayAssignment.C,
                WeekdayAssignment.REST,
                WeekdayAssignment.REST,
            ))
        }
    }
}

fun headlineFor(day: WeekdayAssignment): String = when (day) {
    WeekdayAssignment.A -> "Day A · Squat, push, pull, carry"
    WeekdayAssignment.B -> "Day B · Hinge, push, pull, carry"
    WeekdayAssignment.C -> "Day C · Pause squat, hinge, pull"
    WeekdayAssignment.REST -> "Rest"
}

fun restNoteFor(day: WeekdayAssignment, schedule: WeekSchedule, date: LocalDate): String? {
    if (day != WeekdayAssignment.REST) return null
    val next = schedule.nextStrength(date) ?: return "Rest. No lifting day is on this week."
    val whenLabel = if (next.first == date.plusDays(1)) {
        "Tomorrow"
    } else {
        next.first.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US)
    }
    return "Rest. $whenLabel is ${next.second.spoken}."
}

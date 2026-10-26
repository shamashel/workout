package com.shamashel.workout.engine

internal const val WEIGHT_EPS = 1e-6

fun defaultDbWeights(micro: Boolean): List<Double> {
    val step = if (micro) 2.5 else 5.0
    val start = if (micro) 2.5 else 5.0
    return generateSequence(start) { it + step }
        .takeWhile { it <= 150.0 + WEIGHT_EPS }
        .map { snapLb(it) }
        .toList()
}

/** 45 lb bar, then 5 lb steps through 545. The 5 lb grid is what lets a 10% cut from 135 land on 120. */
fun defaultBarbellWeights(): List<Double> {
    return generateSequence(45.0) { it + 5.0 }
        .takeWhile { it <= 545.0 + WEIGHT_EPS }
        .map { snapLb(it) }
        .toList()
}

fun defaultLatWeights(): List<Double> {
    return generateSequence(10.0) { it + 5.0 }
        .takeWhile { it <= 200.0 + WEIGHT_EPS }
        .map { snapLb(it) }
        .toList()
}

fun snapLb(weight: Double): Double = kotlin.math.round(weight * 10.0) / 10.0

fun weightsEqual(a: Double?, b: Double?): Boolean {
    if (a == null || b == null) return a == b
    return kotlin.math.abs(a - b) <= 0.05
}

fun formatLb(weight: Double): String {
    val text = if (kotlin.math.abs(weight - weight.toInt()) < 0.001) {
        weight.toInt().toString()
    } else {
        String.format(java.util.Locale.US, "%.1f", weight)
    }
    return "$text lb"
}

fun formatSignedLb(delta: Double): String {
    val text = if (kotlin.math.abs(delta - delta.toInt()) < 0.001) {
        delta.toInt().toString()
    } else {
        String.format(java.util.Locale.US, "%.1f", delta)
    }
    return "+$text lb"
}

fun formatReps(reps: List<Int>): String = reps.joinToString(" / ")

fun formatAvg(value: Double): String {
    val rounded = kotlin.math.round(value * 10.0) / 10.0
    return if (kotlin.math.abs(rounded - rounded.toInt()) < 0.001) {
        rounded.toInt().toString()
    } else {
        String.format(java.util.Locale.US, "%.1f", rounded)
    }
}

fun roundDownToAvailable(value: Double, available: List<Double>): Double {
    val sorted = available.map { snapLb(it) }.distinct().sorted()
    if (sorted.isEmpty()) return snapLb(value)
    return sorted.filter { it <= value + 0.05 }.maxOrNull() ?: sorted.first()
}

/** Nearest available weight. A tie rounds down so a deload never rounds up. */
fun roundNearestAvailable(value: Double, available: List<Double>): Double {
    val sorted = available.map { snapLb(it) }.distinct().sorted()
    if (sorted.isEmpty()) return snapLb(value)
    return sorted.minWith(compareBy<Double> { kotlin.math.abs(it - value) }.thenBy { it })
}

/**
 * One increment, and only if that weight is actually available.
 * Returns null when the next step is not in the list (never skips ahead two steps).
 */
fun addOneStep(weight: Double, increment: Double, available: List<Double>): Double? {
    if (increment <= WEIGHT_EPS) return null
    val target = snapLb(weight + increment)
    val candidates = available
        .map { snapLb(it) }
        .filter { it > weight + 0.05 && it <= target + 0.05 }
    return candidates.maxOrNull()
}

fun availableFor(slot: SlotDefinition, settings: EngineSettings): List<Double> {
    return when (slot.equipment) {
        Equipment.DUMBBELL -> settings.availableDb
        Equipment.BARBELL -> settings.availableBarbell
        Equipment.MACHINE -> settings.availableLat
        Equipment.BODYWEIGHT -> emptyList()
    }
}

fun baseIncrement(slot: SlotDefinition, settings: EngineSettings): Double {
    return when (slot.equipment) {
        Equipment.DUMBBELL -> settings.dbIncrement
        Equipment.BARBELL -> settings.barbellIncrement
        Equipment.MACHINE -> settings.latIncrement
        Equipment.BODYWEIGHT -> 0.0
    }
}

fun effectiveIncrement(slot: SlotDefinition, state: SlotState, settings: EngineSettings): Double {
    val base = baseIncrement(slot, settings)
    if (!state.preferMicro || !settings.microPlates) return base
    val micro = when (slot.equipment) {
        Equipment.DUMBBELL -> minOf(base, 2.5)
        Equipment.BARBELL -> minOf(base, 5.0)
        Equipment.MACHINE -> minOf(base, 5.0)
        Equipment.BODYWEIGHT -> base
    }
    return if (micro + WEIGHT_EPS < base) micro else base
}

fun stepFraction(weight: Double, increment: Double): Double {
    if (weight <= WEIGHT_EPS) return Double.POSITIVE_INFINITY
    return increment / weight
}

fun EngineSettings.withMicroPlates(enabled: Boolean): EngineSettings {
    return if (enabled) {
        copy(
            microPlates = true,
            dbIncrement = 2.5,
            barbellIncrement = 5.0,
            availableDb = defaultDbWeights(micro = true),
        )
    } else {
        copy(
            microPlates = false,
            dbIncrement = 5.0,
            barbellIncrement = 10.0,
            availableDb = defaultDbWeights(micro = false),
        )
    }
}

package com.shamashel.workout.data

import com.shamashel.workout.engine.snapLb
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/** A pound value the way a lifter says it: 5, not 5.0. */
fun plainLb(weight: Double): String {
    val snapped = snapLb(weight)
    val whole = snapped.toInt()
    return if (abs(snapped - whole) < 0.001) {
        whole.toString()
    } else {
        String.format(Locale.US, "%.1f", snapped)
    }
}

fun lbPhrase(weight: Double): String = "${plainLb(weight)} lb"

fun encodeWeights(weights: List<Double>): String = weights.joinToString(",") { plainLb(it) }

fun sameWeights(left: List<Double>, right: List<Double>): Boolean {
    if (left.size != right.size) return false
    return left.zip(right).all { (a, b) -> abs(a - b) <= 0.05 }
}

data class RangeKit(
    val min: Double,
    val max: Double,
    val step: Double,
    val added: List<Double> = emptyList(),
    val removed: List<Double> = emptyList(),
) {
    fun weights(): List<Double> = expandRange(min, max, step, added, removed)

    fun preview(): String {
        val span = "${plainLb(min)} to ${plainLb(max)} lb in ${plainLb(step)} lb steps"
        val notes = buildList {
            if (added.isNotEmpty()) add("${added.size} added")
            if (removed.isNotEmpty()) add("${removed.size} removed")
        }
        return if (notes.isEmpty()) span else "$span, ${notes.joinToString(", ")}"
    }

    fun withMin(next: Double): RangeKit {
        val value = snapLb(next).coerceAtLeast(step).coerceAtMost(max)
        return copy(min = value)
    }

    fun withMax(next: Double): RangeKit {
        val value = snapLb(next).coerceAtLeast(min)
        return copy(max = value)
    }

    fun withStep(next: Double): RangeKit {
        val value = snapLb(next).coerceAtLeast(0.5)
        return copy(step = value, removed = emptyList())
    }

    fun addWeight(weight: Double): RangeKit {
        val value = snapLb(weight)
        if (value <= 0.0) return this
        val base = expandRange(min, max, step, emptyList(), emptyList())
        val inBase = base.any { abs(it - value) <= 0.05 }
        return if (inBase) {
            copy(removed = removed.filter { abs(it - value) > 0.05 })
        } else {
            copy(added = (added.map(::snapLb) + value).distinct())
        }
    }

    fun removeWeight(weight: Double): RangeKit {
        val value = snapLb(weight)
        val base = expandRange(min, max, step, emptyList(), emptyList())
        val inBase = base.any { abs(it - value) <= 0.05 }
        return if (inBase) {
            copy(
                removed = (removed.map(::snapLb) + value).distinct(),
                added = added.filter { abs(it - value) > 0.05 },
            )
        } else {
            copy(added = added.filter { abs(it - value) > 0.05 })
        }
    }
}

fun defaultDumbbells(micro: Boolean = false): RangeKit {
    return if (micro) RangeKit(2.5, 150.0, 2.5) else RangeKit(5.0, 150.0, 5.0)
}

fun defaultLatStack(): RangeKit = RangeKit(10.0, 200.0, 5.0)

fun expandRange(
    min: Double,
    max: Double,
    step: Double,
    added: List<Double>,
    removed: List<Double>,
): List<Double> {
    if (step <= 1e-6 || max + 0.05 < min) {
        return normalize(added)
    }
    val removedSet = removed.map(::snapLb)
    val base = generateSequence(snapLb(min)) { snapLb(it + step) }
        .takeWhile { it <= max + 0.05 }
        .filter { value -> removedSet.none { abs(it - value) <= 0.05 } }
        .toMutableList()
    normalize(added).forEach { value ->
        if (base.none { abs(it - value) <= 0.05 }) base.add(value)
    }
    return base.sorted()
}

fun inferRange(weights: List<Double>, fallback: RangeKit): RangeKit {
    val sorted = normalize(weights)
    if (sorted.isEmpty()) return fallback
    if (sorted.size == 1) return RangeKit(sorted.first(), sorted.first(), fallback.step)
    val tenths = sorted.map { (snapLb(it) * 10).roundToInt() }
    var divisor = 0
    for (index in 1 until tenths.size) {
        divisor = gcd(divisor, tenths[index] - tenths[index - 1])
    }
    val step = if (divisor <= 0) fallback.step else divisor / 10.0
    val min = sorted.first()
    val max = sorted.last()
    val expected = expandRange(min, max, step, emptyList(), emptyList())
    val removed = expected.filter { candidate -> sorted.none { abs(it - candidate) <= 0.05 } }
    val added = sorted.filter { value -> expected.none { abs(it - value) <= 0.05 } }
    return RangeKit(min, max, step, added, removed)
}

val PLATE_CHOICES: List<Double> = listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5, 1.25)

val DEFAULT_PLATES: Set<Double> = setOf(45.0, 25.0, 10.0, 5.0, 2.5)

data class BarKit(
    val bar: Double,
    val plates: Set<Double>,
    val max: Double,
    val custom: List<Double>? = null,
) {
    fun weights(): List<Double> = custom ?: loadableBar(bar, plates, max)

    fun preview(): String {
        val saved = custom
        if (saved != null && saved.isNotEmpty()) {
            return "Saved loads kept: ${saved.size} weights, ${plainLb(saved.first())} to ${plainLb(saved.last())} lb"
        }
        val smallest = plates.minOrNull()
        return if (smallest == null) {
            "${plainLb(bar)} lb bar, no plates"
        } else {
            "${plainLb(bar)} lb bar, ${plainLb(snapLb(smallest * 2))} lb jumps, up to ${plainLb(max)} lb"
        }
    }

    fun withBar(next: Double): BarKit = copy(bar = snapLb(next).coerceIn(15.0, 55.0), custom = null)

    fun withMax(next: Double): BarKit = copy(max = snapLb(next).coerceAtLeast(bar), custom = null)

    fun togglePlate(plate: Double): BarKit {
        val value = snapLb(plate)
        val next = if (plates.any { abs(it - value) <= 0.05 }) {
            plates.filter { abs(it - value) > 0.05 }.toSet()
        } else {
            plates + value
        }
        return copy(plates = next, custom = null)
    }

    fun usePlates(next: Set<Double>): BarKit = copy(plates = next, custom = null)
}

fun defaultBar(): BarKit = BarKit(45.0, DEFAULT_PLATES, 545.0)

/**
 * Totals you can load with a bar and pairs of the plates you own.
 * One plate of each size stands for a pair, so the jump is twice the smallest plate.
 */
fun loadableBar(bar: Double, plates: Set<Double>, max: Double): List<Double> {
    val clean = plates.map(::snapLb).filter { it > 0.0 }.distinct().sortedDescending()
    val start = snapLb(bar)
    if (clean.isEmpty()) {
        return listOf(start).filter { it <= max + 0.05 && it > 0.0 }
    }
    val sideMax = ((max - start) / 2.0).coerceAtLeast(0.0)
    var reachable = setOf(0.0)
    for (plate in clean) {
        val count = minOf(20, ceil(sideMax / plate).toInt().coerceAtLeast(0))
        val next = HashSet<Double>(reachable.size * (count + 1))
        for (have in reachable) {
            for (copies in 0..count) {
                val side = snapLb(have + copies * plate)
                if (side <= sideMax + 0.05) next.add(side)
            }
        }
        reachable = next
    }
    return reachable
        .map { snapLb(start + 2 * it) }
        .filter { it <= max + 0.05 && it > 0.0 }
        .distinct()
        .sorted()
}

fun inferBar(weights: List<Double>): BarKit {
    val sorted = normalize(weights)
    if (sorted.isEmpty()) return defaultBar()
    val bar = sorted.first()
    val max = sorted.last()
    if (sorted.size == 1) return BarKit(bar, emptySet(), max)
    if (sameWeights(loadableBar(bar, DEFAULT_PLATES, max), sorted)) {
        return BarKit(bar, DEFAULT_PLATES, max)
    }
    val match = plateSubsets()
        .filter { sameWeights(loadableBar(bar, it, max), sorted) }
        .maxByOrNull { it.size }
    return if (match != null) BarKit(bar, match, max) else BarKit(bar, DEFAULT_PLATES, max, custom = sorted)
}

fun plateSubsets(): List<Set<Double>> {
    val count = PLATE_CHOICES.size
    return (1 until (1 shl count)).map { mask ->
        PLATE_CHOICES.filterIndexed { index, _ -> mask and (1 shl index) != 0 }.toSet()
    }
}

data class SettingsDraft(
    val dumbbells: RangeKit,
    val bar: BarKit,
    val lat: RangeKit,
    val micro: Boolean,
    val dbIncrement: Double,
    val barIncrement: Double,
    val latIncrement: Double,
    val allowSkipDeload: Boolean,
    val writeHealthConnect: Boolean,
    val weekSchedule: String = YEAR1_WEEK,
    val dayOverrideEpoch: Long? = null,
    val dayOverride: String? = null,
) {
    fun toSettings(): SettingsEntity {
        return SettingsEntity(
            microPlates = micro,
            allowSkipDeload = allowSkipDeload,
            dbIncrement = dbIncrement,
            barbellIncrement = barIncrement,
            latIncrement = latIncrement,
            availableDb = encodeWeights(dumbbells.weights()),
            availableBarbell = encodeWeights(bar.weights()),
            availableLat = encodeWeights(lat.weights()),
            writeHealthConnect = writeHealthConnect,
            weekSchedule = weekSchedule,
            dayOverrideEpoch = dayOverrideEpoch,
            dayOverride = dayOverride,
        )
    }

    fun withMicro(enabled: Boolean): SettingsDraft {
        if (enabled == micro) return this
        val dbIsDefault = sameWeights(dumbbells.weights(), defaultDumbbells(micro).weights())
        return copy(
            micro = enabled,
            dumbbells = if (dbIsDefault) defaultDumbbells(enabled) else dumbbells,
            dbIncrement = if (enabled) 2.5 else 5.0,
            barIncrement = if (enabled) 5.0 else 10.0,
        )
    }

    fun resetEquipment(): SettingsDraft {
        return copy(
            dumbbells = defaultDumbbells(false),
            bar = defaultBar(),
            lat = defaultLatStack(),
            micro = false,
            dbIncrement = if (micro) 5.0 else dbIncrement,
            barIncrement = if (micro) 10.0 else barIncrement,
        )
    }

    fun resetProgression(): SettingsDraft {
        return copy(
            dbIncrement = if (micro) 2.5 else 5.0,
            barIncrement = if (micro) 5.0 else 10.0,
            latIncrement = 5.0,
        )
    }

    fun resetDeload(): SettingsDraft = copy(allowSkipDeload = false)

    fun resetHealth(): SettingsDraft = copy(writeHealthConnect = false)
}

fun SettingsEntity.toDraft(): SettingsDraft {
    return SettingsDraft(
        dumbbells = inferRange(parseWeights(availableDb), defaultDumbbells(microPlates)),
        bar = inferBar(parseWeights(availableBarbell)),
        lat = inferRange(parseWeights(availableLat), defaultLatStack()),
        micro = microPlates,
        dbIncrement = dbIncrement,
        barIncrement = barbellIncrement,
        latIncrement = latIncrement,
        allowSkipDeload = allowSkipDeload,
        writeHealthConnect = writeHealthConnect,
        weekSchedule = weekSchedule,
        dayOverrideEpoch = dayOverrideEpoch,
        dayOverride = dayOverride,
    )
}

private fun normalize(weights: List<Double>): List<Double> {
    return weights.map(::snapLb).filter { it > 0.0 }.distinct().sorted()
}

private fun gcd(left: Int, right: Int): Int {
    var a = abs(left)
    var b = abs(right)
    while (b != 0) {
        val remainder = a % b
        a = b
        b = remainder
    }
    return a
}

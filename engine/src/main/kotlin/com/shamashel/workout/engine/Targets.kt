package com.shamashel.workout.engine

/**
 * Week-of-block effort target.
 *
 * Week 2 dumbbell work is displayed as 2–3. The numeric gate stays at 3 so
 * `target - 1` is 2, the bottom of that band: anything under 2 RIR is too hard,
 * and an earned increase still needs an average of at least 2. That is the
 * conservative reading of "2–3", and it never raises the target inside the block
 * (week 1 is 3, week 2 is 3, week 3 is 2). Deload is a separate mode at 4+.
 */
fun rirTarget(slot: SlotDefinition, weekOfBlock: Int, deloadActive: Boolean): RirTarget {
    if (deloadActive || weekOfBlock >= 4) {
        return RirTarget(gate = 4, label = "4+", deload = true)
    }
    val scheduled = when (weekOfBlock) {
        1 -> RirTarget(gate = 3, label = "3", deload = false)
        2 -> if (slot.usesTrapBarRir) {
            RirTarget(gate = 3, label = "3", deload = false)
        } else {
            RirTarget(gate = 3, label = "2–3", deload = false)
        }
        3 -> RirTarget(gate = 2, label = "2", deload = false)
        else -> RirTarget(gate = 3, label = "3", deload = false)
    }
    if (scheduled.gate >= slot.rirFloor) return scheduled
    return scheduled.copy(gate = slot.rirFloor, label = slot.rirFloor.toString())
}

/** RIR the slot should aim for the next time it is trained, assuming one week passes. */
fun nextSessionRir(slot: SlotDefinition, weekOfBlock: Int, deloadActive: Boolean): RirTarget {
    if (deloadActive || weekOfBlock >= 4) return rirTarget(slot, 1, false)
    if (weekOfBlock >= 3) return rirTarget(slot, 4, true)
    return rirTarget(slot, weekOfBlock + 1, false)
}

fun repCap(slot: SlotDefinition, extensionUsed: Boolean): Int {
    if (!extensionUsed) return slot.repHi
    return if (slot.isCarry) slot.repHi + 15 else minOf(slot.repHi + 3, 15)
}

fun extensionCap(slot: SlotDefinition): Int {
    return if (slot.isCarry) slot.repHi + 15 else minOf(slot.repHi + 3, 15)
}

package com.shamashel.workout.data

import com.shamashel.workout.engine.EngineSettings
import com.shamashel.workout.engine.Equipment
import com.shamashel.workout.engine.ProgramClock
import com.shamashel.workout.engine.SlotDefinition
import com.shamashel.workout.engine.SlotKind
import com.shamashel.workout.engine.SlotState
import com.shamashel.workout.engine.defaultBarbellWeights
import com.shamashel.workout.engine.defaultDbWeights
import com.shamashel.workout.engine.defaultLatWeights
import java.time.LocalDate

internal fun SlotDefinition.toEntity(): SlotEntity {
    return SlotEntity(
        id = id,
        day = day.toString(),
        sortOrder = sortOrder,
        name = name,
        kind = kind.name,
        repLo = repLo,
        repHi = repHi,
        nSets = nSets,
        rirFloor = rirFloor,
        substitutes = substitutes.joinToString("|"),
        isCarry = isCarry,
        isOptional = isOptional,
        tracksLoad = tracksLoad,
        equipment = equipment.name,
        perSide = perSide,
        usesTrapBarRir = usesTrapBarRir,
        repStep = repStep,
        notes = notes,
    )
}

internal fun SlotEntity.toDefinition(): SlotDefinition {
    return SlotDefinition(
        id = id,
        day = day.first(),
        sortOrder = sortOrder,
        name = name,
        kind = SlotKind.valueOf(kind),
        repLo = repLo,
        repHi = repHi,
        nSets = nSets,
        rirFloor = rirFloor,
        substitutes = if (substitutes.isBlank()) emptyList() else substitutes.split("|"),
        isCarry = isCarry,
        isOptional = isOptional,
        tracksLoad = tracksLoad,
        equipment = Equipment.valueOf(equipment),
        perSide = perSide,
        usesTrapBarRir = usesTrapBarRir,
        repStep = repStep,
        notes = notes,
    )
}

internal fun SlotState.toEntity(): SlotStateEntity {
    return SlotStateEntity(
        slotId = slotId,
        exerciseName = exerciseName,
        weight = weight,
        targetReps = targetReps.joinToString(","),
        missCount = missCount,
        sessionsAtWeight = sessionsAtWeight,
        lastIncreaseEpochDay = lastIncreaseDate?.toEpochDay(),
        extensionUsed = extensionUsed,
        loadBlockSessions = loadBlockSessions,
        unchangedStreak = unchangedStreak,
        stallSessions = stallSessions,
        ceilingStreak = ceilingStreak,
        preferMicro = preferMicro,
        lastLoggedReps = lastLoggedReps?.joinToString(","),
        lastLoggedWeight = lastLoggedWeight,
        lastLoggedTotalReps = lastLoggedTotalReps,
    )
}

internal fun SlotStateEntity.toState(): SlotState {
    return SlotState(
        slotId = slotId,
        exerciseName = exerciseName,
        weight = weight,
        targetReps = parseInts(targetReps),
        missCount = missCount,
        sessionsAtWeight = sessionsAtWeight,
        lastIncreaseDate = lastIncreaseEpochDay?.let(LocalDate::ofEpochDay),
        extensionUsed = extensionUsed,
        loadBlockSessions = loadBlockSessions,
        unchangedStreak = unchangedStreak,
        stallSessions = stallSessions,
        ceilingStreak = ceilingStreak,
        preferMicro = preferMicro,
        lastLoggedReps = lastLoggedReps?.let(::parseInts),
        lastLoggedWeight = lastLoggedWeight,
        lastLoggedTotalReps = lastLoggedTotalReps,
    )
}

internal fun ProgramEntity.toClock(): ProgramClock {
    return ProgramClock(
        blockIndex = blockIndex,
        weekOfBlock = weekOfBlock,
        deloadActive = deloadActive,
        deloadReason = deloadReason,
        year = year,
    )
}

internal fun SettingsEntity.toEngine(): EngineSettings {
    return EngineSettings(
        microPlates = microPlates,
        allowSkipDeload = allowSkipDeload,
        dbIncrement = dbIncrement,
        barbellIncrement = barbellIncrement,
        latIncrement = latIncrement,
        availableDb = parseWeights(availableDb).ifEmpty { defaultDbWeights(microPlates) },
        availableBarbell = parseWeights(availableBarbell).ifEmpty { defaultBarbellWeights() },
        availableLat = parseWeights(availableLat).ifEmpty { defaultLatWeights() },
    )
}

internal fun EngineSettings.toEntity(writeHealthConnect: Boolean): SettingsEntity {
    return SettingsEntity(
        microPlates = microPlates,
        allowSkipDeload = allowSkipDeload,
        dbIncrement = dbIncrement,
        barbellIncrement = barbellIncrement,
        latIncrement = latIncrement,
        availableDb = availableDb.joinToString(","),
        availableBarbell = availableBarbell.joinToString(","),
        availableLat = availableLat.joinToString(","),
        writeHealthConnect = writeHealthConnect,
    )
}

internal fun parseInts(raw: String): List<Int> {
    if (raw.isBlank()) return emptyList()
    return raw.split(",").mapNotNull { it.trim().toIntOrNull() }
}

internal fun parseWeights(raw: String): List<Double> {
    if (raw.isBlank()) return emptyList()
    return raw.split(",").mapNotNull { it.trim().toDoubleOrNull() }
}

internal fun parseIdSet(raw: String): Set<String> {
    if (raw.isBlank()) return emptySet()
    return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
}

internal fun parseReadiness(raw: String): List<Int> {
    if (raw.isBlank()) return emptyList()
    return raw.split(",").mapNotNull { it.trim().toIntOrNull() }
}

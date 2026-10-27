package com.shamashel.workout.engine

import java.time.LocalDate

internal val day0: LocalDate = LocalDate.of(2026, 10, 5)

internal fun settings(
    micro: Boolean = false,
    skip: Boolean = false,
    db: List<Double>? = null,
    bar: List<Double>? = null,
): EngineSettings {
    val base = EngineSettings(allowSkipDeload = skip).let { if (micro) it.withMicroPlates(true) else it }
    return base.copy(
        availableDb = db ?: base.availableDb,
        availableBarbell = bar ?: base.availableBarbell,
    )
}

internal fun log(
    weight: Double,
    vararg repsRir: Int,
    pain: Int = 0,
    posture: Boolean? = null,
): SessionLog {
    require(repsRir.size % 2 == 0) { "reps and RIR come in pairs" }
    val sets = repsRir.toList().chunked(2).map { SetLog(reps = it[0], rir = it[1], weight = weight) }
    return SessionLog(sets = sets, painFlag = pain, postureClean = posture)
}

internal fun request(
    slot: SlotDefinition,
    state: SlotState,
    session: SessionLog,
    week: Int,
    date: LocalDate = day0,
    settings: EngineSettings = settings(),
    deload: Boolean = false,
    recentPain: List<LocalDate> = emptyList(),
): EvalRequest {
    return EvalRequest(
        slot = slot,
        state = state,
        log = session,
        settings = settings,
        date = date,
        weekOfBlock = week,
        deloadActive = deload,
        recentModeratePainDates = recentPain,
    )
}

internal fun run(
    slot: SlotDefinition,
    state: SlotState,
    session: SessionLog,
    week: Int,
    date: LocalDate = day0,
    settings: EngineSettings = settings(),
    deload: Boolean = false,
    recentPain: List<LocalDate> = emptyList(),
): SessionResult {
    return WorkoutEngine.evaluate(request(slot, state, session, week, date, settings, deload, recentPain))
}

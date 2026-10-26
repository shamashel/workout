package com.shamashel.workout.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.shamashel.workout.data.CheckInOutcome
import com.shamashel.workout.data.DraftSet
import com.shamashel.workout.data.FinishOutcome
import com.shamashel.workout.data.HistorySnapshot
import com.shamashel.workout.data.HomeSnapshot
import com.shamashel.workout.data.SettingsEntity
import com.shamashel.workout.data.StartingSlot
import com.shamashel.workout.data.TodaySlot
import com.shamashel.workout.data.WorkoutRepository
import com.shamashel.workout.engine.YearOutline
import com.shamashel.workout.health.HealthAvailability
import com.shamashel.workout.health.HealthWriter
import kotlinx.coroutines.launch
import java.time.Instant

data class SetField(val weight: String, val reps: String, val rir: Int)

data class EditorUi(
    val slot: TodaySlot,
    val sets: List<SetField>,
    val pain: Int,
    val note: String,
    val posture: Boolean,
    val error: String?,
)

class WorkoutViewModel(
    private val repository: WorkoutRepository,
    private val health: HealthWriter,
) : ViewModel() {
    var home by mutableStateOf<HomeSnapshot?>(null)
    var selectedDay by mutableStateOf<Char?>(null)
    var editor by mutableStateOf<EditorUi?>(null)
    var summary by mutableStateOf<FinishOutcome?>(null)
    var history by mutableStateOf<HistorySnapshot?>(null)
    var slotChoices by mutableStateOf<List<Pair<String, String>>>(emptyList())
    var settings by mutableStateOf<SettingsEntity?>(null)
    var phases by mutableStateOf<List<YearOutline>>(emptyList())
    var starting by mutableStateOf<List<StartingSlot>>(emptyList())
    var message by mutableStateOf<String?>(null)
    var checkInOffer by mutableStateOf(false)
    var healthStatus by mutableStateOf(HealthAvailability.UNAVAILABLE)
    var busy by mutableStateOf(false)

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            loadHome()
        }
    }

    private suspend fun loadHome() {
        repository.bootstrap()
        home = repository.home(selectedDay)
        settings = repository.settingsEntity()
        healthStatus = health.availability()
    }

    fun selectDay(day: Char) {
        selectedDay = day
        refresh()
    }

    fun acknowledgeSafety() {
        viewModelScope.launch {
            repository.acknowledgeSafety()
            refresh()
        }
    }

    fun openSlot(slotId: String) {
        viewModelScope.launch {
            val slot = repository.editorSeed(slotId)
            val draft = repository.loadDraft(slotId)
            val decoded = draft?.let { repository.decodeDraft(it.setsJson) }
            val count = slot.sets.coerceAtLeast(1)
            val fields = List(count) { index ->
                val existing = decoded?.getOrNull(index)
                val target = slot.targetReps.getOrElse(index) { slot.targetReps.lastOrNull() ?: 0 }
                SetField(
                    weight = existing?.weight?.let(::trimWeight) ?: if (slot.weight > 0.0) trimWeight(slot.weight) else "",
                    reps = existing?.reps?.toString() ?: target.toString(),
                    rir = existing?.rir ?: 3,
                )
            }
            editor = EditorUi(
                slot = slot,
                sets = fields,
                pain = draft?.painFlag ?: 0,
                note = draft?.note.orEmpty(),
                posture = draft?.postureClean ?: false,
                error = null,
            )
        }
    }

    fun updateSet(index: Int, field: SetField) {
        val current = editor ?: return
        editor = current.copy(sets = current.sets.mapIndexed { i, set -> if (i == index) field else set }, error = null)
    }

    fun updateEditor(pain: Int? = null, note: String? = null, posture: Boolean? = null) {
        val current = editor ?: return
        editor = current.copy(
            pain = pain ?: current.pain,
            note = note ?: current.note,
            posture = posture ?: current.posture,
        )
    }

    fun saveDraft(onSaved: () -> Unit) {
        val current = editor ?: return
        val parsed = parseSets(current) ?: return
        viewModelScope.launch {
            repository.saveDraft(current.slot.id, parsed, current.pain, current.note, current.posture)
            editor = null
            refresh()
            onSaved()
        }
    }

    fun finishDay(minutes: Int, readiness: Int?, onDone: () -> Unit) {
        val day = home?.day ?: return
        if (day !in listOf('A', 'B', 'C')) return
        viewModelScope.launch {
            busy = true
            val outcome = repository.finishDay(day, minutes.coerceAtLeast(1), readiness)
            val healthNote = if (outcome.medicalMessage == null && repository.settingsEntity().writeHealthConnect) {
                val end = Instant.now()
                val start = end.minusSeconds(minutes.coerceAtLeast(1) * 60L)
                health.writeStrength(start, end, "Day $day strength")
            } else {
                null
            }
            summary = outcome.copy(healthNote = healthNote)
            loadHome()
            busy = false
            onDone()
        }
    }

    fun override(slotId: String, weight: Double, reps: List<Int>) {
        viewModelScope.launch {
            repository.overrideNext(slotId, weight, reps)
            summary = summary?.copy(
                rows = summary?.rows.orEmpty().map { row ->
                    if (row.slotId == slotId) {
                        row.copy(
                            weightLabel = com.shamashel.workout.engine.formatLb(weight),
                            repsLabel = com.shamashel.workout.engine.formatReps(reps),
                            suggestedWeight = weight,
                            suggestedReps = reps,
                            reason = row.reason + " Overridden.",
                        )
                    } else {
                        row
                    }
                },
            )
            message = "Override saved."
        }
    }

    fun substitute(slotId: String, name: String) {
        viewModelScope.launch {
            repository.applySubstitute(slotId, name)
            message = "Switched to $name."
            refresh()
        }
    }

    fun openHistory(slotId: String? = null) {
        viewModelScope.launch {
            slotChoices = repository.slotChoices()
            val id = slotId ?: slotChoices.firstOrNull()?.first ?: return@launch
            history = repository.history(id)
        }
    }

    fun loadSettings() {
        viewModelScope.launch {
            settings = repository.settingsEntity()
            phases = repository.phases()
            healthStatus = health.availability()
        }
    }

    fun saveSettings(entity: SettingsEntity) {
        viewModelScope.launch {
            repository.saveSettings(entity)
            settings = entity
            message = "Settings saved."
            refresh()
        }
    }

    fun setMicro(enabled: Boolean) {
        viewModelScope.launch {
            repository.setMicroPlates(enabled)
            settings = repository.settingsEntity()
            refresh()
        }
    }

    fun loadStarting() {
        viewModelScope.launch {
            starting = repository.startingSlots()
        }
    }

    fun saveStarting(weights: Map<String, Double>) {
        viewModelScope.launch {
            repository.saveStartingWeights(weights)
            message = "Starting weights saved."
            refresh()
        }
    }

    fun practicePass() {
        viewModelScope.launch {
            repository.recordPracticePass()
            refresh()
        }
    }

    fun checkIn(soreness: Int, performance: Int, readiness: Int, skip: Boolean?, onDone: (CheckInOutcome) -> Unit) {
        viewModelScope.launch {
            val outcome = repository.submitCheckIn(soreness, performance, readiness, skip)
            checkInOffer = outcome.offerSkip
            if (!outcome.offerSkip) {
                message = outcome.messages.joinToString(" ")
                refresh()
            }
            onDone(outcome)
        }
    }

    fun savePhase(year: Int, title: String, body: String) {
        viewModelScope.launch {
            repository.savePhase(year, title, body)
            phases = repository.phases()
            message = "Year $year notes saved. Only Year 1 drives the plan."
        }
    }

    fun relockSlot(slotId: String, repLo: Int, repHi: Int, sets: Int, name: String) {
        viewModelScope.launch {
            repository.saveYear1Override(slotId, repLo, repHi, sets, name)
            message = "$slotId re-locked."
            refresh()
        }
    }

    suspend fun exportJson(): String = repository.exportJson()

    fun importJson(raw: String, onDone: (String) -> Unit) {
        viewModelScope.launch {
            try {
                repository.importJson(raw)
                refresh()
                onDone("Backup imported.")
            } catch (error: Exception) {
                onDone(error.message ?: "Couldn't read that backup.")
            }
        }
    }

    fun logSwim(minutes: Int, pool: Boolean) {
        viewModelScope.launch {
            val write = repository.settingsEntity().writeHealthConnect
            val note = if (write) {
                val end = Instant.now()
                health.writeSwim(end.minusSeconds(minutes.coerceAtLeast(1) * 60L), end, pool)
            } else {
                null
            }
            repository.logSwim(minutes, pool, note == null && write)
            message = note ?: "Swim logged."
        }
    }

    fun clearMessage() {
        message = null
    }

    fun healthWriter(): com.shamashel.workout.health.HealthWriter = health

    private fun parseSets(current: EditorUi): List<DraftSet>? {
        val parsed = current.sets.map { set ->
            val weight = set.weight.toDoubleOrNull()
            val reps = set.reps.toIntOrNull()
            if (current.slot.tracksLoad && (weight == null || weight <= 0.0)) {
                editor = current.copy(error = "Enter a weight for every set.")
                return null
            }
            if (reps == null || reps < 0) {
                editor = current.copy(error = "Enter reps (or seconds) for every set.")
                return null
            }
            DraftSet(weight ?: 0.0, reps, set.rir.coerceIn(0, 5))
        }
        return parsed
    }

    companion object {
        fun factory(repository: WorkoutRepository, health: HealthWriter): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return WorkoutViewModel(repository, health) as T
                }
            }
        }
    }
}

private fun trimWeight(weight: Double): String {
    return if (kotlin.math.abs(weight - weight.toInt()) < 0.001) {
        weight.toInt().toString()
    } else {
        String.format(java.util.Locale.US, "%.1f", weight)
    }
}

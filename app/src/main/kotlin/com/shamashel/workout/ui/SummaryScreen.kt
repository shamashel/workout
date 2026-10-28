package com.shamashel.workout.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shamashel.workout.data.FinishOutcome
import com.shamashel.workout.data.HealthSaveStatus
import com.shamashel.workout.data.SummaryRow

@Composable
fun SummaryScreen(
    outcome: FinishOutcome,
    onOverride: (slotId: String, weight: Double, reps: List<Int>) -> Unit,
    onSubstitute: (slotId: String, name: String) -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (outcome.medicalMessage != null) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Stop", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.error)
            Text(outcome.medicalMessage, style = MaterialTheme.typography.bodyLarge)
            SavedBanner(outcome, onHistory, onSettings)
            Button(onClick = onDone) { Text("Close") }
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SavedBanner(outcome, onHistory, onSettings)
            Text("Next session", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            outcome.deloadBanner?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
        }
        items(outcome.rows, key = { it.slotId }) { row ->
            SuggestionCard(row, onOverride, onSubstitute)
        }
        item { Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") } }
    }
}

@Composable
private fun SavedBanner(
    outcome: FinishOutcome,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
) {
    val status = when (outcome.healthStatus) {
        HealthSaveStatus.OFF -> "Health Connect is off."
        HealthSaveStatus.WRITTEN -> "Health Connect: written."
        HealthSaveStatus.SKIPPED -> "Health Connect was skipped."
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Workout saved on this phone", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text("Automatic backup is scheduled. The phone copies it when it is idle, charging, and on Wi-Fi.")
        Text(status, fontWeight = FontWeight.Medium)
        outcome.healthNote?.let { Text(it) }
        Button(onClick = onHistory, modifier = Modifier.fillMaxWidth()) { Text("Open History") }
        TextButton(onClick = onSettings) { Text("Health Connect settings") }
    }
}

@Composable
private fun SuggestionCard(
    row: SummaryRow,
    onOverride: (String, Double, List<Int>) -> Unit,
    onSubstitute: (String, String) -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var weight by rememberSaveable(row.slotId) { mutableStateOf(trim(row.suggestedWeight)) }
    var reps by rememberSaveable(row.slotId) { mutableStateOf(row.suggestedReps.joinToString(",")) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(row.name, fontWeight = FontWeight.SemiBold)
            if (row.weightLabel.isNotBlank()) {
                Text("${row.weightLabel}  ·  ${row.repsLabel}  ·  RIR ${row.rirLabel}", style = MaterialTheme.typography.titleMedium)
            }
            Text(row.reason)
            row.prompts.forEach { prompt ->
                Text(prompt, color = MaterialTheme.colorScheme.secondary)
                val named = prompt.substringAfter("same slot: ", "").substringBefore(". No load")
                if (named.isNotBlank() && prompt.contains("Substitute")) {
                    named.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { name ->
                        OutlinedButton(onClick = { onSubstitute(row.slotId, name) }) { Text("Use $name") }
                    }
                }
            }
            if (row.coaching) {
                TextButton(onClick = { editing = true }) { Text("Change this suggestion") }
            }
        }
    }
    if (editing) {
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Override ${row.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(weight, { weight = it }, label = { Text("Weight, lb") })
                    OutlinedTextField(reps, { reps = it }, label = { Text("Target reps, comma separated") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val chosenWeight = weight.toDoubleOrNull()
                    val chosenReps = reps.split(",").mapNotNull { it.trim().toIntOrNull() }
                    if (chosenWeight != null && chosenReps.isNotEmpty()) {
                        onOverride(row.slotId, chosenWeight, chosenReps)
                        editing = false
                    }
                }) { Text("Save override") }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } },
        )
    }
}

private fun trim(weight: Double): String {
    return if (kotlin.math.abs(weight - weight.toInt()) < 0.001) weight.toInt().toString() else weight.toString()
}

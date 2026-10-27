package com.shamashel.workout.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun LogScreen(editor: EditorUi, onChange: (EditorUi) -> Unit, onSave: () -> Unit) {
    val slot = editor.slot
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("${slot.id}  ${slot.name}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(slot.prescription)
        Text(slot.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("How many more reps could you have done?")
        editor.sets.forEachIndexed { index, set ->
            Text("Set ${index + 1}", fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = set.weight,
                    onValueChange = { onChange(editor.copy(sets = editor.sets.replace(index, set.copy(weight = it)))) },
                    label = { Text(if (slot.tracksLoad) "Weight (lb)" else "—") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = slot.tracksLoad,
                )
                OutlinedTextField(
                    value = set.reps,
                    onValueChange = { onChange(editor.copy(sets = editor.sets.replace(index, set.copy(reps = it.filter { ch -> ch.isDigit() })))) },
                    label = { Text(if (slot.isCarry || !slot.tracksLoad && slot.prescription.contains("sec")) "Seconds" else "Reps") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (0..5).forEach { rir ->
                    FilterChip(
                        selected = set.rir == rir,
                        onClick = { onChange(editor.copy(sets = editor.sets.replace(index, set.copy(rir = rir)))) },
                        label = { Text(if (rir == 5) "5+" else "$rir") },
                    )
                }
            }
        }
        Text("Pain", fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0 to "None", 1 to "Mild", 2 to "Moderate", 3 to "Stop").forEach { (flag, label) ->
                FilterChip(
                    selected = editor.pain == flag,
                    onClick = { onChange(editor.copy(pain = flag)) },
                    label = { Text(label) },
                )
            }
        }
        if (editor.pain == 3) {
            Text(
                "Flag 3 stops the session and shows a seek-medical-care message. No next-weight coaching.",
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (slot.isCarry) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(checked = editor.posture, onCheckedChange = { onChange(editor.copy(posture = it)) })
                Text("Posture and grip stayed clean")
            }
        }
        OutlinedTextField(
            value = editor.note,
            onValueChange = { onChange(editor.copy(note = it)) },
            label = { Text("Note, optional") },
            modifier = Modifier.fillMaxWidth(),
        )
        editor.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("Save slot") }
    }
}

private fun List<SetField>.replace(index: Int, field: SetField): List<SetField> {
    return mapIndexed { i, set -> if (i == index) field else set }
}

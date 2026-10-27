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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shamashel.workout.data.StartingSlot
import com.shamashel.workout.engine.YearOutline

@Composable
fun StartingWeightsScreen(
    slots: List<StartingSlot>,
    passes: Int,
    onPractice: () -> Unit,
    onSave: (Map<String, Double>) -> Unit,
) {
    var values by remember(slots) { mutableStateOf(slots.associate { it.id to if (it.weight > 0) trimNum(it.weight) else "" }) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Week 0", style = MaterialTheme.typography.headlineMedium)
        Text("Do two easy practice sessions, 2×10 at about RIR 5, and don't log them for progression. Practice passes recorded: $passes of 2.")
        Text("Then find a weight where 10 reps feels like 3 RIR, and start Week 1 at the bottom of the rep range. If the first guess feels heavy, start lighter. Trap-bar: empty bar, add 10–20 lb until 8 reps feels like 3 RIR, then begin around 85–90% of that. Carries: a pair you can walk for 30 seconds with clean posture.")
        OutlinedButton(onClick = onPractice) { Text("Count a practice pass") }
        slots.forEach { slot ->
            OutlinedTextField(
                value = values[slot.id].orEmpty(),
                onValueChange = { values = values + (slot.id to it) },
                label = { Text("${slot.id} ${slot.name}") },
                supportingText = { Text(slot.hint) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        Button(onClick = {
            val parsed = values.mapNotNull { (id, raw) -> raw.toDoubleOrNull()?.let { id to it } }.toMap()
            onSave(parsed)
        }, modifier = Modifier.fillMaxWidth()) { Text("Save Week 1 weights") }
    }
}

@Composable
fun CheckInScreen(offerSkip: Boolean, onSubmit: (Int, Int, Int, Boolean?) -> Unit) {
    var soreness by remember { mutableIntStateOf(2) }
    var performance by remember { mutableIntStateOf(2) }
    var readiness by remember { mutableIntStateOf(3) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Weekly check-in", style = MaterialTheme.typography.headlineMedium)
        Text("Year 1 keeps the sets fixed. These scores only decide deloads.")
        ScoreRow("Soreness recovery", "1 none, 4 still sore", soreness, 1..4) { soreness = it }
        ScoreRow("Performance", "1 beat targets, 4 could not match last week", performance, 1..4) { performance = it }
        ScoreRow("Readiness", "1 drained, 5 fresh", readiness, 1..5) { readiness = it }
        Button(onClick = { onSubmit(soreness, performance, readiness, null) }, modifier = Modifier.fillMaxWidth()) {
            Text("Save check-in")
        }
        if (offerSkip) {
            Text("The last two weeks look fresh. Skip this deload?")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onSubmit(soreness, performance, readiness, true) }) { Text("Skip") }
                OutlinedButton(onClick = { onSubmit(soreness, performance, readiness, false) }) { Text("Deload") }
            }
        }
    }
}

@Composable
fun YearScreen(
    phases: List<YearOutline>,
    onSave: (Int, String, String) -> Unit,
    onRelock: (String, Int, Int, Int, String) -> Unit,
) {
    var year by remember { mutableIntStateOf(1) }
    val phase = phases.firstOrNull { it.year == year }
    var title by remember(phase) { mutableStateOf(phase?.title.orEmpty()) }
    var body by remember(phase) { mutableStateOf(phase?.body.orEmpty()) }
    var slot by remember { mutableStateOf("A1") }
    var repLo by remember { mutableStateOf("8") }
    var repHi by remember { mutableStateOf("12") }
    var sets by remember { mutableStateOf("3") }
    var name by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Four years", style = MaterialTheme.typography.headlineMedium)
        Text("Year 1 is the plan the app runs. Years 2–4 are notes you can rewrite from your log. Re-locking a Year 1 slot changes its rep range and sets from the next session on.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..4).forEach { value ->
                FilterChip(selected = year == value, onClick = { year = value }, label = { Text("Year $value") })
            }
        }
        OutlinedTextField(title, { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(body, { body = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth(), minLines = 4)
        Button(onClick = { onSave(year, title, body) }) { Text("Save year notes") }
        if (year == 1) {
            Text("Re-lock a Year 1 slot", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(slot, { slot = it.uppercase() }, label = { Text("Slot, A1") })
            OutlinedTextField(name, { name = it }, label = { Text("Name, blank keeps the current one") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(repLo, { repLo = it }, label = { Text("Rep low") })
            OutlinedTextField(repHi, { repHi = it }, label = { Text("Rep high") })
            OutlinedTextField(sets, { sets = it }, label = { Text("Sets") })
            Button(onClick = {
                val lo = repLo.toIntOrNull()
                val hi = repHi.toIntOrNull()
                val n = sets.toIntOrNull()
                if (lo != null && hi != null && n != null && slot.length == 2) {
                    onRelock(slot, lo, hi, n, name)
                }
            }) { Text("Re-lock slot") }
        }
    }
}

@Composable
fun SwimScreen(onSave: (Int, Boolean) -> Unit) {
    var minutes by remember { mutableStateOf("30") }
    var pool by remember { mutableStateOf(true) }
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Log a swim", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(minutes, { minutes = it.filter { ch -> ch.isDigit() } }, label = { Text("Minutes") })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = pool, onClick = { pool = true }, label = { Text("Pool") })
            FilterChip(selected = !pool, onClick = { pool = false }, label = { Text("Open water") })
        }
        Button(onClick = { onSave(minutes.toIntOrNull() ?: 30, pool) }) { Text("Save swim") }
    }
}

@Composable
private fun ScoreRow(title: String, hint: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Text(title, fontWeight = FontWeight.Medium)
    Text(hint, style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        range.forEach { score ->
            FilterChip(selected = value == score, onClick = { onChange(score) }, label = { Text("$score") })
        }
    }
}

private fun trimNum(weight: Double): String {
    return if (kotlin.math.abs(weight - weight.toInt()) < 0.001) weight.toInt().toString() else weight.toString()
}

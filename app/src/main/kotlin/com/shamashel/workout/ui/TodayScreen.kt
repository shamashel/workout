package com.shamashel.workout.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import com.shamashel.workout.data.HomeSnapshot
import com.shamashel.workout.data.SAFETY_LINE
import com.shamashel.workout.data.START_FRESH_BODY
import com.shamashel.workout.data.TodaySlot
import com.shamashel.workout.data.WeekdayAssignment

@Composable
fun TodayScreen(
    home: HomeSnapshot,
    onUseDayToday: (Char) -> Unit,
    onClearTodayOverride: () -> Unit,
    onStartFresh: () -> Unit,
    onOpenSlot: (String) -> Unit,
    onFinish: (minutes: Int, readiness: Int?) -> Unit,
    onCheckIn: () -> Unit,
    onStartWeights: () -> Unit,
    onDismissSafety: () -> Unit,
    onSwim: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var askFinish by rememberSaveable { mutableStateOf(false) }
    var askDay by rememberSaveable { mutableStateOf(false) }
    var askFresh by rememberSaveable { mutableStateOf(false) }
    if (home.showSafety) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Before you add weight") },
            text = { Text(SAFETY_LINE) },
            confirmButton = { TextButton(onClick = onDismissSafety) { Text("I understand") } },
        )
    }
    if (askDay) {
        AlertDialog(
            onDismissRequest = { askDay = false },
            title = { Text("Do a different day today") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Only today changes. The weekly schedule stays as it is, and sessions already saved keep their day.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf('A', 'B', 'C').forEach { day ->
                            FilterChip(
                                selected = home.day == day && !home.isRestDay,
                                onClick = {
                                    askDay = false
                                    onUseDayToday(day)
                                },
                                label = { Text("Day $day") },
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { askDay = false }) { Text("Cancel") } },
        )
    }
    if (askFresh) {
        AlertDialog(
            onDismissRequest = { askFresh = false },
            title = { Text("Start fresh from Day A today") },
            text = { Text(START_FRESH_BODY) },
            confirmButton = {
                Button(onClick = {
                    askFresh = false
                    onStartFresh()
                }) { Text("Start today") }
            },
            dismissButton = { TextButton(onClick = { askFresh = false }) { Text("Cancel") } },
        )
    }
    if (askFinish) {
        FinishDialog(
            onDismiss = { askFinish = false },
            onConfirm = { minutes, readiness ->
                askFinish = false
                onFinish(minutes, readiness)
            },
        )
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(home.dateLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(home.headline, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(home.weekLabel, style = MaterialTheme.typography.bodyMedium)
            Text(home.rirNote, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
        }
        if (home.todayOnly) {
            item {
                val usual = WeekdayAssignment.fromCode(home.usualDay)?.spoken ?: "Rest"
                Text("Just for today. Your weekly schedule still says $usual.")
                TextButton(onClick = onClearTodayOverride) { Text("Use the usual day") }
            }
        }
        item {
            OutlinedButton(onClick = { askDay = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Do a different day today")
            }
            TextButton(onClick = { askFresh = true }) { Text("Start fresh from Day A today") }
        }
        home.deloadBanner?.let { banner ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Text(banner, modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Medium)
                }
            }
        }
        home.restNote?.let { note ->
            item { Text(note, style = MaterialTheme.typography.bodyLarge) }
        }
        if (home.isRestDay) {
            item { Button(onClick = onSwim) { Text("Log a swim") } }
        }
        if (home.needsWeights && home.day in listOf('A', 'B', 'C')) {
            item {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Set starting weights before the first real week.", fontWeight = FontWeight.Medium)
                        Text("Week 0 practice passes recorded: ${home.week0Passes} of 2. Those passes are not used for progression.")
                        Button(onClick = onStartWeights) { Text("Find starting weights") }
                    }
                }
            }
        }
        items(home.slots, key = { it.id }) { slot ->
            SlotCard(slot, onOpen = { onOpenSlot(slot.id) })
        }
        if (home.day in listOf('A', 'B', 'C') && !home.needsWeights) {
            item {
                val ready = home.slots.count { it.drafted && !it.loggedToday }
                Button(
                    onClick = { askFinish = true },
                    enabled = ready > 0,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (ready == 0) "Log a slot to finish" else "Finish session ($ready ready)")
                }
            }
        }
        if (home.checkInDue) {
            item {
                OutlinedButton(onClick = onCheckIn, modifier = Modifier.fillMaxWidth()) {
                    Text("Weekly check-in")
                }
            }
        }
    }
}

@Composable
private fun SlotCard(slot: TodaySlot, onOpen: () -> Unit) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${slot.id}  ${slot.name}", fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        slot.loggedToday -> "Logged"
                        slot.drafted -> "Ready"
                        slot.optional -> "Optional"
                        else -> "Log"
                    },
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(slot.prescription, style = MaterialTheme.typography.bodyMedium)
            if (slot.perSide) {
                Text("Log the weaker side.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun FinishDialog(onDismiss: () -> Unit, onConfirm: (Int, Int?) -> Unit) {
    var minutes by rememberSaveable { mutableStateOf("55") }
    var readiness by rememberSaveable { mutableStateOf(3) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Finish this session") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Minutes")
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { minutes = it.filter { ch -> ch.isDigit() }.take(3) },
                    singleLine = true,
                )
                Text("Readiness right now, 1 drained to 5 fresh. Two scores of 2 or lower in a week start an early deload.")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..5).forEach { score ->
                        FilterChip(selected = readiness == score, onClick = { readiness = score }, label = { Text("$score") })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(minutes.toIntOrNull() ?: 55, readiness) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Back") } },
    )
}

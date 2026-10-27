package com.shamashel.workout.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import com.shamashel.workout.data.DEFAULT_PLATES
import com.shamashel.workout.data.PLATE_CHOICES
import com.shamashel.workout.data.RangeKit
import com.shamashel.workout.data.SAFETY_LINE
import com.shamashel.workout.data.SettingsDraft
import com.shamashel.workout.data.SettingsEntity
import com.shamashel.workout.data.lbPhrase
import com.shamashel.workout.data.plainLb
import com.shamashel.workout.data.toDraft
import com.shamashel.workout.health.HealthAvailability
import com.shamashel.workout.health.HealthWriter
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun SettingsScreen(
    settings: SettingsEntity,
    healthStatus: HealthAvailability,
    healthGranted: Boolean,
    health: HealthWriter,
    onSave: (SettingsEntity, String?) -> Unit,
    onExport: suspend () -> String,
    onImport: (String) -> Unit,
    onYears: () -> Unit,
    onStarting: () -> Unit,
    onSwim: () -> Unit,
    onCheckIn: () -> Unit,
    onHealthPermission: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var draft by remember(settings) { mutableStateOf(settings.toDraft()) }
    fun commit(next: SettingsDraft, note: String? = null) {
        draft = next
        onSave(next.toSettings(), note)
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { granted ->
        onHealthPermission(granted.contains(health.writePermission()))
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val raw = onExport()
            context.contentResolver.openOutputStream(uri)?.use { it.write(raw.toByteArray()) }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText().orEmpty()
        onImport(raw)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Defaults match a home gym. Change a control and it is saved.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SectionCard(
            title = "Equipment",
            helper = "What you can actually load. The plan only suggests weights from these.",
            onReset = { commit(draft.resetEquipment(), "Equipment reset.") },
        ) {
            Text("Dumbbells", fontWeight = FontWeight.SemiBold)
            Text(draft.dumbbells.preview(), color = MaterialTheme.colorScheme.primary)
            LbStepper("Lightest", draft.dumbbells.min, onMinus = {
                commit(draft.copy(dumbbells = draft.dumbbells.withMin(draft.dumbbells.min - draft.dumbbells.step)))
            }, onPlus = {
                commit(draft.copy(dumbbells = draft.dumbbells.withMin(draft.dumbbells.min + draft.dumbbells.step)))
            })
            LbStepper("Heaviest", draft.dumbbells.max, onMinus = {
                commit(draft.copy(dumbbells = draft.dumbbells.withMax(draft.dumbbells.max - draft.dumbbells.step)))
            }, onPlus = {
                commit(draft.copy(dumbbells = draft.dumbbells.withMax(draft.dumbbells.max + draft.dumbbells.step)))
            })
            Text("Step", style = MaterialTheme.typography.bodyMedium)
            StepChoices(current = draft.dumbbells.step, choices = listOf(2.5, 5.0, 10.0)) { step ->
                commit(draft.copy(dumbbells = draft.dumbbells.withStep(step)))
            }
            OddWeights(
                kit = draft.dumbbells,
                onAdd = { commit(draft.copy(dumbbells = draft.dumbbells.addWeight(it))) },
                onRemove = { commit(draft.copy(dumbbells = draft.dumbbells.removeWeight(it))) },
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text("Barbell", fontWeight = FontWeight.SemiBold)
            Text(draft.bar.preview(), color = MaterialTheme.colorScheme.primary)
            if (draft.bar.custom != null) {
                Text(
                    "These loads were saved as a custom list. Choosing plates replaces them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { commit(draft.copy(bar = draft.bar.usePlates(DEFAULT_PLATES))) }) {
                    Text("Use a standard plate set")
                }
            } else {
                LbStepper("Bar", draft.bar.bar, onMinus = {
                    commit(draft.copy(bar = draft.bar.withBar(draft.bar.bar - 5.0)))
                }, onPlus = {
                    commit(draft.copy(bar = draft.bar.withBar(draft.bar.bar + 5.0)))
                })
                LbStepper("Heaviest load", draft.bar.max, onMinus = {
                    commit(draft.copy(bar = draft.bar.withMax(draft.bar.max - 10.0)))
                }, onPlus = {
                    commit(draft.copy(bar = draft.bar.withMax(draft.bar.max + 10.0)))
                })
                Text("Plates you own, per side", style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PLATE_CHOICES.forEach { plate ->
                        val selected = draft.bar.plates.any { abs(it - plate) <= 0.05 }
                        FilterChip(
                            selected = selected,
                            onClick = { commit(draft.copy(bar = draft.bar.togglePlate(plate))) },
                            label = { Text(lbPhrase(plate)) },
                        )
                    }
                }
            }
            SwitchRow(
                title = "Micro-plates",
                body = "Smaller jumps: 2.5 lb on dumbbells, 5 lb on the bar.",
                checked = draft.micro,
                onCheckedChange = { commit(draft.withMicro(it)) },
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text("Lat stack", fontWeight = FontWeight.SemiBold)
            Text(draft.lat.preview(), color = MaterialTheme.colorScheme.primary)
            LbStepper("Lightest", draft.lat.min, onMinus = {
                commit(draft.copy(lat = draft.lat.withMin(draft.lat.min - draft.lat.step)))
            }, onPlus = {
                commit(draft.copy(lat = draft.lat.withMin(draft.lat.min + draft.lat.step)))
            })
            LbStepper("Heaviest", draft.lat.max, onMinus = {
                commit(draft.copy(lat = draft.lat.withMax(draft.lat.max - draft.lat.step)))
            }, onPlus = {
                commit(draft.copy(lat = draft.lat.withMax(draft.lat.max + draft.lat.step)))
            })
            Text("Step", style = MaterialTheme.typography.bodyMedium)
            StepChoices(current = draft.lat.step, choices = listOf(5.0, 10.0, 15.0)) { step ->
                commit(draft.copy(lat = draft.lat.withStep(step)))
            }
        }
        SectionCard(
            title = "Progression",
            helper = "How much to add when a lift earns the next load. One jump per session.",
            onReset = { commit(draft.resetProgression(), "Progression reset.") },
        ) {
            JumpStepper("Dumbbell jump", draft.dbIncrement, listOf(2.5, 5.0, 10.0)) {
                commit(draft.copy(dbIncrement = it))
            }
            JumpStepper("Barbell jump", draft.barIncrement, listOf(5.0, 10.0, 15.0)) {
                commit(draft.copy(barIncrement = it))
            }
            JumpStepper("Lat stack jump", draft.latIncrement, listOf(5.0, 10.0, 15.0)) {
                commit(draft.copy(latIncrement = it))
            }
        }
        SectionCard(
            title = "Deloads",
            helper = "Every fourth week is lighter. A skip is offered only after two easy, pain-free weeks.",
            onReset = { commit(draft.resetDeload(), "Deload setting reset.") },
        ) {
            SwitchRow(
                title = "Allow skipping a deload",
                body = "Off by default. The app still asks before it skips.",
                checked = draft.allowSkipDeload,
                onCheckedChange = { commit(draft.copy(allowSkipDeload = it)) },
            )
        }
        HealthSection(
            status = healthStatus,
            granted = healthGranted,
            writing = draft.writeHealthConnect,
            onAllow = { permissionLauncher.launch(setOf(health.writePermission())) },
            onInstall = { runCatching { context.startActivity(health.installIntent()) } },
            onWriting = { commit(draft.copy(writeHealthConnect = it)) },
            onReset = { commit(draft.resetHealth(), "Health Connect writing turned off.") },
        )
        SectionCard(
            title = "Backup",
            helper = "A JSON file in a place you choose. Nothing leaves the phone.",
            onReset = null,
        ) {
            Button(onClick = { exportLauncher.launch("trades-prep-backup.json") }, modifier = Modifier.fillMaxWidth()) {
                Text("Export backup")
            }
            OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json")) }, modifier = Modifier.fillMaxWidth()) {
                Text("Import backup")
            }
        }
        SectionCard(
            title = "About",
            helper = "One person's log, on this phone. No account, no cloud, no analytics.",
            onReset = null,
        ) {
            Text(SAFETY_LINE, fontWeight = FontWeight.Medium)
            TextButton(onClick = onStarting) { Text("Starting weights") }
            TextButton(onClick = onCheckIn) { Text("Weekly check-in") }
            TextButton(onClick = onSwim) { Text("Log a swim") }
            TextButton(onClick = onYears) { Text("Year notes") }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    helper: String,
    onReset: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(helper, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                content()
                if (onReset != null) {
                    TextButton(onClick = onReset) { Text("Reset to defaults") }
                }
            },
        )
    }
}

@Composable
private fun HealthSection(
    status: HealthAvailability,
    granted: Boolean,
    writing: Boolean,
    onAllow: () -> Unit,
    onInstall: () -> Unit,
    onWriting: (Boolean) -> Unit,
    onReset: () -> Unit,
) {
    val headline = when (status) {
        HealthAvailability.UNAVAILABLE -> "Not installed"
        HealthAvailability.UPDATE_REQUIRED -> "Out of date"
        HealthAvailability.READY -> if (granted) "Connected" else "Not granted"
    }
    val detail = when (status) {
        HealthAvailability.UNAVAILABLE ->
            "Health Connect isn't on this phone. Workouts stay in this app."
        HealthAvailability.UPDATE_REQUIRED ->
            "Health Connect needs an update before this app can write to it."
        HealthAvailability.READY -> if (granted) {
            "Connected. A finished strength session or swim is written as duration and exercise type. Sets and reps stay in this app."
        } else {
            "Health Connect is installed. This app still needs permission to write a workout."
        }
    }
    SectionCard(
        title = "Health Connect",
        helper = headline,
        onReset = onReset,
    ) {
        Text(detail)
        when (status) {
            HealthAvailability.READY -> {
                if (!granted) {
                    Button(onClick = onAllow, modifier = Modifier.fillMaxWidth()) { Text("Allow Health Connect") }
                }
            }
            HealthAvailability.UPDATE_REQUIRED -> {
                Button(onClick = onInstall, modifier = Modifier.fillMaxWidth()) { Text("Update Health Connect") }
            }
            HealthAvailability.UNAVAILABLE -> {
                Button(onClick = onInstall, modifier = Modifier.fillMaxWidth()) { Text("Get Health Connect") }
            }
        }
        if (status == HealthAvailability.READY && granted) {
            SwitchRow(
                title = "Write workouts",
                body = "Duration and type only.",
                checked = writing,
                onCheckedChange = onWriting,
            )
        }
    }
}

@Composable
private fun LbStepper(label: String, value: Double, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
        IconButton(onClick = onMinus) {
            Icon(Icons.Filled.Remove, contentDescription = "Decrease $label")
        }
        Text(
            lbPhrase(value),
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        IconButton(onClick = onPlus) {
            Icon(Icons.Filled.Add, contentDescription = "Increase $label")
        }
    }
}

@Composable
private fun StepChoices(current: Double, choices: List<Double>, onSelect: (Double) -> Unit) {
    val options = if (choices.any { abs(it - current) <= 0.05 }) choices else listOf(current) + choices
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, step ->
            SegmentedButton(
                selected = abs(step - current) <= 0.05,
                onClick = { onSelect(step) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(plainLb(step))
            }
        }
    }
}

@Composable
private fun JumpStepper(label: String, value: Double, choices: List<Double>, onSelect: (Double) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label)
        StepChoices(current = value, choices = choices, onSelect = onSelect)
    }
}

@Composable
private fun OddWeights(kit: RangeKit, onAdd: (Double) -> Unit, onRemove: (Double) -> Unit) {
    var pick by remember(kit.min, kit.step) { mutableDoubleStateOf(kit.min) }
    Text("Add or remove one dumbbell", style = MaterialTheme.typography.bodyMedium)
    Text(
        "For a gym that skips a pair, or has one odd bell.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    LbStepper("Weight", pick, onMinus = { pick = (pick - kit.step).coerceAtLeast(kit.step) }, onPlus = { pick += kit.step })
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { onAdd(pick) }) { Text("Add") }
        OutlinedButton(onClick = { onRemove(pick) }) { Text("Remove") }
    }
    if (kit.added.isNotEmpty() || kit.removed.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            kit.added.forEach { weight ->
                FilterChip(selected = true, onClick = { onRemove(weight) }, label = { Text("Added ${lbPhrase(weight)}") })
            }
            kit.removed.forEach { weight ->
                FilterChip(selected = false, onClick = { onAdd(weight) }, label = { Text("Removed ${lbPhrase(weight)}") })
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, body: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

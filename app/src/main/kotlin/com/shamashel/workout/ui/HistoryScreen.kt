package com.shamashel.workout.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shamashel.workout.data.HistoryPoint
import com.shamashel.workout.data.HistorySnapshot

@Composable
fun HistoryScreen(
    choices: List<Pair<String, String>>,
    history: HistorySnapshot?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("History", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text("Weight and estimated reps to failure (reps + RIR) for the hardest set. Deload weeks and pain flags are marked.")
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                choices.forEach { (id, name) ->
                    FilterChip(
                        selected = history?.slotId == id,
                        onClick = { onSelect(id) },
                        label = { Text("$id $name") },
                    )
                }
            }
        }
        if (history == null || history.points.isEmpty()) {
            item { Text("No sessions for this slot yet.") }
        } else {
            item { HistoryChart(history.points) }
            items(history.points) { point ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${point.dateLabel}  ·  ${trim(point.weight)} lb × ${point.reps}", fontWeight = FontWeight.Medium)
                        Text("Est. reps to failure ${point.estimatedReps} · RIR ${point.rirLabel}")
                        if (point.deload) Text("Deload", color = MaterialTheme.colorScheme.secondary)
                        if (point.pain > 0) Text("Pain flag ${point.pain}", color = MaterialTheme.colorScheme.error)
                        if (point.reason.isNotBlank()) Text(point.reason, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryChart(points: List<HistoryPoint>, modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.primary
    val estimate = MaterialTheme.colorScheme.secondary
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("Weight and estimated reps", style = MaterialTheme.typography.labelLarge)
            Canvas(Modifier.fillMaxWidth().height(180.dp).padding(top = 8.dp)) {
                if (points.size < 2) return@Canvas
                val maxWeight = points.maxOf { it.weight }.coerceAtLeast(1.0).toFloat()
                val maxReps = points.maxOf { it.estimatedReps }.coerceAtLeast(1).toFloat()
                val stepX = size.width / (points.size - 1).coerceAtLeast(1)
                fun weightY(value: Double) = size.height - (value.toFloat() / maxWeight) * size.height
                fun repY(value: Int) = size.height - (value / maxReps) * size.height
                val weightPath = Path()
                val repPath = Path()
                points.forEachIndexed { index, point ->
                    val x = stepX * index
                    val y = weightY(point.weight)
                    if (index == 0) weightPath.moveTo(x, y) else weightPath.lineTo(x, y)
                    val ry = repY(point.estimatedReps)
                    if (index == 0) repPath.moveTo(x, ry) else repPath.lineTo(x, ry)
                }
                drawPath(weightPath, line, style = Stroke(width = 6f, cap = StrokeCap.Round))
                drawPath(repPath, estimate, style = Stroke(width = 4f, cap = StrokeCap.Round))
                points.forEachIndexed { index, point ->
                    val x = stepX * index
                    if (point.deload) {
                        drawLine(Color(0xFF9C4A2B), Offset(x, 0f), Offset(x, size.height), strokeWidth = 3f)
                    }
                    if (point.pain > 0) {
                        drawCircle(Color(0xFF8E2F2F), radius = 8f, center = Offset(x, weightY(point.weight)))
                    }
                }
            }
        }
    }
}

private fun trim(weight: Double): String {
    return if (kotlin.math.abs(weight - weight.toInt()) < 0.001) weight.toInt().toString() else String.format("%.1f", weight)
}

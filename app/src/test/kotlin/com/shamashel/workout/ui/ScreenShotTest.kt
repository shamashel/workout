package com.shamashel.workout.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.shamashel.workout.data.SettingsDraft
import com.shamashel.workout.data.defaultBar
import com.shamashel.workout.data.defaultDumbbells
import com.shamashel.workout.data.defaultLatStack
import com.shamashel.workout.data.FinishOutcome
import com.shamashel.workout.data.HistoryPoint
import com.shamashel.workout.data.HistorySnapshot
import com.shamashel.workout.data.HomeSnapshot
import com.shamashel.workout.data.SummaryRow
import com.shamashel.workout.data.TodaySlot
import com.shamashel.workout.health.HealthAvailability
import com.shamashel.workout.health.HealthWriter
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenShotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun todaySession() {
        compose.setContent {
            WorkoutTheme {
                TodayScreen(
                    home = sampleHome(),
                    onSelectDay = {},
                    onOpenSlot = {},
                    onFinish = { _, _ -> },
                    onCheckIn = {},
                    onStartWeights = {},
                    onDismissSafety = {},
                    onSwim = {},
                )
            }
        }
        compose.onNodeWithText("Goblet squat", substring = true).assertExists()
        save("today")
    }

    @Test
    fun suggestionSummary() {
        compose.setContent {
            WorkoutTheme {
                SummaryScreen(
                    outcome = sampleSummary(),
                    onOverride = { _, _, _ -> },
                    onSubstitute = { _, _ -> },
                    onDone = {},
                )
            }
        }
        compose.onNodeWithText("All sets at 12 and avg RIR 2.3 ≥ 2, so +5 lb", substring = true).assertExists()
        save("summary")
    }

    @Test
    fun history() {
        compose.setContent {
            WorkoutTheme {
                HistoryScreen(
                    choices = listOf("A1" to "Goblet squat", "B1" to "Trap-bar deadlift"),
                    history = sampleHistory(),
                    onSelect = {},
                )
            }
        }
        compose.onNodeWithText("About 14 reps before failure", substring = true).assertExists()
        save("history")
    }

    @Test
    fun settingsEquipment() {
        compose.setContent {
            val context = LocalContext.current
            val writer = remember { HealthWriter(context) }
            WorkoutTheme {
                SettingsScreen(
                    settings = SettingsDraft(
                        dumbbells = defaultDumbbells(),
                        bar = defaultBar(),
                        lat = defaultLatStack(),
                        micro = false,
                        dbIncrement = 5.0,
                        barIncrement = 10.0,
                        latIncrement = 5.0,
                        allowSkipDeload = false,
                        writeHealthConnect = false,
                    ).toSettings(),
                    healthStatus = HealthAvailability.READY,
                    healthGranted = false,
                    health = writer,
                    onSave = { _, _ -> },
                    onExport = { "" },
                    onImport = {},
                    onYears = {},
                    onStarting = {},
                    onSwim = {},
                    onCheckIn = {},
                    onHealthPermission = {},
                )
            }
        }
        compose.onNodeWithText("5 to 150 lb in 5 lb steps").assertExists()
        compose.onNodeWithText("45 lb bar, 5 lb jumps, up to 545 lb").assertExists()
        save("settings")
        compose.onNodeWithText("Allow Health Connect").performScrollTo()
        compose.waitForIdle()
        save("settings-health")
    }

    private fun save(name: String) {
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        val width = view.width.coerceAtLeast(1)
        val height = view.height.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        writePng(File("build/screenshots"), name, bitmap)
        val artifacts = File("/opt/cursor/artifacts/screenshots")
        if (artifacts.mkdirs() || artifacts.isDirectory) {
            writePng(artifacts, name, bitmap)
        }
        check(bitmap.width > 100 && bitmap.height > 100) { "$name was ${bitmap.width}x${bitmap.height}" }
        check(hasInk(bitmap)) { "$name rendered blank" }
    }
}

private fun writePng(dir: File, name: String, bitmap: Bitmap) {
    dir.mkdirs()
    File(dir, "$name.png").outputStream().use { stream ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
    }
}

private fun hasInk(bitmap: Bitmap): Boolean {
    val first = bitmap.getPixel(0, 0)
    val stepX = (bitmap.width / 12).coerceAtLeast(1)
    val stepY = (bitmap.height / 20).coerceAtLeast(1)
    var y = 0
    while (y < bitmap.height) {
        var x = 0
        while (x < bitmap.width) {
            if (bitmap.getPixel(x, y) != first) return true
            x += stepX
        }
        y += stepY
    }
    return false
}

private fun sampleHome(): HomeSnapshot {
    return HomeSnapshot(
        day = 'A',
        dateLabel = "Monday, Oct 26",
        headline = "Day A",
        weekLabel = "Block 1 · week 1 of 4",
        rirNote = "Target RIR 3. Stop with reps left.",
        deloadBanner = null,
        slots = listOf(
            todaySlot("A1", "Goblet squat", "3 sets · 50 lb · 10, 9, 9 reps · RIR 3", drafted = true),
            todaySlot("A2", "DB overhead press", "3 sets · 30 lb · 8, 8, 8 reps · RIR 3"),
            todaySlot("A3", "One-arm DB row", "3 sets · 35 lb · 8, 8, 8 reps · RIR 3", perSide = true),
            todaySlot("A4", "Farmer carry", "3 sets · 40 lb · 30, 30, 30 sec · RIR 3", carry = true),
            todaySlot("A5", "Dead bug", "2 sets · 8 reps · bodyweight", optional = true, tracksLoad = false),
        ),
        needsWeights = false,
        checkInDue = false,
        showSafety = false,
        week0Passes = 2,
        isRestDay = false,
        restNote = null,
    )
}

private fun todaySlot(
    id: String,
    name: String,
    prescription: String,
    drafted: Boolean = false,
    perSide: Boolean = false,
    carry: Boolean = false,
    optional: Boolean = false,
    tracksLoad: Boolean = true,
): TodaySlot {
    return TodaySlot(
        id = id,
        name = name,
        optional = optional,
        perSide = perSide,
        isCarry = carry,
        tracksLoad = tracksLoad,
        notes = "",
        prescription = prescription,
        rirLabel = "3",
        sets = 3,
        weight = 50.0,
        targetReps = listOf(8, 8, 8),
        drafted = drafted,
        loggedToday = false,
    )
}

private fun sampleSummary(): FinishOutcome {
    return FinishOutcome(
        rows = listOf(
            SummaryRow(
                slotId = "A1",
                name = "Goblet squat",
                reason = "All sets at 12 and avg RIR 2.3 ≥ 2, so +5 lb",
                weightLabel = "55 lb",
                repsLabel = "8 / 8 / 8",
                rirLabel = "3",
                prompts = emptyList(),
                suggestedWeight = 55.0,
                suggestedReps = listOf(8, 8, 8),
                coaching = true,
            ),
            SummaryRow(
                slotId = "A2",
                name = "DB overhead press",
                reason = "In range, hold weight. Next target 9 / 9 / 8",
                weightLabel = "30 lb",
                repsLabel = "9 / 9 / 8",
                rirLabel = "3",
                prompts = emptyList(),
                suggestedWeight = 30.0,
                suggestedReps = listOf(9, 9, 8),
                coaching = true,
            ),
        ),
        medicalMessage = null,
        deloadBanner = null,
        healthNote = null,
    )
}

private fun sampleHistory(): HistorySnapshot {
    return HistorySnapshot(
        slotId = "A1",
        name = "Goblet squat",
        points = listOf(
            HistoryPoint("Oct 6", 50.0, 10, 13, "3", deload = false, pain = 0, reason = "In range"),
            HistoryPoint("Oct 13", 50.0, 11, 13, "2", deload = false, pain = 0, reason = "In range"),
            HistoryPoint("Oct 20", 50.0, 12, 14, "2", deload = false, pain = 1, reason = "Top of the range"),
            HistoryPoint("Oct 27", 45.0, 8, 12, "4+", deload = true, pain = 0, reason = "Deload"),
        ),
    )
}

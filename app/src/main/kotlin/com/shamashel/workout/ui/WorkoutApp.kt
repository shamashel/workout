package com.shamashel.workout.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

@Composable
fun WorkoutApp(viewModel: WorkoutViewModel) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val message = viewModel.message
    LaunchedEffect(message) {
        if (!message.isNullOrBlank()) {
            snackbar.showSnackbar(message)
            viewModel.clearMessage()
        }
    }
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val showBar = route in setOf("today", "history", "settings")
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    NavigationBarItem(
                        selected = route == "today",
                        onClick = { go(nav, "today") },
                        icon = { Icon(Icons.Filled.FitnessCenter, contentDescription = null) },
                        label = { Text("Today") },
                    )
                    NavigationBarItem(
                        selected = route == "history",
                        onClick = {
                            viewModel.openHistory()
                            go(nav, "history")
                        },
                        icon = { Icon(Icons.Filled.History, contentDescription = null) },
                        label = { Text("History") },
                    )
                    NavigationBarItem(
                        selected = route == "settings",
                        onClick = {
                            viewModel.loadSettings()
                            go(nav, "settings")
                        },
                        icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        label = { Text("Settings") },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "today", modifier = Modifier.padding(padding)) {
            composable("today") {
                val home = viewModel.home
                if (home == null) {
                    Text("Loading the plan…", modifier = Modifier.padding(padding))
                } else {
                    TodayScreen(
                        home = home,
                        onSelectDay = viewModel::selectDay,
                        onOpenSlot = { id ->
                            viewModel.openSlot(id)
                            nav.navigate("log/$id")
                        },
                        onFinish = { minutes, readiness ->
                            viewModel.finishDay(minutes, readiness) {
                                nav.navigate("summary")
                            }
                        },
                        onCheckIn = { nav.navigate("checkin") },
                        onStartWeights = {
                            viewModel.loadStarting()
                            nav.navigate("start")
                        },
                        onDismissSafety = viewModel::acknowledgeSafety,
                        onSwim = { nav.navigate("swim") },
                    )
                }
            }
            composable("log/{id}") {
                val editor = viewModel.editor
                if (editor == null) {
                    Text("Opening the slot…")
                } else {
                    LogScreen(
                        editor = editor,
                        onChange = { viewModel.editor = it },
                        onSave = { viewModel.saveDraft { nav.popBackStack() } },
                    )
                }
            }
            composable("summary") {
                val outcome = viewModel.summary
                if (outcome == null) {
                    Text("Nothing to show yet.")
                } else {
                    SummaryScreen(
                        outcome = outcome,
                        onOverride = viewModel::override,
                        onSubstitute = viewModel::substitute,
                        onDone = {
                            nav.popBackStack("today", inclusive = false)
                            if (viewModel.home?.checkInDue == true) nav.navigate("checkin")
                        },
                    )
                }
            }
            composable("history") {
                HistoryScreen(
                    choices = viewModel.slotChoices,
                    history = viewModel.history,
                    onSelect = viewModel::openHistory,
                )
            }
            composable("settings") {
                val settings = viewModel.settings
                if (settings != null) {
                    SettingsScreen(
                        settings = settings,
                        healthStatus = viewModel.healthStatus,
                        healthGranted = viewModel.healthGranted,
                        health = viewModelHealth(viewModel),
                        onSave = viewModel::saveSettings,
                        onExport = { viewModel.exportJson() },
                        onImport = { raw -> viewModel.importJson(raw) { viewModel.message = it } },
                        onYears = { viewModel.loadSettings(); nav.navigate("years") },
                        onStarting = { viewModel.loadStarting(); nav.navigate("start") },
                        onSwim = { nav.navigate("swim") },
                        onCheckIn = { nav.navigate("checkin") },
                        onHealthPermission = viewModel::onHealthPermission,
                    )
                }
            }
            composable("checkin") {
                CheckInScreen(offerSkip = viewModel.checkInOffer) { soreness, performance, readiness, skip ->
                    viewModel.checkIn(soreness, performance, readiness, skip) { outcome ->
                        if (!outcome.offerSkip) nav.popBackStack()
                    }
                }
            }
            composable("start") {
                StartingWeightsScreen(
                    slots = viewModel.starting,
                    passes = viewModel.home?.week0Passes ?: 0,
                    onPractice = viewModel::practicePass,
                    onSave = { weights ->
                        viewModel.saveStarting(weights)
                        nav.popBackStack()
                    },
                )
            }
            composable("years") {
                YearScreen(
                    phases = viewModel.phases,
                    onSave = viewModel::savePhase,
                    onRelock = viewModel::relockSlot,
                )
            }
            composable("swim") {
                SwimScreen { minutes, pool ->
                    viewModel.logSwim(minutes, pool)
                    nav.popBackStack()
                }
            }
        }
    }
}

private fun go(nav: androidx.navigation.NavHostController, route: String) {
    nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun viewModelHealth(viewModel: WorkoutViewModel): com.shamashel.workout.health.HealthWriter {
    return viewModel.healthWriter()
}

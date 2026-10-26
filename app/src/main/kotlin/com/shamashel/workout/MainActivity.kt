package com.shamashel.workout

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shamashel.workout.ui.WorkoutApp
import com.shamashel.workout.ui.WorkoutTheme
import com.shamashel.workout.ui.WorkoutViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as WorkoutApplication
        setContent {
            WorkoutTheme {
                val model: WorkoutViewModel = viewModel(factory = WorkoutViewModel.factory(app.repository, app.health))
                WorkoutApp(model)
            }
        }
    }
}

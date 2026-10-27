package com.shamashel.workout

import android.app.Application
import com.shamashel.workout.data.WorkoutDatabase
import com.shamashel.workout.data.WorkoutRepository
import com.shamashel.workout.health.HealthWriter

class WorkoutApplication : Application() {
    lateinit var database: WorkoutDatabase
        private set

    fun databaseReady(): Boolean = ::database.isInitialized
    lateinit var repository: WorkoutRepository
        private set
    lateinit var health: HealthWriter
        private set

    override fun onCreate() {
        super.onCreate()
        database = WorkoutDatabase.create(this)
        repository = WorkoutRepository(database)
        health = HealthWriter(this)
    }
}

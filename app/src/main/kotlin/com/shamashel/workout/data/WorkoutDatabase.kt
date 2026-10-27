package com.shamashel.workout.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SlotEntity::class,
        SlotStateEntity::class,
        DraftEntity::class,
        SessionEntity::class,
        SetEntity::class,
        CheckInEntity::class,
        OverrideEntity::class,
        ProgramEntity::class,
        SettingsEntity::class,
        WorkoutDayEntity::class,
        SwimEntity::class,
        PhaseNoteEntity::class,
        SlotOverrideEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class WorkoutDatabase : RoomDatabase() {
    abstract fun dao(): WorkoutDao

    companion object {
        fun create(context: Context): WorkoutDatabase {
            return Room.databaseBuilder(context, WorkoutDatabase::class.java, "workout.db").build()
        }
    }
}

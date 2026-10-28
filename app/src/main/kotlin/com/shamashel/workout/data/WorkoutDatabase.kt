package com.shamashel.workout.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
    version = 2,
    exportSchema = false,
)
abstract class WorkoutDatabase : RoomDatabase() {
    abstract fun dao(): WorkoutDao

    /** Fold the write-ahead log into workout.db so a file copy is a complete snapshot. */
    fun checkpoint() {
        val cursor = openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)")
        try {
            cursor.moveToFirst()
        } finally {
            cursor.close()
        }
    }

    companion object {
        const val FILE_NAME = "workout.db"

        fun create(context: Context, name: String = FILE_NAME): WorkoutDatabase {
            return Room.databaseBuilder(context, WorkoutDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2)
                .build()
        }
    }
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE settings ADD COLUMN weekSchedule TEXT NOT NULL DEFAULT '$YEAR1_WEEK'")
        db.execSQL("ALTER TABLE settings ADD COLUMN dayOverrideEpoch INTEGER")
        db.execSQL("ALTER TABLE settings ADD COLUMN dayOverride TEXT")
    }
}

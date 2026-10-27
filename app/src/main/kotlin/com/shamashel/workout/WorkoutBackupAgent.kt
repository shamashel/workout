package com.shamashel.workout

import android.app.backup.BackupAgentHelper
import android.app.backup.FullBackupDataOutput

/**
 * Auto Backup copies files. Room keeps recent writes in workout.db-wal until a checkpoint,
 * so fold that log into workout.db before the copy.
 */
class WorkoutBackupAgent : BackupAgentHelper() {
    override fun onFullBackup(data: FullBackupDataOutput) {
        val app = applicationContext as? WorkoutApplication
        if (app != null && app.databaseReady()) {
            runCatching { app.database.checkpoint() }
        }
        super.onFullBackup(data)
    }
}

package com.shamashel.workout.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.metadata.Metadata
import java.time.Instant
import java.time.ZoneId

enum class HealthAvailability {
    READY,
    UPDATE_REQUIRED,
    UNAVAILABLE,
}

class HealthWriter(private val context: Context) {
    fun availability(): HealthAvailability {
        return try {
            when (HealthConnectClient.getSdkStatus(context)) {
                HealthConnectClient.SDK_AVAILABLE -> HealthAvailability.READY
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthAvailability.UPDATE_REQUIRED
                else -> HealthAvailability.UNAVAILABLE
            }
        } catch (error: Exception) {
            HealthAvailability.UNAVAILABLE
        }
    }

    fun writePermission(): String {
        return HealthPermission.getWritePermission(ExerciseSessionRecord::class)
    }

    suspend fun hasWritePermission(): Boolean {
        if (availability() != HealthAvailability.READY) return false
        return try {
            val granted = client().permissionController.getGrantedPermissions()
            granted.contains(writePermission())
        } catch (error: Exception) {
            false
        }
    }

    suspend fun writeStrength(start: Instant, end: Instant, title: String): String? {
        return write(start, end, ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, title)
    }

    suspend fun writeSwim(start: Instant, end: Instant, pool: Boolean): String? {
        val type = if (pool) {
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL
        } else {
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER
        }
        return write(start, end, type, if (pool) "Pool swim" else "Open-water swim")
    }

    fun installIntent(): Intent {
        return Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private suspend fun write(start: Instant, end: Instant, type: Int, title: String): String? {
        if (end <= start) return "Session length has to be at least a minute."
        when (availability()) {
            HealthAvailability.UNAVAILABLE -> {
                return "Health Connect isn't available on this phone. The session stays in the app."
            }
            HealthAvailability.UPDATE_REQUIRED -> {
                return "Health Connect needs an update. The session stays in the app."
            }
            HealthAvailability.READY -> Unit
        }
        if (!hasWritePermission()) {
            return "Health Connect write permission isn't granted. The session stays in the app."
        }
        return try {
            val offset = ZoneId.systemDefault().rules.getOffset(start)
            val record = ExerciseSessionRecord(
                startTime = start,
                startZoneOffset = offset,
                endTime = end,
                endZoneOffset = offset,
                exerciseType = type,
                title = title,
                metadata = Metadata.manualEntry(),
            )
            client().insertRecords(listOf(record))
            null
        } catch (error: Exception) {
            "Couldn't write to Health Connect (${error.javaClass.simpleName}). The session stays in the app."
        }
    }

    private fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(context)
}

package com.ddgo.wear.runtime

import android.Manifest
import android.content.Context
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

object WearPermissionHelper {
    private const val READ_HEART_RATE_PERMISSION = "android.permission.health.READ_HEART_RATE"
    private const val READ_HEALTH_DATA_IN_BACKGROUND_PERMISSION =
        "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"

    private fun foregroundPermissions(): List<String> {
        return buildList {
            if (Build.VERSION.SDK_INT >= 36) {
                add(READ_HEART_RATE_PERMISSION)
            } else {
                add(Manifest.permission.BODY_SENSORS)
            }
            add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
    }

    fun missingForegroundPermissions(context: Context): List<String> {
        val appContext = context.applicationContext
        return foregroundPermissions().filter { permission ->
            ContextCompat.checkSelfPermission(appContext, permission) != PackageManager.PERMISSION_GRANTED
        }
    }

    fun isBackgroundBodySensorsRequired(): Boolean {
        return Build.VERSION.SDK_INT in Build.VERSION_CODES.TIRAMISU..Build.VERSION_CODES.VANILLA_ICE_CREAM
    }

    fun isBackgroundHealthDataRequired(): Boolean {
        return Build.VERSION.SDK_INT >= 36
    }

    fun hasBackgroundBodySensorsPermission(context: Context): Boolean {
        if (!isBackgroundBodySensorsRequired()) {
            return true
        }
        return ContextCompat.checkSelfPermission(
            context.applicationContext,
            Manifest.permission.BODY_SENSORS_BACKGROUND
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasBackgroundHealthDataPermission(context: Context): Boolean {
        if (!isBackgroundHealthDataRequired()) {
            return true
        }
        return ContextCompat.checkSelfPermission(
            context.applicationContext,
            READ_HEALTH_DATA_IN_BACKGROUND_PERMISSION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun backgroundPermissionToRequest(): String? {
        return when {
            isBackgroundHealthDataRequired() -> READ_HEALTH_DATA_IN_BACKGROUND_PERMISSION
            isBackgroundBodySensorsRequired() -> Manifest.permission.BODY_SENSORS_BACKGROUND
            else -> null
        }
    }

    fun hasAllExercisePermissions(context: Context): Boolean {
        return missingForegroundPermissions(context).isEmpty() &&
            hasBackgroundBodySensorsPermission(context) &&
            hasBackgroundHealthDataPermission(context)
    }
}

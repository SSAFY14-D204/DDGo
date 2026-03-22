package com.ddgo.wear.runtime

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager

object WearPermissionHelper {
    private val foregroundPermissions = listOf(
        Manifest.permission.BODY_SENSORS,
        Manifest.permission.ACTIVITY_RECOGNITION
    )

    fun missingForegroundPermissions(context: Context): List<String> {
        val appContext = context.applicationContext
        return foregroundPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(appContext, permission) != PackageManager.PERMISSION_GRANTED
        }
    }

    fun isBackgroundBodySensorsRequired(): Boolean {
        return Build.VERSION.SDK_INT in Build.VERSION_CODES.TIRAMISU..Build.VERSION_CODES.VANILLA_ICE_CREAM
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

    fun hasAllExercisePermissions(context: Context): Boolean {
        return missingForegroundPermissions(context).isEmpty() &&
            hasBackgroundBodySensorsPermission(context)
    }
}

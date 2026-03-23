package com.ddgo.wear.runtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.ddgo.shared.model.WatchState
import com.ddgo.wear.MainActivity
import com.ddgo.wear.R
import com.ddgo.wear.data.ExerciseRuntimeSnapshot

class OngoingActivityController(
    private val context: Context
) {
    private val appContext = context.applicationContext

    fun startOrUpdate(
        service: Service,
        snapshot: ExerciseRuntimeSnapshot
    ) {
        ensureChannel()
        val notification = buildNotification(snapshot)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            service.startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
            )
        } else {
            service.startForeground(NOTIFICATION_ID, notification)
        }
    }

    fun stop(service: Service) {
        service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
        NotificationManagerCompat.from(appContext).cancel(NOTIFICATION_ID)
    }

    private fun buildNotification(snapshot: ExerciseRuntimeSnapshot): Notification {
        val contentIntent = PendingIntent.getActivity(
            appContext,
            0,
            Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = buildString {
            append(snapshot.watchState.toStatusLabel())
            snapshot.latestHeartRate?.let { heartRate ->
                append(" | ")
                append(heartRate)
                append(" bpm")
            }
        }

        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_watch_session)
            .setContentTitle(appContext.getString(R.string.wear_notification_title))
            .setContentText(contentText)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        OngoingActivity.Builder(appContext, NOTIFICATION_ID, builder)
            .setOngoingActivityId(ONGOING_ACTIVITY_ID)
            .setStaticIcon(R.drawable.ic_watch_session)
            .setAnimatedIcon(R.drawable.ic_watch_session)
            .setTitle(appContext.getString(R.string.wear_app_label))
            .setTouchIntent(contentIntent)
            .setStatus(buildStatus(snapshot))
            .build()
            .apply(appContext)

        return builder.build()
    }

    private fun buildStatus(snapshot: ExerciseRuntimeSnapshot): Status {
        val stateText = snapshot.watchState.toStatusLabel()
        val heartRateText = snapshot.latestHeartRate?.let { "$it bpm" } ?: "--"

        return Status.Builder()
            .addPart("state", Status.TextPart(stateText))
            .addPart("hr", Status.TextPart(heartRateText))
            .addTemplate("#state#")
            .addTemplate("#state# | #hr#")
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val channel = NotificationChannel(
            CHANNEL_ID,
            appContext.getString(R.string.wear_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = appContext.getString(R.string.wear_notification_channel_description)
            setShowBadge(false)
        }
        appContext.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "watch_exercise_session"
        private const val NOTIFICATION_ID = 27001
        private const val ONGOING_ACTIVITY_ID = 270
    }
}

private fun WatchState.toStatusLabel(): String {
    return when (this) {
        WatchState.IDLE -> "대기"
        WatchState.RECORDING -> "측정 중"
        WatchState.ALERTING -> "경고"
        WatchState.SENSOR_UNAVAILABLE -> "센서 오류"
        WatchState.PERMISSION_BLOCKED -> "권한 필요"
        WatchState.SESSION_RECOVERING -> "연결 중"
    }
}

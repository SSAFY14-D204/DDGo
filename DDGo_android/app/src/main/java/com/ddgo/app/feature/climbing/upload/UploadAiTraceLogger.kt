package com.ddgo.app.feature.climbing.upload

import android.os.SystemClock
import android.util.Log
import com.ddgo.app.BuildConfig

internal object UploadAiTraceLogger {
    private const val TAG = "UploadAiTrace"
    private const val PREFIX = "[AI_TRACE]"

    fun log(
        event: String,
        generation: Long? = null,
        playbackUri: String? = null,
        phase: String? = null,
        status: String? = null,
        elapsedMs: Long? = null,
        details: Map<String, Any?> = emptyMap()
    ) {
        if (!BuildConfig.DEBUG) {
            return
        }

        val parts = mutableListOf("$PREFIX event=$event")
        generation?.let { parts += "generation=$it" }
        playbackUri?.let { parts += "playbackUri=${shortPlaybackUri(it)}" }
        phase?.let { parts += "phase=$phase" }
        status?.let { parts += "status=$status" }
        elapsedMs?.let { parts += "elapsedMs=$it" }
        details.forEach { (key, value) ->
            if (value != null) {
                parts += "$key=$value"
            }
        }

        Log.d(TAG, parts.joinToString(" "))
    }

    fun now(): Long = SystemClock.elapsedRealtime()

    fun elapsedSince(startedAtMillis: Long): Long = SystemClock.elapsedRealtime() - startedAtMillis

    private fun shortPlaybackUri(uri: String): String {
        return uri.substringAfterLast('/')
    }
}

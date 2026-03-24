package com.ddgo.app.feature.climbing.upload

import android.os.SystemClock
import android.util.Log
import com.ddgo.app.BuildConfig
import com.ddgo.app.domain.model.Hold
import java.util.Locale

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

    fun formatBoundingBox(boundingBox: Hold.BoundingBox): String {
        return formatRect(
            left = boundingBox.left,
            top = boundingBox.top,
            right = boundingBox.right,
            bottom = boundingBox.bottom
        )
    }

    fun formatBoundingBoxes(boundingBoxes: List<Hold.BoundingBox>): String {
        if (boundingBoxes.isEmpty()) return "[]"
        return boundingBoxes.mapIndexed { index, boundingBox ->
            "#$index=${formatBoundingBox(boundingBox)}"
        }.joinToString(prefix = "[", postfix = "]", separator = ", ")
    }

    fun formatCropBounds(bounds: RawVerticalCropBounds?): String {
        if (bounds == null) return "null"
        return "top=${formatFloat(bounds.topFraction)}, bottom=${formatFloat(bounds.bottomFraction)}"
    }

    fun formatRect(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float
    ): String {
        return "l=${formatFloat(left)},t=${formatFloat(top)},r=${formatFloat(right)},b=${formatFloat(bottom)}"
    }

    private fun shortPlaybackUri(uri: String): String {
        return uri.substringAfterLast('/')
    }

    private fun formatFloat(value: Float): String {
        return String.format(Locale.US, "%.4f", value)
    }
}

package com.ddgo.app.feature.climbing.shared.navigation

import android.net.Uri
import android.os.Bundle
import com.ddgo.app.feature.climbing.shared.model.ClimbingRecordedAttemptDraft

data class ClimbingUploadEntryArgs(
    val recordedVideoUri: String? = null,
    val realtimeSessionId: String? = null
) {
    val hasAnyValue: Boolean
        get() = !recordedVideoUri.isNullOrBlank() || !realtimeSessionId.isNullOrBlank()
}

fun ClimbingRecordedAttemptDraft.toClimbingUploadEntryArgs(): ClimbingUploadEntryArgs {
    return ClimbingUploadEntryArgs(
        recordedVideoUri = videoUri,
        realtimeSessionId = realtimeSessionId
    )
}

fun buildClimbingUploadRoute(
    baseRoute: String,
    recordedVideoUriArgName: String,
    realtimeSessionIdArgName: String,
    entryArgs: ClimbingUploadEntryArgs = ClimbingUploadEntryArgs()
): String {
    return buildString {
        append(baseRoute)
        if (entryArgs.hasAnyValue) {
            append("?")
            append(recordedVideoUriArgName)
            append("=")
            append(Uri.encode(entryArgs.recordedVideoUri.orEmpty()))
            append("&")
            append(realtimeSessionIdArgName)
            append("=")
            append(Uri.encode(entryArgs.realtimeSessionId.orEmpty()))
        }
    }
}

fun Bundle?.toClimbingUploadEntryArgs(
    recordedVideoUriArgName: String,
    realtimeSessionIdArgName: String
): ClimbingUploadEntryArgs {
    return ClimbingUploadEntryArgs(
        recordedVideoUri = this?.getString(recordedVideoUriArgName)?.let(Uri::decode),
        realtimeSessionId = this?.getString(realtimeSessionIdArgName)?.takeIf { it.isNotBlank() }
    )
}

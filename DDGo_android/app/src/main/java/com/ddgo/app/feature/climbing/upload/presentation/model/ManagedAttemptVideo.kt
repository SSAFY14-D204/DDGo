package com.ddgo.app.feature.climbing.upload

data class ManagedAttemptVideo(
    val sourceUri: String,
    val playbackUri: String,
    val tempFilePath: String?,
    val realtimeSessionId: String? = null
)

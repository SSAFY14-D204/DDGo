package com.ddgo.app.feature.climbing.shared.model

data class ClimbingRecordedAttemptDraft(
    val videoUri: String,
    val thumbnailFrame: ClimbingRecordThumbnailFrame? = null,
    val realtimeSessionId: String? = null,
    val frameWidthPx: Int? = null,
    val frameHeightPx: Int? = null
)

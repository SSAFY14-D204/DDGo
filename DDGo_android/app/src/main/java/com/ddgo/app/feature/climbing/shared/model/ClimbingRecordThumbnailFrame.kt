package com.ddgo.app.feature.climbing.shared.model

data class ClimbingRecordThumbnailFrame(
    val frameIndex: Int,
    val timestampMs: Long,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int
)

package com.ddgo.app.domain.model

data class ProcessedPoseDetectionFrame(
    val timestampMs: Long,
    val poseDetected: Boolean
)

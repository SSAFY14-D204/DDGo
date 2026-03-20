package com.ddgo.app.domain.model

data class PrePoseVideoAnalysisResult(
    val poses: List<Pose>,
    val processedFrames: List<ProcessedPoseDetectionFrame>
)

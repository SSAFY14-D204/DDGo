package com.ddgo.app.domain.model

data class AiAnalysisRequestContext(
    val mode: AiAnalysisMode,
    val holds: List<Hold>,
    val poseSequence: AiPoseSequence,
    val frameWidthPx: Int,
    val frameHeightPx: Int,
    val heightCm: Float,
    val weightKg: Float?,
    val wingspanCm: Float?,
    val topKCrux: Int = 3,
    val frameStep: Int = 1
)

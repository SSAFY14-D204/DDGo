package com.ddgo.app.core.config

import com.ddgo.app.domain.model.AiAnalysisMode

enum class AiAnalysisVariant(
    val fastPath: String,
    val physicsPath: String,
    val useGzipRequest: Boolean,
    val uploadPrePoseAnalysisFps: Int,
    val primaryRequestMaxFrameCount: Int,
    val retryRequestMaxFrameCount: Int,
    val defaultVideoPoseAnalysisFps: Int
) {
    V1(
        fastPath = "api/v1/mujoco-complete/analyze/fast",
        physicsPath = "api/v1/mujoco-complete/analyze/physics",
        useGzipRequest = false,
        uploadPrePoseAnalysisFps = 10,
        primaryRequestMaxFrameCount = 90,
        retryRequestMaxFrameCount = 48,
        defaultVideoPoseAnalysisFps = 10
    ),
    V2(
        fastPath = "api/v2/mujoco-complete/analyze/fast",
        physicsPath = "api/v2/mujoco-complete/analyze/physics",
        useGzipRequest = true,
        uploadPrePoseAnalysisFps = 30,
        primaryRequestMaxFrameCount = Int.MAX_VALUE,
        retryRequestMaxFrameCount = 48,
        defaultVideoPoseAnalysisFps = 30
    );

    fun analyzePath(mode: AiAnalysisMode): String {
        return when (mode) {
            AiAnalysisMode.FAST -> fastPath
            AiAnalysisMode.PHYSICS -> physicsPath
        }
    }
}

internal object AiAnalysisVariantConfig {
    val current: AiAnalysisVariant = AiAnalysisVariant.V1
}

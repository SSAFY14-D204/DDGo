package com.ddgo.app.core.config

import com.ddgo.app.domain.model.AiAnalysisMode

enum class AiRequestTransport {
    PLAIN,
    GZIP
}

data class AiAnalysisSamplingPolicy(
    val uploadPrePoseAnalysisFps: Int,
    val primaryRequestMaxFrameCount: Int,
    val retryRequestMaxFrameCount: Int,
    val defaultVideoPoseAnalysisFps: Int
)

enum class AiAnalysisVariant(
    val fastPath: String,
    val physicsPath: String,
    val requestTransport: AiRequestTransport,
    private val samplingPolicy: AiAnalysisSamplingPolicy
) {
    V1(
        fastPath = "api/v1/mujoco-complete/analyze/fast",
        physicsPath = "api/v1/mujoco-complete/analyze/physics",
        requestTransport = AiRequestTransport.PLAIN,
        samplingPolicy = AiAnalysisSamplingPolicy(
            uploadPrePoseAnalysisFps = 10,
            primaryRequestMaxFrameCount = 90,
            retryRequestMaxFrameCount = 48,
            defaultVideoPoseAnalysisFps = 10
        )
    ),
    V2(
        fastPath = "api/v2/mujoco-complete/analyze/fast",
        physicsPath = "api/v2/mujoco-complete/analyze/physics",
        requestTransport = AiRequestTransport.GZIP,
        samplingPolicy = AiAnalysisSamplingPolicy(
            uploadPrePoseAnalysisFps = 30,
            primaryRequestMaxFrameCount = Int.MAX_VALUE,
            retryRequestMaxFrameCount = 48,
            defaultVideoPoseAnalysisFps = 30
        )
    ),
    V2_GZIP_10FPS(
        fastPath = "api/v2/mujoco-complete/analyze/fast",
        physicsPath = "api/v2/mujoco-complete/analyze/physics",
        requestTransport = AiRequestTransport.GZIP,
        samplingPolicy = AiAnalysisSamplingPolicy(
            uploadPrePoseAnalysisFps = 10,
            primaryRequestMaxFrameCount = Int.MAX_VALUE,
            retryRequestMaxFrameCount = 48,
            defaultVideoPoseAnalysisFps = 10
        )
    );

    val uploadPrePoseAnalysisFps: Int
        get() = samplingPolicy.uploadPrePoseAnalysisFps

    val primaryRequestMaxFrameCount: Int
        get() = samplingPolicy.primaryRequestMaxFrameCount

    val retryRequestMaxFrameCount: Int
        get() = samplingPolicy.retryRequestMaxFrameCount

    val defaultVideoPoseAnalysisFps: Int
        get() = samplingPolicy.defaultVideoPoseAnalysisFps

    fun analyzePath(mode: AiAnalysisMode): String {
        return when (mode) {
            AiAnalysisMode.FAST -> fastPath
            AiAnalysisMode.PHYSICS -> physicsPath
        }
    }
}

internal object AiAnalysisVariantConfig {
    val current: AiAnalysisVariant = AiAnalysisVariant.V2_GZIP_10FPS
}

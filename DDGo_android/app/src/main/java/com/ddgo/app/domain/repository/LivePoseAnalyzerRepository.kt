package com.ddgo.app.domain.repository

import com.ddgo.app.domain.model.AiPoseFrame

data class LivePoseSessionConfig(
    val sessionLabel: String = "record",
    val targetAnalysisFps: Int = 10,
    val minPoseDetectionConfidence: Float = 0.5f,
    val minPosePresenceConfidence: Float = 0.5f,
    val minTrackingConfidence: Float = 0.5f
)

data class LivePoseFrameInput(
    val frameIndex: Int,
    val timestampMs: Long,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val argb8888Bytes: ByteArray
)

data class LivePoseAnalysisSummary(
    val submittedFrameCount: Int,
    val detectedFrameCount: Int,
    val lastFrameTimestampMs: Long? = null,
    val endedAtTimestampMs: Long? = null,
    val lastErrorMessage: String? = null
)

interface LivePoseAnalyzerRepository {
    suspend fun start(
        config: LivePoseSessionConfig,
        onPoseFrame: (AiPoseFrame) -> Unit,
        onError: (Throwable) -> Unit = {}
    ): Result<Unit>

    suspend fun submitFrame(frame: LivePoseFrameInput): Result<Unit>

    suspend fun stop(): Result<LivePoseAnalysisSummary>
}

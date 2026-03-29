package com.ddgo.app.domain.model

/**
 * AI 서버 전송용 비디오 포즈 시퀀스.
 *
 * 기존 [Pose] 모델은 그대로 유지하고,
 * 서버 전송에 필요한 frame index, world landmarks, video metadata를 별도로 보관합니다.
 */
data class AiPoseSequence(
    val source: AiPayloadSource,
    val videoMetadata: AiVideoMetadata,
    val frames: List<AiPoseFrame>
)

/**
 * AI 서버 전송용 프레임 데이터입니다.
 */
data class AiPoseFrame(
    val frameIndex: Int,
    val timestampMs: Long,
    val poseDetected: Boolean,
    val poseLandmarks: List<AiLandmark3D>,
    val poseWorldLandmarks: List<AiLandmark3D>
)

/**
 * 3D 랜드마크 좌표입니다.
 */
data class AiLandmark3D(
    val index: Int,
    val x: Float,
    val y: Float,
    val z: Float,
    val visibility: Float? = null,
    val presence: Float? = null
)

/**
 * 비디오 메타데이터입니다.
 */
data class AiVideoMetadata(
    val frameWidth: Int,
    val frameHeight: Int,
    val fps: Float? = null,
    val totalFrames: Int = 0,
    val processedFrames: Int = 0,
    val frameStep: Int = 0,
    val rotationDegrees: Int = 0,
    val mimeType: String? = null,
    val analysisFpsLimit: Int = 1,
    val decodedFrameCount: Int = 0,
    val skippedFrameCount: Int = 0
)

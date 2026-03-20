package com.ddgo.app.domain.poseanalysis

enum class LandmarkSource {
    LANDMARKS,
    WORLD_LANDMARKS
}

enum class TorsoOrientation {
    FRONT,
    BACK,
    MIXED,
    UNKNOWN
}

data class Landmark(
    val index: Int,
    val x: Double,
    val y: Double,
    val z: Double = 0.0,
    val visibility: Double? = null,
    val presence: Double? = null
)

data class PoseFrame(
    val frameTimeMs: Long,
    val landmarks: List<Landmark> = emptyList(),
    val worldLandmarks: List<Landmark> = emptyList()
)

data class HandPeakConfig(
    val landmarkSource: LandmarkSource = LandmarkSource.LANDMARKS,
    val minVisibility: Double = 0.5,
    val minPresence: Double = 0.5,
    val handPeakMedianWindowFrames: Int = 5,
    val handPeakMeanWindowFrames: Int = 7,
    val handPeakBandRadius: Double = 0.05,
    val handTopMinSupportCount: Int = 5,
    val handEndMinDurationMs: Long = 500L,
    val handPeakLowFootHeightThreshold: Double = 0.3,
    val facingMinDurationMs: Long = 500L
)

data class FrameBodyPartHeights(
    val frameTimeMs: Long,
    val handHeight: Double? = null,
    val torsoHeight: Double? = null,
    val footHeight: Double? = null,
    val torsoScale: Double? = null,
    val handHeightSmooth: Double? = null,
    val torsoHeightSmooth: Double? = null,
    val footHeightSmooth: Double? = null,
    val torsoOrientation: TorsoOrientation = TorsoOrientation.UNKNOWN,
    val facingCamera: Boolean = false
)

data class HandPeakAnnotation(
    val globalTopTimeMs: Long,
    val globalTopHeight: Double,
    val selectedTopTimeMs: Long?,
    val selectedTopHeight: Double?,
    val supportCount: Int,
    val endTimeMs: Long?,
    val endHeight: Double?,
    val validTopFound: Boolean
)

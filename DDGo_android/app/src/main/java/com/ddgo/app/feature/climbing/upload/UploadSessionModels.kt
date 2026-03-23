package com.ddgo.app.feature.climbing.upload

import com.ddgo.app.data.remote.pose.PoseSequenceDto
import com.ddgo.app.domain.model.AiPoseSequence
import com.ddgo.app.domain.model.AnalysisPoint
import com.ddgo.app.domain.model.Pose
import com.ddgo.app.domain.model.ProcessedPoseDetectionFrame
import com.ddgo.app.domain.model.UploadedAttemptVideo
import com.ddgo.app.domain.poseanalysis.HandPeakAnnotation
import com.ddgo.app.domain.usecase.AttemptHoldReachResult
import com.ddgo.app.domain.usecase.ClimbEndDetection
import com.ddgo.app.domain.usecase.OverallHoldReachSummary
import com.ddgo.app.domain.usecase.PolygonHoldContactDebugResult

internal const val UPLOAD_PREPOSE_ANALYSIS_FPS = 30

data class ManagedAttemptVideo(
    val sourceUri: String,
    val playbackUri: String,
    val tempFilePath: String?,
    val realtimeSessionId: String? = null
)

enum class PrePoseStatus {
    Pending,
    Running,
    Ready,
    Failed
}

data class PrePoseCacheEntry(
    val playbackUri: String,
    val selectionGeneration: Long,
    val status: PrePoseStatus,
    val aiPoseSequence: AiPoseSequence? = null,
    val poses: List<Pose> = emptyList(),
    val processedFrames: List<ProcessedPoseDetectionFrame> = emptyList(),
    val personObservationStartTimeMs: Long? = null,
    val climbEndDetection: ClimbEndDetection? = null,
    val handPeakAnnotation: HandPeakAnnotation? = null,
    val timelinePoints: List<AnalysisPoint> = emptyList(),
    val errorMessage: String? = null,
    val taskId: Long? = null
)

internal fun PrePoseCacheEntry.toTerminalEntry(): TerminalPrePoseEntry = TerminalPrePoseEntry(
    playbackUri = playbackUri,
    selectionGeneration = selectionGeneration,
    status = status,
    aiPoseSequence = aiPoseSequence,
    poses = poses,
    processedFrames = processedFrames,
    personObservationStartTimeMs = personObservationStartTimeMs,
    climbEndDetection = climbEndDetection,
    handPeakAnnotation = handPeakAnnotation,
    timelinePoints = timelinePoints,
    errorMessage = errorMessage
)

data class TerminalPrePoseSnapshot(
    val generation: Long,
    val entriesByPlaybackUri: Map<String, TerminalPrePoseEntry>
)

data class TerminalPrePoseEntry(
    val playbackUri: String,
    val selectionGeneration: Long,
    val status: PrePoseStatus,
    val aiPoseSequence: AiPoseSequence?,
    val poses: List<Pose>,
    val processedFrames: List<ProcessedPoseDetectionFrame>,
    val personObservationStartTimeMs: Long?,
    val climbEndDetection: ClimbEndDetection?,
    val handPeakAnnotation: HandPeakAnnotation?,
    val timelinePoints: List<AnalysisPoint>,
    val errorMessage: String?
)

data class PrePoseBatchState(
    val generation: Long = 0L,
    val totalCount: Int = 0,
    val pendingCount: Int = 0,
    val runningCount: Int = 0,
    val readyCount: Int = 0,
    val failedCount: Int = 0
) {
    val isTerminal: Boolean
        get() = totalCount > 0 && pendingCount == 0 && runningCount == 0
}

internal data class PrePoseTask(
    val playbackUri: String,
    val taskId: Long
)

internal data class PublishedAttemptResultSession(
    val resultPlaybackUris: List<String>,
    val uploadedAttemptVideos: List<UploadedAttemptVideo>,
    val currentAttemptIndex: Int,
    val attemptAlignedHoldSets: List<AttemptAlignedHoldSet>,
    val holdReachResults: List<AttemptHoldReachResult>,
    val attemptPoseDtos: List<PoseSequenceDto>,
    val attemptAnalyzedPoses: List<List<Pose>>,
    val attemptPolygonHoldContactDebugResults: List<PolygonHoldContactDebugResult>,
    val overallHoldReachSummary: OverallHoldReachSummary?
)

internal enum class UploadFlowMode {
    FullChallenge,
    AttemptOnly
}

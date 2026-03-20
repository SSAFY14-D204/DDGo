package com.ddgo.app.feature.climbing.upload

import com.ddgo.app.domain.model.AnalysisPoint
import com.ddgo.app.domain.model.Pose
import com.ddgo.app.domain.poseanalysis.HandPeakAnnotation
import com.ddgo.app.domain.usecase.ClimbEndDetection

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
    val poses: List<Pose> = emptyList(),
    val personObservationStartTimeMs: Long? = null,
    val climbEndDetection: ClimbEndDetection? = null,
    val handPeakAnnotation: HandPeakAnnotation? = null,
    val timelinePoints: List<AnalysisPoint> = emptyList(),
    val errorMessage: String? = null,
    val taskId: Long? = null
)

data class TerminalPrePoseSnapshot(
    val generation: Long,
    val entriesByPlaybackUri: Map<String, TerminalPrePoseEntry>
)

data class TerminalPrePoseEntry(
    val playbackUri: String,
    val selectionGeneration: Long,
    val status: PrePoseStatus,
    val poses: List<Pose>,
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

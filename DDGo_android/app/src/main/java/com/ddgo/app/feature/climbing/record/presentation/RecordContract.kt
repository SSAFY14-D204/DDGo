package com.ddgo.app.feature.climbing.record.presentation

import com.ddgo.app.domain.model.AiPoseFrame
import com.ddgo.app.domain.repository.LivePoseAnalysisSummary
import com.ddgo.app.feature.climbing.shared.model.ClimbingRecordThumbnailFrame
import com.ddgo.app.feature.climbing.shared.model.ClimbingRecordedAttemptDraft

typealias RecordThumbnailFrame = ClimbingRecordThumbnailFrame
typealias RecordedAttemptDraft = ClimbingRecordedAttemptDraft

data class RecordUiState(
    val hasCameraPermission: Boolean = false,
    val isCameraBound: Boolean = false,
    val isRecording: Boolean = false,
    val isLivePoseAnalyzerStarting: Boolean = false,
    val isLivePoseAnalyzerRunning: Boolean = false,
    val isRealtimeUploadActive: Boolean = false,
    val detectedPoseFrameCount: Int = 0,
    val uploadedPoseFrameCount: Int = 0,
    val bufferedPoseFrameCount: Int = 0,
    val latestPoseFrame: AiPoseFrame? = null,
    val livePoseSummary: LivePoseAnalysisSummary? = null,
    val cameraErrorMessage: String? = null,
    val livePoseErrorMessage: String? = null,
    val recordedDraft: RecordedAttemptDraft? = null,
    val submissionFailureCount: Int = 0,
    val statusMessage: String = "Preparing camera and live pose analysis."
) {
    val canStartRecording: Boolean
        get() = hasCameraPermission && isCameraBound && !isRecording
}

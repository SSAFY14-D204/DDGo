package com.ddgo.app.feature.climbing.record.presentation

import com.ddgo.app.domain.model.AiPoseFrame
import com.ddgo.app.domain.repository.LivePoseAnalysisSummary
import com.ddgo.shared.model.MeasurementStatus
import com.ddgo.shared.model.WatchState

data class RecordThumbnailFrame(
    val frameIndex: Int,
    val timestampMs: Long,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int
)

data class RecordedAttemptDraft(
    val videoUri: String,
    val thumbnailFrame: RecordThumbnailFrame? = null,
    val frameWidthPx: Int? = null,
    val frameHeightPx: Int? = null
)

data class RecordWatchStatus(
    val isConnected: Boolean = false,
    val watchState: WatchState? = null,
    val serviceActive: Boolean = false,
    val alerting: Boolean = false,
    val sensorAvailable: Boolean = false,
    val measurementStatus: MeasurementStatus? = null,
    val latestHeartRate: Int? = null,
    val sessionId: String? = null,
    val lastMeasuredAt: Long? = null,
    val updatedAt: Long? = null,
    val lastAlertReceivedAt: Long? = null
)

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
    val watchStatus: RecordWatchStatus = RecordWatchStatus(),
    val submissionFailureCount: Int = 0,
    val statusMessage: String = "Preparing camera and live pose analysis."
) {
    val canStartRecording: Boolean
        get() = hasCameraPermission && isCameraBound && !isRecording
}

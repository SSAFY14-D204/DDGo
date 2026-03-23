package com.ddgo.app.feature.climbing.record.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ddgo.app.data.wear.RecordingStateSyncManager
import com.ddgo.app.data.wear.WatchRuntimeMonitor
import com.ddgo.app.domain.model.AiPoseFrame
import com.ddgo.app.domain.model.AiVideoMetadata
import com.ddgo.app.domain.repository.LivePoseAnalyzerRepository
import com.ddgo.app.domain.repository.LivePoseFrameInput
import com.ddgo.app.domain.repository.LivePoseSessionConfig
import com.ddgo.shared.model.RecordingState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class RecordViewModel @Inject constructor(
    private val recordingStateSyncManager: RecordingStateSyncManager,
    private val watchRuntimeMonitor: WatchRuntimeMonitor,
    private val livePoseAnalyzerRepository: LivePoseAnalyzerRepository
) : ViewModel() {

    private val sessionConfig = LivePoseSessionConfig(
        sessionLabel = "record",
        targetAnalysisFps = TARGET_ANALYSIS_FPS
    )

    private val _uiState = MutableStateFlow(RecordUiState())
    val uiState: StateFlow<RecordUiState> = _uiState.asStateFlow()

    private var analyzerStartJob: Job? = null
    private var analyzerStopJob: Job? = null
    private var videoMetadata: AiVideoMetadata? = null
    private var currentRecordingSessionId: String? = null

    init {
        watchRuntimeMonitor.start()
        viewModelScope.launch {
            watchRuntimeMonitor.snapshot.collect { snapshot ->
                _uiState.update { current ->
                    current.copy(
                        watchStatus = current.watchStatus.copy(
                            isConnected = snapshot.isWatchConnected,
                            watchState = snapshot.watchSessionStatus?.watchState,
                            serviceActive = snapshot.watchSessionStatus?.serviceActive ?: false,
                            alerting = snapshot.watchSessionStatus?.alerting
                                ?: snapshot.heartRateSnapshot?.alerting
                                ?: false,
                            sensorAvailable = snapshot.watchSessionStatus?.sensorAvailable
                                ?: snapshot.heartRateSnapshot?.sensorAvailable
                                ?: false,
                            measurementStatus = snapshot.heartRateSnapshot?.measurementStatus,
                            latestHeartRate = snapshot.heartRateSnapshot?.heartRate,
                            sessionId = snapshot.watchSessionStatus?.sessionId,
                            lastMeasuredAt = snapshot.heartRateSnapshot?.lastMeasuredAt,
                            updatedAt = listOfNotNull(
                                snapshot.watchSessionStatus?.updatedAt,
                                snapshot.heartRateSnapshot?.updatedAt
                            ).maxOrNull(),
                            lastAlertReceivedAt = snapshot.lastAlertReceivedAt
                        )
                    )
                }
            }
        }
    }

    fun onPermissionChanged(granted: Boolean) {
        _uiState.update { current ->
            current.copy(
                hasCameraPermission = granted,
                cameraErrorMessage = if (granted) null else "Camera permission is required."
            )
        }
    }

    fun onCameraBound() {
        _uiState.update {
            it.copy(
                isCameraBound = true,
                cameraErrorMessage = null,
                statusMessage = "Camera is ready. Live pose analysis will run during recording."
            )
        }
        startLivePoseAnalyzer(forceRestart = !_uiState.value.isLivePoseAnalyzerRunning)
    }

    fun onCameraUnbound() {
        if (_uiState.value.isRecording) {
            syncRecordingState(isRecording = false)
        }
        _uiState.update {
            it.copy(
                isCameraBound = false,
                isRecording = false,
                isRealtimeUploadActive = false
            )
        }
        stopLivePoseAnalyzer()
    }

    fun onRecordingStarted() {
        currentRecordingSessionId = UUID.randomUUID().toString()
        videoMetadata = null

        _uiState.update {
            it.copy(
                isRecording = true,
                isRealtimeUploadActive = false,
                detectedPoseFrameCount = 0,
                uploadedPoseFrameCount = 0,
                bufferedPoseFrameCount = 0,
                livePoseSummary = null,
                recordedDraft = null,
                livePoseErrorMessage = null,
                cameraErrorMessage = null,
                submissionFailureCount = 0,
                statusMessage = if (it.isLivePoseAnalyzerRunning) {
                    "Recording in progress. Live pose is analyzed on-device."
                } else {
                    "Recording in progress. Live pose is unavailable, so upload will continue with batch AI."
                }
            )
        }

        if (!_uiState.value.isLivePoseAnalyzerRunning) {
            startLivePoseAnalyzer(forceRestart = true)
        }
        syncRecordingState(isRecording = true)
    }

    fun onRecordingStopped(draft: RecordedAttemptDraft) {
        syncRecordingState(isRecording = false)
        viewModelScope.launch {
            stopLivePoseAnalyzer()
            _uiState.update {
                it.copy(
                    isRecording = false,
                    isRealtimeUploadActive = false,
                    bufferedPoseFrameCount = 0,
                    recordedDraft = draft.copy(
                        frameWidthPx = videoMetadata?.frameWidth,
                        frameHeightPx = videoMetadata?.frameHeight
                    ),
                    statusMessage = "Recording finished. Upload will continue with batch AI analysis."
                )
            }
        }
    }

    fun onRecordingFailed(message: String) {
        syncRecordingState(isRecording = false)
        viewModelScope.launch {
            stopLivePoseAnalyzer()
            _uiState.update {
                it.copy(
                    isRecording = false,
                    isRealtimeUploadActive = false,
                    cameraErrorMessage = message,
                    statusMessage = "Recording stopped."
                )
            }
        }
    }

    fun onRecordedDraftHandled() {
        _uiState.update { it.copy(recordedDraft = null) }
    }

    fun clearRecordedDraft() {
        _uiState.update {
            it.copy(
                recordedDraft = null,
                statusMessage = "Ready for the next recording."
            )
        }
    }

    fun retryLivePoseAnalysis() {
        if (!_uiState.value.hasCameraPermission || !_uiState.value.isCameraBound) {
            return
        }
        startLivePoseAnalyzer(forceRestart = true)
    }

    fun submitLivePoseFrame(frame: LivePoseFrameInput) {
        if (!_uiState.value.isRecording || !_uiState.value.isLivePoseAnalyzerRunning) {
            return
        }

        videoMetadata = videoMetadata ?: AiVideoMetadata(
            frameWidth = frame.width,
            frameHeight = frame.height,
            fps = TARGET_ANALYSIS_FPS.toFloat(),
            frameStep = 1
        )

        viewModelScope.launch {
            val result = livePoseAnalyzerRepository.submitFrame(frame)
            val throwable = result.exceptionOrNull()
            if (throwable != null) {
                handleLivePoseSubmissionFailure(throwable)
            }
        }
    }

    private fun startLivePoseAnalyzer(forceRestart: Boolean = false) {
        if (analyzerStartJob?.isActive == true) {
            return
        }
        if (_uiState.value.isLivePoseAnalyzerRunning && !forceRestart) {
            return
        }

        analyzerStartJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLivePoseAnalyzerStarting = true,
                    livePoseErrorMessage = null,
                    submissionFailureCount = 0,
                    livePoseSummary = null
                )
            }

            if (forceRestart) {
                livePoseAnalyzerRepository.stop()
            }

            val startResult = livePoseAnalyzerRepository.start(
                config = sessionConfig,
                onPoseFrame = { frame ->
                    viewModelScope.launch {
                        handlePoseFrame(frame)
                    }
                },
                onError = { throwable ->
                    viewModelScope.launch {
                        _uiState.update {
                            it.copy(
                                isLivePoseAnalyzerRunning = false,
                                isLivePoseAnalyzerStarting = false,
                                livePoseErrorMessage = throwable.message ?: throwable.javaClass.simpleName,
                                statusMessage = "Live pose stopped. Upload will continue with batch AI."
                            )
                        }
                    }
                }
            )

            val startError = startResult.exceptionOrNull()
            if (startError == null) {
                _uiState.update {
                    it.copy(
                        isLivePoseAnalyzerRunning = true,
                        isLivePoseAnalyzerStarting = false,
                        livePoseErrorMessage = null,
                        statusMessage = if (it.isRecording) {
                            "Recording in progress. Live pose is analyzed on-device."
                        } else {
                            "Live pose analysis is ready."
                        }
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLivePoseAnalyzerRunning = false,
                        isLivePoseAnalyzerStarting = false,
                        livePoseErrorMessage = startError.message ?: startError.javaClass.simpleName,
                        statusMessage = "Live pose could not start. Upload will continue with batch AI."
                    )
                }
            }
        }
    }

    private fun stopLivePoseAnalyzer() {
        if (analyzerStopJob?.isActive == true) {
            return
        }

        analyzerStopJob = viewModelScope.launch {
            val result = livePoseAnalyzerRepository.stop()
            val summary = result.getOrNull()
            if (summary != null) {
                _uiState.update {
                    it.copy(
                        isLivePoseAnalyzerRunning = false,
                        isLivePoseAnalyzerStarting = false,
                        livePoseSummary = summary
                    )
                }
            } else {
                val throwable = result.exceptionOrNull()
                _uiState.update {
                    it.copy(
                        isLivePoseAnalyzerRunning = false,
                        isLivePoseAnalyzerStarting = false,
                        livePoseErrorMessage = throwable?.message ?: throwable?.javaClass?.simpleName
                    )
                }
            }
        }
    }

    private suspend fun handlePoseFrame(frame: AiPoseFrame) {
        val eligibleFrame = frame.poseDetected &&
            frame.poseLandmarks.size >= MIN_REQUIRED_LANDMARK_COUNT &&
            frame.poseWorldLandmarks.size >= MIN_REQUIRED_LANDMARK_COUNT

        _uiState.update {
            it.copy(
                latestPoseFrame = frame.takeIf { pose -> pose.poseDetected } ?: it.latestPoseFrame
            )
        }

        if (!_uiState.value.isRecording || !eligibleFrame) {
            return
        }

        _uiState.update {
            it.copy(
                latestPoseFrame = frame,
                detectedPoseFrameCount = it.detectedPoseFrameCount + 1,
                bufferedPoseFrameCount = 0
            )
        }
    }

    private suspend fun handleLivePoseSubmissionFailure(throwable: Throwable) {
        val updatedCount = _uiState.value.submissionFailureCount + 1
        _uiState.update {
            it.copy(
                livePoseErrorMessage = throwable.message ?: throwable.javaClass.simpleName,
                submissionFailureCount = updatedCount
            )
        }

        if (updatedCount >= MAX_SUBMISSION_FAILURES) {
            _uiState.update {
                it.copy(
                    isRealtimeUploadActive = false,
                    bufferedPoseFrameCount = 0,
                    statusMessage = "Live pose became unstable. Upload will continue with batch AI."
                )
            }
            stopLivePoseAnalyzer()
        }
    }

    private fun syncRecordingState(isRecording: Boolean) {
        val sessionId = currentRecordingSessionId ?: return
        recordingStateSyncManager.sync(
            RecordingState(
                sessionId = sessionId,
                isRecording = isRecording,
                updatedAt = System.currentTimeMillis()
            )
        )
        if (!isRecording) {
            currentRecordingSessionId = null
        }
    }

    override fun onCleared() {
        analyzerStartJob?.cancel()
        analyzerStopJob?.cancel()
        watchRuntimeMonitor.stop()
        super.onCleared()
    }

    companion object {
        private const val TARGET_ANALYSIS_FPS = 10
        private const val MAX_SUBMISSION_FAILURES = 3
        private const val MIN_REQUIRED_LANDMARK_COUNT = 33
    }
}

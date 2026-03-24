package com.ddgo.app.feature.climbing.record.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ddgo.app.data.wear.RecordingStateSyncManager
import com.ddgo.app.data.wear.WatchRuntimeMonitor
import com.ddgo.app.domain.model.AiAnalysisMode
import com.ddgo.app.domain.model.AiPoseFrame
import com.ddgo.app.domain.model.AiVideoMetadata
import com.ddgo.app.domain.repository.AiRealtimeSessionHandle
import com.ddgo.app.domain.repository.AiRealtimeSessionStartRequest
import com.ddgo.app.domain.repository.LivePoseAnalyzerRepository
import com.ddgo.app.domain.repository.LivePoseFrameInput
import com.ddgo.app.domain.repository.LivePoseSessionConfig
import com.ddgo.app.domain.usecase.AbortAiRealtimeSessionUseCase
import com.ddgo.app.domain.usecase.AppendAiRealtimePoseChunkUseCase
import com.ddgo.app.domain.usecase.BuildAiUserBodyProfileUseCase
import com.ddgo.app.domain.usecase.GetMyInfoUseCase
import com.ddgo.app.domain.usecase.StartAiRealtimeSessionUseCase
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
    private val livePoseAnalyzerRepository: LivePoseAnalyzerRepository,
    private val getMyInfoUseCase: GetMyInfoUseCase,
    private val buildAiUserBodyProfileUseCase: BuildAiUserBodyProfileUseCase,
    private val startAiRealtimeSessionUseCase: StartAiRealtimeSessionUseCase,
    private val appendAiRealtimePoseChunkUseCase: AppendAiRealtimePoseChunkUseCase,
    private val abortAiRealtimeSessionUseCase: AbortAiRealtimeSessionUseCase
) : ViewModel() {

    private val sessionConfig = LivePoseSessionConfig(
        sessionLabel = "record",
        targetAnalysisFps = TARGET_ANALYSIS_FPS
    )

    private val _uiState = MutableStateFlow(RecordUiState())
    val uiState: StateFlow<RecordUiState> = _uiState.asStateFlow()

    private var analyzerStartJob: Job? = null
    private var analyzerStopJob: Job? = null
    private var sessionHandle: AiRealtimeSessionHandle? = null
    private var sessionStartAttempted = false
    private var realtimeUploadDisabled = false
    private var chunkFailureCount = 0
    private var lastChunkUploadedAtMs: Long = 0L
    private var videoMetadata: AiVideoMetadata? = null
    private var sessionTransferredToUpload = false
    private var currentRecordingSessionId: String? = null
    private val bufferedPoseFrames = mutableListOf<AiPoseFrame>()

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

    fun onRecordScreenEntered() {
        recordingStateSyncManager.launchWatchApp()
    }

    fun onPermissionChanged(granted: Boolean) {
        _uiState.update { current ->
            current.copy(
                hasCameraPermission = granted,
                cameraErrorMessage = if (granted) null else "카메라 권한이 필요합니다."
            )
        }
    }

    fun onCameraBound() {
        _uiState.update {
            it.copy(
                isCameraBound = true,
                cameraErrorMessage = null,
                statusMessage = "촬영 준비가 끝났어요. 바로 녹화를 시작할 수 있어요."
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
        sessionTransferredToUpload = false
        sessionHandle = null
        sessionStartAttempted = false
        realtimeUploadDisabled = false
        chunkFailureCount = 0
        lastChunkUploadedAtMs = 0L
        videoMetadata = null
        bufferedPoseFrames.clear()

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
                    "녹화 중이에요. MediaPipe Pose를 실시간으로 분석하고 있어요."
                } else {
                    "녹화 중이에요. 실시간 Pose가 준비되지 않아 저장 영상 분석으로 이어집니다."
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
            flushBufferedPoseFrames(force = true)
            stopLivePoseAnalyzer()

            val reservedSessionId = sessionHandle?.sessionId?.takeIf { !realtimeUploadDisabled }

            _uiState.update {
                it.copy(
                    isRecording = false,
                    isRealtimeUploadActive = false,
                    bufferedPoseFrameCount = 0,
                    recordedDraft = draft.copy(
                        realtimeSessionId = reservedSessionId,
                        frameWidthPx = videoMetadata?.frameWidth,
                        frameHeightPx = videoMetadata?.frameHeight
                    ),
                    statusMessage = if (reservedSessionId != null) {
                        "촬영이 끝났어요. 홀 선택이 끝나면 서버 세션을 바로 finalize 합니다."
                    } else {
                        "촬영이 끝났어요. 기존 저장 영상 분석 흐름으로 이어집니다."
                    }
                )
            }
        }
    }

    fun onRecordingFailed(message: String) {
        syncRecordingState(isRecording = false)
        viewModelScope.launch {
            abortRealtimeSessionIfNeeded()
            stopLivePoseAnalyzer()
            _uiState.update {
                it.copy(
                    isRecording = false,
                    isRealtimeUploadActive = false,
                    cameraErrorMessage = message,
                    statusMessage = "녹화가 중단되었어요."
                )
            }
        }
    }

    fun onRecordedDraftHandled() {
        sessionTransferredToUpload = true
        _uiState.update { it.copy(recordedDraft = null) }
    }

    fun clearRecordedDraft() {
        if (!sessionTransferredToUpload) {
            viewModelScope.launch {
                abortRealtimeSessionIfNeeded()
            }
        }
        sessionTransferredToUpload = false
        _uiState.update {
            it.copy(
                recordedDraft = null,
                statusMessage = "새로운 녹화를 시작할 수 있어요."
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

        ensureRealtimeSessionStarted()

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
                                statusMessage = "실시간 Pose 분석이 중단되어 저장 영상 분석으로 전환됩니다."
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
                            "녹화 중이에요. MediaPipe Pose를 실시간으로 분석하고 있어요."
                        } else {
                            "MediaPipe Pose 실시간 분석 준비가 끝났어요."
                        }
                    )
                }
            } else {
                realtimeUploadDisabled = true
                _uiState.update {
                    it.copy(
                        isLivePoseAnalyzerRunning = false,
                        isLivePoseAnalyzerStarting = false,
                        livePoseErrorMessage = startError.message ?: startError.javaClass.simpleName,
                        statusMessage = "실시간 Pose를 시작하지 못해 저장 영상 분석으로 전환됩니다."
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

        bufferedPoseFrames += frame
        _uiState.update {
            it.copy(
                latestPoseFrame = frame,
                detectedPoseFrameCount = it.detectedPoseFrameCount + 1,
                bufferedPoseFrameCount = bufferedPoseFrames.size
            )
        }

        flushBufferedPoseFrames(force = false)
    }

    private fun ensureRealtimeSessionStarted() {
        if (sessionStartAttempted || realtimeUploadDisabled || videoMetadata == null) {
            return
        }

        sessionStartAttempted = true
        viewModelScope.launch {
            val user = getMyInfoUseCase().getOrElse { throwable ->
                disableRealtimeUpload(
                    message = throwable.message ?: "사용자 정보를 가져오지 못해 batch 분석으로 전환합니다."
                )
                return@launch
            }

            val profile = buildAiUserBodyProfileUseCase(
                user = user,
                allowMissingWeight = true
            ).getOrElse { throwable ->
                disableRealtimeUpload(
                    message = throwable.message ?: "체형 정보를 만들지 못해 batch 분석으로 전환합니다."
                )
                return@launch
            }

            val handle = startAiRealtimeSessionUseCase(
                AiRealtimeSessionStartRequest(
                    mode = AiAnalysisMode.PHYSICS,
                    userBodyProfile = profile,
                    videoMetadata = videoMetadata ?: return@launch,
                    frameStep = 1
                )
            ).getOrElse { throwable ->
                disableRealtimeUpload(
                    message = throwable.message ?: "실시간 세션 시작에 실패해 batch 분석으로 전환합니다."
                )
                return@launch
            }

            sessionHandle = handle
            chunkFailureCount = 0
            _uiState.update {
                it.copy(
                    isRealtimeUploadActive = true,
                    statusMessage = "녹화 중이에요. 포즈 프레임을 AI 서버 세션에 선전송하고 있어요."
                )
            }
        }
    }

    private suspend fun flushBufferedPoseFrames(force: Boolean) {
        val handle = sessionHandle ?: return
        if (bufferedPoseFrames.isEmpty()) {
            return
        }

        val latestFrameTimestamp = bufferedPoseFrames.last().timestampMs
        if (!force && bufferedPoseFrames.size < CHUNK_FRAME_COUNT &&
            latestFrameTimestamp - lastChunkUploadedAtMs < CHUNK_INTERVAL_MS
        ) {
            return
        }

        val framesToSend = bufferedPoseFrames.toList()
        bufferedPoseFrames.clear()

        val ack = appendAiRealtimePoseChunkUseCase(handle, framesToSend).getOrElse { throwable ->
            bufferedPoseFrames.addAll(0, framesToSend)
            chunkFailureCount += 1
            Log.w(TAG, "Realtime pose chunk upload failed.", throwable)
            if (chunkFailureCount >= MAX_CHUNK_FAILURES) {
                disableRealtimeUpload(
                    message = "실시간 청크 업로드가 반복 실패해 저장 영상 분석으로 전환합니다."
                )
            } else {
                _uiState.update {
                    it.copy(
                        bufferedPoseFrameCount = bufferedPoseFrames.size,
                        livePoseErrorMessage = throwable.message ?: throwable.javaClass.simpleName
                    )
                }
            }
            return
        }

        chunkFailureCount = 0
        lastChunkUploadedAtMs = latestFrameTimestamp
        _uiState.update {
            it.copy(
                isRealtimeUploadActive = true,
                uploadedPoseFrameCount = it.uploadedPoseFrameCount + ack.acceptedFrames,
                bufferedPoseFrameCount = bufferedPoseFrames.size
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
            disableRealtimeUpload(
                message = "실시간 Pose 프레임 처리가 불안정해 저장 영상 분석으로 전환합니다."
            )
            stopLivePoseAnalyzer()
        }
    }

    private suspend fun disableRealtimeUpload(message: String) {
        realtimeUploadDisabled = true
        bufferedPoseFrames.clear()
        abortRealtimeSessionIfNeeded()
        _uiState.update {
            it.copy(
                isRealtimeUploadActive = false,
                bufferedPoseFrameCount = 0,
                livePoseErrorMessage = message,
                statusMessage = message
            )
        }
    }

    private suspend fun abortRealtimeSessionIfNeeded() {
        val handle = sessionHandle ?: return
        sessionHandle = null
        if (sessionTransferredToUpload) {
            return
        }
        abortAiRealtimeSessionUseCase(handle)
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
        private const val TAG = "RecordViewModel"
        private const val TARGET_ANALYSIS_FPS = 10
        private const val CHUNK_FRAME_COUNT = 10
        private const val CHUNK_INTERVAL_MS = 1_000L
        private const val MAX_SUBMISSION_FAILURES = 3
        private const val MAX_CHUNK_FAILURES = 3
        private const val MIN_REQUIRED_LANDMARK_COUNT = 33
    }
}

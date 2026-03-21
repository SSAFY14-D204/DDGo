package com.ddgo.app.feature.climbing.upload

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ddgo.app.data.mapper.toPoseSequenceDto
import com.ddgo.app.data.remote.pose.PoseSequenceDto
import com.ddgo.app.domain.model.AiAnalysisFallbackReason
import com.ddgo.app.domain.model.AiAnalysisMode
import com.ddgo.app.domain.model.AiAnalysisResult
import com.ddgo.app.domain.model.AiPoseSequence
import com.ddgo.app.domain.model.AiVideoMetadata
import com.ddgo.app.domain.model.ChallengeHoldCoordinate
import com.ddgo.app.domain.model.Hold
import com.ddgo.app.domain.model.Pose
import com.ddgo.app.domain.model.SavedChallengeHolds
import com.ddgo.app.domain.model.UploadedAttemptVideo
import com.ddgo.app.domain.repository.AiRealtimeSessionContextRequest
import com.ddgo.app.domain.repository.AiRealtimeSessionHandle
import com.ddgo.app.domain.usecase.AttemptHoldReachResult
import com.ddgo.app.domain.usecase.AnalyzeAttemptWithAiUseCase
import com.ddgo.app.domain.usecase.AttachAiRealtimeContextUseCase
import com.ddgo.app.domain.usecase.EndAttemptUseCase
import com.ddgo.app.domain.usecase.FinalizeAiRealtimeSessionUseCase
import com.ddgo.app.domain.usecase.GetMyInfoUseCase
import com.ddgo.app.domain.usecase.HoldNumbered
import com.ddgo.app.domain.usecase.OverallHoldReachSummary
import com.ddgo.app.domain.usecase.PolygonHoldContactDebugResult
import com.ddgo.app.domain.usecase.SaveChallengeHoldsUseCase
import com.ddgo.app.domain.usecase.UploadAttemptVideoUseCase
import com.ddgo.app.domain.usecase.analyzePolygonHoldContacts
import com.ddgo.app.domain.usecase.summarizeHoldReachResults
import com.ddgo.app.domain.usecase.toAttemptHoldReachResult
import com.ddgo.app.domain.usecase.toHolds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import retrofit2.HttpException

private const val SUBMISSION_TAG = "UploadSubmissionDelegate"
private const val HOLD_CONTACT_ANALYSIS_TAG = "HoldContactAnalysis"
private const val HOLD_CONTACT_LOG_PREFIX = "[DDGO_HOLD_CONTACT]"
private const val DEFAULT_AI_REQUEST_FRAME_STEP = 1

internal data class UploadSubmissionRequest(
    val challengeId: Long?,
    val useLocalAnalysisOnly: Boolean,
    val isAttemptOnlyUploadMode: Boolean,
    val attemptUris: List<String>,
    val detectedHolds: List<Hold>,
    val numberedHolds: List<HoldNumbered>,
    val bestFrameBitmap: Bitmap?,
    val aiMode: AiAnalysisMode,
    val primaryRealtimeSessionId: String?,
    val holdCoordinates: List<ChallengeHoldCoordinate>
)

internal interface UploadSubmissionCallbacks {
    suspend fun awaitSubmitReadyPrePose(playbackUris: List<String>): TerminalPrePoseSnapshot
    fun currentAttemptIndex(): Int
    fun setCurrentAttemptIndex(index: Int)
    fun clearCurrentPoseLandmarks()
    fun syncDisplayedAnalysisPoints()
    fun resetDisplayedAnalysisPoints()
    fun sessionResultPlaybackUris(): List<String>
    fun setSessionResultPlaybackUris(uris: List<String>)
    fun publishedSession(): PublishedAttemptResultSession?
    fun setPublishedSession(session: PublishedAttemptResultSession?)
    fun setSavedChallengeHolds(saved: SavedChallengeHolds?)
}

/**
 * Submit/AI/result publishing logic owner.
 *
 * Result playback and published-session state stays in UploadSessionDelegate so
 * retention and cleanup continue to use a single source of truth.
 */
internal class UploadSubmissionDelegate(
    private val saveChallengeHoldsUseCase: SaveChallengeHoldsUseCase,
    private val uploadAttemptVideoUseCase: UploadAttemptVideoUseCase,
    private val endAttemptUseCase: EndAttemptUseCase,
    private val getMyInfoUseCase: GetMyInfoUseCase,
    private val analyzeAttemptWithAiUseCase: AnalyzeAttemptWithAiUseCase,
    private val attachAiRealtimeContextUseCase: AttachAiRealtimeContextUseCase,
    private val finalizeAiRealtimeSessionUseCase: FinalizeAiRealtimeSessionUseCase
) {

    private val _uploadSubmissionUiState =
        MutableStateFlow<UploadSubmissionUiState>(UploadSubmissionUiState.Idle)
    val uploadSubmissionUiState: StateFlow<UploadSubmissionUiState> = _uploadSubmissionUiState.asStateFlow()

    var uploadedAttemptVideos by mutableStateOf<List<UploadedAttemptVideo>>(emptyList())
    var attemptHoldReachResults by mutableStateOf<List<AttemptHoldReachResult>>(emptyList())
    var attemptPoseDtos by mutableStateOf<List<PoseSequenceDto>>(emptyList())
    var attemptAnalyzedPoses by mutableStateOf<List<List<Pose>>>(emptyList())
    var attemptPolygonHoldContactDebugResults by mutableStateOf<List<PolygonHoldContactDebugResult>>(emptyList())
    var overallHoldReachSummary by mutableStateOf<OverallHoldReachSummary?>(null)
    var attemptAiAnalysisResults by mutableStateOf<List<AiAnalysisResult?>>(emptyList())

    suspend fun submitUpload(
        request: UploadSubmissionRequest,
        callbacks: UploadSubmissionCallbacks
    ) {
        if (_uploadSubmissionUiState.value is UploadSubmissionUiState.Loading) {
            return
        }

        val currentChallengeId = request.challengeId
        if (!request.useLocalAnalysisOnly && (currentChallengeId == null || currentChallengeId <= 0L)) {
            _uploadSubmissionUiState.value = UploadSubmissionUiState.Error("생성된 challenge가 없습니다.")
            return
        }

        val currentBitmap = request.bestFrameBitmap
        val numberedHoldsForAnalysis = request.numberedHolds.takeIf { it.isNotEmpty() }

        if (!request.isAttemptOnlyUploadMode) {
            if (currentBitmap == null) {
                _uploadSubmissionUiState.value = UploadSubmissionUiState.Error("홀드 기준 이미지가 없습니다.")
                return
            }

            if (request.detectedHolds.isEmpty()) {
                _uploadSubmissionUiState.value = UploadSubmissionUiState.Error("저장할 홀드가 없습니다.")
                return
            }

            if (numberedHoldsForAnalysis == null) {
                _uploadSubmissionUiState.value =
                    UploadSubmissionUiState.Error("시작 홀드와 끝 홀드를 먼저 선택해주세요.")
                return
            }
        }

        if (request.attemptUris.isEmpty()) {
            _uploadSubmissionUiState.value = UploadSubmissionUiState.Error("업로드할 영상이 없습니다.")
            return
        }

        val shouldRunAiAnalysis =
            !request.isAttemptOnlyUploadMode &&
                numberedHoldsForAnalysis != null &&
                currentBitmap != null

        val aiProfile = if (shouldRunAiAnalysis) {
            resolveAiProfile(request.aiMode)
                .onFailure { throwable ->
                    _uploadSubmissionUiState.value = UploadSubmissionUiState.Error(
                        throwable.message ?: "AI 분석에 사용할 프로필을 확인할 수 없습니다."
                    )
                }
                .getOrNull()
        } else {
            null
        }

        if (shouldRunAiAnalysis && aiProfile == null) {
            return
        }

        if (!request.isAttemptOnlyUploadMode && !request.useLocalAnalysisOnly) {
            if (currentBitmap == null) {
                _uploadSubmissionUiState.value = UploadSubmissionUiState.Error("홀드 기준 이미지가 없습니다.")
                return
            }

            if (request.detectedHolds.isEmpty()) {
                _uploadSubmissionUiState.value = UploadSubmissionUiState.Error("저장할 홀드가 없습니다.")
                return
            }

            _uploadSubmissionUiState.value =
                UploadSubmissionUiState.Loading("홀드 정보를 저장하고 있습니다.")

            saveChallengeHoldsUseCase(
                challengeId = currentChallengeId!!,
                holds = request.holdCoordinates
            ).onSuccess { saved ->
                callbacks.setSavedChallengeHolds(saved)
                Log.d(
                    SUBMISSION_TAG,
                    "submitUpload: holds saved, challengeId=${saved.challengeId}, holdCount=${saved.holdCount}"
                )
            }.onFailure { throwable ->
                Log.e(SUBMISSION_TAG, "submitUpload: save holds failed", throwable)
                _uploadSubmissionUiState.value = UploadSubmissionUiState.Error(
                    throwable.message ?: "Failed to save challenge holds."
                )
                return
            }
        }

        val uploadedVideos = mutableListOf<UploadedAttemptVideo>()
        if (request.useLocalAnalysisOnly) {
            Log.d(SUBMISSION_TAG, "submitUpload: dev local analysis mode, skipping challenge save and video upload")
        } else {
            request.attemptUris.forEachIndexed { index, uri ->
                _uploadSubmissionUiState.value = UploadSubmissionUiState.Loading(
                    "영상 업로드 중입니다. (${index + 1}/${request.attemptUris.size})"
                )

                uploadAttemptVideoUseCase(
                    challengeId = currentChallengeId!!,
                    videoUri = uri
                ).onSuccess { uploaded ->
                    endAttemptUseCase(
                        challengeId = currentChallengeId,
                        attemptId = uploaded.attemptId,
                        attemptResult = null
                    ).onFailure { throwable ->
                        Log.e(SUBMISSION_TAG, "submitUpload: end attempt failed", throwable)
                        _uploadSubmissionUiState.value = UploadSubmissionUiState.Error(
                            throwable.message ?: "Failed to end attempt."
                        )
                        return
                    }

                    uploadedVideos += uploaded
                    Log.d(
                        SUBMISSION_TAG,
                        "submitUpload: attempt upload success, attemptId=${uploaded.attemptId}, " +
                            "attemptNo=${uploaded.attemptNo}, objectKey=${uploaded.objectKey}"
                    )
                }.onFailure { throwable ->
                    Log.e(SUBMISSION_TAG, "submitUpload: attempt upload failed", throwable)
                    _uploadSubmissionUiState.value = UploadSubmissionUiState.Error(
                        throwable.message ?: "Failed to upload attempt video."
                    )
                    return
                }
            }
        }

        val terminalSnapshot = callbacks.awaitSubmitReadyPrePose(request.attemptUris)
        val failedPrePoseUris = request.attemptUris.filter { playbackUri ->
            val entry = terminalSnapshot.entriesByPlaybackUri[playbackUri]
            entry?.status != PrePoseStatus.Ready || entry?.aiPoseSequence == null
        }
        if (failedPrePoseUris.isNotEmpty()) {
            _uploadSubmissionUiState.value = UploadSubmissionUiState.Error(
                buildPrePoseFailureMessage(
                    playbackUris = failedPrePoseUris,
                    terminalSnapshot = terminalSnapshot
                )
            )
            return
        }

        if (numberedHoldsForAnalysis != null) {
            _uploadSubmissionUiState.value =
                UploadSubmissionUiState.Loading("최고 도달 홀드를 분석하고 있습니다.")

            analyzeAllAttemptHoldReach(
                attemptUris = request.attemptUris,
                holds = numberedHoldsForAnalysis,
                terminalSnapshot = terminalSnapshot
            )
        } else {
            clearHoldReachAnalysis(callbacks)
        }

        if (shouldRunAiAnalysis) {
            val bitmapForAi = currentBitmap ?: run {
                _uploadSubmissionUiState.value = UploadSubmissionUiState.Error("AI 분석용 홀드 기준 이미지가 없습니다.")
                return
            }
            val holdsForAi = numberedHoldsForAnalysis ?: run {
                _uploadSubmissionUiState.value = UploadSubmissionUiState.Error("AI 분석용 홀드 번호가 없습니다.")
                return
            }
            val profileForAi = aiProfile ?: run {
                _uploadSubmissionUiState.value = UploadSubmissionUiState.Error("AI 분석용 사용자 프로필을 확인할 수 없습니다.")
                return
            }

            analyzeAllAttemptsWithAi(
                attemptUris = request.attemptUris,
                holds = holdsForAi,
                frameBitmap = bitmapForAi,
                mode = request.aiMode,
                primaryRealtimeSessionId = request.primaryRealtimeSessionId,
                profile = profileForAi,
                terminalSnapshot = terminalSnapshot,
                callbacks = callbacks
            ).onFailure { throwable ->
                val serverDetail = throwable.extractHttpErrorDetail()
                Log.e(
                    SUBMISSION_TAG,
                    "submitUpload: AI analysis failed" + serverDetail?.let { " detail=$it" }.orEmpty(),
                    throwable
                )
                _uploadSubmissionUiState.value = UploadSubmissionUiState.Error(
                    serverDetail ?: throwable.message ?: "AI 분석이 실패했습니다."
                )
                return
            }
        } else {
            clearAiAnalysisState(callbacks)
        }

        publishAttemptResultSession(
            callbacks = callbacks,
            playbackUris = request.attemptUris,
            uploadedVideos = uploadedVideos,
            currentAttemptIndex = 0,
            holdReachResults = attemptHoldReachResults,
            poseDtos = attemptPoseDtos,
            analyzedPoses = attemptAnalyzedPoses,
            polygonHoldContactDebugResults = attemptPolygonHoldContactDebugResults,
            overallSummary = overallHoldReachSummary
        )
        _uploadSubmissionUiState.value = UploadSubmissionUiState.Success(uploadedVideos)
    }

    fun resetUploadSubmissionState() {
        _uploadSubmissionUiState.value = UploadSubmissionUiState.Idle
    }

    fun setUploadSubmissionLoading(message: String) {
        _uploadSubmissionUiState.value = UploadSubmissionUiState.Loading(message)
    }

    fun clearAiAnalysisState(callbacks: UploadSubmissionCallbacks) {
        attemptAiAnalysisResults = emptyList()
        callbacks.resetDisplayedAnalysisPoints()
    }

    fun clearHoldReachAnalysis(callbacks: UploadSubmissionCallbacks) {
        attemptHoldReachResults = emptyList()
        attemptPoseDtos = emptyList()
        attemptAnalyzedPoses = emptyList()
        attemptPolygonHoldContactDebugResults = emptyList()
        overallHoldReachSummary = null
        callbacks.clearCurrentPoseLandmarks()
    }

    fun clearAttemptResultState(
        callbacks: UploadSubmissionCallbacks,
        clearPublishedSession: Boolean
    ) {
        callbacks.setCurrentAttemptIndex(0)
        callbacks.setSessionResultPlaybackUris(emptyList())
        uploadedAttemptVideos = emptyList()
        clearHoldReachAnalysis(callbacks)
        clearAiAnalysisState(callbacks)
        if (clearPublishedSession) {
            callbacks.setPublishedSession(null)
        }
    }

    fun publishAttemptResultSession(
        callbacks: UploadSubmissionCallbacks,
        playbackUris: List<String>,
        uploadedVideos: List<UploadedAttemptVideo>,
        currentAttemptIndex: Int,
        holdReachResults: List<AttemptHoldReachResult>,
        poseDtos: List<PoseSequenceDto>,
        analyzedPoses: List<List<Pose>>,
        polygonHoldContactDebugResults: List<PolygonHoldContactDebugResult>,
        overallSummary: OverallHoldReachSummary?
    ) {
        callbacks.setSessionResultPlaybackUris(playbackUris)
        uploadedAttemptVideos = uploadedVideos
        callbacks.setCurrentAttemptIndex(
            currentAttemptIndex.coerceIn(
                minimumValue = 0,
                maximumValue = playbackUris.lastIndex.coerceAtLeast(0)
            )
        )
        attemptHoldReachResults = holdReachResults
        attemptPoseDtos = poseDtos
        attemptAnalyzedPoses = analyzedPoses
        attemptPolygonHoldContactDebugResults = polygonHoldContactDebugResults
        overallHoldReachSummary = overallSummary
        callbacks.syncDisplayedAnalysisPoints()
        callbacks.setPublishedSession(
            PublishedAttemptResultSession(
                resultPlaybackUris = playbackUris,
                uploadedAttemptVideos = uploadedVideos,
                currentAttemptIndex = callbacks.currentAttemptIndex(),
                holdReachResults = holdReachResults,
                attemptPoseDtos = poseDtos,
                attemptAnalyzedPoses = analyzedPoses,
                attemptPolygonHoldContactDebugResults = polygonHoldContactDebugResults,
                overallHoldReachSummary = overallSummary
            )
        )
    }

    fun captureCurrentAttemptResultSession(callbacks: UploadSubmissionCallbacks) {
        val playbackUris = callbacks.sessionResultPlaybackUris().takeIf { it.isNotEmpty() } ?: return
        callbacks.setPublishedSession(
            PublishedAttemptResultSession(
                resultPlaybackUris = playbackUris,
                uploadedAttemptVideos = uploadedAttemptVideos,
                currentAttemptIndex = callbacks.currentAttemptIndex().coerceIn(
                    minimumValue = 0,
                    maximumValue = playbackUris.lastIndex.coerceAtLeast(0)
                ),
                holdReachResults = attemptHoldReachResults,
                attemptPoseDtos = attemptPoseDtos,
                attemptAnalyzedPoses = attemptAnalyzedPoses,
                attemptPolygonHoldContactDebugResults = attemptPolygonHoldContactDebugResults,
                overallHoldReachSummary = overallHoldReachSummary
            )
        )
    }

    fun restorePublishedAttemptResultSession(callbacks: UploadSubmissionCallbacks) {
        val session = callbacks.publishedSession() ?: run {
            clearAttemptResultState(callbacks, clearPublishedSession = false)
            return
        }

        callbacks.setSessionResultPlaybackUris(session.resultPlaybackUris)
        uploadedAttemptVideos = session.uploadedAttemptVideos
        callbacks.setCurrentAttemptIndex(
            session.currentAttemptIndex.coerceIn(
                minimumValue = 0,
                maximumValue = session.resultPlaybackUris.lastIndex.coerceAtLeast(0)
            )
        )
        attemptHoldReachResults = session.holdReachResults
        attemptPoseDtos = session.attemptPoseDtos
        attemptAnalyzedPoses = session.attemptAnalyzedPoses
        attemptPolygonHoldContactDebugResults = session.attemptPolygonHoldContactDebugResults
        overallHoldReachSummary = session.overallHoldReachSummary
        callbacks.clearCurrentPoseLandmarks()
        callbacks.syncDisplayedAnalysisPoints()
    }

    fun resolveAttemptSuccess(
        index: Int,
        fallback: Boolean,
        totalHoldCount: Int
    ): Boolean {
        val highestReachedHoldNo = attemptHoldReachResults
            .getOrNull(index)
            ?.highestReachedHoldNo

        return if (totalHoldCount > 0 && highestReachedHoldNo != null) {
            highestReachedHoldNo >= totalHoldCount
        } else {
            fallback
        }
    }

    private suspend fun analyzeAllAttemptHoldReach(
        attemptUris: List<String>,
        holds: List<HoldNumbered>,
        terminalSnapshot: TerminalPrePoseSnapshot
    ) {
        if (attemptUris.isEmpty() || holds.isEmpty()) {
            attemptHoldReachResults = emptyList()
            attemptPoseDtos = emptyList()
            attemptAnalyzedPoses = emptyList()
            attemptPolygonHoldContactDebugResults = emptyList()
            overallHoldReachSummary = null
            return
        }

        val analyses = attemptUris.mapIndexed { index, uri ->
            _uploadSubmissionUiState.value = UploadSubmissionUiState.Loading(
                "최고 도달 홀드를 분석하고 있습니다. (${index + 1}/${attemptUris.size})"
            )

            analyzeSingleAttemptPoseAnalysis(
                playbackUri = uri,
                poses = terminalSnapshot.entriesByPlaybackUri[uri]?.poses.orEmpty(),
                holds = holds
            )
        }

        attemptHoldReachResults = analyses.map(AttemptPoseAnalysis::holdReachResult)
        attemptPoseDtos = analyses.map(AttemptPoseAnalysis::poseSequenceDto)
        attemptAnalyzedPoses = analyses.map(AttemptPoseAnalysis::poses)
        attemptPolygonHoldContactDebugResults =
            analyses.map(AttemptPoseAnalysis::polygonHoldContactDebugResult)
        overallHoldReachSummary = summarizeHoldReachResults(
            results = attemptHoldReachResults,
            totalHoldCount = holds.size
        )
    }

    private fun analyzeSingleAttemptPoseAnalysis(
        playbackUri: String,
        poses: List<Pose>,
        holds: List<HoldNumbered>
    ): AttemptPoseAnalysis {
        val stablePoses = poses
        val poseSequenceDto = stablePoses.toPoseSequenceDto()

        Log.i(
            HOLD_CONTACT_ANALYSIS_TAG,
            "$HOLD_CONTACT_LOG_PREFIX HoldContactAnalysis 시작: " +
                "uri=$playbackUri, " +
                "poseCount=${stablePoses.size}, holdCount=${holds.size}, " +
                "dtoFrameCount=${poseSequenceDto.poses.size}"
        )

        val polygonHoldContactDebugResult = analyzePolygonHoldContacts(
            poses = stablePoses,
            holds = holds
        )

        val holdReachResult = polygonHoldContactDebugResult
            .toAttemptHoldReachResult(holds = holds)
            .also { result ->
                Log.d(
                    SUBMISSION_TAG,
                    "최고 도달 홀드 분석 완료(Polygon Main): uri=$playbackUri, " +
                        "highestHoldNo=${result.highestReachedHoldNo}, " +
                        "contacted=${result.contactedHoldNos}"
                )
            }

        return AttemptPoseAnalysis(
            holdReachResult = holdReachResult,
            poseSequenceDto = poseSequenceDto,
            poses = stablePoses,
            polygonHoldContactDebugResult = polygonHoldContactDebugResult
        )
    }

    private suspend fun analyzeAllAttemptsWithAi(
        attemptUris: List<String>,
        holds: List<HoldNumbered>,
        frameBitmap: Bitmap,
        mode: AiAnalysisMode,
        primaryRealtimeSessionId: String?,
        profile: ResolvedAiProfile,
        terminalSnapshot: TerminalPrePoseSnapshot,
        callbacks: UploadSubmissionCallbacks
    ): Result<List<AiAnalysisResult>> {
        if (attemptUris.isEmpty()) {
            clearAiAnalysisState(callbacks)
            return Result.success(emptyList())
        }

        val results = mutableListOf<AiAnalysisResult>()
        val analysisHolds = holds.toHolds()

        attemptUris.forEachIndexed { index, uri ->
            _uploadSubmissionUiState.value = UploadSubmissionUiState.Loading(
                "AI ${mode.pathSegment} 분석 중입니다. (${index + 1}/${attemptUris.size})"
            )

            val result = if (index == 0 && primaryRealtimeSessionId != null) {
                finalizeRealtimeAttemptOrFallback(
                    sessionId = primaryRealtimeSessionId,
                    requestedMode = mode,
                    videoUri = uri,
                    holds = analysisHolds,
                    frameBitmap = frameBitmap,
                    profile = profile,
                    cachedPoseSequence = terminalSnapshot.entriesByPlaybackUri[uri]?.aiPoseSequence
                )
            } else {
                val cachedPoseSequence = terminalSnapshot.entriesByPlaybackUri[uri]?.aiPoseSequence
                    ?: return Result.failure(
                        IllegalStateException("Missing cached pre-pose AI sequence for $uri")
                    )
                analyzeAttemptWithBatchAi(
                    mode = mode,
                    videoUri = uri,
                    holds = analysisHolds,
                    frameBitmap = frameBitmap,
                    profile = profile,
                    cachedPoseSequence = cachedPoseSequence
                )
            }

            if (result.isFailure) {
                return Result.failure(
                    result.exceptionOrNull()
                        ?: IllegalStateException("AI 분석 결과를 가져오지 못했습니다.")
                )
            }

            results += result.getOrThrow()
            attemptAiAnalysisResults = results + List(attemptUris.size - results.size) { null }
            callbacks.syncDisplayedAnalysisPoints()
        }

        attemptAiAnalysisResults = results
        callbacks.syncDisplayedAnalysisPoints()
        return Result.success(results)
    }

    private suspend fun finalizeRealtimeAttemptOrFallback(
        sessionId: String,
        requestedMode: AiAnalysisMode,
        videoUri: String,
        holds: List<Hold>,
        frameBitmap: Bitmap,
        profile: ResolvedAiProfile,
        cachedPoseSequence: AiPoseSequence?
    ): Result<AiAnalysisResult> {
        val sessionHandle = buildRealtimeSessionHandle(
            sessionId = sessionId,
            requestedMode = requestedMode,
            profile = profile
        )

        val attachResult = attachAiRealtimeContextUseCase(
            session = sessionHandle,
            request = AiRealtimeSessionContextRequest(
                holds = holds,
                videoMetadata = buildRealtimeVideoMetadata(frameBitmap)
            )
        )

        if (attachResult.isSuccess) {
            val finalizeResult = finalizeAiRealtimeSessionUseCase(sessionHandle)
                .map { result ->
                    val fallbackReason = when {
                        requestedMode == AiAnalysisMode.PHYSICS &&
                            sessionHandle.effectiveMode != AiAnalysisMode.PHYSICS -> {
                            AiAnalysisFallbackReason.MISSING_WEIGHT
                        }

                        else -> result.fallbackReason
                    }

                    result.copy(
                        requestedMode = requestedMode,
                        fallbackReason = fallbackReason
                    )
                }

            if (finalizeResult.isSuccess) {
                return finalizeResult
            }

            Log.w(
                SUBMISSION_TAG,
                "Realtime finalize failed. Falling back to local batch analysis.",
                finalizeResult.exceptionOrNull()
            )
        } else {
            Log.w(
                SUBMISSION_TAG,
                "Realtime context attach failed. Falling back to local batch analysis.",
                attachResult.exceptionOrNull()
            )
        }

        return analyzeAttemptWithBatchAi(
            mode = requestedMode,
            videoUri = videoUri,
            holds = holds,
            frameBitmap = frameBitmap,
            profile = profile,
            cachedPoseSequence = cachedPoseSequence
                ?: return Result.failure(
                    IllegalStateException("Missing cached pre-pose AI sequence for $videoUri")
                )
        )
    }

    private suspend fun analyzeAttemptWithBatchAi(
        mode: AiAnalysisMode,
        videoUri: String,
        holds: List<Hold>,
        frameBitmap: Bitmap,
        profile: ResolvedAiProfile,
        cachedPoseSequence: AiPoseSequence
    ): Result<AiAnalysisResult> {
        return analyzeAttemptWithAiUseCase(
            mode = mode,
            videoUri = videoUri,
            holds = holds,
            frameWidthPx = frameBitmap.width,
            frameHeightPx = frameBitmap.height,
            heightCm = profile.heightCm,
            weightKg = profile.weightKg,
            wingspanCm = profile.wingspanCm,
            analysisFpsLimit = UPLOAD_PREPOSE_ANALYSIS_FPS,
            cachedPoseSequence = cachedPoseSequence,
            frameStep = DEFAULT_AI_REQUEST_FRAME_STEP
        )
    }

    private fun buildRealtimeSessionHandle(
        sessionId: String,
        requestedMode: AiAnalysisMode,
        profile: ResolvedAiProfile
    ): AiRealtimeSessionHandle {
        val effectiveMode = if (
            requestedMode == AiAnalysisMode.PHYSICS &&
            (profile.weightKg == null || profile.weightKg <= 0f)
        ) {
            AiAnalysisMode.FAST
        } else {
            requestedMode
        }

        return AiRealtimeSessionHandle(
            sessionId = sessionId,
            requestedMode = requestedMode,
            effectiveMode = effectiveMode
        )
    }

    private fun buildRealtimeVideoMetadata(frameBitmap: Bitmap): AiVideoMetadata {
        return AiVideoMetadata(
            frameWidth = frameBitmap.width,
            frameHeight = frameBitmap.height,
            frameStep = DEFAULT_AI_REQUEST_FRAME_STEP
        )
    }

    private suspend fun resolveAiProfile(
        mode: AiAnalysisMode
    ): Result<ResolvedAiProfile> {
        val user = getMyInfoUseCase()
            .getOrElse { throwable ->
                return Result.failure(throwable)
            }

        val heightCm = user.heightCm?.takeIf { it > 0f }
        val wingspanCm = user.wingspanCm?.takeIf { it > 0f }
        val weightKg = user.weightKg?.takeIf { it > 0f }
        val resolvedHeightCm = heightCm ?: wingspanCm

        if (resolvedHeightCm == null) {
            return Result.failure(
                IllegalStateException("AI 분석을 위해 프로필에 키 또는 윙스팬이 필요합니다.")
            )
        }

        if (false && mode == AiAnalysisMode.PHYSICS && weightKg == null) {
            return Result.failure(
                IllegalStateException("Physics 분석을 위해 프로필에 몸무게가 필요합니다.")
            )
        }

        return Result.success(
            ResolvedAiProfile(
                heightCm = resolvedHeightCm,
                weightKg = weightKg,
                wingspanCm = wingspanCm
            )
        )
    }

    private fun Throwable.extractHttpErrorDetail(): String? {
        val httpException = this as? HttpException ?: return null
        return runCatching {
            httpException.response()
                ?.errorBody()
                ?.string()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    private fun buildPrePoseFailureMessage(
        playbackUris: List<String>,
        terminalSnapshot: TerminalPrePoseSnapshot
    ): String {
        return playbackUris
            .mapNotNull { playbackUri ->
                terminalSnapshot.entriesByPlaybackUri[playbackUri]?.errorMessage
            }
            .firstOrNull()
            ?: "pre-pose 분석을 완료하지 못해 업로드를 진행할 수 없습니다."
    }
}

private data class AttemptPoseAnalysis(
    val holdReachResult: AttemptHoldReachResult,
    val poseSequenceDto: PoseSequenceDto,
    val poses: List<Pose>,
    val polygonHoldContactDebugResult: PolygonHoldContactDebugResult
)

private data class ResolvedAiProfile(
    val heightCm: Float,
    val weightKg: Float?,
    val wingspanCm: Float?
)

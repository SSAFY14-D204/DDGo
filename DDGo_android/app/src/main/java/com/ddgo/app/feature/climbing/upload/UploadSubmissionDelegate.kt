package com.ddgo.app.feature.climbing.upload

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ddgo.app.data.mapper.toPoseSequenceDto
import com.ddgo.app.data.remote.pose.PoseSequenceDto
import com.ddgo.app.domain.model.AiAnalysisMode
import com.ddgo.app.domain.model.AiAnalysisResult
import com.ddgo.app.domain.model.AiPoseSequence
import com.ddgo.app.domain.model.AttemptCompletionPayload
import com.ddgo.app.domain.model.ChallengeHoldCoordinate
import com.ddgo.app.domain.model.Hold
import com.ddgo.app.domain.model.Pose
import com.ddgo.app.domain.model.SavedChallengeHolds
import com.ddgo.app.domain.model.UploadedAttemptVideo
import com.ddgo.app.domain.usecase.AttemptHoldReachResult
import com.ddgo.app.domain.usecase.AnalyzeAttemptWithAiUseCase
import com.ddgo.app.domain.usecase.EndAttemptUseCase
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

private const val SUBMISSION_TAG = "UploadSubmissionDelegate"
private const val HOLD_CONTACT_ANALYSIS_TAG = "HoldContactAnalysis"
private const val HOLD_CONTACT_LOG_PREFIX = "[DDGO_HOLD_CONTACT]"
private const val ATTEMPT_ALIGNMENT_LOG_PREFIX = "[DDGO_ATTEMPT_HOLD_ALIGN]"
private const val DEFAULT_AI_REQUEST_FRAME_STEP = 1

internal data class UploadSubmissionRequest(
    val selectionGeneration: Long,
    val challengeId: Long?,
    val useLocalAnalysisOnly: Boolean,
    val isAttemptOnlyUploadMode: Boolean,
    val attemptUris: List<String>,
    val attemptAlignedHoldSets: Map<String, AttemptAlignedHoldSet>,
    val detectedHolds: List<Hold>,
    val numberedHolds: List<HoldNumbered>,
    val bestFrameBitmap: Bitmap?,
    val aiMode: AiAnalysisMode,
    val holdCoordinates: List<ChallengeHoldCoordinate>
)

internal interface UploadSubmissionCallbacks {
    suspend fun awaitSubmitReadyPrePose(
        playbackUris: List<String>,
        emitLoading: Boolean = true
    ): TerminalPrePoseSnapshot
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
    private val analyzeAttemptWithAiUseCase: AnalyzeAttemptWithAiUseCase
) {

    private val _uploadSubmissionUiState =
        MutableStateFlow<UploadSubmissionUiState>(UploadSubmissionUiState.Idle)
    val uploadSubmissionUiState: StateFlow<UploadSubmissionUiState> = _uploadSubmissionUiState.asStateFlow()
    private val _finalAnalysisPreparationUiState =
        MutableStateFlow<FinalAnalysisPreparationUiState>(FinalAnalysisPreparationUiState.Idle)
    val finalAnalysisPreparationUiState: StateFlow<FinalAnalysisPreparationUiState> =
        _finalAnalysisPreparationUiState.asStateFlow()
    private val _backgroundUploadState =
        MutableStateFlow(BackgroundUploadState.Idle)
    val backgroundUploadState: StateFlow<BackgroundUploadState> = _backgroundUploadState.asStateFlow()
    private val _backgroundUploadNotice =
        MutableStateFlow<BackgroundUploadNotice?>(null)
    val backgroundUploadNotice: StateFlow<BackgroundUploadNotice?> = _backgroundUploadNotice.asStateFlow()

    var uploadedAttemptVideos by mutableStateOf<List<UploadedAttemptVideo>>(emptyList())
    var attemptAlignedHoldSets by mutableStateOf<List<AttemptAlignedHoldSet>>(emptyList())
    var attemptHoldReachResults by mutableStateOf<List<AttemptHoldReachResult>>(emptyList())
    var attemptPoseDtos by mutableStateOf<List<PoseSequenceDto>>(emptyList())
    var attemptAnalyzedPoses by mutableStateOf<List<List<Pose>>>(emptyList())
    var attemptPolygonHoldContactDebugResults by mutableStateOf<List<PolygonHoldContactDebugResult>>(emptyList())
    var overallHoldReachSummary by mutableStateOf<OverallHoldReachSummary?>(null)
    var attemptAiAnalysisResults by mutableStateOf<List<AiAnalysisResult?>>(emptyList())
    private var finalizedAttemptIds by mutableStateOf<Set<Long>>(emptySet())
    private var analysisPrewarmEntry by mutableStateOf<SubmissionAnalysisPrewarmEntry?>(null)
    private var analysisPrewarmJob: Deferred<Result<SubmissionAnalysisPrewarmResult>>? = null
    private var backgroundUploadJob: Job? = null
    private var backgroundUploadRequest: BackgroundUploadRequest? = null
    private var backgroundUploadKey: BackgroundUploadKey? = null
    private var nextBackgroundUploadNoticeId = 1L
    private var attemptResultPreparationInFlight = false

    fun invalidateAnalysisPrewarm() {
        analysisPrewarmJob?.cancel()
        analysisPrewarmJob = null
        analysisPrewarmEntry = null
        _finalAnalysisPreparationUiState.value = FinalAnalysisPreparationUiState.Idle
    }

    fun requestAnalysisPrewarm(
        scope: CoroutineScope,
        request: UploadSubmissionRequest,
        callbacks: UploadSubmissionCallbacks
    ) {
        val requestKey = buildAnalysisPrewarmKey(request) ?: return
        val existingEntry = analysisPrewarmEntry

        if (existingEntry?.key == requestKey) {
            when (existingEntry.status) {
                SubmissionAnalysisPrewarmStatus.Running -> {
                    UploadAiTraceLogger.log(
                        event = "FINAL_AI_PREWARM_REUSE_RUNNING",
                        generation = request.selectionGeneration,
                        playbackUri = request.attemptUris.firstOrNull(),
                        status = "running"
                    )
                    return
                }

                SubmissionAnalysisPrewarmStatus.Ready -> {
                    UploadAiTraceLogger.log(
                        event = "FINAL_AI_PREWARM_REUSE_READY",
                        generation = request.selectionGeneration,
                        playbackUri = request.attemptUris.firstOrNull(),
                        status = "ready"
                    )
                    return
                }

                SubmissionAnalysisPrewarmStatus.Failed -> {
                    UploadAiTraceLogger.log(
                        event = "FINAL_AI_PREWARM_FAILED_RETRY",
                        generation = request.selectionGeneration,
                        playbackUri = request.attemptUris.firstOrNull(),
                        status = "retry"
                    )
                }
            }
        }

        analysisPrewarmJob?.cancel()
        analysisPrewarmEntry = SubmissionAnalysisPrewarmEntry(
            key = requestKey,
            status = SubmissionAnalysisPrewarmStatus.Running
        )
        UploadAiTraceLogger.log(
            event = "FINAL_AI_PREWARM_REQUESTED",
            generation = request.selectionGeneration,
            playbackUri = request.attemptUris.firstOrNull(),
            details = mapOf(
                "attemptCount" to request.attemptUris.size,
                "numberedHoldCount" to request.numberedHolds.size,
                "aiMode" to request.aiMode.name
            )
        )
        analysisPrewarmJob = scope.async {
            val aiProfile = resolveAiProfile(request.aiMode)
                .getOrElse { throwable ->
                    val failure = Result.failure<SubmissionAnalysisPrewarmResult>(throwable)
                    storeAnalysisPrewarmResult(requestKey, failure)
                    return@async failure
                }

            val result = executeAnalysisPipeline(
                request = request,
                aiProfile = aiProfile,
                callbacks = callbacks,
                emitLoading = false
            )
            storeAnalysisPrewarmResult(requestKey, result)
            result
        }
    }

    suspend fun submitUploadForAttemptResult(
        scope: CoroutineScope,
        request: UploadSubmissionRequest,
        callbacks: UploadSubmissionCallbacks
    ) = coroutineScope {
        if (attemptResultPreparationInFlight) {
            UploadAiTraceLogger.log(
                event = "ATTEMPT_RESULT_PREP_SKIPPED",
                generation = request.selectionGeneration,
                playbackUri = request.attemptUris.firstOrNull(),
                phase = "AttemptResultPreparation",
                status = "already_running"
            )
            return@coroutineScope
        }
        attemptResultPreparationInFlight = true
        try {
            val startedAt = UploadAiTraceLogger.now()
            UploadAiTraceLogger.log(
                event = "ATTEMPT_RESULT_PREP_BEGIN",
                generation = request.selectionGeneration,
                playbackUri = request.attemptUris.firstOrNull(),
                phase = "AttemptResultPreparation",
                details = mapOf(
                    "attemptCount" to request.attemptUris.size,
                    "numberedHoldCount" to request.numberedHolds.size
                )
            )

            val currentChallengeId = request.challengeId
            if (!request.useLocalAnalysisOnly && (currentChallengeId == null || currentChallengeId <= 0L)) {
                _uploadSubmissionUiState.value = UploadSubmissionUiState.Error("생성된 챌린지가 없습니다.")
                return@coroutineScope
            }

            val currentBitmap = request.bestFrameBitmap
            val alignedHoldSets = resolveAlignedHoldSets(request)
            attemptAlignedHoldSets = alignedHoldSets
            val numberedHoldsForAnalysis = resolveNumberedHoldsForAnalysis(
                request = request,
                alignedHoldSets = alignedHoldSets
            )
            logAlignedHoldSets(alignedHoldSets)

            if (!request.isAttemptOnlyUploadMode) {
                if (currentBitmap == null) {
                    _uploadSubmissionUiState.value = UploadSubmissionUiState.Error("대표 기준 이미지가 없습니다.")
                    return@coroutineScope
                }

                if (request.detectedHolds.isEmpty()) {
                    _uploadSubmissionUiState.value = UploadSubmissionUiState.Error("저장할 홀드가 없습니다.")
                    return@coroutineScope
                }

                if (numberedHoldsForAnalysis == null) {
                    _uploadSubmissionUiState.value =
                        UploadSubmissionUiState.Error("시작 홀드와 종료 홀드를 먼저 선택해 주세요.")
                    return@coroutineScope
                }
            }

            if (request.attemptUris.isEmpty()) {
                _uploadSubmissionUiState.value = UploadSubmissionUiState.Error("업로드할 영상이 없습니다.")
                return@coroutineScope
            }

            if (request.isAttemptOnlyUploadMode && numberedHoldsForAnalysis == null) {
                _uploadSubmissionUiState.value = UploadSubmissionUiState.Error(
                    "기존 챌린지의 기준 홀드를 찾지 못했습니다. 챌린지 생성 화면에서 다시 시도해주세요."
                )
                return@coroutineScope
            }

            if (!request.isAttemptOnlyUploadMode && !request.useLocalAnalysisOnly) {
                _uploadSubmissionUiState.value =
                    UploadSubmissionUiState.Loading("홀드 정보를 저장하고 있어요.")

                UploadAiTraceLogger.log(
                    event = "ATTEMPT_RESULT_HOLDS_SAVE_BEGIN",
                    generation = request.selectionGeneration,
                    playbackUri = request.attemptUris.firstOrNull(),
                    phase = "AttemptResultPreparation"
                )
                saveChallengeHoldsUseCase(
                    challengeId = currentChallengeId!!,
                    holds = request.holdCoordinates
                ).onSuccess { saved ->
                    callbacks.setSavedChallengeHolds(saved)
                    UploadAiTraceLogger.log(
                        event = "ATTEMPT_RESULT_HOLDS_SAVE_DONE",
                        generation = request.selectionGeneration,
                        playbackUri = request.attemptUris.firstOrNull(),
                        phase = "AttemptResultPreparation",
                        status = "success",
                        details = mapOf(
                            "challengeId" to saved.challengeId,
                            "holdCount" to saved.holdCount
                        )
                    )
                    Log.d(
                        SUBMISSION_TAG,
                        "submitUploadForAttemptResult: holds saved, challengeId=${saved.challengeId}, holdCount=${saved.holdCount}"
                    )
                }.onFailure { throwable ->
                    Log.e(SUBMISSION_TAG, "submitUploadForAttemptResult: save holds failed", throwable)
                    _uploadSubmissionUiState.value = UploadSubmissionUiState.Error(
                        throwable.message ?: "홀드 정보를 저장하지 못했습니다."
                    )
                    return@coroutineScope
                }
            }

            startBackgroundAttemptUploads(
                scope = scope,
                request = request,
                challengeId = currentChallengeId,
                callbacks = callbacks
            )

            if (
                !request.isAttemptOnlyUploadMode &&
                numberedHoldsForAnalysis != null &&
                currentBitmap != null
            ) {
                requestAnalysisPrewarm(
                    scope = scope,
                    request = request,
                    callbacks = callbacks
                )
            }

            _uploadSubmissionUiState.value = UploadSubmissionUiState.Loading("자세 데이터를 준비하고 있어요.")

            val analysisResult = runAttemptResultPreparationPipeline(
                request = request,
                alignedHoldSets = alignedHoldSets,
                callbacks = callbacks
            )
            if (analysisResult.isFailure) {
                val throwable = analysisResult.exceptionOrNull()
                _uploadSubmissionUiState.value = UploadSubmissionUiState.Error(
                    throwable?.extractHttpErrorDetail()
                        ?: throwable?.message
                        ?: "업로드 분석을 완료하지 못했습니다."
                )
                return@coroutineScope
            }

            finalizeUploadedAttemptsIfReady(
                challengeId = currentChallengeId,
                uploadedVideos = uploadedAttemptVideos,
                playbackUris = request.attemptUris,
                totalHoldCount = resolveTotalHoldCount(
                    request = request,
                    alignedHoldSets = alignedHoldSets
                )
            ).onFailure { throwable ->
                Log.e(SUBMISSION_TAG, "submitUploadForAttemptResult: end attempt failed", throwable)
                _uploadSubmissionUiState.value = UploadSubmissionUiState.Error(
                    throwable.message ?: "Failed to finalize uploaded attempts."
                )
                return@coroutineScope
            }
 
            publishAttemptResultSession(
                callbacks = callbacks,
                playbackUris = request.attemptUris,
                uploadedVideos = uploadedAttemptVideos,
                currentAttemptIndex = 0,
                attemptAlignedHoldSets = attemptAlignedHoldSets,
                holdReachResults = attemptHoldReachResults,
                attemptAiAnalysisResults = attemptAiAnalysisResults,
                poseDtos = attemptPoseDtos,
                analyzedPoses = attemptAnalyzedPoses,
                polygonHoldContactDebugResults = attemptPolygonHoldContactDebugResults,
                overallSummary = overallHoldReachSummary
            )
            UploadAiTraceLogger.log(
                event = "ATTEMPT_RESULT_SESSION_PUBLISH_DONE",
                generation = request.selectionGeneration,
                playbackUri = request.attemptUris.firstOrNull(),
                phase = "AttemptResultPreparation",
                status = "success",
                elapsedMs = UploadAiTraceLogger.elapsedSince(startedAt)
            )
            UploadAiTraceLogger.log(
                event = "ATTEMPT_RESULT_PREP_DONE",
                generation = request.selectionGeneration,
                playbackUri = request.attemptUris.firstOrNull(),
                phase = "AttemptResultPreparation",
                status = "success",
                elapsedMs = UploadAiTraceLogger.elapsedSince(startedAt),
                details = mapOf(
                    "uploadedVideoCount" to uploadedAttemptVideos.size,
                    "holdReachCount" to attemptHoldReachResults.size
                )
            )
            _uploadSubmissionUiState.value = UploadSubmissionUiState.Success(uploadedAttemptVideos)
        } finally {
            attemptResultPreparationInFlight = false
        }
    }

    suspend fun ensureFinalAnalysisReady(
        request: UploadSubmissionRequest,
        callbacks: UploadSubmissionCallbacks
    ) {
        if (_finalAnalysisPreparationUiState.value is FinalAnalysisPreparationUiState.Loading) {
            return
        }
        val startedAt = UploadAiTraceLogger.now()
        UploadAiTraceLogger.log(
            event = "FINAL_ANALYSIS_READY_BEGIN",
            generation = request.selectionGeneration,
            playbackUri = request.attemptUris.firstOrNull(),
            phase = "FinalAnalysisPreparation",
            details = mapOf("attemptCount" to request.attemptUris.size)
        )

        val requestKey = buildAnalysisPrewarmKey(request)
        if (requestKey == null) {
            _finalAnalysisPreparationUiState.value =
                FinalAnalysisPreparationUiState.Error("최종 분석에 필요한 AI 데이터를 준비할 수 없습니다.")
            return
        }

        _finalAnalysisPreparationUiState.value = FinalAnalysisPreparationUiState.Loading
        val existingEntry = analysisPrewarmEntry
        if (existingEntry?.key == requestKey) {
            when (existingEntry.status) {
                SubmissionAnalysisPrewarmStatus.Ready -> UploadAiTraceLogger.log(
                    event = "FINAL_ANALYSIS_READY_REUSE_READY",
                    generation = request.selectionGeneration,
                    playbackUri = request.attemptUris.firstOrNull(),
                    phase = "FinalAnalysisPreparation",
                    status = "ready"
                )

                SubmissionAnalysisPrewarmStatus.Running -> UploadAiTraceLogger.log(
                    event = "FINAL_ANALYSIS_READY_WAIT_RUNNING",
                    generation = request.selectionGeneration,
                    playbackUri = request.attemptUris.firstOrNull(),
                    phase = "FinalAnalysisPreparation",
                    status = "running"
                )

                SubmissionAnalysisPrewarmStatus.Failed -> Unit
            }
        }

        val aiProfile = resolveAiProfile(request.aiMode)
            .getOrElse { throwable ->
                _finalAnalysisPreparationUiState.value =
                    FinalAnalysisPreparationUiState.Error(
                        throwable.message ?: "AI 분석에 사용할 프로필을 확인할 수 없습니다."
                    )
                return
            }

        val result = awaitOrReuseAnalysisPrewarm(
            request = request,
            aiProfile = aiProfile,
            callbacks = callbacks,
            emitLoading = false
        )

        if (result.isFailure) {
            val throwable = result.exceptionOrNull()
            _finalAnalysisPreparationUiState.value =
                FinalAnalysisPreparationUiState.Error(
                    throwable?.extractHttpErrorDetail()
                        ?: throwable?.message
                        ?: "최종 분석 결과를 준비하지 못했습니다."
                )
            return
        }

        applyAnalysisPrewarmResult(
            result = result.getOrThrow(),
            callbacks = callbacks
        )
        finalizeUploadedAttemptsIfReady(
            challengeId = request.challengeId,
            uploadedVideos = uploadedAttemptVideos,
            playbackUris = request.attemptUris,
            totalHoldCount = request.numberedHolds.size
        ).onFailure { throwable ->
            _finalAnalysisPreparationUiState.value =
                FinalAnalysisPreparationUiState.Error(
                    throwable.message ?: "Failed to finalize uploaded attempts."
                )
            return
        }
        UploadAiTraceLogger.log(
            event = "FINAL_ANALYSIS_READY_DONE",
            generation = request.selectionGeneration,
            playbackUri = request.attemptUris.firstOrNull(),
            phase = "FinalAnalysisPreparation",
            status = "success",
            elapsedMs = UploadAiTraceLogger.elapsedSince(startedAt),
            details = mapOf("resultCount" to attemptAiAnalysisResults.size)
        )
        _finalAnalysisPreparationUiState.value = FinalAnalysisPreparationUiState.Success
    }

    suspend fun submitUpload(
        request: UploadSubmissionRequest,
        callbacks: UploadSubmissionCallbacks
    ) {
        if (shouldUseBackgroundSubmissionPipeline()) {
            submitUploadWithBackgroundUploads(
                request = request,
                callbacks = callbacks
            )
            return
        }

        if (_uploadSubmissionUiState.value is UploadSubmissionUiState.Loading) {
            return
        }

        val currentChallengeId = request.challengeId
        if (!request.useLocalAnalysisOnly && (currentChallengeId == null || currentChallengeId <= 0L)) {
            _uploadSubmissionUiState.value = UploadSubmissionUiState.Error("생성된 challenge가 없습니다.")
            return
        }

        val currentBitmap = request.bestFrameBitmap
        val alignedHoldSets = request.attemptUris.map { playbackUri ->
            request.attemptAlignedHoldSets[playbackUri] ?: AttemptAlignedHoldSet(
                playbackUri = playbackUri,
                frameWidthPx = currentBitmap?.width ?: 1000,
                frameHeightPx = currentBitmap?.height ?: 1000,
                mode = AttemptHoldAlignmentMode.ReferenceFallback,
                confidence = 0f,
                matchedHoldCount = 0,
                warpOnlyHoldCount = request.numberedHolds.size,
                alignedHolds = request.numberedHolds,
                debugSummary = "reference fallback"
            )
        }
        attemptAlignedHoldSets = alignedHoldSets
        val numberedHoldsForAnalysis = alignedHoldSets
            .firstOrNull { it.alignedHolds.isNotEmpty() }
            ?.alignedHolds
            ?.takeIf { it.isNotEmpty() }
            ?: request.numberedHolds.takeIf { it.isNotEmpty() }
        alignedHoldSets.forEach { alignedHoldSet ->
            Log.d(
                SUBMISSION_TAG,
                "$ATTEMPT_ALIGNMENT_LOG_PREFIX submit uses aligned holds: " +
                    "uri=${alignedHoldSet.playbackUri}, mode=${alignedHoldSet.mode}, " +
                    "matched=${alignedHoldSet.matchedHoldCount}, warpOnly=${alignedHoldSet.warpOnlyHoldCount}, " +
                "confidence=${"%.3f".format(alignedHoldSet.confidence)}"
            )
        }

        if (request.isAttemptOnlyUploadMode && numberedHoldsForAnalysis == null) {
            Log.e(
                SUBMISSION_TAG,
                "$ATTEMPT_ALIGNMENT_LOG_PREFIX missing reference holds for attempt-only upload"
            )
            _uploadSubmissionUiState.value = UploadSubmissionUiState.Error(
                "기존 챌린지의 기준 홀드를 찾지 못했습니다. 챌린지 생성 화면에서 다시 시도해주세요."
            )
            return
        }

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
                    "분석에 필요한 데이터를 준비하고 있습니다. (${index + 1}/${request.attemptUris.size})"
                )

                uploadAttemptVideoUseCase(
                    challengeId = currentChallengeId!!,
                    videoUri = uri
                ).onSuccess { uploaded ->
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
            finalizeUploadedAttempts(
                challengeId = currentChallengeId,
                uploadedVideos = uploadedVideos,
                playbackUris = request.attemptUris,
                terminalSnapshot = terminalSnapshot,
                totalHoldCount = numberedHoldsForAnalysis?.size ?: 0
            )
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
                alignedHoldSets = alignedHoldSets,
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
            val profileForAi = aiProfile ?: run {
                _uploadSubmissionUiState.value = UploadSubmissionUiState.Error("AI 분석용 사용자 프로필을 확인할 수 없습니다.")
                return
            }

            analyzeAllAttemptsWithAi(
                alignedHoldSets = alignedHoldSets,
                referenceFrameBitmap = bitmapForAi,
                mode = request.aiMode,
                profile = profileForAi,
                terminalSnapshot = terminalSnapshot,
                callbacks = callbacks
            ).onFailure { throwable ->
                val serverDetail = throwable.extractHttpErrorDetail()
                finalizeUploadedAttempts(
                    challengeId = currentChallengeId,
                    uploadedVideos = uploadedVideos,
                    playbackUris = request.attemptUris,
                    terminalSnapshot = terminalSnapshot,
                    totalHoldCount = resolveTotalHoldCount(
                        request = request,
                        alignedHoldSets = alignedHoldSets
                    )
                )
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

        finalizeUploadedAttempts(
            challengeId = currentChallengeId,
            uploadedVideos = uploadedVideos,
            playbackUris = request.attemptUris,
            terminalSnapshot = terminalSnapshot,
            totalHoldCount = numberedHoldsForAnalysis?.size ?: 0
        ).onFailure { throwable ->
            Log.e(SUBMISSION_TAG, "submitUpload: end attempt failed", throwable)
            _uploadSubmissionUiState.value = UploadSubmissionUiState.Error(
                throwable.message ?: "Failed to end attempt."
            )
            return
        }

        publishAttemptResultSession(
            callbacks = callbacks,
            playbackUris = request.attemptUris,
            uploadedVideos = uploadedVideos,
            currentAttemptIndex = 0,
            attemptAlignedHoldSets = attemptAlignedHoldSets,
            holdReachResults = attemptHoldReachResults,
            attemptAiAnalysisResults = attemptAiAnalysisResults,
            poseDtos = attemptPoseDtos,
            analyzedPoses = attemptAnalyzedPoses,
            polygonHoldContactDebugResults = attemptPolygonHoldContactDebugResults,
            overallSummary = overallHoldReachSummary
        )
        _uploadSubmissionUiState.value = UploadSubmissionUiState.Success(uploadedVideos)
    }

    private fun shouldUseBackgroundSubmissionPipeline(): Boolean = true

    private suspend fun submitUploadWithBackgroundUploads(
        request: UploadSubmissionRequest,
        callbacks: UploadSubmissionCallbacks
    ) = coroutineScope {
        if (_uploadSubmissionUiState.value is UploadSubmissionUiState.Loading) {
            return@coroutineScope
        }

        val currentChallengeId = request.challengeId
        if (!request.useLocalAnalysisOnly && (currentChallengeId == null || currentChallengeId <= 0L)) {
            _uploadSubmissionUiState.value = UploadSubmissionUiState.Error("생성된 챌린지가 없습니다.")
            return@coroutineScope
        }

        val currentBitmap = request.bestFrameBitmap
        val alignedHoldSets = resolveAlignedHoldSets(request)
        attemptAlignedHoldSets = alignedHoldSets
        val numberedHoldsForAnalysis = resolveNumberedHoldsForAnalysis(
            request = request,
            alignedHoldSets = alignedHoldSets
        )
        logAlignedHoldSets(alignedHoldSets)

        if (!request.isAttemptOnlyUploadMode) {
            if (currentBitmap == null) {
                _uploadSubmissionUiState.value = UploadSubmissionUiState.Error("대표 기준 이미지가 없습니다.")
                return@coroutineScope
            }

            if (request.detectedHolds.isEmpty()) {
                _uploadSubmissionUiState.value = UploadSubmissionUiState.Error("저장할 홀드가 없습니다.")
                return@coroutineScope
            }

            if (numberedHoldsForAnalysis == null) {
                _uploadSubmissionUiState.value =
                    UploadSubmissionUiState.Error("시작 홀드와 종료 홀드를 먼저 선택해 주세요.")
                return@coroutineScope
            }
        }

        if (request.attemptUris.isEmpty()) {
            _uploadSubmissionUiState.value = UploadSubmissionUiState.Error("업로드할 영상이 없습니다.")
            return@coroutineScope
        }

        if (request.isAttemptOnlyUploadMode && numberedHoldsForAnalysis == null) {
            _uploadSubmissionUiState.value = UploadSubmissionUiState.Error(
                "기존 챌린지의 기준 홀드를 찾지 못했습니다. 챌린지 생성 화면에서 다시 시도해주세요."
            )
            return@coroutineScope
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
            return@coroutineScope
        }

        if (!request.isAttemptOnlyUploadMode && !request.useLocalAnalysisOnly) {
            _uploadSubmissionUiState.value =
                UploadSubmissionUiState.Loading("홀드 정보를 저장하고 있어요.")

            saveChallengeHoldsUseCase(
                challengeId = currentChallengeId!!,
                holds = request.holdCoordinates
            ).onSuccess { saved ->
                callbacks.setSavedChallengeHolds(saved)
                Log.d(
                    SUBMISSION_TAG,
                    "submitUpload(background): holds saved, challengeId=${saved.challengeId}, holdCount=${saved.holdCount}"
                )
            }.onFailure { throwable ->
                Log.e(SUBMISSION_TAG, "submitUpload(background): save holds failed", throwable)
                _uploadSubmissionUiState.value = UploadSubmissionUiState.Error(
                    throwable.message ?: "홀드 정보를 저장하지 못했습니다."
                )
                return@coroutineScope
            }
        }

        val backgroundUploadJob = launchAttemptUploadsInBackground(
            request = request,
            challengeId = currentChallengeId
        )

        _uploadSubmissionUiState.value = UploadSubmissionUiState.Loading("자세 데이터를 준비하고 있어요.")

        val analysisResult = runAnalysisPipelineForSubmit(
            request = request,
            alignedHoldSets = alignedHoldSets,
            currentBitmap = currentBitmap,
            aiProfile = aiProfile,
            callbacks = callbacks
        )
        if (analysisResult.isFailure) {
            backgroundUploadJob?.cancel()
            val throwable = analysisResult.exceptionOrNull()
            _uploadSubmissionUiState.value = UploadSubmissionUiState.Error(
                throwable?.extractHttpErrorDetail()
                    ?: throwable?.message
                    ?: "업로드 분석을 완료하지 못했습니다."
            )
            return@coroutineScope
        }

        val uploadedVideosResult = awaitBackgroundUploadsOrThrow(backgroundUploadJob)
        if (uploadedVideosResult.isFailure) {
            val throwable = uploadedVideosResult.exceptionOrNull()
            _uploadSubmissionUiState.value = UploadSubmissionUiState.Error(
                throwable?.message ?: "영상 업로드를 완료하지 못했습니다."
            )
            return@coroutineScope
        }

        val uploadedVideos = uploadedVideosResult.getOrThrow()
        finalizeUploadedAttempts(
            challengeId = currentChallengeId,
            uploadedVideos = uploadedVideos,
            playbackUris = request.attemptUris,
            terminalSnapshot = null,
            totalHoldCount = resolveTotalHoldCount(
                request = request,
                alignedHoldSets = alignedHoldSets
            )
        ).onFailure { throwable ->
            Log.e(SUBMISSION_TAG, "submitUpload(background): end attempt failed", throwable)
            _uploadSubmissionUiState.value = UploadSubmissionUiState.Error(
                throwable.message ?: "Failed to finalize uploaded attempts."
            )
            return@coroutineScope
        }

        publishAttemptResultSession(
            callbacks = callbacks,
            playbackUris = request.attemptUris,
            uploadedVideos = uploadedVideos,
            currentAttemptIndex = 0,
            attemptAlignedHoldSets = attemptAlignedHoldSets,
            holdReachResults = attemptHoldReachResults,
            attemptAiAnalysisResults = attemptAiAnalysisResults,
            poseDtos = attemptPoseDtos,
            analyzedPoses = attemptAnalyzedPoses,
            polygonHoldContactDebugResults = attemptPolygonHoldContactDebugResults,
            overallSummary = overallHoldReachSummary
        )
        _uploadSubmissionUiState.value = UploadSubmissionUiState.Success(uploadedVideos)
    }

    private fun startBackgroundAttemptUploads(
        scope: CoroutineScope,
        request: UploadSubmissionRequest,
        challengeId: Long?,
        callbacks: UploadSubmissionCallbacks
    ) {
        if (request.useLocalAnalysisOnly) {
            backgroundUploadRequest = null
            backgroundUploadKey = null
            backgroundUploadJob?.cancel()
            backgroundUploadJob = null
            _backgroundUploadState.value = BackgroundUploadState.Ready
            _backgroundUploadNotice.value = null
            return
        }

        val resolvedChallengeId = requireNotNull(challengeId)
        startBackgroundAttemptUploads(
            scope = scope,
            request = BackgroundUploadRequest(
                key = BackgroundUploadKey(
                    selectionGeneration = request.selectionGeneration,
                    challengeId = resolvedChallengeId,
                    attemptUrisSignature = request.attemptUris.joinToString("|")
                ),
                challengeId = resolvedChallengeId,
                attemptUris = request.attemptUris,
                totalHoldCount = resolveTotalHoldCount(
                    request = request,
                    alignedHoldSets = resolveAlignedHoldSets(request)
                )
            ),
            callbacks = callbacks
        )
    }

    private fun kotlinx.coroutines.CoroutineScope.launchAttemptUploadsInBackground(
        request: UploadSubmissionRequest,
        challengeId: Long?
    ): Deferred<Result<List<UploadedAttemptVideo>>>? {
        if (request.useLocalAnalysisOnly) {
            Log.d(SUBMISSION_TAG, "submitUpload(background): skipping video upload in local analysis mode")
            return null
        }

        return async {
            uploadAttemptVideosQuietly(
                challengeId = requireNotNull(challengeId),
                attemptUris = request.attemptUris
            )
        }
    }

    private fun startBackgroundAttemptUploads(
        scope: CoroutineScope,
        request: BackgroundUploadRequest,
        callbacks: UploadSubmissionCallbacks
    ) {
        val existingKey = backgroundUploadKey
        if (existingKey == request.key) {
            when (_backgroundUploadState.value) {
                BackgroundUploadState.Running,
                BackgroundUploadState.Ready -> return
                BackgroundUploadState.Idle,
                BackgroundUploadState.Failed -> Unit
            }
        }

        backgroundUploadJob?.cancel()
        backgroundUploadRequest = request
        backgroundUploadKey = request.key
        _backgroundUploadState.value = BackgroundUploadState.Running
        _backgroundUploadNotice.value = null
        val startedAt = UploadAiTraceLogger.now()
        UploadAiTraceLogger.log(
            event = "BACKGROUND_UPLOAD_BEGIN",
            generation = request.key.selectionGeneration,
            playbackUri = request.attemptUris.firstOrNull(),
            phase = "BackgroundUpload",
            details = mapOf("attemptCount" to request.attemptUris.size)
        )

        backgroundUploadJob = scope.launch {
            val result = uploadAttemptVideosQuietly(
                challengeId = request.challengeId,
                attemptUris = request.attemptUris
            )

            if (backgroundUploadKey != request.key) {
                return@launch
            }

            result.onSuccess { videos ->
                uploadedAttemptVideos = videos
                _backgroundUploadState.value = BackgroundUploadState.Ready
                _backgroundUploadNotice.value = null
                UploadAiTraceLogger.log(
                    event = "BACKGROUND_UPLOAD_DONE",
                    generation = request.key.selectionGeneration,
                    playbackUri = request.attemptUris.firstOrNull(),
                    phase = "BackgroundUpload",
                    status = "success",
                    elapsedMs = UploadAiTraceLogger.elapsedSince(startedAt),
                    details = mapOf("uploadedVideoCount" to videos.size)
                )
                updatePublishedUploadedVideos(
                    callbacks = callbacks,
                    uploadedVideos = videos
                )
                finalizeUploadedAttemptsIfReady(
                    challengeId = request.challengeId,
                    uploadedVideos = videos,
                    playbackUris = request.attemptUris,
                    totalHoldCount = request.totalHoldCount
                ).onFailure { throwable ->
                    Log.e(SUBMISSION_TAG, "background upload finalize failed", throwable)
                    _backgroundUploadState.value = BackgroundUploadState.Failed
                    _backgroundUploadNotice.value = BackgroundUploadNotice(
                        id = nextBackgroundUploadNoticeId++,
                        message = throwable.message ?: "업로드된 시도 결과를 저장하지 못했습니다.",
                        actionLabel = "다시 시도"
                    )
                }
            }.onFailure { throwable ->
                Log.e(SUBMISSION_TAG, "background upload failed", throwable)
                _backgroundUploadState.value = BackgroundUploadState.Failed
                UploadAiTraceLogger.log(
                    event = "BACKGROUND_UPLOAD_FAILED",
                    generation = request.key.selectionGeneration,
                    playbackUri = request.attemptUris.firstOrNull(),
                    phase = "BackgroundUpload",
                    status = "failed",
                    elapsedMs = UploadAiTraceLogger.elapsedSince(startedAt),
                    details = mapOf("message" to throwable.message)
                )
                _backgroundUploadNotice.value = BackgroundUploadNotice(
                    id = nextBackgroundUploadNoticeId++,
                    message = throwable.message ?: "영상 업로드에 실패했어요. 다시 시도할 수 있어요.",
                    actionLabel = "다시 시도"
                )
            }
        }
    }

    fun retryBackgroundAttemptUpload(
        scope: CoroutineScope,
        callbacks: UploadSubmissionCallbacks
    ) {
        val request = backgroundUploadRequest ?: return
        if (_backgroundUploadState.value == BackgroundUploadState.Running) {
            return
        }

        startBackgroundAttemptUploads(
            scope = scope,
            request = request,
            callbacks = callbacks
        )
    }

    fun consumeBackgroundUploadNotice(id: Long) {
        val currentNotice = _backgroundUploadNotice.value ?: return
        if (currentNotice.id == id) {
            _backgroundUploadNotice.value = null
        }
    }

    private suspend fun uploadAttemptVideosQuietly(
        challengeId: Long,
        attemptUris: List<String>
    ): Result<List<UploadedAttemptVideo>> {
        val uploadedVideos = mutableListOf<UploadedAttemptVideo>()

        attemptUris.forEach { uri ->
            val uploaded = uploadAttemptVideoUseCase(
                challengeId = challengeId,
                videoUri = uri
            ).getOrElse { throwable ->
                Log.e(SUBMISSION_TAG, "submitUpload(background): attempt upload failed", throwable)
                return Result.failure(throwable)
            }

            uploadedVideos += uploaded
            Log.d(
                SUBMISSION_TAG,
                "submitUpload(background): attempt upload success, attemptId=${uploaded.attemptId}, " +
                    "attemptNo=${uploaded.attemptNo}, objectKey=${uploaded.objectKey}"
            )
        }

        return Result.success(uploadedVideos)
    }

    private suspend fun awaitOrReuseAnalysisPrewarm(
        request: UploadSubmissionRequest,
        aiProfile: ResolvedAiProfile,
        callbacks: UploadSubmissionCallbacks,
        emitLoading: Boolean = true
    ): Result<SubmissionAnalysisPrewarmResult> {
        val requestKey = buildAnalysisPrewarmKey(request)
            ?: return executeAnalysisPipeline(
                request = request,
                aiProfile = aiProfile,
                callbacks = callbacks,
                emitLoading = emitLoading
            )

        val existingEntry = analysisPrewarmEntry
        if (existingEntry?.key == requestKey) {
            when (existingEntry.status) {
                SubmissionAnalysisPrewarmStatus.Ready -> {
                    existingEntry.result?.let { return Result.success(it) }
                }

                SubmissionAnalysisPrewarmStatus.Running -> {
                    val runningJob = analysisPrewarmJob
                    if (runningJob != null) {
                        return try {
                            runningJob.await()
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (throwable: Throwable) {
                            Result.failure(throwable)
                        }
                    }
                }

                SubmissionAnalysisPrewarmStatus.Failed -> Unit
            }
        }

        val result = executeAnalysisPipeline(
            request = request,
            aiProfile = aiProfile,
            callbacks = callbacks,
            emitLoading = emitLoading
        )
        storeAnalysisPrewarmResult(requestKey, result)
        return result
    }

    private suspend fun runAttemptResultPreparationPipeline(
        request: UploadSubmissionRequest,
        alignedHoldSets: List<AttemptAlignedHoldSet>,
        callbacks: UploadSubmissionCallbacks
    ): Result<Unit> {
        val startedAt = UploadAiTraceLogger.now()
        UploadAiTraceLogger.log(
            event = "ATTEMPT_RESULT_PREPOSE_AWAIT_BEGIN",
            generation = request.selectionGeneration,
            playbackUri = request.attemptUris.firstOrNull(),
            phase = "AttemptResultPreparation"
        )
        val terminalSnapshot = callbacks.awaitSubmitReadyPrePose(request.attemptUris)
        val failedPrePoseUris = request.attemptUris.filter { playbackUri ->
            val entry = terminalSnapshot.entriesByPlaybackUri[playbackUri]
            entry?.status != PrePoseStatus.Ready || entry?.aiPoseSequence == null
        }
        if (failedPrePoseUris.isNotEmpty()) {
            return Result.failure(
                IllegalStateException(
                    buildPrePoseFailureMessage(
                        playbackUris = failedPrePoseUris,
                        terminalSnapshot = terminalSnapshot
                    )
                )
            )
        }

        if (alignedHoldSets.any { it.alignedHolds.isNotEmpty() }) {
            UploadAiTraceLogger.log(
                event = "ATTEMPT_RESULT_HOLD_REACH_BEGIN",
                generation = request.selectionGeneration,
                playbackUri = request.attemptUris.firstOrNull(),
                phase = "AttemptResultPreparation",
                details = mapOf(
                    "numberedHoldCount" to resolveTotalHoldCount(
                        request = request,
                        alignedHoldSets = alignedHoldSets
                    )
                )
            )
            analyzeAllAttemptHoldReach(
                alignedHoldSets = alignedHoldSets,
                terminalSnapshot = terminalSnapshot
            )
            UploadAiTraceLogger.log(
                event = "ATTEMPT_RESULT_HOLD_REACH_DONE",
                generation = request.selectionGeneration,
                playbackUri = request.attemptUris.firstOrNull(),
                phase = "AttemptResultPreparation",
                status = "success",
                elapsedMs = UploadAiTraceLogger.elapsedSince(startedAt),
                details = mapOf("holdReachCount" to attemptHoldReachResults.size)
            )
        } else {
            clearHoldReachAnalysis(callbacks)
        }

        return Result.success(Unit)
    }

    private suspend fun runAnalysisPipelineForSubmit(
        request: UploadSubmissionRequest,
        alignedHoldSets: List<AttemptAlignedHoldSet>,
        currentBitmap: Bitmap?,
        aiProfile: ResolvedAiProfile?,
        callbacks: UploadSubmissionCallbacks
    ): Result<Unit> {
        val numberedHoldsForAnalysis = resolveNumberedHoldsForAnalysis(
            request = request,
            alignedHoldSets = alignedHoldSets
        )
        val shouldUsePrewarm =
            !request.isAttemptOnlyUploadMode &&
                numberedHoldsForAnalysis != null &&
                currentBitmap != null &&
                aiProfile != null

        if (shouldUsePrewarm) {
            val analysisResult = awaitOrReuseAnalysisPrewarm(
                request = request,
                aiProfile = aiProfile,
                callbacks = callbacks
            )
            if (analysisResult.isFailure) {
                return Result.failure(
                    analysisResult.exceptionOrNull()
                        ?: IllegalStateException("분석 prewarm 결과를 불러오지 못했습니다.")
                )
            }

            applyAnalysisPrewarmResult(
                result = analysisResult.getOrThrow(),
                callbacks = callbacks
            )
            return Result.success(Unit)
        }

        val terminalSnapshot = callbacks.awaitSubmitReadyPrePose(request.attemptUris)
        val failedPrePoseUris = request.attemptUris.filter { playbackUri ->
            val entry = terminalSnapshot.entriesByPlaybackUri[playbackUri]
            entry?.status != PrePoseStatus.Ready || entry?.aiPoseSequence == null
        }
        if (failedPrePoseUris.isNotEmpty()) {
            return Result.failure(
                IllegalStateException(
                    buildPrePoseFailureMessage(
                        playbackUris = failedPrePoseUris,
                        terminalSnapshot = terminalSnapshot
                    )
                )
            )
        }

        if (alignedHoldSets.any { it.alignedHolds.isNotEmpty() }) {
            analyzeAllAttemptHoldReach(
                alignedHoldSets = alignedHoldSets,
                terminalSnapshot = terminalSnapshot
            )
        } else {
            clearHoldReachAnalysis(callbacks)
        }

        val shouldRunAiAnalysis =
            !request.isAttemptOnlyUploadMode &&
                numberedHoldsForAnalysis != null &&
                currentBitmap != null

        if (!shouldRunAiAnalysis) {
            clearAiAnalysisState(callbacks)
            return Result.success(Unit)
        }

        val bitmapForAi = currentBitmap
            ?: return Result.failure(IllegalStateException("AI 분석용 대표 이미지가 없습니다."))
        val profileForAi = aiProfile
            ?: return Result.failure(IllegalStateException("AI 분석용 프로필이 없습니다."))

        return analyzeAllAttemptsWithAi(
            alignedHoldSets = alignedHoldSets,
            referenceFrameBitmap = bitmapForAi,
            mode = request.aiMode,
            profile = profileForAi,
            terminalSnapshot = terminalSnapshot,
            callbacks = callbacks
        ).map { Unit }
    }

    private suspend fun executeAnalysisPipeline(
        request: UploadSubmissionRequest,
        aiProfile: ResolvedAiProfile,
        callbacks: UploadSubmissionCallbacks,
        emitLoading: Boolean
    ): Result<SubmissionAnalysisPrewarmResult> = coroutineScope {
        val startedAt = UploadAiTraceLogger.now()
        UploadAiTraceLogger.log(
            event = "FINAL_AI_PIPELINE_BEGIN",
            generation = request.selectionGeneration,
            playbackUri = request.attemptUris.firstOrNull(),
            phase = "FinalAnalysisPreparation",
            details = mapOf(
                "attemptCount" to request.attemptUris.size,
                "emitLoading" to emitLoading,
                "aiMode" to request.aiMode.name
            )
        )
        val alignedHoldSets = resolveAlignedHoldSets(request)
        if (alignedHoldSets.none { it.alignedHolds.isNotEmpty() }) {
            return@coroutineScope Result.failure(
                IllegalStateException("AI 분석용 홀드 번호가 없습니다.")
            )
        }
        val currentBitmap = request.bestFrameBitmap
            ?: return@coroutineScope Result.failure(
                IllegalStateException("AI 분석용 대표 이미지가 없습니다.")
            )

        UploadAiTraceLogger.log(
            event = "FINAL_AI_PREPOSE_AWAIT_BEGIN",
            generation = request.selectionGeneration,
            playbackUri = request.attemptUris.firstOrNull(),
            phase = "FinalAnalysisPreparation"
        )
        val terminalSnapshot = callbacks.awaitSubmitReadyPrePose(
            playbackUris = request.attemptUris,
            emitLoading = emitLoading
        )
        val failedPrePoseUris = request.attemptUris.filter { playbackUri ->
            val entry = terminalSnapshot.entriesByPlaybackUri[playbackUri]
            entry?.status != PrePoseStatus.Ready || entry?.aiPoseSequence == null
        }
        if (failedPrePoseUris.isNotEmpty()) {
            return@coroutineScope Result.failure(
                IllegalStateException(
                    buildPrePoseFailureMessage(
                        playbackUris = failedPrePoseUris,
                        terminalSnapshot = terminalSnapshot
                    )
                )
            )
        }

        val aiResults = analyzeAllAttemptsWithAiResult(
            alignedHoldSets = alignedHoldSets,
            referenceFrameBitmap = currentBitmap,
            mode = request.aiMode,
            profile = aiProfile,
            terminalSnapshot = terminalSnapshot,
            seedAiResults = attemptAiAnalysisResults,
            emitLoading = emitLoading
        ).getOrElse { throwable ->
            return@coroutineScope Result.failure(throwable)
        }

        UploadAiTraceLogger.log(
            event = "FINAL_AI_PIPELINE_DONE",
            generation = request.selectionGeneration,
            playbackUri = request.attemptUris.firstOrNull(),
            phase = "FinalAnalysisPreparation",
            status = "success",
            elapsedMs = UploadAiTraceLogger.elapsedSince(startedAt),
            details = mapOf("resultCount" to aiResults.size)
        )
        return@coroutineScope Result.success(
            SubmissionAnalysisPrewarmResult(
                aiAnalysisResults = aiResults
            )
        )
    }

    private fun applyAnalysisPrewarmResult(
        result: SubmissionAnalysisPrewarmResult,
        callbacks: UploadSubmissionCallbacks
    ) {
        attemptAiAnalysisResults = result.aiAnalysisResults
        callbacks.setPublishedSession(
            callbacks.publishedSession()?.copy(
                attemptAiAnalysisResults = result.aiAnalysisResults
            )
        )
        callbacks.clearCurrentPoseLandmarks()
        callbacks.syncDisplayedAnalysisPoints()
    }

    private fun storeAnalysisPrewarmResult(
        requestKey: SubmissionAnalysisPrewarmKey,
        result: Result<SubmissionAnalysisPrewarmResult>
    ) {
        val currentEntry = analysisPrewarmEntry ?: return
        if (currentEntry.key != requestKey) {
            return
        }

        analysisPrewarmEntry = if (result.isSuccess) {
            SubmissionAnalysisPrewarmEntry(
                key = requestKey,
                status = SubmissionAnalysisPrewarmStatus.Ready,
                result = result.getOrNull()
            )
        } else {
            SubmissionAnalysisPrewarmEntry(
                key = requestKey,
                status = SubmissionAnalysisPrewarmStatus.Failed,
                errorMessage = result.exceptionOrNull()?.message
            )
        }
    }

    private fun buildAnalysisPrewarmKey(
        request: UploadSubmissionRequest
    ): SubmissionAnalysisPrewarmKey? {
        if (
            request.isAttemptOnlyUploadMode ||
            request.bestFrameBitmap == null ||
            request.numberedHolds.isEmpty() ||
            request.attemptUris.isEmpty()
        ) {
            return null
        }

        val alignedHoldsFingerprint = buildAlignedHoldsFingerprint(resolveAlignedHoldSets(request))
        if (alignedHoldsFingerprint.isBlank()) {
            return null
        }

        return SubmissionAnalysisPrewarmKey(
            selectionGeneration = request.selectionGeneration,
            attemptUrisSignature = request.attemptUris.joinToString("|"),
            numberedHoldsFingerprint = alignedHoldsFingerprint,
            aiMode = request.aiMode,
            frameWidthPx = request.bestFrameBitmap.width,
            frameHeightPx = request.bestFrameBitmap.height
        )
    }

    private fun resolveAlignedHoldSets(request: UploadSubmissionRequest): List<AttemptAlignedHoldSet> {
        val currentBitmap = request.bestFrameBitmap
        return request.attemptUris.map { playbackUri ->
            request.attemptAlignedHoldSets[playbackUri] ?: AttemptAlignedHoldSet(
                playbackUri = playbackUri,
                frameWidthPx = currentBitmap?.width ?: 1000,
                frameHeightPx = currentBitmap?.height ?: 1000,
                mode = AttemptHoldAlignmentMode.ReferenceFallback,
                confidence = 0f,
                matchedHoldCount = 0,
                warpOnlyHoldCount = request.numberedHolds.size,
                alignedHolds = request.numberedHolds,
                debugSummary = "reference fallback"
            )
        }
    }

    private fun resolveNumberedHoldsForAnalysis(
        request: UploadSubmissionRequest,
        alignedHoldSets: List<AttemptAlignedHoldSet>
    ): List<HoldNumbered>? {
        return alignedHoldSets
            .firstOrNull { it.alignedHolds.isNotEmpty() }
            ?.alignedHolds
            ?.takeIf { it.isNotEmpty() }
            ?: request.numberedHolds.takeIf { it.isNotEmpty() }
    }

    private fun resolveTotalHoldCount(
        request: UploadSubmissionRequest,
        alignedHoldSets: List<AttemptAlignedHoldSet>
    ): Int {
        return resolveNumberedHoldsForAnalysis(
            request = request,
            alignedHoldSets = alignedHoldSets
        )?.size ?: 0
    }

    private fun logAlignedHoldSets(alignedHoldSets: List<AttemptAlignedHoldSet>) {
        alignedHoldSets.forEach { alignedHoldSet ->
            Log.d(
                SUBMISSION_TAG,
                "$ATTEMPT_ALIGNMENT_LOG_PREFIX submit uses aligned holds: " +
                    "uri=${alignedHoldSet.playbackUri}, mode=${alignedHoldSet.mode}, " +
                    "matched=${alignedHoldSet.matchedHoldCount}, warpOnly=${alignedHoldSet.warpOnlyHoldCount}, " +
                    "confidence=${"%.3f".format(alignedHoldSet.confidence)}"
            )
        }
    }

    private fun buildAlignedHoldsFingerprint(alignedHoldSets: List<AttemptAlignedHoldSet>): String {
        return alignedHoldSets.joinToString("||") { alignedHoldSet ->
            buildString {
                append(alignedHoldSet.playbackUri)
                append('#')
                append(alignedHoldSet.mode.name)
                append('#')
                append(
                    alignedHoldSet.alignedHolds.joinToString(";") { hold ->
                        buildHoldFingerprint(hold)
                    }
                )
            }
        }
    }

    private fun buildHoldFingerprint(hold: HoldNumbered): String {
        return buildString {
            append(hold.hold.holdNo)
            append(':')
            append(hold.role.name)
            append(':')
            append(hold.hold.boundingBox.left)
            append(':')
            append(hold.hold.boundingBox.top)
            append(':')
            append(hold.hold.boundingBox.right)
            append(':')
            append(hold.hold.boundingBox.bottom)
        }
    }

    private suspend fun awaitBackgroundUploadsOrThrow(
        backgroundUploadJob: Deferred<Result<List<UploadedAttemptVideo>>>?
    ): Result<List<UploadedAttemptVideo>> {
        if (backgroundUploadJob == null) {
            return Result.success(emptyList())
        }

        return try {
            backgroundUploadJob.await()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            Result.failure(throwable)
        }
    }

    fun resetUploadSubmissionState() {
        _uploadSubmissionUiState.value = UploadSubmissionUiState.Idle
    }

    fun resetFinalAnalysisPreparationState() {
        _finalAnalysisPreparationUiState.value = FinalAnalysisPreparationUiState.Idle
    }

    fun setUploadSubmissionLoading(message: String) {
        _uploadSubmissionUiState.value = UploadSubmissionUiState.Loading(message)
    }

    fun setUploadSubmissionError(message: String) {
        _uploadSubmissionUiState.value = UploadSubmissionUiState.Error(message)
    }

    fun setFinalAnalysisPreparationError(message: String) {
        _finalAnalysisPreparationUiState.value = FinalAnalysisPreparationUiState.Error(message)
    }

    fun clearAiAnalysisState(callbacks: UploadSubmissionCallbacks) {
        invalidateAnalysisPrewarm()
        attemptAiAnalysisResults = emptyList()
        callbacks.resetDisplayedAnalysisPoints()
    }

    fun clearHoldReachAnalysis(callbacks: UploadSubmissionCallbacks) {
        attemptAlignedHoldSets = emptyList()
        invalidateAnalysisPrewarm()
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
        invalidateAnalysisPrewarm()
        clearBackgroundUploadState(cancelJob = true)
        callbacks.setCurrentAttemptIndex(0)
        callbacks.setSessionResultPlaybackUris(emptyList())
        uploadedAttemptVideos = emptyList()
        clearHoldReachAnalysis(callbacks)
        clearAiAnalysisState(callbacks)
        if (clearPublishedSession) {
            finalizedAttemptIds = emptySet()
            callbacks.setPublishedSession(null)
        }
    }

    fun publishAttemptResultSession(
        callbacks: UploadSubmissionCallbacks,
        playbackUris: List<String>,
        uploadedVideos: List<UploadedAttemptVideo>,
        currentAttemptIndex: Int,
        attemptAlignedHoldSets: List<AttemptAlignedHoldSet>,
        holdReachResults: List<AttemptHoldReachResult>,
        attemptAiAnalysisResults: List<AiAnalysisResult?>,
        poseDtos: List<PoseSequenceDto>,
        analyzedPoses: List<List<Pose>>,
        polygonHoldContactDebugResults: List<PolygonHoldContactDebugResult>,
        overallSummary: OverallHoldReachSummary?
    ) {
        val existingSession = callbacks.publishedSession()
        val existingPlaybackUris = existingSession?.resultPlaybackUris.orEmpty()
        val shouldAppend = existingSession != null &&
            existingPlaybackUris.isNotEmpty() &&
            existingPlaybackUris != playbackUris

        val combinedPlaybackUris = if (shouldAppend) {
            existingSession!!.resultPlaybackUris + playbackUris.filterNot { it in existingSession.resultPlaybackUris }
        } else {
            playbackUris
        }

        val combinedUploadedVideos = if (shouldAppend) {
            (existingSession!!.uploadedAttemptVideos + uploadedVideos)
                .distinctBy(UploadedAttemptVideo::attemptId)
                .sortedBy(UploadedAttemptVideo::attemptNo)
        } else {
            uploadedVideos
        }

        val combinedHoldReachResults = if (shouldAppend) {
            existingSession!!.holdReachResults + holdReachResults
        } else {
            holdReachResults
        }
        val combinedAttemptAlignedHoldSets = if (shouldAppend) {
            val existingByPlaybackUri =
                existingSession!!.attemptAlignedHoldSets.associateBy(AttemptAlignedHoldSet::playbackUri)
            val incomingByPlaybackUri =
                attemptAlignedHoldSets.associateBy(AttemptAlignedHoldSet::playbackUri)
            combinedPlaybackUris.mapNotNull { playbackUri ->
                incomingByPlaybackUri[playbackUri] ?: existingByPlaybackUri[playbackUri]
            }
        } else {
            attemptAlignedHoldSets
        }
        val combinedAttemptAiAnalysisResults = if (shouldAppend) {
            existingSession!!.attemptAiAnalysisResults + attemptAiAnalysisResults
        } else {
            attemptAiAnalysisResults
        }
        val combinedPoseDtos = if (shouldAppend) {
            existingSession!!.attemptPoseDtos + poseDtos
        } else {
            poseDtos
        }
        val combinedAnalyzedPoses = if (shouldAppend) {
            existingSession!!.attemptAnalyzedPoses + analyzedPoses
        } else {
            analyzedPoses
        }
        val combinedPolygonHoldContactDebugResults = if (shouldAppend) {
            existingSession!!.attemptPolygonHoldContactDebugResults + polygonHoldContactDebugResults
        } else {
            polygonHoldContactDebugResults
        }
        val resolvedCurrentAttemptIndex = if (shouldAppend) {
            combinedPlaybackUris.lastIndex.coerceAtLeast(0)
        } else {
            currentAttemptIndex.coerceIn(
                minimumValue = 0,
                maximumValue = combinedPlaybackUris.lastIndex.coerceAtLeast(0)
            )
        }
        val resolvedOverallSummary = overallSummary ?: existingSession?.overallHoldReachSummary

        callbacks.setSessionResultPlaybackUris(combinedPlaybackUris)
        uploadedAttemptVideos = combinedUploadedVideos
        callbacks.setCurrentAttemptIndex(resolvedCurrentAttemptIndex)
        this.attemptAlignedHoldSets = combinedAttemptAlignedHoldSets
        attemptHoldReachResults = combinedHoldReachResults
        this.attemptAiAnalysisResults = combinedAttemptAiAnalysisResults
        attemptPoseDtos = combinedPoseDtos
        attemptAnalyzedPoses = combinedAnalyzedPoses
        attemptPolygonHoldContactDebugResults = combinedPolygonHoldContactDebugResults
        overallHoldReachSummary = resolvedOverallSummary
        callbacks.syncDisplayedAnalysisPoints()
        callbacks.setPublishedSession(
            PublishedAttemptResultSession(
                resultPlaybackUris = combinedPlaybackUris,
                uploadedAttemptVideos = combinedUploadedVideos,
                currentAttemptIndex = resolvedCurrentAttemptIndex,
                attemptAlignedHoldSets = combinedAttemptAlignedHoldSets,
                holdReachResults = combinedHoldReachResults,
                attemptAiAnalysisResults = combinedAttemptAiAnalysisResults,
                attemptPoseDtos = combinedPoseDtos,
                attemptAnalyzedPoses = combinedAnalyzedPoses,
                attemptPolygonHoldContactDebugResults = combinedPolygonHoldContactDebugResults,
                overallHoldReachSummary = resolvedOverallSummary
            )
        )
    }

    fun updatePublishedUploadedVideos(
        callbacks: UploadSubmissionCallbacks,
        uploadedVideos: List<UploadedAttemptVideo>
    ) {
        this.uploadedAttemptVideos = uploadedVideos
        callbacks.setPublishedSession(
            callbacks.publishedSession()?.copy(uploadedAttemptVideos = uploadedVideos)
        )
    }

    private fun clearBackgroundUploadState(cancelJob: Boolean) {
        if (cancelJob) {
            backgroundUploadJob?.cancel()
        }
        backgroundUploadJob = null
        backgroundUploadRequest = null
        backgroundUploadKey = null
        _backgroundUploadState.value = BackgroundUploadState.Idle
        _backgroundUploadNotice.value = null
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
                attemptAlignedHoldSets = attemptAlignedHoldSets,
                holdReachResults = attemptHoldReachResults,
                attemptAiAnalysisResults = attemptAiAnalysisResults,
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
        attemptAlignedHoldSets = session.attemptAlignedHoldSets
        attemptHoldReachResults = session.holdReachResults
        attemptAiAnalysisResults = session.attemptAiAnalysisResults
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

    private suspend fun analyzeAllAttemptHoldReachResult(
        attemptUris: List<String>,
        holds: List<HoldNumbered>,
        terminalSnapshot: TerminalPrePoseSnapshot
    ): HoldReachAnalysisBundle {
        if (attemptUris.isEmpty() || holds.isEmpty()) {
            return HoldReachAnalysisBundle(
                holdReachResults = emptyList(),
                poseDtos = emptyList(),
                analyzedPoses = emptyList(),
                polygonHoldContactDebugResults = emptyList(),
                overallSummary = null
            )
        }

        val analyses = attemptUris.map { uri ->
            analyzeSingleAttemptPoseAnalysis(
                playbackUri = uri,
                poses = terminalSnapshot.entriesByPlaybackUri[uri]?.poses.orEmpty(),
                holds = holds
            )
        }

        val holdReachResults = analyses.map(AttemptPoseAnalysis::holdReachResult)
        return HoldReachAnalysisBundle(
            holdReachResults = holdReachResults,
            poseDtos = analyses.map(AttemptPoseAnalysis::poseSequenceDto),
            analyzedPoses = analyses.map(AttemptPoseAnalysis::poses),
            polygonHoldContactDebugResults = analyses.map(AttemptPoseAnalysis::polygonHoldContactDebugResult),
            overallSummary = summarizeHoldReachResults(
                results = holdReachResults,
                totalHoldCount = holds.size
            )
        )
    }

    private suspend fun analyzeAllAttemptHoldReach(
        alignedHoldSets: List<AttemptAlignedHoldSet>,
        terminalSnapshot: TerminalPrePoseSnapshot
    ) {
        if (alignedHoldSets.isEmpty()) {
            attemptHoldReachResults = emptyList()
            attemptPoseDtos = emptyList()
            attemptAnalyzedPoses = emptyList()
            attemptPolygonHoldContactDebugResults = emptyList()
            overallHoldReachSummary = null
            return
        }

        val analyses = alignedHoldSets.mapIndexed { index, alignedHoldSet ->
            _uploadSubmissionUiState.value = UploadSubmissionUiState.Loading(
                "최고 도달 홀드를 분석하고 있습니다. (${index + 1}/${alignedHoldSets.size})"
            )

            analyzeSingleAttemptPoseAnalysis(
                playbackUri = alignedHoldSet.playbackUri,
                poses = terminalSnapshot.entriesByPlaybackUri[alignedHoldSet.playbackUri]?.poses.orEmpty(),
                holds = alignedHoldSet.alignedHolds
            )
        }

        attemptHoldReachResults = analyses.map(AttemptPoseAnalysis::holdReachResult)
        attemptPoseDtos = analyses.map(AttemptPoseAnalysis::poseSequenceDto)
        attemptAnalyzedPoses = analyses.map(AttemptPoseAnalysis::poses)
        attemptPolygonHoldContactDebugResults =
            analyses.map(AttemptPoseAnalysis::polygonHoldContactDebugResult)
        overallHoldReachSummary = summarizeHoldReachResults(
            results = attemptHoldReachResults,
            totalHoldCount = alignedHoldSets.firstOrNull()?.alignedHolds?.size ?: 0
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

    private suspend fun analyzeAllAttemptsWithAiResult(
        alignedHoldSets: List<AttemptAlignedHoldSet>,
        referenceFrameBitmap: Bitmap,
        mode: AiAnalysisMode,
        profile: ResolvedAiProfile,
        terminalSnapshot: TerminalPrePoseSnapshot,
        seedAiResults: List<AiAnalysisResult?> = emptyList(),
        emitLoading: Boolean
    ): Result<List<AiAnalysisResult>> {
        if (alignedHoldSets.isEmpty()) {
            return Result.success(emptyList())
        }

        val results = mutableListOf<AiAnalysisResult>()

        alignedHoldSets.forEachIndexed { index, alignedHoldSet ->
            val uri = alignedHoldSet.playbackUri
            val analysisHolds = alignedHoldSet.alignedHolds.toHolds()
            val frameWidthPx = alignedHoldSet.frameWidthPx.takeIf { it > 0 } ?: referenceFrameBitmap.width
            val frameHeightPx = alignedHoldSet.frameHeightPx.takeIf { it > 0 } ?: referenceFrameBitmap.height
            val attemptStartedAt = UploadAiTraceLogger.now()
            UploadAiTraceLogger.log(
                event = "FINAL_AI_ATTEMPT_BEGIN",
                playbackUri = uri,
                phase = "FinalAnalysisPreparation",
                status = "running",
                details = mapOf(
                    "attemptIndex" to index,
                    "attemptCount" to alignedHoldSets.size,
                    "mode" to mode.name
                )
            )
            if (emitLoading) {
                _uploadSubmissionUiState.value = UploadSubmissionUiState.Loading(
                    "AI ${mode.pathSegment} 분석 중입니다. (${index + 1}/${alignedHoldSets.size})"
                )
            }

            val seededResult = seedAiResults.getOrNull(index)
            if (seededResult != null) {
                results += seededResult
                return@forEachIndexed
            }

            val cachedPoseSequence = terminalSnapshot.entriesByPlaybackUri[uri].preferredAiPoseSequence()
                ?: return Result.failure(
                    IllegalStateException("Missing cached pre-pose AI sequence for $uri")
                )
            val result = analyzeAttemptWithBatchAi(
                mode = mode,
                videoUri = uri,
                holds = analysisHolds,
                frameWidthPx = frameWidthPx,
                frameHeightPx = frameHeightPx,
                profile = profile,
                cachedPoseSequence = cachedPoseSequence
            )

            if (result.isFailure) {
                return Result.failure(
                    result.exceptionOrNull()
                        ?: IllegalStateException("AI 분석 결과를 가져오지 못했습니다.")
                )
            }

            results += result.getOrThrow()
            UploadAiTraceLogger.log(
                event = "FINAL_AI_ATTEMPT_DONE",
                playbackUri = uri,
                phase = "FinalAnalysisPreparation",
                status = "success",
                elapsedMs = UploadAiTraceLogger.elapsedSince(attemptStartedAt),
                details = mapOf(
                    "attemptIndex" to index,
                    "attemptCount" to alignedHoldSets.size
                )
            )
        }

        return Result.success(results)
    }

    private suspend fun analyzeAllAttemptsWithAi(
        alignedHoldSets: List<AttemptAlignedHoldSet>,
        referenceFrameBitmap: Bitmap,
        mode: AiAnalysisMode,
        profile: ResolvedAiProfile,
        terminalSnapshot: TerminalPrePoseSnapshot,
        callbacks: UploadSubmissionCallbacks
    ): Result<List<AiAnalysisResult>> {
        if (alignedHoldSets.isEmpty()) {
            clearAiAnalysisState(callbacks)
            return Result.success(emptyList())
        }

        val results = mutableListOf<AiAnalysisResult>()

        alignedHoldSets.forEachIndexed { index, alignedHoldSet ->
            _uploadSubmissionUiState.value = UploadSubmissionUiState.Loading(
                "AI ${mode.pathSegment} 분석 중입니다. (${index + 1}/${alignedHoldSets.size})"
            )

            val analysisHolds = alignedHoldSet.alignedHolds.toHolds()
            val frameWidthPx = alignedHoldSet.frameWidthPx.takeIf { it > 0 } ?: referenceFrameBitmap.width
            val frameHeightPx = alignedHoldSet.frameHeightPx.takeIf { it > 0 } ?: referenceFrameBitmap.height
            val cachedPoseSequence = terminalSnapshot.entriesByPlaybackUri[alignedHoldSet.playbackUri]
                .preferredAiPoseSequence()
                ?: return Result.failure(
                    IllegalStateException(
                        "Missing cached pre-pose AI sequence for ${alignedHoldSet.playbackUri}"
                    )
                )
            val result = analyzeAttemptWithBatchAi(
                mode = mode,
                videoUri = alignedHoldSet.playbackUri,
                holds = analysisHolds,
                frameWidthPx = frameWidthPx,
                frameHeightPx = frameHeightPx,
                profile = profile,
                cachedPoseSequence = cachedPoseSequence
            )

            if (result.isFailure) {
                return Result.failure(
                    result.exceptionOrNull()
                        ?: IllegalStateException("AI 분석 결과를 가져오지 못했습니다.")
                )
            }

            results += result.getOrThrow()
            attemptAiAnalysisResults = results + List(alignedHoldSets.size - results.size) { null }
            callbacks.syncDisplayedAnalysisPoints()
        }

        attemptAiAnalysisResults = results
        callbacks.syncDisplayedAnalysisPoints()
        return Result.success(results)
    }

    private suspend fun analyzeAttemptWithBatchAi(
        mode: AiAnalysisMode,
        videoUri: String,
        holds: List<Hold>,
        frameWidthPx: Int,
        frameHeightPx: Int,
        profile: ResolvedAiProfile,
        cachedPoseSequence: AiPoseSequence
    ): Result<AiAnalysisResult> {
        return analyzeAttemptWithAiUseCase(
            mode = mode,
            videoUri = videoUri,
            holds = holds,
            frameWidthPx = frameWidthPx,
            frameHeightPx = frameHeightPx,
            heightCm = profile.heightCm,
            weightKg = profile.weightKg,
            wingspanCm = profile.wingspanCm,
            analysisFpsLimit = UPLOAD_PREPOSE_ANALYSIS_FPS,
            cachedPoseSequence = cachedPoseSequence,
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

    private suspend fun finalizeUploadedAttempts(
        challengeId: Long?,
        uploadedVideos: List<UploadedAttemptVideo>,
        playbackUris: List<String>,
        terminalSnapshot: TerminalPrePoseSnapshot?,
        totalHoldCount: Int
    ): Result<Unit> {
        if (challengeId == null || challengeId <= 0L || uploadedVideos.isEmpty()) {
            return Result.success(Unit)
        }

        val aiSummaries = buildFinalAnalysisAttemptSummaries(
            attemptCount = playbackUris.size,
            totalHolds = totalHoldCount,
            aiResults = attemptAiAnalysisResults
        )

        uploadedVideos.forEachIndexed { index, uploadedVideo ->
            if (uploadedVideo.attemptId in finalizedAttemptIds) {
                return@forEachIndexed
            }
            val playbackUri = playbackUris.getOrNull(index) ?: uploadedVideo.videoUri
            val payload = buildAttemptCompletionPayload(
                playbackUri = playbackUri,
                holdReachResult = attemptHoldReachResults.getOrNull(index),
                aiSummary = aiSummaries.getOrNull(index)
                    ?: emptyAttemptCompletionSummary(index + 1),
                terminalSnapshot = terminalSnapshot,
                totalHoldCount = totalHoldCount
            )

            val result = endAttemptUseCase(
                challengeId = challengeId,
                attemptId = uploadedVideo.attemptId,
                payload = payload
            )

            if (result.isFailure) {
                return result
            }
            finalizedAttemptIds = finalizedAttemptIds + uploadedVideo.attemptId
        }

        return Result.success(Unit)
    }

    private suspend fun finalizeUploadedAttemptsIfReady(
        challengeId: Long?,
        uploadedVideos: List<UploadedAttemptVideo>,
        playbackUris: List<String>,
        totalHoldCount: Int
    ): Result<Unit> {
        if (uploadedVideos.isEmpty()) {
            return Result.success(Unit)
        }

        val expectedAttemptCount = playbackUris.size
        val holdReachReady = attemptHoldReachResults.size >= expectedAttemptCount
        val aiReady = attemptAiAnalysisResults.count { it != null } >= expectedAttemptCount
        if (!holdReachReady && !aiReady && totalHoldCount > 0) {
            return Result.success(Unit)
        }

        return finalizeUploadedAttempts(
            challengeId = challengeId,
            uploadedVideos = uploadedVideos,
            playbackUris = playbackUris,
            terminalSnapshot = null,
            totalHoldCount = totalHoldCount
        )
    }

    private fun buildAttemptCompletionPayload(
        playbackUri: String,
        holdReachResult: AttemptHoldReachResult?,
        aiSummary: FinalAnalysisAttemptSummary,
        terminalSnapshot: TerminalPrePoseSnapshot?,
        totalHoldCount: Int
    ): AttemptCompletionPayload {
        val attemptResult = resolveAttemptResult(
            holdReachResult = holdReachResult,
            aiSummary = aiSummary,
            totalHoldCount = totalHoldCount
        )
        val durationMs = terminalSnapshot?.resolveDurationMs(playbackUri)
        val maxHoldNo = holdReachResult?.highestReachedHoldNo ?: aiSummary.reachedHolds
        val failureReason = aiSummary.feedbackLine.takeIf { it.isNotBlank() }
            ?: defaultFailureReason(attemptResult)
        val riskAlert = aiSummary.failureNarrative.takeIf { it.isNotBlank() }
            ?: defaultRiskAlert(attemptResult)
        val nextMission = aiSummary.coachingLine.takeIf { it.isNotBlank() }
            ?: defaultNextMission(attemptResult)

        return AttemptCompletionPayload(
            attemptResult = attemptResult,
            durationMs = durationMs,
            maxHoldNo = maxHoldNo,
            centerStabilityRatio = aiSummary.insideSupportRatio?.let { it / 100.0 },
            cruxHoldNo = aiSummary.primaryCruxHoldNo,
            cruxDurationMs = aiSummary.primaryCruxDurationMs,
            dangerEventCount = aiSummary.dangerEventCount ?: 0,
            failureReason = failureReason,
            riskAlert = riskAlert,
            nextMission = nextMission
        )
    }

    private fun resolveAttemptResult(
        holdReachResult: AttemptHoldReachResult?,
        aiSummary: FinalAnalysisAttemptSummary,
        totalHoldCount: Int
    ): String {
        if (totalHoldCount > 0 && holdReachResult != null) {
            return if (holdReachResult.highestReachedHoldNo >= totalHoldCount) {
                "SUCCESS"
            } else {
                "FAIL"
            }
        }

        return when {
            aiSummary.isSuccess -> "SUCCESS"
            aiSummary.hasAiResult || holdReachResult != null -> "FAIL"
            else -> "UNKNOWN"
        }
    }

    private fun defaultFailureReason(attemptResult: String): String {
        return when (attemptResult) {
            "SUCCESS" -> "Completed this attempt successfully."
            "FAIL" -> "This attempt did not reach the target hold sequence."
            else -> "This attempt was recorded without a full analysis result."
        }
    }

    private fun defaultRiskAlert(attemptResult: String): String {
        return when (attemptResult) {
            "SUCCESS" -> "No major risk pattern was detected in this attempt."
            "FAIL" -> "A clear risk pattern could not be fully measured, but the attempt ended before completion."
            else -> "Risk signals could not be fully measured for this attempt."
        }
    }

    private fun defaultNextMission(attemptResult: String): String {
        return when (attemptResult) {
            "SUCCESS" -> "Keep this attempt as the baseline for your next challenge."
            "FAIL" -> "Use this attempt as a baseline record for the next comparison."
            else -> "Store this attempt as a reference for the next analyzed try."
        }
    }

    private fun TerminalPrePoseSnapshot.resolveDurationMs(playbackUri: String): Int? {
        val entry = entriesByPlaybackUri[playbackUri] ?: return null
        val poseDurationMs = entry.poses.lastOrNull()?.frameTimeMs ?: 0L
        val aiDurationMs = entry.aiPoseSequence?.frames?.lastOrNull()?.timestampMs ?: 0L
        val resolvedDurationMs = maxOf(poseDurationMs, aiDurationMs)
        if (resolvedDurationMs <= 0L) {
            return null
        }
        return resolvedDurationMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
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

private fun TerminalPrePoseEntry?.preferredAiPoseSequence(): AiPoseSequence? {
    return this?.filteredAiPoseSequence ?: this?.aiPoseSequence
}

private data class AttemptPoseAnalysis(
    val holdReachResult: AttemptHoldReachResult,
    val poseSequenceDto: PoseSequenceDto,
    val poses: List<Pose>,
    val polygonHoldContactDebugResult: PolygonHoldContactDebugResult
)

private data class HoldReachAnalysisBundle(
    val holdReachResults: List<AttemptHoldReachResult>,
    val poseDtos: List<PoseSequenceDto>,
    val analyzedPoses: List<List<Pose>>,
    val polygonHoldContactDebugResults: List<PolygonHoldContactDebugResult>,
    val overallSummary: OverallHoldReachSummary?
)

private enum class SubmissionAnalysisPrewarmStatus {
    Running,
    Ready,
    Failed
}

private data class SubmissionAnalysisPrewarmKey(
    val selectionGeneration: Long,
    val attemptUrisSignature: String,
    val numberedHoldsFingerprint: String,
    val aiMode: AiAnalysisMode,
    val frameWidthPx: Int,
    val frameHeightPx: Int
)

private data class SubmissionAnalysisPrewarmResult(
    val aiAnalysisResults: List<AiAnalysisResult>
)

private data class SubmissionAnalysisPrewarmEntry(
    val key: SubmissionAnalysisPrewarmKey,
    val status: SubmissionAnalysisPrewarmStatus,
    val result: SubmissionAnalysisPrewarmResult? = null,
    val errorMessage: String? = null
)

private data class BackgroundUploadKey(
    val selectionGeneration: Long,
    val challengeId: Long,
    val attemptUrisSignature: String
)

private data class BackgroundUploadRequest(
    val key: BackgroundUploadKey,
    val challengeId: Long,
    val attemptUris: List<String>,
    val totalHoldCount: Int
)

private data class ResolvedAiProfile(
    val heightCm: Float,
    val weightKg: Float?,
    val wingspanCm: Float?
)

private fun emptyAttemptCompletionSummary(attemptNo: Int): FinalAnalysisAttemptSummary {
    return FinalAnalysisAttemptSummary(
        attemptNo = attemptNo,
        hasAiResult = false,
        isSuccess = false,
        analysisPoints = emptyList(),
        reachedHolds = null,
        reachedHoldsText = FinalAnalysisUnknownMetricText,
        processedFrames = null,
        processedFramesText = FinalAnalysisUnknownMetricText,
        highConfidenceRatio = null,
        highConfidenceRatioText = FinalAnalysisUnknownMetricText,
        insideSupportRatio = null,
        insideSupportRatioText = FinalAnalysisUnknownMetricText,
        stableContactFrameCount = null,
        stableContactFrameCountText = FinalAnalysisUnknownMetricText,
        stableContactRatio = null,
        stableContactRatioText = FinalAnalysisUnknownMetricText,
        stabilityTimeline = DefaultFinalAnalysisTimeline,
        stabilityFocusFraction = null,
        stabilityHighlights = emptyList(),
        stabilityNarrative = "",
        failureHighlights = emptyList(),
        failureNarrative = "",
        primaryCruxHoldNo = null,
        primaryCruxDurationMs = null,
        primaryReasonLabel = null,
        dangerEventCount = null,
        feedbackTypes = emptyList(),
        loadFocusLabel = null,
        feedbackLine = "",
        coachingLine = "",
        effectiveModeLabel = "",
        fallbackLabel = null
    )
}

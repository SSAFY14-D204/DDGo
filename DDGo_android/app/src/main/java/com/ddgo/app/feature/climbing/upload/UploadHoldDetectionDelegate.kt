package com.ddgo.app.feature.climbing.upload

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ddgo.app.data.ml.color.HoldColorClassifier
import com.ddgo.app.domain.model.Hold
import com.ddgo.app.domain.repository.HoldDetector
import com.ddgo.app.domain.repository.PersonDetector
import com.ddgo.app.domain.usecase.HoldNumbered
import com.ddgo.app.domain.usecase.assignHoldNumbers
import com.ddgo.app.domain.usecase.toHolds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.LinkedHashSet
import kotlin.math.sqrt
import wseemann.media.FFmpegMediaMetadataRetriever

internal class UploadHoldDetectionDelegate(
    private val context: Context,
    private val personDetector: PersonDetector,
    private val holdDetector: HoldDetector,
    private val holdColorClassifier: HoldColorClassifier
) {

    var bestFrameBitmap by mutableStateOf<Bitmap?>(null)
    var debugBestFrameImageUri by mutableStateOf<String?>(null)
    var allRawHolds by mutableStateOf<List<Hold>>(emptyList())
    var detectedHolds by mutableStateOf<List<Hold>>(emptyList())
    var candidateHolds by mutableStateOf<List<Hold>>(emptyList())
    var showCandidatePopup by mutableStateOf(false)
    var selectedStartHold by mutableStateOf<Hold?>(null)
    var selectedEndHold by mutableStateOf<Hold?>(null)
    var numberedHolds by mutableStateOf<List<HoldNumbered>>(emptyList())
    private var lastSuccessfulDetectionInput: DetectionInputKey? = null
    private var holdDetectionPrecomputeEntry by mutableStateOf<HoldDetectionPrecomputeEntry?>(null)
    private var activePrecomputeJob: Job? = null
    private var activePrecomputeTaskId: Long = 0L
    private var activePrecomputeSourceKey: HoldDetectionPrecomputeSourceKey? = null
    private var nextPrecomputeTaskId: Long = 0L

    fun useDebugBestFrameImage(uri: String) {
        debugBestFrameImageUri = uri
        cancelPrecompute(clearDebugSource = false)
        clearSelectedHoldSelection()
    }

    fun resetHoldDetectionState(clearDebugSource: Boolean) {
        cancelPrecompute(clearDebugSource = clearDebugSource)
        clearSelectedHoldSelection()
    }

    fun requestHoldPrecompute(
        selectionGeneration: Long,
        sourceVideoUri: String?
    ) {
        val sourceKey = buildPrecomputeSourceKey(sourceVideoUri) ?: return
        val existingEntry = holdDetectionPrecomputeEntry

        if (existingEntry != null && existingEntry.matches(selectionGeneration, sourceKey)) {
            if (existingEntry.status != HoldDetectionPrecomputeStatus.Failed) {
                return
            }
        }

        holdDetectionPrecomputeEntry = HoldDetectionPrecomputeEntry(
            selectionGeneration = selectionGeneration,
            sourceVideoUri = sourceKey.sourceVideoUri,
            debugBestFrameImageUri = sourceKey.debugBestFrameImageUri,
            status = HoldDetectionPrecomputeStatus.Waiting
        )
    }

    fun isPrecomputeRunning(
        selectionGeneration: Long,
        sourceVideoUri: String?
    ): Boolean {
        val sourceKey = buildPrecomputeSourceKey(sourceVideoUri) ?: return false
        return holdDetectionPrecomputeEntry?.matches(selectionGeneration, sourceKey) == true &&
            holdDetectionPrecomputeEntry?.status == HoldDetectionPrecomputeStatus.Running &&
            activePrecomputeSourceKey == sourceKey &&
            activePrecomputeJob?.isActive == true
    }

    fun isPrecomputeReady(
        selectionGeneration: Long,
        sourceVideoUri: String?
    ): Boolean {
        val sourceKey = buildPrecomputeSourceKey(sourceVideoUri) ?: return false
        return holdDetectionPrecomputeEntry?.matches(selectionGeneration, sourceKey) == true &&
            holdDetectionPrecomputeEntry?.status == HoldDetectionPrecomputeStatus.Ready
    }

    fun ensurePrecomputeStarted(
        scope: CoroutineScope,
        selectionGeneration: Long,
        sourceVideoUri: String?
    ): HoldDetectionPrecomputeStartResult {
        val sourceKey = buildPrecomputeSourceKey(sourceVideoUri)
            ?: return HoldDetectionPrecomputeStartResult.MissingSource
        val existingEntry = holdDetectionPrecomputeEntry

        if (existingEntry != null && existingEntry.matches(selectionGeneration, sourceKey)) {
            when (existingEntry.status) {
                HoldDetectionPrecomputeStatus.Ready -> {
                    syncPublicDetectionState(existingEntry)
                    return HoldDetectionPrecomputeStartResult.ReusedReady
                }

                HoldDetectionPrecomputeStatus.Running -> {
                    if (activePrecomputeSourceKey == sourceKey && activePrecomputeJob?.isActive == true) {
                        return HoldDetectionPrecomputeStartResult.ReusedRunning
                    }
                }

                HoldDetectionPrecomputeStatus.Failed,
                HoldDetectionPrecomputeStatus.Idle,
                HoldDetectionPrecomputeStatus.Waiting -> Unit
            }
        }

        if (
            activePrecomputeJob?.isActive == true &&
            activePrecomputeSourceKey != null &&
            activePrecomputeSourceKey != sourceKey
        ) {
            activePrecomputeJob?.cancel()
        }

        val taskId = ++nextPrecomputeTaskId
        activePrecomputeTaskId = taskId
        activePrecomputeSourceKey = sourceKey
        holdDetectionPrecomputeEntry = HoldDetectionPrecomputeEntry(
            selectionGeneration = selectionGeneration,
            sourceVideoUri = sourceKey.sourceVideoUri,
            debugBestFrameImageUri = sourceKey.debugBestFrameImageUri,
            status = HoldDetectionPrecomputeStatus.Running
        )
        activePrecomputeJob = scope.launch {
            val startedAt = UploadAiTraceLogger.now()
            UploadAiTraceLogger.log(
                event = "HOLD_PRECOMPUTE_BEGIN",
                generation = selectionGeneration,
                playbackUri = sourceKey.sourceVideoUri ?: sourceKey.debugBestFrameImageUri,
                status = "running"
            )
            try {
                val precomputed = executePrecomputeWork(sourceKey)
                if (!isActivePrecomputeTask(taskId, sourceKey)) {
                    return@launch
                }
                val readyEntry = buildReadyEntry(
                    selectionGeneration = selectionGeneration,
                    sourceKey = sourceKey,
                    precomputed = precomputed
                )
                holdDetectionPrecomputeEntry = readyEntry
                syncPublicDetectionState(readyEntry)
                UploadAiTraceLogger.log(
                    event = "HOLD_PRECOMPUTE_DONE",
                    generation = selectionGeneration,
                    playbackUri = sourceKey.sourceVideoUri ?: sourceKey.debugBestFrameImageUri,
                    status = "ready",
                    elapsedMs = UploadAiTraceLogger.elapsedSince(startedAt),
                    details = mapOf(
                        "rawHoldCount" to precomputed.rawYoloHolds.size,
                        "allHoldCount" to precomputed.allRawHolds.size
                    )
                )
            } catch (cancelled: CancellationException) {
                if (isActivePrecomputeTask(taskId, sourceKey)) {
                    UploadAiTraceLogger.log(
                        event = "HOLD_PRECOMPUTE_CANCELLED",
                        generation = selectionGeneration,
                        playbackUri = sourceKey.sourceVideoUri ?: sourceKey.debugBestFrameImageUri,
                        status = "cancelled",
                        elapsedMs = UploadAiTraceLogger.elapsedSince(startedAt)
                    )
                }
                throw cancelled
            } catch (throwable: Throwable) {
                if (!isActivePrecomputeTask(taskId, sourceKey)) {
                    return@launch
                }
                holdDetectionPrecomputeEntry = HoldDetectionPrecomputeEntry(
                    selectionGeneration = selectionGeneration,
                    sourceVideoUri = sourceKey.sourceVideoUri,
                    debugBestFrameImageUri = sourceKey.debugBestFrameImageUri,
                    status = HoldDetectionPrecomputeStatus.Failed,
                    errorMessage = throwable.message
                )
                UploadAiTraceLogger.log(
                    event = "HOLD_PRECOMPUTE_FAILED",
                    generation = selectionGeneration,
                    playbackUri = sourceKey.sourceVideoUri ?: sourceKey.debugBestFrameImageUri,
                    status = "failed",
                    details = mapOf("error" to throwable.message)
                )
            } finally {
                clearActivePrecomputeIfNeeded(taskId, sourceKey)
            }
        }
        return HoldDetectionPrecomputeStartResult.Started
    }

    suspend fun awaitPrecomputeTerminal(
        selectionGeneration: Long,
        sourceVideoUri: String?
    ): HoldDetectionPrecomputeTerminalResult {
        val sourceKey = buildPrecomputeSourceKey(sourceVideoUri)
            ?: return HoldDetectionPrecomputeTerminalResult.Missing
        val currentEntry = holdDetectionPrecomputeEntry

        if (currentEntry != null && currentEntry.matches(selectionGeneration, sourceKey)) {
            when (currentEntry.status) {
                HoldDetectionPrecomputeStatus.Ready -> {
                    syncPublicDetectionState(currentEntry)
                    return HoldDetectionPrecomputeTerminalResult.Ready
                }

                HoldDetectionPrecomputeStatus.Failed -> {
                    return HoldDetectionPrecomputeTerminalResult.Failed
                }

                HoldDetectionPrecomputeStatus.Running -> {
                    if (activePrecomputeSourceKey == sourceKey && activePrecomputeJob != null) {
                        activePrecomputeJob?.join()
                    } else {
                        return HoldDetectionPrecomputeTerminalResult.Missing
                    }
                }

                HoldDetectionPrecomputeStatus.Idle,
                HoldDetectionPrecomputeStatus.Waiting -> return HoldDetectionPrecomputeTerminalResult.Missing
            }
        }

        val terminalEntry = holdDetectionPrecomputeEntry
        return when {
            terminalEntry?.matches(selectionGeneration, sourceKey) == true &&
                terminalEntry.status == HoldDetectionPrecomputeStatus.Ready -> {
                syncPublicDetectionState(terminalEntry)
                HoldDetectionPrecomputeTerminalResult.Ready
            }

            terminalEntry?.matches(selectionGeneration, sourceKey) == true &&
                terminalEntry.status == HoldDetectionPrecomputeStatus.Failed -> {
                HoldDetectionPrecomputeTerminalResult.Failed
            }

            else -> HoldDetectionPrecomputeTerminalResult.Missing
        }
    }

    fun cancelPrecompute(clearDebugSource: Boolean) {
        activePrecomputeJob?.cancel()
        activePrecomputeJob = null
        activePrecomputeTaskId = 0L
        activePrecomputeSourceKey = null
        clearDetectionOutput(preserveDebugSource = !clearDebugSource)
    }

    fun clearAppliedHoldStatePreservingSourceCache() {
        candidateHolds = emptyList()
        showCandidatePopup = false
        lastSuccessfulDetectionInput = null
        clearSelectedHoldSelection()

        val currentEntry = holdDetectionPrecomputeEntry
        if (currentEntry?.status == HoldDetectionPrecomputeStatus.Ready) {
            val updatedEntry = currentEntry.copy(
                lastAppliedColorKey = null,
                detectedHolds = emptyList(),
                errorMessage = null
            )
            holdDetectionPrecomputeEntry = updatedEntry
            syncPublicDetectionState(updatedEntry)
            return
        }

        detectedHolds = emptyList()
    }

    fun updateSelectedStartHold(hold: Hold) {
        selectedStartHold = hold
        selectedEndHold = null
        numberedHolds = emptyList()
    }

    fun updateSelectedEndHold(hold: Hold) {
        selectedEndHold = hold
        recomputeHoldNumbers()
    }

    fun findCandidatesNearTap(tapNormX: Float, tapNormY: Float) {
        val candidates = findNearbyCandidates(tapNormX, tapNormY)
        if (candidates.isNotEmpty()) {
            candidateHolds = candidates
            showCandidatePopup = true
        }
    }

    fun applyHoldChanges(toAdd: List<Hold>, toRemove: List<Hold>) {
        toAdd.forEach(::addManualHold)
        toRemove.forEach(::removeHold)
        dismissCandidatePopup()
        syncDetectedHoldsToPrecomputeEntry()
    }

    fun dismissCandidatePopup() {
        showCandidatePopup = false
        candidateHolds = emptyList()
    }

    fun removeHold(hold: Hold) {
        detectedHolds = detectedHolds.filter { existing ->
            existing.boundingBox != hold.boundingBox
        }
        syncDetectedHoldsToPrecomputeEntry()
        clearSelectedHoldSelection()
        Log.d(TAG, "removeHold: bbox=${hold.boundingBox}, color=${hold.colorLabel}")
    }

    suspend fun precomputeHoldDetection(
        selectionGeneration: Long,
        sourceVideoUri: String?,
        allowRetryOnFailure: Boolean = false
    ): Result<Unit> = runCatching {
        val sourceKey = buildPrecomputeSourceKey(sourceVideoUri)
            ?: throw IllegalStateException("videoUri/debugBestFrameImageUri ?놁쓬")
        val existingEntry = holdDetectionPrecomputeEntry

        if (existingEntry != null && existingEntry.matches(selectionGeneration, sourceKey)) {
            when (existingEntry.status) {
                HoldDetectionPrecomputeStatus.Ready -> {
                    syncPublicDetectionState(existingEntry)
                    return@runCatching
                }

                HoldDetectionPrecomputeStatus.Running -> {
                    Log.d(TAG, "precomputeHoldDetection: reuse running task for identical source")
                    return@runCatching
                }

                HoldDetectionPrecomputeStatus.Failed -> {
                    if (!allowRetryOnFailure) {
                        throw IllegalStateException(
                            existingEntry.errorMessage ?: "hold detection precompute failed."
                        )
                    }
                }

                HoldDetectionPrecomputeStatus.Idle,
                HoldDetectionPrecomputeStatus.Waiting -> Unit
            }
        }

        holdDetectionPrecomputeEntry = HoldDetectionPrecomputeEntry(
            selectionGeneration = selectionGeneration,
            sourceVideoUri = sourceKey.sourceVideoUri,
            debugBestFrameImageUri = sourceKey.debugBestFrameImageUri,
            status = HoldDetectionPrecomputeStatus.Running
        )
        val startedAt = UploadAiTraceLogger.now()
        UploadAiTraceLogger.log(
            event = "HOLD_PRECOMPUTE_BEGIN",
            generation = selectionGeneration,
            playbackUri = sourceKey.sourceVideoUri ?: sourceKey.debugBestFrameImageUri,
            status = "running"
        )

        val precomputed = withContext(Dispatchers.IO) {
            val preparedFrame = prepareBestFrame(sourceKey)
            val rawHolds = detectRawHoldsFromBestFrame(
                bitmap = preparedFrame.bitmap,
                bestFrameTimeUs = preparedFrame.bestFrameTimeUs
            )
            val classified = classifyAllHoldsFromBestFrame(
                bitmap = preparedFrame.bitmap,
                rawHolds = rawHolds,
                bestFrameTimeUs = preparedFrame.bestFrameTimeUs
            )
            PreparedHoldPrecomputeResult(
                bitmap = preparedFrame.bitmap,
                bestFrameTimeUs = preparedFrame.bestFrameTimeUs,
                rawYoloHolds = rawHolds,
                classifiedAllRich = classified.classifiedHolds,
                allRawHolds = classified.allHolds
            )
        }

        val readyEntry = HoldDetectionPrecomputeEntry(
            selectionGeneration = selectionGeneration,
            sourceVideoUri = sourceKey.sourceVideoUri,
            debugBestFrameImageUri = sourceKey.debugBestFrameImageUri,
            status = HoldDetectionPrecomputeStatus.Ready,
            bestFrameBitmap = precomputed.bitmap,
            bestFrameTimeUs = precomputed.bestFrameTimeUs,
            rawYoloHolds = precomputed.rawYoloHolds,
            classifiedAllRich = precomputed.classifiedAllRich,
            allRawHolds = precomputed.allRawHolds,
            detectedHolds = emptyList(),
            errorMessage = null
        )
        holdDetectionPrecomputeEntry = readyEntry
        syncPublicDetectionState(readyEntry)
        UploadAiTraceLogger.log(
            event = "HOLD_PRECOMPUTE_DONE",
            generation = selectionGeneration,
            playbackUri = sourceKey.sourceVideoUri ?: sourceKey.debugBestFrameImageUri,
            status = "ready",
            elapsedMs = UploadAiTraceLogger.elapsedSince(startedAt),
            details = mapOf(
                "rawHoldCount" to precomputed.rawYoloHolds.size,
                "allHoldCount" to precomputed.allRawHolds.size
            )
        )
    }.onFailure { throwable ->
        val sourceKey = buildPrecomputeSourceKey(sourceVideoUri)
        holdDetectionPrecomputeEntry = HoldDetectionPrecomputeEntry(
            selectionGeneration = selectionGeneration,
            sourceVideoUri = sourceKey?.sourceVideoUri,
            debugBestFrameImageUri = sourceKey?.debugBestFrameImageUri,
            status = HoldDetectionPrecomputeStatus.Failed,
            errorMessage = throwable.message
        )
        UploadAiTraceLogger.log(
            event = "HOLD_PRECOMPUTE_FAILED",
            generation = selectionGeneration,
            playbackUri = sourceKey?.sourceVideoUri ?: sourceKey?.debugBestFrameImageUri,
            status = "failed",
            details = mapOf("error" to throwable.message)
        )
    }

    fun applyHoldColorFilter(
        selectionGeneration: Long,
        detectionTargetColor: String
    ): Result<Boolean> = runCatching {
        val currentEntry = holdDetectionPrecomputeEntry
            ?: throw IllegalStateException("hold detection precompute cache missing.")
        val normalizedColor = detectionTargetColor.trim().lowercase()

        if (
            currentEntry.selectionGeneration != selectionGeneration ||
            currentEntry.status != HoldDetectionPrecomputeStatus.Ready
        ) {
            throw IllegalStateException(
                currentEntry.errorMessage ?: "hold detection precompute is not ready."
            )
        }

        if (currentEntry.lastAppliedColorKey == normalizedColor) {
            syncPublicDetectionState(currentEntry)
            lastSuccessfulDetectionInput = DetectionInputKey(
                sourceVideoUri = currentEntry.sourceVideoUri,
                debugBestFrameImageUri = currentEntry.debugBestFrameImageUri,
                normalizedDetectionTargetColor = normalizedColor
            )
            return@runCatching false
        }

        val filteredHolds = holdColorClassifier.filterClassifiedHolds(
            classifiedHolds = currentEntry.classifiedAllRich,
            targetColorName = detectionTargetColor,
            scoreThreshold = 0.25f
        )
        val updatedEntry = currentEntry.copy(
            lastAppliedColorKey = normalizedColor,
            detectedHolds = filteredHolds,
            errorMessage = null
        )
        holdDetectionPrecomputeEntry = updatedEntry
        syncPublicDetectionState(updatedEntry)
        clearSelectedHoldSelection()
        lastSuccessfulDetectionInput = DetectionInputKey(
            sourceVideoUri = updatedEntry.sourceVideoUri,
            debugBestFrameImageUri = updatedEntry.debugBestFrameImageUri,
            normalizedDetectionTargetColor = normalizedColor
        )
        UploadAiTraceLogger.log(
            event = "HOLD_FILTER_APPLIED",
            generation = selectionGeneration,
            playbackUri = updatedEntry.sourceVideoUri ?: updatedEntry.debugBestFrameImageUri,
            details = mapOf(
                "targetColor" to normalizedColor,
                "allHoldCount" to updatedEntry.allRawHolds.size,
                "filteredHoldCount" to filteredHolds.size
            )
        )
        true
    }

    suspend fun runHoldDetection(
        scope: CoroutineScope? = null,
        sourceVideoUri: String?,
        detectionTargetColor: String,
        selectionGeneration: Long = holdDetectionPrecomputeEntry?.selectionGeneration ?: 0L
    ): Result<Unit> = runCatching {
        val debugImageUri = debugBestFrameImageUri
        val currentInput = DetectionInputKey(
            sourceVideoUri = sourceVideoUri,
            debugBestFrameImageUri = debugImageUri,
            normalizedDetectionTargetColor = detectionTargetColor.trim().lowercase()
        )
        if (debugImageUri == null && sourceVideoUri == null) {
            throw IllegalStateException("videoUri/debugBestFrameImageUri 없음")
        }

        if (canReuseDetectionResult(currentInput)) {
            Log.d(TAG, "runHoldDetection: reuse cached result for identical input")
            return@runCatching
        }

        val startedAt = UploadAiTraceLogger.now()
        UploadAiTraceLogger.log(
            event = "HOLD_FALLBACK_RUN_BEGIN",
            generation = selectionGeneration,
            playbackUri = sourceVideoUri ?: debugImageUri,
            status = "fallback"
        )
        var terminalResult = awaitPrecomputeTerminal(
            selectionGeneration = selectionGeneration,
            sourceVideoUri = sourceVideoUri
        )
        if (terminalResult != HoldDetectionPrecomputeTerminalResult.Ready) {
            val startResult = scope?.let {
                ensurePrecomputeStarted(
                    scope = it,
                    selectionGeneration = selectionGeneration,
                    sourceVideoUri = sourceVideoUri
                )
            }
            if (startResult == HoldDetectionPrecomputeStartResult.MissingSource) {
                throw IllegalStateException("videoUri/debugBestFrameImageUri ?놁쓬")
            }
            terminalResult = if (scope != null) {
                awaitPrecomputeTerminal(
                    selectionGeneration = selectionGeneration,
                    sourceVideoUri = sourceVideoUri
                )
            } else {
                precomputeHoldDetection(
                    selectionGeneration = selectionGeneration,
                    sourceVideoUri = sourceVideoUri,
                    allowRetryOnFailure = true
                ).getOrThrow()
                HoldDetectionPrecomputeTerminalResult.Ready
            }
        }
        if (terminalResult != HoldDetectionPrecomputeTerminalResult.Ready) {
            throw IllegalStateException(
                holdDetectionPrecomputeEntry?.errorMessage ?: "hold detection precompute is not ready."
            )
        }
        applyHoldColorFilter(
            selectionGeneration = selectionGeneration,
            detectionTargetColor = detectionTargetColor
        ).getOrThrow()
        lastSuccessfulDetectionInput = currentInput
        UploadAiTraceLogger.log(
            event = "HOLD_FALLBACK_RUN_DONE",
            generation = selectionGeneration,
            playbackUri = sourceVideoUri ?: debugImageUri,
            status = "success",
            elapsedMs = UploadAiTraceLogger.elapsedSince(startedAt)
        )
        return@runCatching

        val (bitmap, allHolds, filteredHolds) = withContext(Dispatchers.IO) {
            val preparedBitmap = if (debugImageUri != null) {
                Log.d(TAG, "runHoldDetection: use debug image as best frame, uri=$debugImageUri")
                loadBitmapFromUri(Uri.parse(debugImageUri))
            } else {
                val uri = sourceVideoUri
                    ?: throw IllegalStateException("videoUri 없음")

                Log.d(TAG, "runHoldDetection: PersonDetector start")
                val bestTimeUs = personDetector.findBestFrameTime(uri)
                Log.d(TAG, "runHoldDetection: best frame at ${bestTimeUs / 1000}ms")

                val retriever = FFmpegMediaMetadataRetriever()
                val parsedUri = Uri.parse(uri)
                val rotationDegrees = readUploadVideoRotationDegrees(
                    context = context,
                    uri = parsedUri,
                    logTag = TAG
                )
                try {
                    if (!setUploadRetrieverDataSource(
                            context = context,
                            retriever = retriever,
                            uri = parsedUri,
                            logTag = TAG
                        )
                    ) {
                        throw IllegalStateException("setDataSource 실패 (scheme=${parsedUri.scheme})")
                    }
                    retriever.getFrameAtTime(
                        bestTimeUs,
                        FFmpegMediaMetadataRetriever.OPTION_CLOSEST
                    )?.let { rawBitmap ->
                        orientBitmapForUploadRotation(
                            bitmap = rawBitmap,
                            rotationDegrees = rotationDegrees
                        )
                    } ?: throw IllegalStateException(
                        "getFrameAtTime returned null (PTS=${bestTimeUs / 1000}ms)"
                    )
                } finally {
                    retriever.release()
                }
            }

            Log.d(
                TAG,
                "runHoldDetection: prepared best frame (${preparedBitmap.width}x${preparedBitmap.height})"
            )

            val detectionResult = detectHoldsFromBestFrame(
                bitmap = preparedBitmap,
                detectionTargetColor = detectionTargetColor
            )
            Triple(preparedBitmap, detectionResult.allHolds, detectionResult.filteredHolds)
        }

        bestFrameBitmap = bitmap
        allRawHolds = allHolds
        detectedHolds = filteredHolds
        lastSuccessfulDetectionInput = currentInput
        clearSelectedHoldSelection()
    }

    fun clearSelectedHoldSelection() {
        selectedStartHold = null
        selectedEndHold = null
        numberedHolds = emptyList()
    }

    private fun clearDetectionOutput(preserveDebugSource: Boolean) {
        if (!preserveDebugSource) {
            debugBestFrameImageUri = null
        }
        bestFrameBitmap = null
        allRawHolds = emptyList()
        detectedHolds = emptyList()
        candidateHolds = emptyList()
        showCandidatePopup = false
        lastSuccessfulDetectionInput = null
        holdDetectionPrecomputeEntry = null
    }

    private suspend fun executePrecomputeWork(
        sourceKey: HoldDetectionPrecomputeSourceKey
    ): PreparedHoldPrecomputeResult = withContext(Dispatchers.IO) {
        val preparedFrame = prepareBestFrame(sourceKey)
        val rawHolds = detectRawHoldsFromBestFrame(
            bitmap = preparedFrame.bitmap,
            bestFrameTimeUs = preparedFrame.bestFrameTimeUs
        )
        val classified = classifyAllHoldsFromBestFrame(
            bitmap = preparedFrame.bitmap,
            rawHolds = rawHolds,
            bestFrameTimeUs = preparedFrame.bestFrameTimeUs
        )
        PreparedHoldPrecomputeResult(
            bitmap = preparedFrame.bitmap,
            bestFrameTimeUs = preparedFrame.bestFrameTimeUs,
            rawYoloHolds = rawHolds,
            classifiedAllRich = classified.classifiedHolds,
            allRawHolds = classified.allHolds
        )
    }

    private fun buildReadyEntry(
        selectionGeneration: Long,
        sourceKey: HoldDetectionPrecomputeSourceKey,
        precomputed: PreparedHoldPrecomputeResult
    ): HoldDetectionPrecomputeEntry {
        return HoldDetectionPrecomputeEntry(
            selectionGeneration = selectionGeneration,
            sourceVideoUri = sourceKey.sourceVideoUri,
            debugBestFrameImageUri = sourceKey.debugBestFrameImageUri,
            status = HoldDetectionPrecomputeStatus.Ready,
            bestFrameBitmap = precomputed.bitmap,
            bestFrameTimeUs = precomputed.bestFrameTimeUs,
            rawYoloHolds = precomputed.rawYoloHolds,
            classifiedAllRich = precomputed.classifiedAllRich,
            allRawHolds = precomputed.allRawHolds,
            detectedHolds = emptyList(),
            errorMessage = null
        )
    }

    private fun isActivePrecomputeTask(
        taskId: Long,
        sourceKey: HoldDetectionPrecomputeSourceKey
    ): Boolean {
        return activePrecomputeTaskId == taskId &&
            activePrecomputeSourceKey == sourceKey
    }

    private fun clearActivePrecomputeIfNeeded(
        taskId: Long,
        sourceKey: HoldDetectionPrecomputeSourceKey
    ) {
        if (!isActivePrecomputeTask(taskId, sourceKey)) {
            return
        }
        activePrecomputeJob = null
        activePrecomputeTaskId = 0L
        activePrecomputeSourceKey = null
    }

    private fun canReuseDetectionResult(input: DetectionInputKey): Boolean {
        return lastSuccessfulDetectionInput == input && bestFrameBitmap != null
    }

    private fun syncPublicDetectionState(entry: HoldDetectionPrecomputeEntry) {
        bestFrameBitmap = entry.bestFrameBitmap
        allRawHolds = entry.allRawHolds
        detectedHolds = entry.detectedHolds
    }

    private fun syncDetectedHoldsToPrecomputeEntry() {
        val currentEntry = holdDetectionPrecomputeEntry ?: return
        if (currentEntry.status != HoldDetectionPrecomputeStatus.Ready) {
            return
        }
        holdDetectionPrecomputeEntry = currentEntry.copy(detectedHolds = detectedHolds)
    }

    private fun buildPrecomputeSourceKey(
        sourceVideoUri: String?
    ): HoldDetectionPrecomputeSourceKey? {
        val debugImageUri = debugBestFrameImageUri
        if (debugImageUri == null && sourceVideoUri == null) {
            return null
        }
        return HoldDetectionPrecomputeSourceKey(
            sourceVideoUri = sourceVideoUri,
            debugBestFrameImageUri = debugImageUri
        )
    }

    private fun addManualHold(hold: Hold) {
        val alreadyExists = detectedHolds.any { existing ->
            existing.boundingBox == hold.boundingBox
        }
        if (!alreadyExists) {
            detectedHolds = detectedHolds + hold
            syncDetectedHoldsToPrecomputeEntry()
            clearSelectedHoldSelection()
            Log.d(TAG, "addManualHold: bbox=${hold.boundingBox}, color=${hold.colorLabel}")
        }
    }

    private fun findNearbyCandidates(
        tapNormX: Float,
        tapNormY: Float,
        searchRadius: Float = 0.12f
    ): List<Hold> {
        return allRawHolds
            .filter { hold ->
                val cx = (hold.boundingBox.left + hold.boundingBox.right) / 2f
                val cy = (hold.boundingBox.top + hold.boundingBox.bottom) / 2f
                val dist = sqrt(
                    (cx - tapNormX) * (cx - tapNormX) + (cy - tapNormY) * (cy - tapNormY)
                )
                dist <= searchRadius
            }
            .sortedBy { hold ->
                val cx = (hold.boundingBox.left + hold.boundingBox.right) / 2f
                val cy = (hold.boundingBox.top + hold.boundingBox.bottom) / 2f
                sqrt((cx - tapNormX) * (cx - tapNormX) + (cy - tapNormY) * (cy - tapNormY))
            }
            .take(8)
    }

    private fun recomputeHoldNumbers() {
        val startHold = selectedStartHold ?: return
        val endHold = selectedEndHold ?: return

        runCatching {
            assignHoldNumbers(
                holds = detectedHolds,
                startHold = startHold,
                endHold = endHold
            )
        }.onSuccess { numbered ->
            numberedHolds = numbered
            detectedHolds = numbered.toHolds()
            syncDetectedHoldsToPrecomputeEntry()
            selectedStartHold = numbered.firstOrNull { it.isStart }?.hold
            selectedEndHold = numbered.firstOrNull { it.isEnd }?.hold
            Log.d(TAG, "recomputeHoldNumbers: success, count=${numbered.size}")
        }.onFailure { throwable ->
            Log.e(TAG, "recomputeHoldNumbers: failed", throwable)
            numberedHolds = emptyList()
        }
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap {
        if (uri.scheme == "file") {
            return BitmapFactory.decodeFile(uri.path)
                ?: throw IllegalStateException("선택한 이미지를 읽을 수 없습니다.")
        }

        val bitmap = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input)
        }

        return bitmap ?: throw IllegalStateException("선택한 이미지를 읽을 수 없습니다.")
    }

    private suspend fun detectRawHoldsFromBestFrame(
        bitmap: Bitmap,
        bestFrameTimeUs: Long? = null
    ): List<Hold> {
        val startedAt = UploadAiTraceLogger.now()
        UploadAiTraceLogger.log(
            event = "HOLD_YOLO_BEGIN",
            generation = holdDetectionPrecomputeEntry?.selectionGeneration,
            playbackUri = holdDetectionPrecomputeEntry?.sourceVideoUri ?: holdDetectionPrecomputeEntry?.debugBestFrameImageUri,
            details = mapOf("bestTimeUs" to bestFrameTimeUs)
        )
        Log.d(TAG, "detectHoldsFromBestFrame: HoldDetector start")
        val rawHolds = holdDetector.detectFromFrame(bitmap)
        Log.d(TAG, "detectHoldsFromBestFrame: raw hold count=${rawHolds.size}")
        UploadAiTraceLogger.log(
            event = "attempt_upload_raw_holds_detected",
            generation = holdDetectionPrecomputeEntry?.selectionGeneration,
            playbackUri = holdDetectionPrecomputeEntry?.sourceVideoUri ?: holdDetectionPrecomputeEntry?.debugBestFrameImageUri,
            details = mapOf(
                "bestTimeUs" to bestFrameTimeUs,
                "rawHoldCount" to rawHolds.size,
                "rawBBoxes" to UploadAiTraceLogger.formatBoundingBoxes(
                    rawHolds.map { hold -> hold.boundingBox }
                )
            )
        )
        UploadAiTraceLogger.log(
            event = "HOLD_YOLO_DONE",
            generation = holdDetectionPrecomputeEntry?.selectionGeneration,
            playbackUri = holdDetectionPrecomputeEntry?.sourceVideoUri ?: holdDetectionPrecomputeEntry?.debugBestFrameImageUri,
            elapsedMs = UploadAiTraceLogger.elapsedSince(startedAt),
            details = mapOf(
                "bestTimeUs" to bestFrameTimeUs,
                "rawHoldCount" to rawHolds.size
            )
        )
        return rawHolds
    }

    private fun classifyAllHoldsFromBestFrame(
        bitmap: Bitmap,
        rawHolds: List<Hold>,
        bestFrameTimeUs: Long? = null
    ): HoldColorClassifier.ClassifiedHoldPrecomputeResult {
        val startedAt = UploadAiTraceLogger.now()
        UploadAiTraceLogger.log(
            event = "HOLD_CLASSIFY_ALL_BEGIN",
            generation = holdDetectionPrecomputeEntry?.selectionGeneration,
            playbackUri = holdDetectionPrecomputeEntry?.sourceVideoUri ?: holdDetectionPrecomputeEntry?.debugBestFrameImageUri,
            details = mapOf(
                "bestTimeUs" to bestFrameTimeUs,
                "rawHoldCount" to rawHolds.size
            )
        )
        Log.d(TAG, "detectHoldsFromBestFrame: classify all colors")
        return holdColorClassifier.classifyAllRich(
            bitmap = bitmap,
            holds = rawHolds,
            relaxedRejection = true
        ).also { classified ->
            UploadAiTraceLogger.log(
                event = "HOLD_CLASSIFY_ALL_DONE",
                generation = holdDetectionPrecomputeEntry?.selectionGeneration,
                playbackUri = holdDetectionPrecomputeEntry?.sourceVideoUri ?: holdDetectionPrecomputeEntry?.debugBestFrameImageUri,
                elapsedMs = UploadAiTraceLogger.elapsedSince(startedAt),
                details = mapOf(
                    "bestTimeUs" to bestFrameTimeUs,
                    "rawHoldCount" to rawHolds.size,
                    "allHoldCount" to classified.allHolds.size
                )
            )
        }
    }

    private suspend fun prepareBestFrame(sourceKey: HoldDetectionPrecomputeSourceKey): PreparedBestFrame {
        if (sourceKey.debugBestFrameImageUri != null) {
            Log.d(
                TAG,
                "prepareBestFrame: use debug image as best frame, uri=${sourceKey.debugBestFrameImageUri}"
            )
            UploadAiTraceLogger.log(
                event = "HOLD_BEST_FRAME_READY",
                generation = holdDetectionPrecomputeEntry?.selectionGeneration,
                playbackUri = sourceKey.debugBestFrameImageUri,
                status = "debug_image"
            )
            return PreparedBestFrame(
                bitmap = loadBitmapFromUri(Uri.parse(sourceKey.debugBestFrameImageUri)),
                bestFrameTimeUs = null
            )
        }

        val uri = sourceKey.sourceVideoUri
            ?: throw IllegalStateException("videoUri ?놁쓬")

        val personDetectStartedAt = UploadAiTraceLogger.now()
        UploadAiTraceLogger.log(
            event = "HOLD_PERSON_DETECT_BEGIN",
            generation = holdDetectionPrecomputeEntry?.selectionGeneration,
            playbackUri = sourceKey.sourceVideoUri,
            details = mapOf("requestedPlaybackUri" to uri)
        )
        val bestTimeUs = try {
            Log.d(TAG, "prepareBestFrame: PersonDetector start")
            personDetector.findBestFrameTime(uri).also { resolvedBestTimeUs ->
                Log.d(TAG, "prepareBestFrame: best frame at ${resolvedBestTimeUs / 1000}ms")
                UploadAiTraceLogger.log(
                    event = "HOLD_PERSON_DETECT_DONE",
                    generation = holdDetectionPrecomputeEntry?.selectionGeneration,
                    playbackUri = sourceKey.sourceVideoUri,
                    elapsedMs = UploadAiTraceLogger.elapsedSince(personDetectStartedAt),
                    details = mapOf(
                        "requestedPlaybackUri" to uri,
                        "bestTimeUs" to resolvedBestTimeUs
                    )
                )
            }
        } catch (throwable: Throwable) {
            UploadAiTraceLogger.log(
                event = "HOLD_PERSON_DETECT_FAILED",
                generation = holdDetectionPrecomputeEntry?.selectionGeneration,
                playbackUri = sourceKey.sourceVideoUri,
                status = "failed",
                elapsedMs = UploadAiTraceLogger.elapsedSince(personDetectStartedAt),
                details = mapOf(
                    "requestedPlaybackUri" to uri,
                    "error" to throwable.message
                )
            )
            throw throwable
        }

        val retriever = FFmpegMediaMetadataRetriever()
        val parsedUri = Uri.parse(uri)
        val rotationDegrees = readUploadVideoRotationDegrees(
            context = context,
            uri = parsedUri,
            logTag = TAG
        )
        val extractedBestFrame = try {
            val ffmpegResult = runCatching {
                if (!setUploadRetrieverDataSource(
                        context = context,
                        retriever = retriever,
                        uri = parsedUri,
                        logTag = TAG
                    )
                ) {
                    throw IllegalStateException("FFmpeg setDataSource failed")
                }
                extractBestFrameWithFallback(
                    retriever = retriever,
                    requestedBestTimeUs = bestTimeUs,
                    rotationDegrees = rotationDegrees,
                    generation = holdDetectionPrecomputeEntry?.selectionGeneration,
                    playbackUri = sourceKey.sourceVideoUri
                )
            }

            ffmpegResult.getOrElse { ffmpegError ->
                // Some Android camera files can be opened by the platform retriever but
                // rejected by the bundled FFmpeg retriever. Use the same decoder as
                // PersonDetector so hold detection can continue on those devices.
                Log.w(
                    TAG,
                    "FFmpeg best-frame extraction failed; falling back to Android retriever " +
                        "(scheme=${parsedUri.scheme})",
                    ffmpegError
                )
                extractBestFrameWithPlatformRetriever(
                    uri = parsedUri,
                    requestedBestTimeUs = bestTimeUs,
                    rotationDegrees = rotationDegrees
                ) ?: throw IllegalStateException(
                    "선택한 영상을 열 수 없어요. 다른 영상으로 다시 시도해주세요.",
                    ffmpegError
                )
            }
        } finally {
            retriever.release()
        }

        Log.d(
            TAG,
            "prepareBestFrame: prepared best frame (${extractedBestFrame.bitmap.width}x${extractedBestFrame.bitmap.height})"
        )
        UploadAiTraceLogger.log(
            event = "HOLD_BEST_FRAME_READY",
            generation = holdDetectionPrecomputeEntry?.selectionGeneration,
            playbackUri = sourceKey.sourceVideoUri,
            details = mapOf(
                "requestedBestTimeUs" to bestTimeUs,
                "resolvedBestTimeUs" to extractedBestFrame.resolvedTimeUs,
                "width" to extractedBestFrame.bitmap.width,
                "height" to extractedBestFrame.bitmap.height
            )
        )
        return PreparedBestFrame(
            bitmap = extractedBestFrame.bitmap,
            bestFrameTimeUs = extractedBestFrame.resolvedTimeUs
        )
    }

    /**
     * FFmpeg가 특정 기기/코덱의 로컬 영상을 열지 못할 때 Android 기본 디코더로
     * 기준 프레임을 추출합니다.
     */
    private fun extractBestFrameWithPlatformRetriever(
        uri: Uri,
        requestedBestTimeUs: Long,
        rotationDegrees: Int
    ): ExtractedBestFrame? {
        val retriever = MediaMetadataRetriever()
        return try {
            if (uri.scheme == "file") {
                val path = uri.path ?: return null
                val file = File(path)
                if (!file.isFile || file.length() <= 0L) {
                    Log.w(TAG, "Platform retriever cannot open missing file: ${file.absolutePath}")
                    return null
                }
                retriever.setDataSource(file.absolutePath)
            } else {
                retriever.setDataSource(context, uri)
            }

            val durationUs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.takeIf { it > 0L }
                ?.times(1000L)
            val upperBoundUs = durationUs?.minus(1L)?.coerceAtLeast(0L)
            val requestedUs = upperBoundUs?.let {
                requestedBestTimeUs.coerceIn(0L, it)
            } ?: requestedBestTimeUs.coerceAtLeast(0L)
            val candidateTimesUs = listOf(
                requestedUs,
                (requestedUs - 500_000L).coerceAtLeast(0L),
                0L
            ).distinct()

            candidateTimesUs.firstNotNullOfOrNull { timeUs ->
                retriever.getFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST
                )?.let { rawBitmap ->
                    ExtractedBestFrame(
                        bitmap = orientBitmapForUploadRotation(rawBitmap, rotationDegrees),
                        resolvedTimeUs = timeUs
                    )
                }
            }
        } catch (error: Exception) {
            Log.e(TAG, "Android platform retriever fallback failed", error)
            null
        } finally {
            retriever.release()
        }
    }

    private fun extractBestFrameWithFallback(
        retriever: FFmpegMediaMetadataRetriever,
        requestedBestTimeUs: Long,
        rotationDegrees: Int,
        generation: Long?,
        playbackUri: String?
    ): ExtractedBestFrame {
        val startedAt = UploadAiTraceLogger.now()
        val durationUs = retriever
            .extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()
            ?.takeIf { it > 0L }
            ?.times(1000L)
        val attempts = buildBestFrameExtractionAttempts(
            requestedBestTimeUs = requestedBestTimeUs,
            durationUs = durationUs
        )
        UploadAiTraceLogger.log(
            event = "HOLD_BEST_FRAME_EXTRACT_BEGIN",
            generation = generation,
            playbackUri = playbackUri,
            details = mapOf(
                "requestedBestTimeUs" to requestedBestTimeUs,
                "durationUs" to durationUs,
                "attemptCount" to attempts.size
            )
        )

        attempts.forEachIndexed { index, attempt ->
            val rawBitmap = retriever.getFrameAtTime(
                attempt.timeUs,
                attempt.mode
            )
            if (rawBitmap != null) {
                val orientedBitmap = orientBitmapForUploadRotation(
                    bitmap = rawBitmap,
                    rotationDegrees = rotationDegrees
                )
                UploadAiTraceLogger.log(
                    event = "HOLD_BEST_FRAME_EXTRACT_SUCCESS",
                    generation = generation,
                    playbackUri = playbackUri,
                    elapsedMs = UploadAiTraceLogger.elapsedSince(startedAt),
                    details = mapOf(
                        "requestedBestTimeUs" to requestedBestTimeUs,
                        "resolvedBestTimeUs" to attempt.timeUs,
                        "durationUs" to durationUs,
                        "mode" to attempt.modeLabel,
                        "attemptIndex" to (index + 1)
                    )
                )
                Log.d(
                    TAG,
                    "extractBestFrameWithFallback: success requested=${requestedBestTimeUs / 1000}ms " +
                        "resolved=${attempt.timeUs / 1000}ms mode=${attempt.modeLabel} " +
                        "attempt=${index + 1}/${attempts.size}"
                )
                return ExtractedBestFrame(
                    bitmap = orientedBitmap,
                    resolvedTimeUs = attempt.timeUs
                )
            }

            UploadAiTraceLogger.log(
                event = "HOLD_BEST_FRAME_EXTRACT_RETRY",
                generation = generation,
                playbackUri = playbackUri,
                details = mapOf(
                    "requestedBestTimeUs" to requestedBestTimeUs,
                    "attemptTimeUs" to attempt.timeUs,
                    "durationUs" to durationUs,
                    "mode" to attempt.modeLabel,
                    "attemptIndex" to (index + 1)
                )
            )
            Log.d(
                TAG,
                "extractBestFrameWithFallback: null frame requested=${requestedBestTimeUs / 1000}ms " +
                    "attempt=${attempt.timeUs / 1000}ms mode=${attempt.modeLabel} " +
                    "index=${index + 1}/${attempts.size}"
            )
        }

        UploadAiTraceLogger.log(
            event = "HOLD_BEST_FRAME_EXTRACT_FAILED",
            generation = generation,
            playbackUri = playbackUri,
            status = "failed",
            elapsedMs = UploadAiTraceLogger.elapsedSince(startedAt),
            details = mapOf(
                "requestedBestTimeUs" to requestedBestTimeUs,
                "durationUs" to durationUs,
                "attemptCount" to attempts.size
            )
        )
        Log.e(
            TAG,
            "extractBestFrameWithFallback: failed requested=${requestedBestTimeUs / 1000}ms durationUs=$durationUs"
        )
        throw IllegalStateException("홀드 기준 프레임을 추출하지 못했습니다.")
    }

    private suspend fun detectHoldsFromBestFrame(
        bitmap: Bitmap,
        detectionTargetColor: String
    ): DetectedHoldFrameResult {
        val rawHolds = detectRawHoldsFromBestFrame(bitmap)
        val filteredHolds = holdColorClassifier.filterClassifiedHolds(
            classifiedHolds = classifyAllHoldsFromBestFrame(
                bitmap = bitmap,
                rawHolds = rawHolds
            ).classifiedHolds,
            targetColorName = detectionTargetColor,
            scoreThreshold = 0.25f
        )
        return DetectedHoldFrameResult(
            allHolds = classifyAllHoldsFromBestFrame(
                bitmap = bitmap,
                rawHolds = rawHolds
            ).allHolds,
            filteredHolds = filteredHolds
        )
    }

    private data class DetectedHoldFrameResult(
        val allHolds: List<Hold>,
        val filteredHolds: List<Hold>
    )

    private data class DetectionInputKey(
        val sourceVideoUri: String?,
        val debugBestFrameImageUri: String?,
        val normalizedDetectionTargetColor: String
    )

    internal enum class HoldDetectionPrecomputeStatus {
        Idle,
        Waiting,
        Running,
        Ready,
        Failed
    }

    internal enum class HoldDetectionPrecomputeStartResult {
        MissingSource,
        Started,
        ReusedRunning,
        ReusedReady
    }

    internal enum class HoldDetectionPrecomputeTerminalResult {
        Missing,
        Ready,
        Failed
    }

    internal data class HoldDetectionPrecomputeSourceKey(
        val sourceVideoUri: String?,
        val debugBestFrameImageUri: String?
    )

    internal data class HoldDetectionPrecomputeEntry(
        val selectionGeneration: Long,
        val sourceVideoUri: String?,
        val debugBestFrameImageUri: String?,
        val status: HoldDetectionPrecomputeStatus,
        val bestFrameBitmap: Bitmap? = null,
        val bestFrameTimeUs: Long? = null,
        val rawYoloHolds: List<Hold> = emptyList(),
        val classifiedAllRich: List<HoldColorClassifier.ClassifiedHoldRich> = emptyList(),
        val allRawHolds: List<Hold> = emptyList(),
        val lastAppliedColorKey: String? = null,
        val detectedHolds: List<Hold> = emptyList(),
        val errorMessage: String? = null
    ) {
        fun matches(
            selectionGeneration: Long,
            sourceKey: HoldDetectionPrecomputeSourceKey
        ): Boolean {
            return this.selectionGeneration == selectionGeneration &&
                sourceVideoUri == sourceKey.sourceVideoUri &&
                debugBestFrameImageUri == sourceKey.debugBestFrameImageUri
        }
    }

    private data class PreparedBestFrame(
        val bitmap: Bitmap,
        val bestFrameTimeUs: Long?
    )

    private data class ExtractedBestFrame(
        val bitmap: Bitmap,
        val resolvedTimeUs: Long
    )

    private data class PreparedHoldPrecomputeResult(
        val bitmap: Bitmap,
        val bestFrameTimeUs: Long?,
        val rawYoloHolds: List<Hold>,
        val classifiedAllRich: List<HoldColorClassifier.ClassifiedHoldRich>,
        val allRawHolds: List<Hold>
    )

    companion object {
        private const val TAG = "UploadHoldDetectionDelegate"
    }
}

internal data class BestFrameExtractionAttempt(
    val timeUs: Long,
    val mode: Int,
    val modeLabel: String
)

private val BEST_FRAME_EXTRACTION_BACKOFFS_US = listOf(
    33_000L,
    100_000L,
    250_000L,
    500_000L,
    1_000_000L,
    2_000_000L
)

internal fun buildBestFrameExtractionAttempts(
    requestedBestTimeUs: Long,
    durationUs: Long?
): List<BestFrameExtractionAttempt> {
    val upperBoundUs = durationUs
        ?.takeIf { it > 0L }
        ?.minus(1L)
        ?.coerceAtLeast(0L)
    val clampedRequestedUs = upperBoundUs?.let {
        requestedBestTimeUs.coerceIn(0L, it)
    } ?: requestedBestTimeUs.coerceAtLeast(0L)

    val attempts = mutableListOf<BestFrameExtractionAttempt>()
    val seen = LinkedHashSet<Pair<Long, Int>>()

    fun appendAttempt(timeUs: Long, mode: Int, modeLabel: String) {
        val normalizedTimeUs = upperBoundUs?.let { timeUs.coerceIn(0L, it) } ?: timeUs
        if (normalizedTimeUs < 0L) {
            return
        }
        val key = normalizedTimeUs to mode
        if (seen.add(key)) {
            attempts += BestFrameExtractionAttempt(
                timeUs = normalizedTimeUs,
                mode = mode,
                modeLabel = modeLabel
            )
        }
    }

    appendAttempt(
        timeUs = clampedRequestedUs,
        mode = FFmpegMediaMetadataRetriever.OPTION_CLOSEST,
        modeLabel = "closest"
    )
    appendAttempt(
        timeUs = clampedRequestedUs,
        mode = FFmpegMediaMetadataRetriever.OPTION_CLOSEST_SYNC,
        modeLabel = "closest_sync"
    )

    BEST_FRAME_EXTRACTION_BACKOFFS_US.forEach { backoffUs ->
        val candidateTimeUs = (clampedRequestedUs - backoffUs).coerceAtLeast(0L)
        appendAttempt(
            timeUs = candidateTimeUs,
            mode = FFmpegMediaMetadataRetriever.OPTION_CLOSEST,
            modeLabel = "closest"
        )
        appendAttempt(
            timeUs = candidateTimeUs,
            mode = FFmpegMediaMetadataRetriever.OPTION_CLOSEST_SYNC,
            modeLabel = "closest_sync"
        )
    }

    appendAttempt(
        timeUs = 0L,
        mode = FFmpegMediaMetadataRetriever.OPTION_CLOSEST_SYNC,
        modeLabel = "closest_sync"
    )

    return attempts
}

internal fun setUploadRetrieverDataSource(
    context: Context,
    retriever: FFmpegMediaMetadataRetriever,
    uri: Uri,
    logTag: String
): Boolean {
    return try {
        if (uri.scheme == "file") {
            val path = uri.path ?: return false
            val file = File(path)
            if (!file.isFile || file.length() <= 0L) {
                Log.e(logTag, "Local video file is missing or empty: ${file.absolutePath}")
                return false
            }

            runCatching {
                retriever.setDataSource(file.absolutePath)
            }.getOrElse { pathError ->
                // A file descriptor is more reliable for app-private/cache files on
                // newer Android releases and avoids path handling differences in FFmpeg.
                Log.w(logTag, "FFmpeg path data source failed; retrying with file descriptor", pathError)
                ParcelFileDescriptor.open(
                    file,
                    ParcelFileDescriptor.MODE_READ_ONLY
                ).use { descriptor ->
                    retriever.setDataSource(descriptor.fileDescriptor)
                }
            }
        } else {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                retriever.setDataSource(descriptor.fileDescriptor)
            } ?: return false
        }
        true
    } catch (error: Exception) {
        Log.e(logTag, "setUploadRetrieverDataSource failed (scheme=${uri.scheme}): ${error.message}")
        false
    }
}

internal fun readUploadVideoRotationDegrees(
    context: Context,
    uri: Uri,
    logTag: String
): Int {
    val extractor = MediaExtractor()
    return try {
        if (uri.scheme == "file") {
            extractor.setDataSource(uri.path ?: return 0)
        } else {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return 0
            extractor.setDataSource(pfd.fileDescriptor)
            pfd.close()
        }

        for (trackIndex in 0 until extractor.trackCount) {
            val trackFormat = extractor.getTrackFormat(trackIndex)
            val mime = trackFormat.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("video/") == true) {
                return if (trackFormat.containsKey(MediaFormat.KEY_ROTATION)) {
                    normalizeVideoRotationDegrees(trackFormat.getInteger(MediaFormat.KEY_ROTATION))
                } else {
                    0
                }
            }
        }

        0
    } catch (error: Exception) {
        Log.w(logTag, "Failed to read video rotation metadata: ${error.message}", error)
        0
    } finally {
        extractor.release()
    }
}

internal fun orientBitmapForUploadRotation(
    bitmap: Bitmap,
    rotationDegrees: Int
): Bitmap {
    val normalizedRotationDegrees = normalizeVideoRotationDegrees(rotationDegrees)
    if (normalizedRotationDegrees == 0) {
        return bitmap
    }

    val matrix = Matrix().apply {
        postRotate(normalizedRotationDegrees.toFloat())
    }
    return Bitmap.createBitmap(
        bitmap,
        0,
        0,
        bitmap.width,
        bitmap.height,
        matrix,
        true
    ).also { orientedBitmap ->
        if (orientedBitmap !== bitmap && !bitmap.isRecycled) {
            bitmap.recycle()
        }
    }
}

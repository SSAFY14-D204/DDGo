package com.ddgo.app.feature.climbing.upload

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ddgo.app.data.ml.color.HoldColorClassifier
import com.ddgo.app.domain.model.Hold
import com.ddgo.app.domain.repository.HoldDetector
import com.ddgo.app.domain.repository.PersonDetector
import com.ddgo.app.domain.usecase.AttemptHoldAlignmentStatus as UseCaseAlignmentStatus
import com.ddgo.app.domain.usecase.HoldNumbered
import com.ddgo.app.domain.usecase.alignAttemptHolds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import wseemann.media.FFmpegMediaMetadataRetriever

internal class UploadAttemptHoldAlignmentDelegate(
    private val context: Context,
    private val personDetector: PersonDetector,
    private val holdDetector: HoldDetector,
    private val holdColorClassifier: HoldColorClassifier,
    private val scope: CoroutineScope
) {

    var attemptHoldAlignmentEntries by mutableStateOf<Map<String, AttemptHoldAlignmentEntry>>(emptyMap())
        private set
    var attemptHoldAlignmentBatchState by mutableStateOf(AttemptHoldAlignmentBatchState())
        private set

    private val alignmentJobs = mutableMapOf<String, Job>()
    private var nextTaskId = 0L

    fun refreshTargets(
        selectionGeneration: Long,
        referenceVideoUri: String?,
        referenceFrameWidthPx: Int?,
        referenceFrameHeightPx: Int?,
        playbackUris: List<String>,
        referenceHolds: List<HoldNumbered>
        ,
        detectionTargetColor: String
    ) {
        val distinctUris = playbackUris.distinct()
        val normalizedColor = detectionTargetColor.trim().lowercase()
        val referenceSignature = buildReferenceSignature(referenceHolds)
        val updatedEntries = attemptHoldAlignmentEntries.toMutableMap()
        val launchEntries = mutableListOf<AttemptHoldAlignmentEntry>()

        if (referenceVideoUri != null || referenceFrameWidthPx != null || referenceFrameHeightPx != null) {
            Log.d(
                TAG,
                "$LOG_PREFIX refreshTargets reference anchor: video=$referenceVideoUri, frame=${referenceFrameWidthPx ?: -1}x${referenceFrameHeightPx ?: -1}"
            )
        }

        alignmentJobs.keys
            .filterNot { it in distinctUris }
            .forEach { playbackUri ->
                alignmentJobs.remove(playbackUri)?.cancel()
                updatedEntries.remove(playbackUri)
            }

        distinctUris.forEach { playbackUri ->
            val existing = updatedEntries[playbackUri]
            val nextInputKey = AttemptHoldAlignmentInputKey(
                normalizedDetectionTargetColor = normalizedColor,
                referenceSignature = referenceSignature
            )

            if (
                existing != null &&
                existing.selectionGeneration == selectionGeneration &&
                existing.inputKey == nextInputKey &&
                existing.status != AttemptHoldAlignmentStatus.Failed
            ) {
                return@forEach
            }

            val canReuseCandidates = existing != null &&
                existing.selectionGeneration == selectionGeneration &&
                existing.inputKey.normalizedDetectionTargetColor == normalizedColor &&
                existing.candidateHolds.isNotEmpty() &&
                existing.frameWidthPx != null &&
                existing.frameHeightPx != null

            val nextEntry = AttemptHoldAlignmentEntry(
                playbackUri = playbackUri,
                selectionGeneration = selectionGeneration,
                inputKey = nextInputKey,
                status = when {
                    canReuseCandidates && referenceHolds.isEmpty() -> AttemptHoldAlignmentStatus.WaitingReference
                    canReuseCandidates -> AttemptHoldAlignmentStatus.Aligning
                    else -> AttemptHoldAlignmentStatus.Pending
                },
                frameWidthPx = existing?.frameWidthPx.takeIf { canReuseCandidates },
                frameHeightPx = existing?.frameHeightPx.takeIf { canReuseCandidates },
                bestFrameTimeUs = existing?.bestFrameTimeUs.takeIf { canReuseCandidates },
                candidateHolds = existing?.candidateHolds.takeIf { canReuseCandidates }.orEmpty(),
                rawCropBounds = existing?.rawCropBounds.takeIf { canReuseCandidates },
                alignedHoldSet = null,
                errorMessage = null,
                taskId = nextTaskId()
            )

            updatedEntries[playbackUri] = nextEntry
            launchEntries += nextEntry
        }

        attemptHoldAlignmentEntries = updatedEntries
        updateBatchState(selectionGeneration, distinctUris)
        logBatchState("refreshTargets")

        launchEntries.forEach { entry ->
            launchAlignmentTask(
                entry = entry,
                referenceHolds = referenceHolds
            )
        }
    }

    suspend fun awaitTerminal(
        playbackUris: List<String>,
        onLoadingMessage: (String) -> Unit
    ) {
        val distinctUris = playbackUris.distinct()
        if (distinctUris.isEmpty()) {
            return
        }

        while (true) {
            val entries = distinctUris.mapNotNull(attemptHoldAlignmentEntries::get)
            val activeCount = entries.count { entry ->
                entry.status == AttemptHoldAlignmentStatus.Pending ||
                    entry.status == AttemptHoldAlignmentStatus.Precomputing ||
                    entry.status == AttemptHoldAlignmentStatus.Aligning
            }

            if (entries.size == distinctUris.size && activeCount == 0) {
                updateBatchState(entries.firstOrNull()?.selectionGeneration ?: 0L, distinctUris)
                return
            }

            val completedCount = entries.count { entry ->
                entry.status == AttemptHoldAlignmentStatus.Ready ||
                    entry.status == AttemptHoldAlignmentStatus.WaitingReference ||
                    entry.status == AttemptHoldAlignmentStatus.Failed
            }
            onLoadingMessage("추가 영상 홀드를 기준 루트에 맞추고 있습니다. ($completedCount/${distinctUris.size})")
            delay(100L)
        }
    }

    fun resolvedSelection(playbackUri: String): AttemptAlignedHoldSet? {
        return attemptHoldAlignmentEntries[playbackUri]?.alignedHoldSet
    }

    fun alignedHoldSetFor(playbackUri: String): AttemptAlignedHoldSet? = resolvedSelection(playbackUri)

    fun clear(preservePlaybackUris: Set<String> = emptySet()) {
        alignmentJobs.entries
            .filterNot { (playbackUri, _) -> playbackUri in preservePlaybackUris }
            .forEach { (playbackUri, job) ->
                job.cancel()
                alignmentJobs.remove(playbackUri)
            }

        attemptHoldAlignmentEntries = attemptHoldAlignmentEntries.filterKeys { playbackUri ->
            playbackUri in preservePlaybackUris
        }
        updateBatchState(
            generation = attemptHoldAlignmentBatchState.generation,
            playbackUris = preservePlaybackUris.toList()
        )
    }

    fun clearState(preservePlaybackUris: Set<String> = emptySet()) {
        clear(preservePlaybackUris = preservePlaybackUris)
    }

    private fun launchAlignmentTask(
        entry: AttemptHoldAlignmentEntry,
        referenceHolds: List<HoldNumbered>
    ) {
        alignmentJobs.remove(entry.playbackUri)?.cancel()
        alignmentJobs[entry.playbackUri] = scope.launch(Dispatchers.Default) {
            try {
                var workingEntry = entry

                if (workingEntry.candidateHolds.isEmpty()) {
                    updateEntry(entry.playbackUri, entry.taskId) { current ->
                        current.copy(
                            status = AttemptHoldAlignmentStatus.Precomputing,
                            errorMessage = null
                        )
                    }

                    val precomputed = precomputeAttemptHolds(
                        playbackUri = entry.playbackUri,
                        normalizedColor = entry.inputKey.normalizedDetectionTargetColor,
                        referenceHoldCount = referenceHolds.size
                    )

                    workingEntry = updateEntry(entry.playbackUri, entry.taskId) { current ->
                        current.copy(
                            status = if (referenceHolds.isEmpty()) {
                                AttemptHoldAlignmentStatus.WaitingReference
                            } else {
                                AttemptHoldAlignmentStatus.Aligning
                            },
                            frameWidthPx = precomputed.frameWidthPx,
                            frameHeightPx = precomputed.frameHeightPx,
                            bestFrameTimeUs = precomputed.bestFrameTimeUs,
                            candidateHolds = precomputed.candidateHolds,
                            rawCropBounds = precomputed.rawCropBounds,
                            errorMessage = null
                        )
                    } ?: return@launch

                    logAttempt(
                        playbackUri = entry.playbackUri,
                        message = "precompute complete, candidates=${precomputed.candidateHolds.size}, frame=${precomputed.frameWidthPx}x${precomputed.frameHeightPx}"
                    )
                }

                if (referenceHolds.isEmpty()) {
                    updateEntry(entry.playbackUri, workingEntry.taskId) { current ->
                        current.copy(
                            status = AttemptHoldAlignmentStatus.WaitingReference,
                            alignedHoldSet = null,
                            errorMessage = null
                        )
                    }
                    logAttempt(
                        playbackUri = entry.playbackUri,
                        message = "waiting for reference holds"
                    )
                    return@launch
                }

                updateEntry(entry.playbackUri, workingEntry.taskId) { current ->
                    current.copy(
                        status = AttemptHoldAlignmentStatus.Aligning,
                        errorMessage = null
                    )
                }

                val alignmentResult = withContext(Dispatchers.Default) {
                    alignAttemptHolds(
                        referenceHolds = referenceHolds,
                        candidateHolds = workingEntry.candidateHolds
                    )
                }

                val alignedHoldSet = buildAlignedHoldSet(
                    playbackUri = entry.playbackUri,
                    frameWidthPx = workingEntry.frameWidthPx ?: 1000,
                    frameHeightPx = workingEntry.frameHeightPx ?: 1000,
                    referenceHolds = referenceHolds,
                    rawCropBounds = workingEntry.rawCropBounds,
                    alignmentResult = alignmentResult
                )

                updateEntry(entry.playbackUri, workingEntry.taskId) { current ->
                    current.copy(
                        status = AttemptHoldAlignmentStatus.Ready,
                        alignedHoldSet = alignedHoldSet,
                        errorMessage = null
                    )
                }
                logAlignmentReady(entry.playbackUri, alignedHoldSet)
            } catch (error: Exception) {
                updateEntry(entry.playbackUri, entry.taskId) { current ->
                    current.copy(
                        status = AttemptHoldAlignmentStatus.Failed,
                        alignedHoldSet = null,
                        errorMessage = error.message
                    )
                }
                Log.e(TAG, "$LOG_PREFIX [${entry.playbackUri}] alignment failed", error)
            } finally {
                alignmentJobs.remove(entry.playbackUri)
                updateBatchState(
                    generation = attemptHoldAlignmentEntries[entry.playbackUri]?.selectionGeneration
                        ?: attemptHoldAlignmentBatchState.generation,
                    playbackUris = attemptHoldAlignmentEntries.keys.toList()
                )
            }
        }
    }

    private suspend fun precomputeAttemptHolds(
        playbackUri: String,
        normalizedColor: String,
        referenceHoldCount: Int
    ): PrecomputedAttemptHolds = withContext(Dispatchers.IO) {
        Log.d(TAG, "$LOG_PREFIX [$playbackUri] precompute start, color=${normalizedColor.ifBlank { "<all>" }}")
        val bestFrameTimeUs = personDetector.findBestFrameTime(playbackUri)
        val parsedUri = Uri.parse(playbackUri)
        val retriever = FFmpegMediaMetadataRetriever()
        val rotationDegrees = readUploadVideoRotationDegrees(
            context = context,
            uri = parsedUri,
            logTag = TAG
        )

        val bitmap = try {
            if (!setUploadRetrieverDataSource(
                    context = context,
                    retriever = retriever,
                    uri = parsedUri,
                    logTag = TAG
                )
            ) {
                throw IllegalStateException("Failed to open attempt video for hold alignment.")
            }

            retriever.getFrameAtTime(
                bestFrameTimeUs,
                FFmpegMediaMetadataRetriever.OPTION_CLOSEST
            )?.let { rawBitmap ->
                orientBitmapForUploadRotation(
                    bitmap = rawBitmap,
                    rotationDegrees = rotationDegrees
                )
            } ?: throw IllegalStateException("Failed to extract best frame for attempt hold alignment.")
        } finally {
            retriever.release()
        }

        try {
            val rawHolds = holdDetector.detectFromFrame(bitmap)
            UploadAiTraceLogger.log(
                event = "attempt_result_raw_holds_detected",
                playbackUri = playbackUri,
                details = mapOf(
                    "targetColor" to normalizedColor.ifBlank { "<all>" },
                    "bestFrameTimeUs" to bestFrameTimeUs,
                    "rawHoldCount" to rawHolds.size,
                    "rawBBoxes" to UploadAiTraceLogger.formatBoundingBoxes(
                        rawHolds.map { hold -> hold.boundingBox }
                    )
                )
            )
            val rawCropBounds = calculateRawVerticalCropBounds(rawHolds)
            UploadAiTraceLogger.log(
                event = "attempt_result_crop_bounds_resolved",
                playbackUri = playbackUri,
                details = mapOf(
                    "targetColor" to normalizedColor.ifBlank { "<all>" },
                    "bestFrameTimeUs" to bestFrameTimeUs,
                    "rawHoldCount" to rawHolds.size,
                    "rawCropBounds" to UploadAiTraceLogger.formatCropBounds(rawCropBounds)
                )
            )
            val classified = holdColorClassifier.classifyAllRich(
                bitmap = bitmap,
                holds = rawHolds,
                relaxedRejection = true
            )
            val filteredHolds = if (normalizedColor.isBlank()) {
                classified.allHolds
            } else {
                holdColorClassifier.filterClassifiedHolds(
                    classifiedHolds = classified.classifiedHolds,
                    targetColorName = normalizedColor,
                    scoreThreshold = 0.25f
                )
            }
            val candidateHolds = chooseCandidatePool(
                referenceHoldCount = referenceHoldCount,
                filteredHolds = filteredHolds,
                allHolds = classified.allHolds
            )

            PrecomputedAttemptHolds(
                bestFrameTimeUs = bestFrameTimeUs,
                frameWidthPx = bitmap.width,
                frameHeightPx = bitmap.height,
                candidateHolds = candidateHolds,
                rawCropBounds = rawCropBounds
            )
        } finally {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }

    private fun buildAlignedHoldSet(
        playbackUri: String,
        frameWidthPx: Int,
        frameHeightPx: Int,
        referenceHolds: List<HoldNumbered>,
        rawCropBounds: RawVerticalCropBounds?,
        alignmentResult: com.ddgo.app.domain.usecase.AttemptHoldAlignmentResult
    ): AttemptAlignedHoldSet {
        if (alignmentResult.status == UseCaseAlignmentStatus.Failed) {
            return AttemptAlignedHoldSet(
                playbackUri = playbackUri,
                frameWidthPx = frameWidthPx,
                frameHeightPx = frameHeightPx,
                mode = AttemptHoldAlignmentMode.ReferenceFallback,
                confidence = 0f,
                matchedHoldCount = 0,
                warpOnlyHoldCount = referenceHolds.size,
                alignedHolds = referenceHolds,
                rawCropBounds = rawCropBounds,
                debugSummary = alignmentResult.debugSummary
            )
        }

        val mode = when (alignmentResult.status) {
            UseCaseAlignmentStatus.Matched,
            UseCaseAlignmentStatus.ExactReference -> AttemptHoldAlignmentMode.Matched
            UseCaseAlignmentStatus.PartialWarpFallback -> AttemptHoldAlignmentMode.PartialWarpFallback
            UseCaseAlignmentStatus.ReferenceFallback,
            UseCaseAlignmentStatus.Failed -> AttemptHoldAlignmentMode.ReferenceFallback
        }

        return AttemptAlignedHoldSet(
            playbackUri = playbackUri,
            frameWidthPx = frameWidthPx,
            frameHeightPx = frameHeightPx,
            mode = mode,
            confidence = alignmentResult.coverage.coerceIn(0f, 1f),
            matchedHoldCount = alignmentResult.matchedCount,
            warpOnlyHoldCount = alignmentResult.warpedCount,
            alignedHolds = alignmentResult.alignedHolds,
            rawCropBounds = rawCropBounds,
            debugSummary = alignmentResult.debugSummary
        )
    }

    private fun calculateRawVerticalCropBounds(rawHolds: List<Hold>): RawVerticalCropBounds? {
        if (rawHolds.isEmpty()) return null

        val topFraction = rawHolds.minOf { hold ->
            hold.boundingBox.top.coerceIn(0f, 1f)
        }
        val bottomFraction = rawHolds.maxOf { hold ->
            hold.boundingBox.bottom.coerceIn(0f, 1f)
        }
        if (bottomFraction <= topFraction) {
            return null
        }

        return RawVerticalCropBounds(
            topFraction = topFraction,
            bottomFraction = bottomFraction
        )
    }

    private fun chooseCandidatePool(
        referenceHoldCount: Int,
        filteredHolds: List<Hold>,
        allHolds: List<Hold>
    ): List<Hold> {
        val minimumFilteredCount = when {
            referenceHoldCount >= 6 -> 3
            referenceHoldCount >= 3 -> 2
            else -> 1
        }
        return if (filteredHolds.size >= minimumFilteredCount) {
            filteredHolds
        } else {
            allHolds
        }
    }

    private fun updateEntry(
        playbackUri: String,
        taskId: Long?,
        block: (AttemptHoldAlignmentEntry) -> AttemptHoldAlignmentEntry
    ): AttemptHoldAlignmentEntry? {
        val current = attemptHoldAlignmentEntries[playbackUri] ?: return null
        if (taskId != null && current.taskId != taskId) {
            return null
        }

        val updated = block(current)
        attemptHoldAlignmentEntries = attemptHoldAlignmentEntries.toMutableMap().apply {
            put(playbackUri, updated)
        }
        updateBatchState(updated.selectionGeneration, attemptHoldAlignmentEntries.keys.toList())
        return updated
    }

    private fun updateBatchState(
        generation: Long,
        playbackUris: List<String>
    ) {
        val entries = playbackUris.distinct().mapNotNull(attemptHoldAlignmentEntries::get)
        if (entries.isEmpty()) {
            attemptHoldAlignmentBatchState = AttemptHoldAlignmentBatchState()
            return
        }

        attemptHoldAlignmentBatchState = AttemptHoldAlignmentBatchState(
            generation = generation,
            totalCount = entries.size,
            pendingCount = entries.count { it.status == AttemptHoldAlignmentStatus.Pending },
            precomputingCount = entries.count { it.status == AttemptHoldAlignmentStatus.Precomputing },
            waitingReferenceCount = entries.count { it.status == AttemptHoldAlignmentStatus.WaitingReference },
            aligningCount = entries.count { it.status == AttemptHoldAlignmentStatus.Aligning },
            readyCount = entries.count { it.status == AttemptHoldAlignmentStatus.Ready },
            failedCount = entries.count { it.status == AttemptHoldAlignmentStatus.Failed }
        )
    }

    private fun buildReferenceSignature(referenceHolds: List<HoldNumbered>): String {
        if (referenceHolds.isEmpty()) {
            return "none"
        }

        return referenceHolds
            .sortedBy(HoldNumbered::holdNo)
            .joinToString(separator = "|") { hold ->
                val bbox = hold.hold.boundingBox
                listOf(
                    hold.holdNo.toString(),
                    formatFloat(bbox.left),
                    formatFloat(bbox.top),
                    formatFloat(bbox.right),
                    formatFloat(bbox.bottom)
                ).joinToString(separator = ",")
            }
    }

    private fun formatFloat(value: Float): String = "%.4f".format(value)

    private fun nextTaskId(): Long {
        nextTaskId += 1L
        return nextTaskId
    }

    private fun logAttempt(playbackUri: String, message: String) {
        Log.d(TAG, "$LOG_PREFIX [$playbackUri] $message")
    }

    private fun logAlignmentReady(
        playbackUri: String,
        alignedHoldSet: AttemptAlignedHoldSet
    ) {
        Log.i(
            TAG,
            "$LOG_PREFIX [$playbackUri] ready, mode=${alignedHoldSet.mode}, " +
                "matched=${alignedHoldSet.matchedHoldCount}, warpOnly=${alignedHoldSet.warpOnlyHoldCount}, " +
                "confidence=${"%.3f".format(alignedHoldSet.confidence)}, detail=${alignedHoldSet.debugSummary}"
        )
    }

    private fun logBatchState(reason: String) {
        Log.d(
            TAG,
            "$LOG_PREFIX $reason: total=${attemptHoldAlignmentBatchState.totalCount}, " +
                "pending=${attemptHoldAlignmentBatchState.pendingCount}, " +
                "precomputing=${attemptHoldAlignmentBatchState.precomputingCount}, " +
                "waitingReference=${attemptHoldAlignmentBatchState.waitingReferenceCount}, " +
                "aligning=${attemptHoldAlignmentBatchState.aligningCount}, " +
                "ready=${attemptHoldAlignmentBatchState.readyCount}, " +
                "failed=${attemptHoldAlignmentBatchState.failedCount}"
        )
    }

    private data class PrecomputedAttemptHolds(
        val bestFrameTimeUs: Long,
        val frameWidthPx: Int,
        val frameHeightPx: Int,
        val candidateHolds: List<Hold>,
        val rawCropBounds: RawVerticalCropBounds?
    )

    companion object {
        private const val TAG = "AttemptHoldAlignment"
        private const val LOG_PREFIX = "[DDGO_ATTEMPT_HOLD_ALIGN]"
    }
}

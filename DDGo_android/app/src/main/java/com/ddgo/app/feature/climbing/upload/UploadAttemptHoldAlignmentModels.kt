package com.ddgo.app.feature.climbing.upload

import com.ddgo.app.domain.model.Hold
import com.ddgo.app.domain.usecase.HoldNumbered

internal enum class AttemptHoldAlignmentStatus {
    Pending,
    Precomputing,
    WaitingReference,
    Aligning,
    Ready,
    Failed
}

internal enum class AttemptHoldAlignmentMode {
    Matched,
    PartialWarpFallback,
    ReferenceFallback
}

internal data class RawVerticalCropBounds(
    val topFraction: Float,
    val bottomFraction: Float
)

internal data class AttemptAlignedHoldSet(
    val playbackUri: String,
    val frameWidthPx: Int,
    val frameHeightPx: Int,
    val mode: AttemptHoldAlignmentMode,
    val confidence: Float,
    val matchedHoldCount: Int,
    val warpOnlyHoldCount: Int,
    val alignedHolds: List<HoldNumbered>,
    val rawCropBounds: RawVerticalCropBounds? = null,
    val debugSummary: String
)

data class AttemptHoldAlignmentBatchState(
    val generation: Long = 0L,
    val totalCount: Int = 0,
    val pendingCount: Int = 0,
    val precomputingCount: Int = 0,
    val waitingReferenceCount: Int = 0,
    val aligningCount: Int = 0,
    val readyCount: Int = 0,
    val failedCount: Int = 0
) {
    val isTerminal: Boolean
        get() = totalCount > 0 &&
            pendingCount == 0 &&
            precomputingCount == 0 &&
            aligningCount == 0
}

internal data class AttemptHoldAlignmentInputKey(
    val normalizedDetectionTargetColor: String,
    val referenceSignature: String
)

internal data class AttemptHoldAlignmentEntry(
    val playbackUri: String,
    val selectionGeneration: Long,
    val inputKey: AttemptHoldAlignmentInputKey,
    val status: AttemptHoldAlignmentStatus,
    val frameWidthPx: Int? = null,
    val frameHeightPx: Int? = null,
    val bestFrameTimeUs: Long? = null,
    val candidateHolds: List<Hold> = emptyList(),
    val rawCropBounds: RawVerticalCropBounds? = null,
    val alignedHoldSet: AttemptAlignedHoldSet? = null,
    val errorMessage: String? = null,
    val taskId: Long? = null
)

internal data class AttemptHoldAlignmentTask(
    val playbackUri: String,
    val taskId: Long
)

internal data class TerminalAttemptHoldAlignmentSnapshot(
    val generation: Long,
    val entriesByPlaybackUri: Map<String, TerminalAttemptHoldAlignmentEntry>
)

internal data class TerminalAttemptHoldAlignmentEntry(
    val playbackUri: String,
    val selectionGeneration: Long,
    val status: AttemptHoldAlignmentStatus,
    val alignedHoldSet: AttemptAlignedHoldSet?,
    val errorMessage: String?
)

internal fun AttemptHoldAlignmentEntry.toTerminalEntry(): TerminalAttemptHoldAlignmentEntry =
    TerminalAttemptHoldAlignmentEntry(
        playbackUri = playbackUri,
        selectionGeneration = selectionGeneration,
        status = status,
        alignedHoldSet = alignedHoldSet,
        errorMessage = errorMessage
    )

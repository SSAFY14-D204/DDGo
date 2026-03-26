package com.ddgo.app.feature.climbing.upload.ui.analysis.organism

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.ddgo.app.domain.model.AnalysisPoint
import com.ddgo.app.domain.model.Hold
import com.ddgo.app.domain.model.Pose
import com.ddgo.app.domain.usecase.HoldNumbered
import com.ddgo.app.feature.climbing.upload.AnalysisMuted
import com.ddgo.app.feature.climbing.upload.AnalysisText
import com.ddgo.app.feature.climbing.upload.AttemptPoseOverlayCache
import com.ddgo.app.feature.climbing.upload.COLOR_END
import com.ddgo.app.feature.climbing.upload.COLOR_START
import com.ddgo.app.feature.climbing.upload.CroppedVideoViewport
import com.ddgo.app.feature.climbing.upload.HoldOverviewPreview
import com.ddgo.app.feature.climbing.upload.PoseOverlay
import com.ddgo.app.feature.climbing.upload.PoseScrubberColors
import com.ddgo.app.feature.climbing.upload.PoseScrubberMarker
import com.ddgo.app.feature.climbing.upload.PoseVideoScrubber
import com.ddgo.app.feature.climbing.upload.RawVerticalCropBounds
import com.ddgo.app.feature.climbing.upload.ScreenRect
import com.ddgo.app.feature.climbing.upload.VideoContentRect
import com.ddgo.app.feature.climbing.upload.calculateVerticalVideoViewportCropSpecFromRawHolds
import com.ddgo.app.feature.climbing.upload.calculateVideoContentRect
import com.ddgo.app.feature.climbing.upload.findNearestOverlayFrameForPlayback
import com.ddgo.app.feature.climbing.upload.holdLabelToComposeColor
import com.ddgo.app.feature.climbing.upload.holdColorToUiColor
import com.ddgo.app.feature.climbing.upload.resolveActiveAnalysisCardIndex
import com.ddgo.app.feature.climbing.upload.resolveDisplayedVideoAspectRatio
import com.ddgo.app.feature.climbing.upload.resolveInitialAttemptPlaybackStartTimeMs
import com.ddgo.app.feature.climbing.upload.toScreenRect
import com.ddgo.app.feature.climbing.upload.ui.analysis.molecule.HeaderChip
import com.ddgo.app.feature.climbing.upload.ui.shared.organism.AttemptVideoSection
import com.ddgo.app.feature.climbing.upload.ui.shared.organism.AttemptVideoSectionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs

internal data class AttemptPreviewHeroState(
    val gymName: String,
    val displayDate: String,
    val difficultyLabel: String,
    val holdColorLabel: String,
    val selectedAttempt: Int,
    val isSuccess: Boolean,
    val analysisModeLabel: String? = null,
    val fallbackLabel: String? = null,
    val previewBitmap: Bitmap?,
    val previewHolds: List<Hold>,
    val numberedHolds: List<HoldNumbered> = emptyList(),
    val selectedAttemptVideoUri: String? = null,
    val analysisPoints: List<AnalysisPoint> = emptyList(),
    val attemptPoseSequence: List<Pose> = emptyList(),
    val overlayCache: AttemptPoseOverlayCache? = null,
    val rawHolds: List<Hold> = emptyList(),
    val viewportCropBounds: RawVerticalCropBounds? = null,
    val wallArrivalTimeMs: Long? = null,
    val personObservationStartTimeMs: Long? = null,
    val usesPoseTimeline: Boolean = false,
    val seekRequestId: Long = 0L,
    val seekRequestTimeMs: Long? = null
)

private val AttemptPreviewOverlayLineColor = Color(0xFF00E5FF).copy(alpha = 0.88f)
private val AttemptPreviewOverlayPointColor = Color.White
private val AttemptPreviewScrubberAccent = Color(0xFF82B1FF)
private val AttemptPreviewHiddenFaceLandmarks = (1..10).toSet()
private val AttemptPreviewTopSafeInset = 24.dp
private val AttemptPreviewBottomSafeInset = 64.dp
private val AttemptPreviewControlHeight = 132.dp

@Composable
internal fun AttemptPreviewHero(
    state: AttemptPreviewHeroState,
    onShareClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showHoldOverviewDialog by remember(state.previewBitmap, state.previewHolds) { mutableStateOf(false) }
    val difficultyChipTone = remember(state.difficultyLabel) {
        buildHeaderChipTone(holdColorToUiColor(state.difficultyLabel))
    }
    val holdChipTone = remember(state.holdColorLabel) {
        buildHeaderChipTone(holdColorToUiColor(state.holdColorLabel))
    }
    val statusChipTone = remember(state.isSuccess) {
        buildHeaderChipTone(
            if (state.isSuccess) Color(0xFF39C66D) else Color(0xFFFF5E63)
        )
    }
    var displayedPositionMs by remember(state.selectedAttemptVideoUri) { mutableLongStateOf(0L) }
    val activeTimelineIndex by remember(state.analysisPoints, displayedPositionMs) {
        derivedStateOf {
            resolveActiveAnalysisCardIndex(
                points = state.analysisPoints,
                displayedPositionMs = displayedPositionMs,
                tappedCardOverrideIdx = -1
            )
        }
    }
    val scrubberMarkers = remember(state.analysisPoints, activeTimelineIndex) {
        state.analysisPoints.mapIndexed { index, point ->
            PoseScrubberMarker(
                index = point.index,
                timeMs = point.timeMs,
                kind = point.kind,
                isSelected = index == activeTimelineIndex
            )
        }
    }

    LaunchedEffect(state.selectedAttemptVideoUri) {
        displayedPositionMs = 0L
    }

    LaunchedEffect(state.seekRequestId, state.seekRequestTimeMs) {
        displayedPositionMs = state.seekRequestTimeMs?.coerceAtLeast(0L) ?: displayedPositionMs
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = state.gymName.ifBlank { "클라이밍장 미지정" },
                    color = AnalysisText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = state.displayDate,
                    color = AnalysisMuted,
                    fontSize = 13.sp
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HeaderChip(
                        text = "${state.selectedAttempt}차 ${if (state.isSuccess) "성공" else "실패"}",
                        background = statusChipTone.background,
                        contentColor = statusChipTone.content,
                        borderColor = statusChipTone.border
                    )
                    if (state.difficultyLabel.isNotBlank()) {
                        HeaderChip(
                            text = "난이도 ${state.difficultyLabel}",
                            background = difficultyChipTone.background,
                            contentColor = difficultyChipTone.content,
                            borderColor = difficultyChipTone.border
                        )
                    }
                    if (state.holdColorLabel.isNotBlank()) {
                        HeaderChip(
                            text = "홀드 ${state.holdColorLabel}",
                            background = holdChipTone.background,
                            contentColor = holdChipTone.content,
                            borderColor = holdChipTone.border
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(width = 116.dp, height = 96.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(
                        enabled = state.previewBitmap != null || state.previewHolds.isNotEmpty()
                    ) { showHoldOverviewDialog = true }
            ) {
                HoldOverviewPreview(
                    bitmap = state.previewBitmap,
                    holds = state.previewHolds,
                    modifier = Modifier.fillMaxSize(),
                    showZoomBadge = true
                )
            }
        }

        if (showHoldOverviewDialog) {
            Dialog(onDismissRequest = { showHoldOverviewDialog = false }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF17181C))
                        .padding(16.dp)
                ) {
                    HoldOverviewPreview(
                        bitmap = state.previewBitmap,
                        holds = state.previewHolds,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(360.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF101114))
        ) {
            if (state.selectedAttemptVideoUri != null) {
                AttemptVideoSection(
                    state = AttemptVideoSectionState(
                        videoUri = state.selectedAttemptVideoUri,
                        numberedHolds = state.numberedHolds,
                        rawHolds = state.rawHolds,
                        viewportCropBounds = state.viewportCropBounds,
                        analysisPoints = state.analysisPoints,
                        markers = scrubberMarkers,
                        attemptPoseSequence = state.attemptPoseSequence,
                        overlayCache = state.overlayCache,
                        wallArrivalTimeMs = state.wallArrivalTimeMs,
                        personObservationStartTimeMs = state.personObservationStartTimeMs,
                        seekRequestId = state.seekRequestId,
                        seekRequestTimeMs = state.seekRequestTimeMs
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    lineColor = AttemptPreviewOverlayLineColor,
                    pointColor = AttemptPreviewOverlayPointColor,
                    scrubberColors = PoseScrubberColors(
                        trackColor = Color.White.copy(alpha = 0.18f),
                        progressColor = AttemptPreviewScrubberAccent,
                        thumbColor = Color.White,
                        textColor = Color.White.copy(alpha = 0.82f)
                    ),
                    controlSurfaceColor = Color(0xFF101114),
                    hiddenLandmarkIndices = AttemptPreviewHiddenFaceLandmarks,
                    topSafeInset = AttemptPreviewTopSafeInset,
                    bottomSafeInset = AttemptPreviewBottomSafeInset,
                    controlAreaHeight = AttemptPreviewControlHeight,
                    onDisplayedPositionChanged = { displayedPositionMs = it }
                )

                if (onShareClick != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 72.dp)
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF101114).copy(alpha = 0.82f))
                            .clickable(onClick = onShareClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "시도 분석 결과 공유",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            } else {
                HoldOverviewPreview(
                    bitmap = state.previewBitmap,
                    holds = state.previewHolds,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(242.dp)
                )
            }

        }

        Spacer(modifier = Modifier.height(18.dp))
    }
}

private data class HeaderChipTone(
    val background: Color,
    val content: Color,
    val border: Color
)

private fun buildHeaderChipTone(baseColor: Color): HeaderChipTone {
    val contentColor = when {
        baseColor.luminance() < 0.12f -> lerp(baseColor, Color.White, 0.72f)
        baseColor.luminance() < 0.22f -> lerp(baseColor, Color.White, 0.52f)
        baseColor.luminance() > 0.84f -> lerp(baseColor, Color.Black, 0.28f)
        else -> baseColor
    }

    return HeaderChipTone(
        background = baseColor.copy(alpha = 0.32f),
        content = contentColor,
        border = baseColor.copy(alpha = 0.62f)
    )
}

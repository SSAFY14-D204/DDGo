package com.ddgo.app.feature.climbing.upload.ui.analysis.organism

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.ddgo.app.domain.model.AnalysisPoint
import com.ddgo.app.domain.model.Hold
import com.ddgo.app.domain.model.Pose
import com.ddgo.app.domain.usecase.HoldNumbered
import com.ddgo.app.feature.climbing.upload.AnalysisCardColor
import com.ddgo.app.feature.climbing.upload.AnalysisMuted
import com.ddgo.app.feature.climbing.upload.AnalysisText
import com.ddgo.app.feature.climbing.upload.AttemptPoseOverlayCache
import com.ddgo.app.feature.climbing.upload.HoldOverviewPreview
import com.ddgo.app.feature.climbing.upload.PoseOverlay
import com.ddgo.app.feature.climbing.upload.PoseScrubberColors
import com.ddgo.app.feature.climbing.upload.PoseScrubberMarker
import com.ddgo.app.feature.climbing.upload.RawVerticalCropBounds
import com.ddgo.app.feature.climbing.upload.holdColorToUiColor
import com.ddgo.app.feature.climbing.upload.resolveActiveAnalysisCardIndex
import com.ddgo.app.feature.climbing.upload.ui.analysis.molecule.HeaderChip
import com.ddgo.app.feature.climbing.upload.ui.analysis.molecule.buildHeaderChipTone
import com.ddgo.app.feature.climbing.upload.ui.shared.organism.AttemptPhysicsLimbHeatmapAndComOverlay
import com.ddgo.app.feature.climbing.upload.ui.shared.organism.AttemptVideoSection
import com.ddgo.app.feature.climbing.upload.ui.shared.organism.AttemptVideoSectionState
import kotlinx.serialization.json.JsonObject

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
    val physicsResult: JsonObject? = null,
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
private val AttemptPreviewHiddenFaceLandmarks = (0..10).toSet()
private val AttemptPreviewHiddenFingerTipPoints = setOf(17, 18, 19, 20, 21, 22)
private const val AttemptPreviewPointRadiusScale = 0.75f
private val AttemptPreviewTopSafeInset = 24.dp
private val AttemptPreviewBottomSafeInset = 64.dp
private val AttemptPreviewControlHeight = 132.dp

@Composable
internal fun AttemptPreviewHero(
    state: AttemptPreviewHeroState,
    onShareClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        AttemptPreviewHeroMetaSection(state = state)
        AttemptPreviewHeroVideoSection(
            state = state,
            onShareClick = onShareClick
        )
        Spacer(modifier = Modifier.height(18.dp))
    }
}

@Composable
internal fun AttemptPreviewHeroMetaSection(
    state: AttemptPreviewHeroState,
    selectedAttempt: Int = state.selectedAttempt,
    attemptSuccessStates: List<Boolean> = emptyList(),
    onAttemptSelected: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val difficultyChipTone = remember(state.difficultyLabel) {
        buildHeaderChipTone(holdColorToUiColor(state.difficultyLabel))
    }
    val holdChipTone = remember(state.holdColorLabel) {
        buildHeaderChipTone(holdColorToUiColor(state.holdColorLabel))
    }

    Column(
        modifier = modifier.padding(horizontal = 22.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = state.gymName.ifBlank { "클라이밍 기록" },
                color = AnalysisText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Row(
                modifier = Modifier.padding(start = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.difficultyLabel.isNotBlank()) {
                    HeaderChip(
                        text = "난이도 ${state.difficultyLabel}",
                        background = difficultyChipTone.background,
                        contentColor = Color.White,
                        borderColor = difficultyChipTone.border,
                        cornerRadius = 7.dp,
                        horizontalPadding = 8.dp,
                        verticalPadding = 3.dp,
                        fontSize = 10.sp
                    )
                }
                if (state.holdColorLabel.isNotBlank()) {
                    HeaderChip(
                        text = "홀드 ${state.holdColorLabel}",
                        background = holdChipTone.background,
                        contentColor = Color.White,
                        borderColor = holdChipTone.border,
                        cornerRadius = 7.dp,
                        horizontalPadding = 8.dp,
                        verticalPadding = 3.dp,
                        fontSize = 10.sp
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = state.displayDate,
                color = AnalysisMuted,
                fontSize = 13.sp
            )

            if (attemptSuccessStates.isNotEmpty()) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    AttemptMetaSelectorRow(
                        selectedAttempt = selectedAttempt,
                        attemptSuccessStates = attemptSuccessStates,
                        onAttemptSelected = onAttemptSelected
                    )
                }
            }
        }
    }
}

@Composable
private fun AttemptMetaSelectorRow(
    selectedAttempt: Int,
    attemptSuccessStates: List<Boolean>,
    onAttemptSelected: ((Int) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier.horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        attemptSuccessStates.forEachIndexed { index, isSuccess ->
            val attemptNo = index + 1
            AttemptMetaSelectorChip(
                attemptNo = attemptNo,
                isSelected = attemptNo == selectedAttempt,
                isSuccess = isSuccess,
                onClick = onAttemptSelected?.let { callback ->
                    { callback(attemptNo) }
                }
            )
        }
    }
}

@Composable
private fun AttemptMetaSelectorChip(
    attemptNo: Int,
    isSelected: Boolean,
    isSuccess: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val chipTone = remember(isSuccess) {
        buildHeaderChipTone(
            if (isSuccess) {
                Color(0xFF39C66D)
            } else {
                Color(0xFFFF6B71)
            }
        )
    }
    val isEnabled = onClick != null && !isSelected

    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = isEnabled, onClick = { onClick?.invoke() })
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        HeaderChip(
            text = "${attemptNo}차",
            background = if (isSelected) chipTone.background else AnalysisCardColor,
            contentColor = if (isSelected) Color.White else AnalysisText.copy(alpha = 0.86f),
            borderColor = chipTone.border,
            cornerRadius = 7.dp,
            horizontalPadding = 8.dp,
            verticalPadding = 3.dp,
            fontSize = 10.sp
        )
    }
}

@Composable
internal fun AttemptPreviewHeroVideoSection(
    state: AttemptPreviewHeroState,
    onShareClick: (() -> Unit)? = null,
    isExpanded: Boolean = true,
    onToggleExpanded: (() -> Unit)? = null,
    viewportHeightOverride: Dp? = null,
    controlAreaHeight: Dp = AttemptPreviewControlHeight,
    onContainerHeightChanged: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
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
                isSelected = index == activeTimelineIndex,
                flagAnchorFractionX = when (point.kind) {
                    com.ddgo.app.domain.model.AnalysisPointKind.CLIMB_END -> 0f
                    else -> 0.5f
                }
            )
        }
    }

    LaunchedEffect(state.selectedAttemptVideoUri) {
        displayedPositionMs = 0L
    }

    LaunchedEffect(state.seekRequestId, state.seekRequestTimeMs) {
        displayedPositionMs = state.seekRequestTimeMs?.coerceAtLeast(0L) ?: displayedPositionMs
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { onContainerHeightChanged(it.height) }
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
                showScrubberTimeLabels = false,
                hiddenLandmarkIndices = AttemptPreviewHiddenFaceLandmarks,
                hiddenPointIndices = AttemptPreviewHiddenFingerTipPoints,
                pointRadiusScale = AttemptPreviewPointRadiusScale,
                topSafeInset = AttemptPreviewTopSafeInset,
                bottomSafeInset = AttemptPreviewBottomSafeInset,
                controlAreaHeight = controlAreaHeight,
                viewportHeightOverride = viewportHeightOverride,
                onDisplayedPositionChanged = { displayedPositionMs = it },
                midOverlayContent = { renderState ->
                    AttemptPhysicsLimbHeatmapAndComOverlay(
                        renderState = renderState,
                        physicsResult = state.physicsResult,
                        showLimbHeatmap = true,
                        showCom = false
                    )
                    renderState.currentOverlayPose?.let { pose ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(6f)
                        ) {
                            PoseOverlay(
                                pose = pose,
                                contentRect = renderState.videoContentRect,
                                modifier = Modifier.fillMaxSize(),
                                lineColor = AttemptPreviewOverlayLineColor,
                                pointColor = AttemptPreviewOverlayPointColor,
                                hiddenLandmarkIndices = AttemptPreviewHiddenFaceLandmarks,
                                hiddenPointIndices = AttemptPreviewHiddenFingerTipPoints,
                                pointRadiusScale = AttemptPreviewPointRadiusScale,
                                showConnections = false,
                                showPoints = true
                            )
                        }
                    }
                },
                overlayContent = { renderState ->
                    AttemptPhysicsLimbHeatmapAndComOverlay(
                        renderState = renderState,
                        physicsResult = state.physicsResult,
                        showLimbHeatmap = false,
                        showCom = true
                    )
                }
            )

            if (onShareClick != null || onToggleExpanded != null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 72.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (onShareClick != null) {
                        HeroActionButton(
                            onClick = onShareClick
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (onToggleExpanded != null) {
                        HeroActionButton(
                            onClick = onToggleExpanded
                        ) {
                            Icon(
                                imageVector = if (isExpanded) {
                                    Icons.Filled.FullscreenExit
                                } else {
                                    Icons.Filled.Fullscreen
                                },
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        } else {
            HoldOverviewPreview(
                bitmap = state.previewBitmap,
                holds = state.previewHolds,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(viewportHeightOverride ?: 242.dp)
            )
        }
    }
}

@Composable
private fun HeroActionButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(Color(0xFF101114).copy(alpha = 0.82f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier.size(20.dp)) {
            content()
        }
    }
}

package com.ddgo.app.feature.climbing.upload.ui.analysis.page

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.ddgo.app.core.ui.components.SafeAreaScreen
import com.ddgo.app.domain.model.AnalysisPoint
import com.ddgo.app.feature.climbing.record.presentation.HeartRatePoint
import com.ddgo.app.feature.climbing.upload.AnalysisBgColor
import com.ddgo.app.feature.climbing.upload.AnalysisCardColor
import com.ddgo.app.feature.climbing.upload.AnalysisGradientButton
import com.ddgo.app.feature.climbing.upload.AnalysisText
import com.ddgo.app.feature.climbing.upload.FinalAnalysisAttemptSummary
import com.ddgo.app.feature.climbing.upload.resolveAnalysisSeekTimeMs
import com.ddgo.app.feature.climbing.upload.ui.analysis.organism.AttemptAnalysisContentSection
import com.ddgo.app.feature.climbing.upload.ui.analysis.organism.AttemptAnalysisTabRow
import com.ddgo.app.feature.climbing.upload.ui.analysis.organism.AttemptAnalysisTimelineRow
import com.ddgo.app.feature.climbing.upload.ui.analysis.organism.AttemptPreviewHeroMetaSection
import com.ddgo.app.feature.climbing.upload.ui.analysis.organism.AttemptPreviewHeroState
import com.ddgo.app.feature.climbing.upload.ui.analysis.organism.AttemptPreviewHeroVideoSection
import kotlin.math.min

internal data class FinalAnalysisPageState(
    val heroState: AttemptPreviewHeroState,
    val selectedAttempt: Int,
    val totalAttempts: Int,
    val currentSummary: FinalAnalysisAttemptSummary,
    val previousSummary: FinalAnalysisAttemptSummary?,
    val analysisStartTimeMs: Long?,
    val heartRateSeries: List<HeartRatePoint>,
    val timelinePoints: List<AnalysisPoint>,
    val reachedHoldsText: String,
    val reachedHoldsSuffix: String?,
    val feedbackLine: String,
    val riskLine: String,
    val coachingLine: String,
    val previousActionText: String?,
    val actionText: String
)

@Composable
internal fun FinalAnalysisPage(
    state: FinalAnalysisPageState,
    onAnalysisPointSelected: (Long) -> Unit,
    onSecondaryAction: (() -> Unit)?,
    onShareAction: (() -> Unit)?,
    onPrimaryAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    var selectedTimelinePointMs by rememberSaveable(state.selectedAttempt) {
        mutableLongStateOf(-1L)
    }
    var selectedTabIndex by rememberSaveable(state.selectedAttempt) {
        mutableIntStateOf(0)
    }
    var expandedPlayerHeightPx by remember(state.selectedAttempt) {
        mutableIntStateOf(0)
    }
    var playerHeightPx by rememberSaveable(state.selectedAttempt) {
        mutableFloatStateOf(0f)
    }

    val collapsedPlayerTargetDp = remember(configuration.screenHeightDp) {
        (configuration.screenHeightDp.dp * 0.25f).coerceIn(220.dp, 300.dp)
    }
    val collapsedPlayerTargetPx = with(density) { collapsedPlayerTargetDp.toPx() }
    val minPlayerHeightPx = remember(expandedPlayerHeightPx, collapsedPlayerTargetPx) {
        if (expandedPlayerHeightPx > 0) {
            min(collapsedPlayerTargetPx, expandedPlayerHeightPx.toFloat())
        } else {
            0f
        }
    }
    val collapseFraction = remember(playerHeightPx, expandedPlayerHeightPx, minPlayerHeightPx) {
        val maxHeightPx = expandedPlayerHeightPx.toFloat()
        val collapseRangePx = (maxHeightPx - minPlayerHeightPx).coerceAtLeast(0f)
        if (maxHeightPx <= 0f || collapseRangePx <= 0f) {
            0f
        } else {
            ((maxHeightPx - playerHeightPx) / collapseRangePx).coerceIn(0f, 1f)
        }
    }
    val controlAreaHeight = lerp(132.dp, 84.dp, collapseFraction)
    val viewportHeightOverride = remember(playerHeightPx, density) {
        if (playerHeightPx > 0f) {
            with(density) { playerHeightPx.toDp() }
        } else {
            null
        }
    }

    LaunchedEffect(expandedPlayerHeightPx, minPlayerHeightPx) {
        if (expandedPlayerHeightPx <= 0) return@LaunchedEffect

        val maxHeightPx = expandedPlayerHeightPx.toFloat()
        playerHeightPx = if (playerHeightPx <= 0f) {
            maxHeightPx
        } else {
            playerHeightPx.coerceIn(minPlayerHeightPx, maxHeightPx)
        }
    }

    val nestedScrollConnection = remember(
        listState,
        expandedPlayerHeightPx,
        minPlayerHeightPx
    ) {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                val maxHeightPx = expandedPlayerHeightPx.toFloat()
                if (maxHeightPx <= 0f) return Offset.Zero

                val deltaY = available.y
                if (deltaY < 0f && playerHeightPx > minPlayerHeightPx) {
                    val previousHeightPx = playerHeightPx
                    playerHeightPx = (playerHeightPx + deltaY).coerceIn(minPlayerHeightPx, maxHeightPx)
                    return Offset(0f, playerHeightPx - previousHeightPx)
                }

                if (
                    deltaY > 0f &&
                    listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset == 0 &&
                    playerHeightPx < maxHeightPx
                ) {
                    val previousHeightPx = playerHeightPx
                    playerHeightPx = (playerHeightPx + deltaY).coerceIn(minPlayerHeightPx, maxHeightPx)
                    return Offset(0f, playerHeightPx - previousHeightPx)
                }

                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                val maxHeightPx = expandedPlayerHeightPx.toFloat()
                if (maxHeightPx <= 0f) return Offset.Zero

                val deltaY = available.y
                if (
                    deltaY > 0f &&
                    listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset == 0 &&
                    playerHeightPx < maxHeightPx
                ) {
                    val previousHeightPx = playerHeightPx
                    playerHeightPx = (playerHeightPx + deltaY).coerceIn(minPlayerHeightPx, maxHeightPx)
                    return Offset(0f, playerHeightPx - previousHeightPx)
                }

                return Offset.Zero
            }
        }
    }

    SafeAreaScreen(
        modifier = modifier,
        containerColor = AnalysisBgColor
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AttemptPreviewHeroVideoSection(
                state = state.heroState,
                onShareClick = onShareAction,
                viewportHeightOverride = viewportHeightOverride,
                controlAreaHeight = controlAreaHeight,
                onContainerHeightChanged = { measuredHeightPx ->
                    if (measuredHeightPx > expandedPlayerHeightPx) {
                        expandedPlayerHeightPx = measuredHeightPx
                    }
                    if (playerHeightPx <= 0f) {
                        playerHeightPx = measuredHeightPx.toFloat()
                    }
                }
            )

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .nestedScroll(nestedScrollConnection),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                stickyHeader {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AnalysisBgColor)
                            .padding(top = 10.dp)
                    ) {
                        AttemptAnalysisTabRow(
                            selectedTabIndex = selectedTabIndex,
                            onTabSelected = { selectedTabIndex = it },
                            modifier = Modifier.padding(horizontal = 22.dp)
                        )
                    }
                }
                item {
                    AttemptPreviewHeroMetaSection(
                        state = state.heroState,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                item {
                    AttemptAnalysisTimelineRow(
                        points = state.timelinePoints,
                        selectedTimeMs = selectedTimelinePointMs.takeIf { it >= 0L },
                        modifier = Modifier.padding(top = 8.dp),
                        onPointSelected = { point ->
                            selectedTimelinePointMs = point.timeMs
                            onAnalysisPointSelected(
                                resolveAnalysisSeekTimeMs(
                                    point = point,
                                    usesPoseDetectorTimeline = state.heroState.usesPoseTimeline
                                )
                            )
                        }
                    )
                }
                item {
                    AttemptAnalysisContentSection(
                        currentSummary = state.currentSummary,
                        previousSummary = state.previousSummary,
                        analysisStartTimeMs = state.analysisStartTimeMs,
                        heartRateSeries = state.heartRateSeries,
                        reachedHoldsText = state.reachedHoldsText,
                        reachedHoldsSuffix = state.reachedHoldsSuffix,
                        isSuccess = state.currentSummary.isSuccess,
                        feedbackLine = state.feedbackLine,
                        riskLine = state.riskLine,
                        coachingLine = state.coachingLine,
                        selectedTabIndex = selectedTabIndex,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp)
                    )
                }
                item {
                    if (state.previousActionText != null && onSecondaryAction != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 22.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AnalysisSecondaryButton(
                                text = state.previousActionText,
                                onClick = onSecondaryAction,
                                modifier = Modifier.fillMaxWidth()
                            )
                            AnalysisGradientButton(
                                text = state.actionText,
                                onClick = onPrimaryAction,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        AnalysisGradientButton(
                            text = state.actionText,
                            onClick = onPrimaryAction,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 22.dp)
                        )
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun AnalysisSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(AnalysisCardColor)
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = AnalysisText,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

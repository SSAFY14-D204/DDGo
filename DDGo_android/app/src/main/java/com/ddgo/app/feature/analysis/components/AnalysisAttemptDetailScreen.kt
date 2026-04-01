package com.ddgo.app.feature.analysis.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.ddgo.app.core.ui.tokens.DdgoColorTokens
import com.ddgo.app.feature.analysis.AnalysisStrings
import com.ddgo.app.feature.analysis.model.AnalysisAttemptDetailUiModel
import com.ddgo.app.feature.analysis.model.AnalysisAttemptStabilityGraphUiModel
import com.ddgo.app.feature.analysis.model.AnalysisBadgeTone
import com.ddgo.app.feature.analysis.model.AnalysisCoachCardUiModel
import com.ddgo.app.feature.analysis.model.AnalysisTimelineItemUiModel
import com.ddgo.app.feature.analysis.style.AnalysisPalette
import com.ddgo.app.feature.climbing.record.presentation.HeartRatePoint
import com.ddgo.app.feature.climbing.upload.AnalysisCardColor
import com.ddgo.app.feature.climbing.upload.AnalysisFailure
import com.ddgo.app.feature.climbing.upload.AnalysisMuted
import com.ddgo.app.feature.climbing.upload.AnalysisPanelColor
import com.ddgo.app.feature.climbing.upload.AnalysisPrimary
import com.ddgo.app.feature.climbing.upload.AnalysisSuccess
import com.ddgo.app.feature.climbing.upload.ui.analysis.molecule.HeaderChip
import com.ddgo.app.feature.climbing.upload.ui.analysis.molecule.StabilityInsightTimelineChart
import com.ddgo.app.feature.climbing.upload.ui.analysis.organism.AttemptPreviewHeroState
import com.ddgo.app.feature.climbing.upload.ui.analysis.organism.AttemptPreviewHeroVideoSection
import com.ddgo.app.feature.main.MainChromeDefaults
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
internal fun AnalysisAttemptDetailScreen(
    detail: AnalysisAttemptDetailUiModel,
    onBack: () -> Unit
) {
    val videoUrl = detail.videoUrl?.takeIf { it.isNotBlank() }
    val stickyVideoUrl: String? = null
    val backLabel = "돌아가기"
    val listState = rememberLazyListState()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    var expandedPlayerHeightPx by remember(videoUrl) { mutableIntStateOf(0) }
    var playerHeightPx by rememberSaveable(videoUrl) { mutableFloatStateOf(0f) }

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
    val heroVideoState = remember(videoUrl, detail.resultBadge.tone) {
        AttemptPreviewHeroState(
            gymName = "",
            displayDate = "",
            difficultyLabel = "",
            holdColorLabel = "",
            selectedAttempt = 0,
            isSuccess = detail.resultBadge.tone == AnalysisBadgeTone.Success,
            previewBitmap = null,
            previewHolds = emptyList(),
            selectedAttemptVideoUri = videoUrl
        )
    }

    LaunchedEffect(videoUrl, expandedPlayerHeightPx, minPlayerHeightPx) {
        if (videoUrl == null || expandedPlayerHeightPx <= 0) return@LaunchedEffect

        val maxHeightPx = expandedPlayerHeightPx.toFloat()
        playerHeightPx = if (playerHeightPx <= 0f) {
            maxHeightPx
        } else {
            playerHeightPx.coerceIn(minPlayerHeightPx, maxHeightPx)
        }
    }

    val nestedScrollConnection = remember(
        videoUrl,
        listState,
        expandedPlayerHeightPx,
        minPlayerHeightPx
    ) {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (videoUrl == null) return Offset.Zero

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
                if (videoUrl == null) return Offset.Zero

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AnalysisPalette.BackgroundTop)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            stickyVideoUrl?.let {
                Box(modifier = Modifier.fillMaxWidth()) {
                    AttemptPreviewHeroVideoSection(
                        state = heroVideoState,
                        viewportHeightOverride = viewportHeightOverride,
                        controlAreaHeight = controlAreaHeight,
                        onContainerHeightChanged = { measuredHeightPx ->
                            if (measuredHeightPx > expandedPlayerHeightPx) {
                                expandedPlayerHeightPx = measuredHeightPx
                            }
                            if (playerHeightPx <= 0f) {
                                playerHeightPx = measuredHeightPx.toFloat()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp)
                    ) {
                        AnalysisBackChip(
                            label = backLabel,
                            onClick = onBack,
                            compact = true
                        )
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .nestedScroll(nestedScrollConnection),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Column(
                        modifier = Modifier.padding(
                            start = 20.dp,
                            end = 20.dp,
                            top = 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AnalysisBackChip(
                            label = "돌아가기",
                            onClick = onBack,
                            compact = true
                        )

                        videoUrl?.let {
                            AnalysisChallengeStyleVideoCard(
                                attemptTitle = detail.title,
                                videoUrl = it,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                item {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        AttemptResultAndScoreSection(detail = detail)
                        detail.stabilityGraph?.let { graph ->
                            AttemptStabilityGraphPanel(graph = graph)
                        }
                        AttemptTimelineSection(items = detail.timelineItems)
                        AttemptCoachSection(cards = detail.coachCards)
                    }
                }

                item {
                    Box(modifier = Modifier.height(MainChromeDefaults.ContentBottomPadding + 28.dp))
                }
            }
        }
    }
}

@Composable
private fun AnalysisChallengeStyleVideoCard(
    attemptTitle: String,
    videoUrl: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    var isFullscreen by remember(videoUrl) { mutableStateOf(false) }
    val player = remember(videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = false
            repeatMode = Player.REPEAT_MODE_OFF
            setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)))
            prepare()
        }
    }
    var isPlaying by remember(videoUrl) { mutableStateOf(false) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    isPlaying = false
                    player.seekTo(0L)
                    player.pause()
                }
            }

            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                isPlaying = isPlayingNow
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    if (isFullscreen) {
        Dialog(
            onDismissRequest = { isFullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { viewContext ->
                        PlayerView(viewContext).apply {
                            this.player = player
                            useController = true
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            setShutterBackgroundColor(android.graphics.Color.BLACK)
                            setBackgroundColor(android.graphics.Color.BLACK)
                        }
                    },
                    update = { playerView ->
                        playerView.player = player
                        playerView.useController = true
                        playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        playerView.setBackgroundColor(android.graphics.Color.BLACK)
                    }
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(16.dp)
                        .size(40.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.52f),
                            shape = CircleShape
                        )
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) { isFullscreen = false },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "전체화면 닫기",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }

    Box(
        modifier = modifier
            .height(232.dp)
            .background(
                color = Color(0xFF1F2026),
                shape = RoundedCornerShape(22.dp)
            )
    ) {
        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    this.player = player
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                }
            },
            update = { playerView ->
                playerView.player = player
                playerView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        )

        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color(0x14000000), shape = RoundedCornerShape(22.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) {
                    if (player.isPlaying) {
                        player.pause()
                    } else {
                        player.play()
                    }
                }
        )

        HeaderChip(
            text = attemptTitle,
            background = Color(0xCC101114),
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(14.dp)
        )

        if (!isPlaying) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(56.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.42f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "영상 재생",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(14.dp)
                .size(40.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.42f),
                    shape = CircleShape
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) { isFullscreen = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.OpenInFull,
                contentDescription = "영상 전체화면",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun AttemptHeroCard(
    detail: AnalysisAttemptDetailUiModel
) {
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(30.dp),
        shadowElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = AnalysisPalette.HeroStart
                )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = detail.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = AnalysisPalette.OnAccent
                    )
                    AnalysisBadge(badge = detail.resultBadge)
                }

                Text(
                    text = detail.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = AnalysisPalette.OnAccent.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun AttemptResultAndScoreSection(
    detail: AnalysisAttemptDetailUiModel
) {
    val resultAccentColor =
        if (detail.resultBadge.tone == AnalysisBadgeTone.Success) AnalysisPrimary else AnalysisFailure
    val overallScoreAccentColor = resultAccentColor
    AnalysisCardSurface {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AttemptHeroCard(detail = detail)

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = AnalysisPalette.SurfaceMuted
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "종합 점수",
                                color = AnalysisPalette.TextPrimary,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                            Text(
                                text = detail.overallMovementScore?.let { "${it}점" } ?: "-",
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontSize = 34.sp,
                                    lineHeight = 40.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = overallScoreAccentColor
                            )
                        }
                        AttemptOverallScoreRing(
                            score = detail.overallMovementScore,
                            accentColor = overallScoreAccentColor
                        )
                    }

                    DividerLine()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AttemptInlineMetric(
                            label = "문제 풀이 여부",
                            value = detail.attemptResultLabel,
                            accentColor = resultAccentColor,
                            modifier = Modifier.weight(1f)
                        )
                        AttemptInlineDivider()
                        AttemptInlineMetric(
                            label = "도달 홀드",
                            value = detail.reachedHoldLabel,
                            trailingValue = detail.reachedHoldSuffix,
                            accentColor = resultAccentColor,
                            modifier = Modifier.weight(1f)
                        )
                        AttemptInlineDivider()
                        AttemptInlineMetric(
                            label = "대표 크럭스",
                            value = detail.cruxHoldLabel,
                            accentColor = AnalysisPalette.TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            DividerLine()

            AnalysisSectionTitle(title = "항목별 점수")

            AttemptDetailScoreRow(
                label = "안정성 유지",
                progress = detail.stabilityScore,
                valueLabel = "${(detail.stabilityScore.coerceIn(0f, 1f) * 100f).toInt()}점"
            )
            AttemptDetailScoreRow(
                label = "안정성 회복력",
                progress = detail.recoveryScore,
                valueLabel = detail.recoveryValueLabel
            )
            AttemptDetailScoreRow(
                label = "하체 주도성",
                progress = detail.lowerBodyDriveScore,
                valueLabel = detail.lowerBodyDriveValueLabel
            )

            DividerLine()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "부담 집중 부위",
                    color = AnalysisPalette.TextPrimary,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Text(
                    text = detail.loadFocusLabel,
                    color = AnalysisPalette.Danger,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }
    }
}

@Composable
private fun AttemptOverallScoreRing(
    score: Int?,
    accentColor: Color
) {
    val safeScore = score?.coerceIn(0, 100)

    Box(
        modifier = Modifier.size(100.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = { (safeScore ?: 0) / 100f },
            modifier = Modifier.size(92.dp),
            color = accentColor,
            trackColor = AnalysisCardColor,
            strokeWidth = 8.dp
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = safeScore?.toString() ?: "--",
                color = accentColor,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 28.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = "100점 만점",
                color = AnalysisPalette.TextHint,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun AttemptDetailScoreRow(
    label: String,
    progress: Float,
    valueLabel: String
) {
    val percentScore = (progress.coerceIn(0f, 1f) * 100f).toInt()
    val accentColor = scoreColor(percentScore)

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = AnalysisPalette.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = valueLabel,
                color = AnalysisPalette.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(
                    color = AnalysisPalette.Border,
                    shape = RoundedCornerShape(999.dp)
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(
                        color = accentColor,
                        shape = RoundedCornerShape(999.dp)
                    )
            )
        }
    }
}

@Composable
private fun AttemptStabilityGraphSection(
    graph: AnalysisAttemptStabilityGraphUiModel
) {
    AnalysisCardSurface {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AnalysisSectionTitle(title = "안정성 그래프")
            StabilityInsightTimelineChart(
                data = graph.stabilityTimeline,
                durationMs = graph.durationMs,
                dangerFractions = graph.dangerFractions,
                cruxStartFraction = graph.cruxStartFraction,
                cruxEndFraction = graph.cruxEndFraction,
                failureFraction = graph.failureFraction,
                heartRateSeries = graph.heartRateSeries,
                chartSurfaceColor = AnalysisPalette.SurfaceMuted,
                chartGridColor = AnalysisPalette.Border.copy(alpha = 0.9f),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun AttemptStabilityGraphContainer(
    graph: AnalysisAttemptStabilityGraphUiModel?
) {
    if (graph == null) {
        AnalysisCardSurface {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AnalysisSectionTitle(
                    title = "안정성 · 심박 그래프",
                    subtitle = "이 시도에는 아직 저장된 시계열 데이터가 없어요."
                )

                Surface(
                    color = AnalysisPalette.SurfaceMuted,
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "안정성 그래프와 심박 그래프는 시도 종료 시 저장된 시계열 데이터가 있을 때 표시됩니다.",
                            color = AnalysisPalette.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 21.sp
                        )
                        Text(
                            text = "기존 시도이거나 서버에 아직 timeline 데이터가 없는 경우에는 이 영역이 비어 있을 수 있어요.",
                            color = AnalysisPalette.TextSecondary,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                    }
                }
            }
        }
        return
    }

    AttemptStabilityGraphPanel(graph = graph)
}

@Composable
private fun AttemptStabilityGraphPanel(
    graph: AnalysisAttemptStabilityGraphUiModel
) {
    AnalysisCardSurface {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AnalysisSectionTitle(title = "안정성 그래프")

            StabilityInsightTimelineChart(
                data = graph.stabilityTimeline,
                durationMs = graph.durationMs,
                dangerFractions = graph.dangerFractions,
                cruxStartFraction = null,
                cruxEndFraction = null,
                failureFraction = graph.failureFraction,
                heartRateSeries = graph.heartRateSeries,
                chartSurfaceColor = AnalysisPalette.SurfaceMuted,
                chartGridColor = AnalysisPalette.Border.copy(alpha = 0.9f),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun AttemptTimelineSection(
    items: List<AnalysisTimelineItemUiModel>
) {
    AnalysisCardSurface {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AnalysisSectionTitle(title = "핵심 흐름")

            items.forEachIndexed { index, item ->
                AttemptTimelineRow(
                    step = index + 1,
                    item = item
                )
                if (index < items.lastIndex) {
                    DividerLine()
                }
            }
        }
    }
}

@Composable
private fun AttemptTimelineRow(
    step: Int,
    item: AnalysisTimelineItemUiModel
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "$step.",
            color = DdgoColorTokens.BrandBlue,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Bold
            )
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = item.title,
                color = AnalysisPalette.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = item.description,
                color = AnalysisPalette.TextSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
        }
    }
}

@Composable
private fun AttemptCoachSection(
    cards: List<AnalysisCoachCardUiModel>
) {
    AnalysisCardSurface {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AnalysisSectionTitle(title = "이번 시도 핵심")

            cards.forEachIndexed { index, card ->
                AttemptCoachRow(
                    card = card,
                    index = index
                )
                if (index < cards.lastIndex) {
                    DividerLine()
                }
            }
        }
    }
}

@Composable
private fun AttemptCoachRow(
    card: AnalysisCoachCardUiModel,
    index: Int
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier
                .width(4.dp)
                .height(44.dp),
            shape = RoundedCornerShape(999.dp),
            color = coachBarColor(index)
        ) {}

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = card.title,
                color = AnalysisPalette.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = card.body,
                color = AnalysisPalette.TextSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AttemptInlineMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    trailingValue: String? = null,
    accentColor: Color = AnalysisPalette.TextPrimary
) {
    val primaryValueFontSize = when {
        value.length >= 7 -> 18.sp
        value.length >= 5 || value.contains(" ") -> 20.sp
        trailingValue != null -> 22.sp
        else -> 24.sp
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = label,
            color = AnalysisPalette.TextSecondary,
            fontSize = 12.sp
        )

        if (trailingValue != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = value,
                    color = accentColor,
                    fontSize = primaryValueFontSize,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 28.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip
                )
                Text(
                    text = trailingValue,
                    color = AnalysisPalette.TextHint,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 18.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip
                )
            }
        } else {
            Text(
                text = value,
                color = accentColor,
                fontSize = primaryValueFontSize,
                fontWeight = FontWeight.Bold,
                lineHeight = 28.sp,
                maxLines = 2,
                softWrap = true,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AttemptInlineDivider() {
    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(1.dp),
        color = AnalysisPalette.Border
    ) {}
}

@Composable
private fun DividerLine() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp),
        color = AnalysisPalette.Border
    ) {}
}

private fun toneColor(tone: AnalysisBadgeTone): Color =
    when (tone) {
        AnalysisBadgeTone.Accent -> AnalysisPrimary
        AnalysisBadgeTone.Success -> AnalysisSuccess
        AnalysisBadgeTone.Danger -> AnalysisSuccess
        AnalysisBadgeTone.Warning -> Color(0xFFFFC857)
        AnalysisBadgeTone.Neutral -> AnalysisMuted
    }

private fun scoreColor(score: Int?): Color =
    when {
        score == null -> AnalysisMuted
        score >= 85 -> AnalysisPalette.Success
        score >= 70 -> DdgoColorTokens.BrandBlue
        score >= 55 -> AnalysisPalette.WarningBright
        else -> AnalysisPalette.Danger
    }

private fun coachBarColor(index: Int): Color =
    when (index % 3) {
        0 -> Color(0xFFFFC857)
        1 -> AnalysisPalette.Danger
        else -> DdgoColorTokens.BrandBlue
    }

private fun timelineStepColor(@Suppress("UNUSED_PARAMETER") step: Int): Color =
    DdgoColorTokens.BrandBlue

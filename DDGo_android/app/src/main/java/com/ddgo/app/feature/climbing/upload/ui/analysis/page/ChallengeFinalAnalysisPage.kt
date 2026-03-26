package com.ddgo.app.feature.climbing.upload.ui.analysis.page

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddgo.app.core.ui.components.SafeAreaScreen
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.ddgo.app.domain.model.Hold
import com.ddgo.app.feature.climbing.upload.AnalysisBgColor
import com.ddgo.app.feature.climbing.upload.AnalysisGradientButton
import com.ddgo.app.feature.climbing.upload.AnalysisMuted
import com.ddgo.app.feature.climbing.upload.AnalysisText
import com.ddgo.app.feature.climbing.upload.ChallengeFinalAnalysisSummary
import com.ddgo.app.feature.climbing.upload.HoldOverviewPreview
import com.ddgo.app.feature.climbing.upload.holdColorToUiColor
import com.ddgo.app.feature.climbing.upload.ui.analysis.molecule.HeaderChip
import com.ddgo.app.feature.climbing.upload.ui.analysis.molecule.buildHeaderChipTone
import com.ddgo.app.feature.climbing.upload.ui.analysis.organism.ChallengeAnalysisContentSection

internal data class ChallengePreviewHeroState(
    val gymName: String,
    val displayDate: String,
    val difficultyLabel: String,
    val holdColorLabel: String,
    val attemptCount: Int,
    val overallSuccess: Boolean,
    val successAttemptCount: Int,
    val previewBitmap: Bitmap?,
    val previewHolds: List<Hold>,
    val attemptVideoUris: List<String>
)

internal data class ChallengeFinalAnalysisPageState(
    val heroState: ChallengePreviewHeroState,
    val summary: ChallengeFinalAnalysisSummary
)

@Composable
internal fun ChallengeFinalAnalysisPage(
    state: ChallengeFinalAnalysisPageState,
    onNavigateBack: () -> Unit,
    onPrimaryAction: () -> Unit,
    onAttemptVideoShare: ((attemptNo: Int) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    SafeAreaScreen(
        modifier = modifier,
        containerColor = AnalysisBgColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            ChallengeFinalAnalysisTopBar(
                onNavigateBack = onNavigateBack
            )

            ChallengePreviewHero(
                state = state.heroState,
                onAttemptVideoShare = onAttemptVideoShare,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            ChallengeAnalysisContentSection(
                summary = state.summary,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            AnalysisGradientButton(
                text = "홈으로 이동",
                onClick = onPrimaryAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ChallengeFinalAnalysisTopBar(
    onNavigateBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .align(Alignment.CenterStart)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onNavigateBack
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "뒤로 가기",
                tint = Color.White
            )
        }

        Text(
            text = "챌린지 종합 분석",
            modifier = Modifier.align(Alignment.Center),
            color = AnalysisText,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )

    }
}

@Composable
private fun ChallengePreviewHero(
    state: ChallengePreviewHeroState,
    onAttemptVideoShare: ((attemptNo: Int) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val difficultyChipTone = remember(state.difficultyLabel) {
        buildHeaderChipTone(holdColorToUiColor(state.difficultyLabel))
    }
    val holdChipTone = remember(state.holdColorLabel) {
        buildHeaderChipTone(holdColorToUiColor(state.holdColorLabel))
    }
    val statusChipTone = remember(state.overallSuccess) {
        buildHeaderChipTone(
            if (state.overallSuccess) Color(0xFF39C66D) else Color(0xFFFF5E63)
        )
    }
    val challengeHeadline = when {
        state.overallSuccess && state.successAttemptCount > 0 ->
            "${state.attemptCount}번의 시도 끝에 완등에 성공했어요"
        else -> "${state.attemptCount}번의 시도에서 끝까지 도전했어요"
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
                    text = state.gymName.ifBlank { "챌린지 종합 분석" },
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
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HeaderChip(
                        text = if (state.overallSuccess) "완등 성공" else "미완등",
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

            HoldOverviewPreview(
                bitmap = state.previewBitmap,
                holds = state.previewHolds,
                modifier = Modifier.size(width = 116.dp, height = 96.dp)
            )
        }

        if (state.attemptVideoUris.isNotEmpty()) {
            ChallengeAttemptVideoCarousel(
                videoUris = state.attemptVideoUris,
                onAttemptVideoShare = onAttemptVideoShare,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF101114))
            ) {
                HoldOverviewPreview(
                    bitmap = state.previewBitmap,
                    holds = state.previewHolds,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(232.dp)
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "챌린지 분석 결과",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = challengeHeadline,
                        color = Color.White.copy(alpha = 0.88f),
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ChallengeAttemptVideoCarousel(
    videoUris: List<String>,
    onAttemptVideoShare: ((attemptNo: Int) -> Unit)?,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val itemWidth = maxWidth - 60.dp
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(
                items = videoUris,
                key = { index, uri -> "$index-$uri" }
            ) { index, videoUri ->
                ChallengeAttemptVideoCard(
                    attemptNo = index + 1,
                    videoUri = videoUri,
                    headline = "${index + 1}차 시도",
                    onShareClick = onAttemptVideoShare?.let { share ->
                        { share(index + 1) }
                    },
                    modifier = Modifier.width(itemWidth)
                )
            }
        }
    }
}

@Composable
private fun ChallengeAttemptVideoCard(
    attemptNo: Int,
    videoUri: String,
    headline: String,
    onShareClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val player = remember(videoUri) {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = false
            repeatMode = Player.REPEAT_MODE_OFF
            setMediaItem(MediaItem.fromUri(Uri.parse(videoUri)))
            prepare()
        }
    }
    var isPlaying by remember(videoUri) { mutableStateOf(false) }
    var durationMs by remember(videoUri) { mutableLongStateOf(0L) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                durationMs = player.duration.coerceAtLeast(0L)
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

    Box(
        modifier = modifier
            .height(232.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF1A1B21),
                        Color(0xFF262831),
                        Color(0xFF1A1B21)
                    )
                )
            )
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                PlayerView(context).apply {
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
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            Color(0xCC101114)
                        )
                    )
                )
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
            text = headline,
            background = Color(0xCC101114),
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(14.dp)
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "시도 영상",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (durationMs > 0L) {
                    "${attemptNo}차 시도 영상을 확인해 보세요"
                } else {
                    "${attemptNo}차 시도 영상을 불러오는 중이에요"
                },
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 13.sp
            )
        }

        if (!isPlaying) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.42f)),
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

        if (onShareClick != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 14.dp, bottom = 14.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xCC101114))
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onShareClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = "${attemptNo}차 시도 영상 공유",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

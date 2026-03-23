package com.ddgo.app.feature.climbing.upload.ui.analysis.organism

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.ddgo.app.domain.model.Hold
import com.ddgo.app.feature.climbing.upload.AnalysisMuted
import com.ddgo.app.feature.climbing.upload.AnalysisText
import com.ddgo.app.feature.climbing.upload.HoldOverviewPreview
import com.ddgo.app.feature.climbing.upload.holdColorToUiColor
import com.ddgo.app.feature.climbing.upload.ui.analysis.molecule.HeaderChip

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
    val selectedAttemptVideoUri: String? = null,
    val seekRequestId: Long = 0L,
    val seekRequestTimeMs: Long? = null
)

@Composable
internal fun AttemptPreviewHero(
    state: AttemptPreviewHeroState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val holdChipBackground = holdColorToUiColor(state.holdColorLabel)
    val holdChipIsBright =
        (holdChipBackground.red + holdChipBackground.green + holdChipBackground.blue) / 3f > 0.7f
    val exoPlayer = remember(context, state.selectedAttemptVideoUri) {
        state.selectedAttemptVideoUri?.let { videoUri ->
            ExoPlayer.Builder(context).build().apply {
                playWhenReady = false
                repeatMode = Player.REPEAT_MODE_OFF
                setMediaItem(MediaItem.fromUri(Uri.parse(videoUri)))
                prepare()
            }
        }
    }

    LaunchedEffect(exoPlayer, state.seekRequestId, state.seekRequestTimeMs) {
        val seekTimeMs = state.seekRequestTimeMs ?: return@LaunchedEffect
        exoPlayer?.seekTo(seekTimeMs.coerceAtLeast(0L))
        exoPlayer?.play()
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer?.release()
        }
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
                    text = state.gymName.ifBlank { "암장 정보 없음" },
                    color = AnalysisText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = state.displayDate,
                    color = AnalysisMuted,
                    fontSize = 13.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (state.difficultyLabel.isNotBlank()) {
                        HeaderChip(
                            text = state.difficultyLabel,
                            background = Color.White,
                            contentColor = Color.Black
                        )
                    }
                    if (state.holdColorLabel.isNotBlank()) {
                        HeaderChip(
                            text = state.holdColorLabel,
                            background = holdChipBackground,
                            contentColor = if (holdChipIsBright) Color.Black else Color.White
                        )
                    }
                    state.analysisModeLabel?.let { modeLabel ->
                        HeaderChip(
                            text = modeLabel,
                            background = Color(0xFF2C3E50),
                            contentColor = Color.White
                        )
                    }
                    state.fallbackLabel?.let { fallbackLabel ->
                        HeaderChip(
                            text = fallbackLabel,
                            background = Color(0xFF5C2B35),
                            contentColor = Color.White
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

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF101114))
        ) {
            if (exoPlayer != null) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(242.dp)
                        .clickable {
                            if (exoPlayer.isPlaying) {
                                exoPlayer.pause()
                            } else {
                                exoPlayer.play()
                            }
                        },
                    factory = { viewContext ->
                        PlayerView(viewContext).apply {
                            player = exoPlayer
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            setShutterBackgroundColor(android.graphics.Color.BLACK)
                        }
                    },
                    update = { playerView ->
                        playerView.player = exoPlayer
                    }
                )
            } else {
                HoldOverviewPreview(
                    bitmap = state.previewBitmap,
                    holds = state.previewHolds,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(242.dp)
                )
            }

            Text(
                text = "${state.selectedAttempt}차 시도 ${if (state.isSuccess) "성공" else "실패"}",
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(18.dp))
    }
}

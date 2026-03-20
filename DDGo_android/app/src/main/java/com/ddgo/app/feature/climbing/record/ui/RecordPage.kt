package com.ddgo.app.feature.climbing.record.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddgo.app.feature.climbing.record.presentation.RecordUiState

@Composable
fun RecordPage(
    uiState: RecordUiState,
    previewContent: @Composable BoxScope.() -> Unit,
    onNavigateBack: () -> Unit,
    onRequestPermission: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onRetryLivePose: () -> Unit,
    onClearDraft: () -> Unit
) {
    val background = Brush.verticalGradient(
        listOf(
            Color(0xFF07111E),
            Color(0xFF0D1B2A),
            Color(0xFF111827)
        )
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowBack,
                        contentDescription = "back",
                        tint = Color.White
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Record",
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Record while analyzing MediaPipe Pose live.",
                        color = Color(0xFFB8C4D9),
                        fontSize = 13.sp
                    )
                }
            }

            if (uiState.cameraErrorMessage != null) {
                AssistChip(
                    onClick = onRequestPermission,
                    label = { Text(text = uiState.cameraErrorMessage.orEmpty(), color = Color.White) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.CloudOff,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                )
            }

            RecordCameraSection(
                previewContent = previewContent,
                overlayContent = {
                    LivePoseOverlay(
                        modifier = Modifier.fillMaxSize(),
                        poseFrame = uiState.latestPoseFrame
                    )
                }
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                AssistChip(
                    onClick = { },
                    label = {
                        Text(
                            text = if (uiState.hasCameraPermission) "camera granted" else "camera denied",
                            color = Color.White
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.CameraAlt,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                )
                AssistChip(
                    onClick = { },
                    label = {
                        Text(
                            text = if (uiState.isLivePoseAnalyzerRunning) "live pose on" else "live pose off",
                            color = Color.White
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.RadioButtonChecked,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                )
                AssistChip(
                    onClick = { },
                    label = {
                        Text(
                            text = if (uiState.isRecording) "recording" else "idle",
                            color = Color.White
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                )
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF101A2B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "session",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = uiState.statusMessage,
                        color = Color(0xFFCCD7EA),
                        fontSize = 14.sp
                    )

                    uiState.livePoseSummary?.let { summary ->
                        Text(
                            text = "submitted=${summary.submittedFrameCount}, detected=${summary.detectedFrameCount}, uploaded=${uiState.uploadedPoseFrameCount}",
                            color = Color(0xFF8EE3C4),
                            fontSize = 13.sp
                        )
                    }

                    uiState.recordedDraft?.let { draft ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "recorded file",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = draft.videoUri,
                                color = Color(0xFFB8C4D9),
                                fontSize = 12.sp
                            )
                            draft.thumbnailFrame?.let { thumbnail ->
                                Text(
                                    text = "thumbnail frame #${thumbnail.frameIndex} / ${thumbnail.timestampMs}ms",
                                    color = Color(0xFFB8C4D9),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (uiState.isRecording) {
                    Button(
                        onClick = onStopRecording,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = "stop")
                    }
                } else {
                    Button(
                        onClick = onStartRecording,
                        enabled = uiState.canStartRecording,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = "record")
                    }
                }

                OutlinedButton(
                    onClick = onRetryLivePose,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "retry pose")
                }
            }

            if (uiState.recordedDraft != null) {
                OutlinedButton(
                    onClick = onClearDraft,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "new recording")
                }
            }

            uiState.livePoseErrorMessage?.let { message ->
                Text(
                    text = message,
                    color = Color(0xFFFFB4A9),
                    fontSize = 12.sp
                )
            }
        }
    }
}

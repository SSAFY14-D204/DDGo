package com.ddgo.app.feature.upload

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ddgo.app.core.ui.components.DdgoPrimaryButton

/**
 * 영상 업로드 & 분석 화면.
 *
 * ── Compose 학습 포인트 ───────────────────────────────────────────
 * 1. rememberLauncherForActivityResult: 갤러리 접근 등 Activity Result API 처리
 * 2. AnimatedContent: 상태에 따라 UI가 부드럽게 전환
 * 3. Modifier 체이닝: clip, border, background를 함께 사용하는 패턴
 * ────────────────────────────────────────────────────────────────
 */
@Composable
fun UploadScreen(
    onAnalyzeDone: () -> Unit,
    viewModel: UploadViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedVideoUri by viewModel.selectedVideoUri.collectAsState()

    // LaunchedEffect: uiState가 Done이면 화면 이동
    LaunchedEffect(uiState) {
        if (uiState is UploadUiState.Done) onAnalyzeDone()
    }

    // 갤러리에서 영상 파일을 선택하는 런처
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.selectVideo(it) }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "영상 분석",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            // 영상 선택 영역 (점선 박스)
            VideoPickerBox(
                selectedUri = selectedVideoUri,
                onClick = { videoPickerLauncher.launch("video/*") }
            )

            // 분석 시작 버튼 (영상 선택 후 활성화)
            AnimatedContent(
                targetState = uiState,
                label = "upload_state_animation"
            ) { state ->
                when (state) {
                    is UploadUiState.Analyzing -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "AI가 영상을 분석 중입니다...\n앱을 닫아도 계속 진행됩니다",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                    else -> {
                        DdgoPrimaryButton(
                            text = "🤖 AI 분석 시작",
                            onClick = { viewModel.startAnalyze("V4") },
                            enabled = selectedVideoUri != null
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoPickerBox(
    selectedUri: Uri?,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
                RoundedCornerShape(16.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (selectedUri != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.VideoFile,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "영상 선택됨 ✓",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "탭하여 클라이밍 영상 선택",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

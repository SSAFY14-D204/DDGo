package com.ddgo.app.feature.climbing.upload

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ddgo.app.core.ui.components.SafeAreaScreen
import kotlinx.coroutines.delay

@Composable
fun AnalysisLoadingScreen(
    viewModel: UploadViewModel = hiltViewModel(),
    onLoadingFinished: () -> Unit = {}
) {
    val uploadSubmissionUiState by viewModel.uploadSubmissionUiState.collectAsState()

    LaunchedEffect(Unit) {
        if (uploadSubmissionUiState is UploadSubmissionUiState.Idle) {
            viewModel.submitUpload()
        }
    }

    LaunchedEffect(uploadSubmissionUiState) {
        if (uploadSubmissionUiState is UploadSubmissionUiState.Success) {
            delay(500)
            onLoadingFinished()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val offsetY by infiniteTransition.animateFloat(
        initialValue = -100f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scan_offset"
    )

    SafeAreaScreen(containerColor = Color(0xFF0D0D0D)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "시도 분석",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 24.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.2f))
            )

            Spacer(modifier = Modifier.height(60.dp))

            Text(
                text = when (val state = uploadSubmissionUiState) {
                    is UploadSubmissionUiState.Loading -> state.message
                    is UploadSubmissionUiState.Error -> "업로드 중 오류가 발생했어요"
                    is UploadSubmissionUiState.Success -> "업로드가 완료되었어요"
                    UploadSubmissionUiState.Idle -> "디디고가 자세를 분석하고 있어요"
                },
                color = Color.White,
                fontSize = 24.sp
            )

            if (uploadSubmissionUiState is UploadSubmissionUiState.Error) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = (uploadSubmissionUiState as UploadSubmissionUiState.Error).message,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        viewModel.resetUploadSubmissionState()
                        viewModel.submitUpload()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4A90E2),
                        contentColor = Color.White
                    )
                ) {
                    Text("다시 시도")
                }
            }

            Spacer(modifier = Modifier.height(100.dp))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(250.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .background(Color.DarkGray, RoundedCornerShape(24.dp))
                ) {
                    Text(
                        text = "캐릭터 이미지 자리",
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(8.dp)
                        .offset(y = offsetY.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0xFF42A5F5), Color.Transparent)
                            )
                        )
                )
            }
        }
    }
}

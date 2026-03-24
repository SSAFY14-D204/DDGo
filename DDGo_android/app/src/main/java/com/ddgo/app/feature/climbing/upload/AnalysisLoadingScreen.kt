package com.ddgo.app.feature.climbing.upload

import android.os.SystemClock
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ddgo.app.R
import com.ddgo.app.core.ui.components.SafeAreaScreen
import kotlinx.coroutines.delay

@Composable
fun AnalysisLoadingScreen(
    viewModel: UploadViewModel = hiltViewModel(),
    onLoadingFinished: () -> Unit = {}
) {
    val uploadSubmissionUiState by viewModel.uploadSubmissionUiState.collectAsState()
    val finalAnalysisPreparationUiState by viewModel.finalAnalysisPreparationUiState.collectAsState()
    val phase = viewModel.analysisLoadingPhase
    val screenShownAtMillis = remember(phase) { SystemClock.elapsedRealtime() }
    val ddLoadingBaseSize = 220.dp
    val ddLoadingScale = 1.2f
    val ddLoadingSize = ddLoadingBaseSize * ddLoadingScale
    val scanLightScale = 2.5f
    val scanTravelAmplitude = ddLoadingSize.value * 0.6f
    val phaseTitle = when (phase) {
        AnalysisLoadingPhase.AttemptResultPreparation -> "디디고가 자세를 분석하고 있어요"
        AnalysisLoadingPhase.FinalAnalysisPreparation -> "최종 결과물을 가져오고 있습니다."
    }
    val isSuccess = when (phase) {
        AnalysisLoadingPhase.AttemptResultPreparation ->
            uploadSubmissionUiState is UploadSubmissionUiState.Success
        AnalysisLoadingPhase.FinalAnalysisPreparation ->
            finalAnalysisPreparationUiState is FinalAnalysisPreparationUiState.Success
    }
    val errorMessage = when (phase) {
        AnalysisLoadingPhase.AttemptResultPreparation ->
            (uploadSubmissionUiState as? UploadSubmissionUiState.Error)?.message
        AnalysisLoadingPhase.FinalAnalysisPreparation ->
            (finalAnalysisPreparationUiState as? FinalAnalysisPreparationUiState.Error)?.message
    }
    val tracePhaseName = when (phase) {
        AnalysisLoadingPhase.AttemptResultPreparation -> "AttemptResultPreparation"
        AnalysisLoadingPhase.FinalAnalysisPreparation -> "FinalAnalysisPreparation"
    }

    LaunchedEffect(phase) {
        UploadAiTraceLogger.log(
            event = "ANALYSIS_LOADING_ENTER",
            phase = tracePhaseName,
            details = mapOf("title" to phaseTitle)
        )
    }

    LaunchedEffect(phase, uploadSubmissionUiState, finalAnalysisPreparationUiState) {
        val shouldStart = when (phase) {
            AnalysisLoadingPhase.AttemptResultPreparation ->
                uploadSubmissionUiState is UploadSubmissionUiState.Idle
            AnalysisLoadingPhase.FinalAnalysisPreparation ->
                finalAnalysisPreparationUiState is FinalAnalysisPreparationUiState.Idle
        }
        if (shouldStart) {
            UploadAiTraceLogger.log(
                event = "ANALYSIS_LOADING_TRIGGER_SUBMIT",
                phase = tracePhaseName
            )
            viewModel.submitUpload()
        }
    }

    LaunchedEffect(phase, isSuccess) {
        if (isSuccess) {
            val waitMillis = remainingLoadingDisplayMillis(
                startedAtMillis = screenShownAtMillis,
                nowMillis = SystemClock.elapsedRealtime()
            )
            if (waitMillis > 0L) {
                delay(waitMillis)
            }
            UploadAiTraceLogger.log(
                event = when (phase) {
                    AnalysisLoadingPhase.AttemptResultPreparation ->
                        "ANALYSIS_LOADING_FINISH_TO_ATTEMPT_RESULT"
                    AnalysisLoadingPhase.FinalAnalysisPreparation ->
                        "ANALYSIS_LOADING_FINISH_TO_FINAL_ANALYSIS"
                },
                phase = tracePhaseName,
                status = "success",
                elapsedMs = SystemClock.elapsedRealtime() - screenShownAtMillis
            )
            onLoadingFinished()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val offsetY by infiniteTransition.animateFloat(
        initialValue = -scanTravelAmplitude,
        targetValue = scanTravelAmplitude,
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
                    .padding(top = 16.dp)
            )

            Spacer(modifier = Modifier.height(60.dp))
            Text(
                text = if (errorMessage != null) {
                    "분석 준비 중 오류가 발생했어요"
                } else {
                    phaseTitle
                },
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = errorMessage,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = { viewModel.retryCurrentAnalysisLoadingPhase() },
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
                modifier = Modifier.size(300.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.dd_loading),
                    contentDescription = "DDGO loading character",
                    modifier = Modifier
                        .size(ddLoadingBaseSize)
                        .align(Alignment.Center)
                        .graphicsLayer {
                            scaleX = ddLoadingScale
                            scaleY = ddLoadingScale
                        }
                )

                Image(
                    painter = painterResource(id = R.drawable.scan_light_loading),
                    contentDescription = "Scan light",
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                        .offset(y = offsetY.dp)
                        .graphicsLayer {
                            scaleX = scanLightScale
                            scaleY = scanLightScale
                        }
                )
            }
        }
    }
}

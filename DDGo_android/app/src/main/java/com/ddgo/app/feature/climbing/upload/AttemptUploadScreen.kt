package com.ddgo.app.feature.climbing.upload

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ddgo.app.core.ui.components.SafeAreaScreen

@Composable
fun AttemptUploadScreen(
    viewModel: UploadViewModel = hiltViewModel(),
    initialRecordedVideoUri: String? = null,
    onNavigateToNext: () -> Unit = {}
) {
    LaunchedEffect(initialRecordedVideoUri) {
        viewModel.beginNewChallengeUploadFlow()
        initialRecordedVideoUri
            ?.takeIf { it.isNotBlank() }
            ?.let { recordedUri ->
                viewModel.updateVideoUri(uri = recordedUri)
                onNavigateToNext()
            }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.updateVideoUri(uri = uri.toString())
            onNavigateToNext()
        }
    }

    SafeAreaScreen(containerColor = Color.Black) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 23.dp, end = 23.dp, top = 78.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "분석하고 싶은 문제의\n첫번째 시도 영상을 골라주세요",
                    modifier = Modifier
                        .width(279.dp),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        lineHeight = 28.6.sp,
                        letterSpacing = (-0.22).sp
                    ),
                    color = Color(0xFFFFFFFF)
                )
            }

            Button(
                onClick = {
                    videoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                )
            ) {
                Text(
                    text = "동영상 선택",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

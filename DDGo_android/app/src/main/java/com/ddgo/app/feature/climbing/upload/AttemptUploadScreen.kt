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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
    initialRealtimeSessionId: String? = null,
    onNavigateToNext: () -> Unit = {}
) {
    LaunchedEffect(initialRecordedVideoUri, initialRealtimeSessionId) {
        viewModel.beginNewChallengeUploadFlow()
        initialRecordedVideoUri
            ?.takeIf { it.isNotBlank() }
            ?.let { recordedUri ->
                viewModel.updateVideoUri(
                    uri = recordedUri,
                    realtimeSessionId = initialRealtimeSessionId
                )
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
                .padding(horizontal = 24.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Upload a climbing video for analysis.",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Pick a saved attempt video, or continue automatically with the video you just recorded.",
                    fontSize = 16.sp,
                    color = Color(0xFFB8B8B8)
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
                    text = "Choose video",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

package com.ddgo.app.feature.climbing.upload

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ddgo.app.core.ui.components.SafeAreaScreen

@Composable
fun AdditionalUploadScreen(
    viewModel: UploadViewModel = hiltViewModel(),
    onNavigateToNext: () -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    val isAttemptOnlyMode = viewModel.isAttemptOnlyUploadMode

    if (isAttemptOnlyMode) {
        BackHandler {
            viewModel.cancelAttemptOnlyUploadMode()
            onNavigateBack()
        }
    }

    val multipleVideoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.updateAdditionalVideoUris(uris.map(Uri::toString))
            onNavigateToNext()
        }
    }

    SafeAreaScreen(containerColor = Color(0xFF0D0D0D)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = if (isAttemptOnlyMode) {
                        "이 챌린지의 추가 시도 영상을\n모두 선택해주세요"
                    } else {
                        "이 문제의 추가 시도 영상을\n모두 선택해주세요"
                    },
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    lineHeight = 34.sp
                )
            }

            Column {
                Button(
                    onClick = {
                        multipleVideoPicker.launch(
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
                        text = "동영상을 여러 개 선택하기",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        viewModel.updateAdditionalVideoUris(emptyList())
                        if (isAttemptOnlyMode) {
                            viewModel.cancelAttemptOnlyUploadMode()
                            onNavigateBack()
                        } else {
                            onNavigateToNext()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2E2E2E),
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = if (isAttemptOnlyMode) "돌아가기" else "건너뛰기",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

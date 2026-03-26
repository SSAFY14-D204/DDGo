package com.ddgo.app.feature.climbing.upload

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ddgo.app.core.ui.tokens.DdgoColorTokens

@Composable
internal fun AdditionalAttemptPromptDialog(
    onNavigateToAdditional: () -> Unit,
    onNavigateToNext: () -> Unit
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp),
                shape = RoundedCornerShape(32.dp),
                color = Color.White,
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(28.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "이 문제의 추가 시도 영상이 있나요?",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 20.sp,
                                lineHeight = 26.sp
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            color = DdgoColorTokens.TextPrimary,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "시도 별로 비교해서 분석을 보여줄게요",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                lineHeight = 14.sp
                            ),
                            color = DdgoColorTokens.BrandGray,
                            textAlign = TextAlign.Start
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        AdditionalAttemptPromptButton(
                            text = "없어요",
                            backgroundColor = Color(0xFFADADAD),
                            contentColor = DdgoColorTokens.BrandGray,
                            onClick = onNavigateToNext,
                            modifier = Modifier.weight(1f)
                        )
                        AdditionalAttemptPromptButton(
                            text = "더 있어요!",
                            backgroundColor = DdgoColorTokens.BrandBlue,
                            contentColor = Color.White,
                            onClick = onNavigateToAdditional,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdditionalAttemptPromptButton(
    text: String,
    backgroundColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor = contentColor
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                lineHeight = 21.sp
            ),
            textAlign = TextAlign.Center
        )
    }
}

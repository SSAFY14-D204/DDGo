package com.ddgo.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 세션 만료 안내를 앱 톤에 맞춘 카드형 다이얼로그로 보여준다.
 *
 * 갑작스러운 자동 로그아웃 상황에서도 정보 위계를 또렷하게 보여주기 위해
 * 짧은 안내 문구와 단일 CTA 버튼 중심으로 구성한다.
 */
@Composable
internal fun SessionExpiredDialog(
    onConfirm: () -> Unit
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
                color = SessionExpiredDialogPalette.Surface,
                shadowElevation = 18.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        SessionExpiredDialogPalette.BadgeStart,
                                        SessionExpiredDialogPalette.BadgeEnd
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = SessionExpiredDialogPalette.ChipBackground
                    ) {
                        Text(
                            text = "세션 만료",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = SessionExpiredDialogPalette.ChipText
                            )
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "로그인이 풀렸어요",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = SessionExpiredDialogPalette.Title
                            )
                        )

                        Text(
                            text = "세션이 만료되어 자동으로 로그아웃되었어요.\n다시 로그인하면 바로 이어서 이용할 수 있어요.",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = SessionExpiredDialogPalette.Body
                            )
                        )
                    }

                    Button(
                        onClick = onConfirm,
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SessionExpiredDialogPalette.BadgeEnd,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "다시 로그인",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }
    }
}

private object SessionExpiredDialogPalette {
    val Surface = Color.White
    val Title = Color(0xFF1E232C)
    val Body = Color(0xFF647789)
    val ChipBackground = Color(0xFFEAF3FF)
    val ChipText = Color(0xFF1E88E5)
    val BadgeStart = Color(0xFF55AFFA)
    val BadgeEnd = Color(0xFF1E88E5)
}

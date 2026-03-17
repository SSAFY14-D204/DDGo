package com.ddgo.app.feature.climbing.upload

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddgo.app.R
import kotlinx.coroutines.delay

@Composable
fun AnalysisLoadingScreen(
    onLoadingFinished: () -> Unit = {}
) {
    // 3초간 가상 딜레이 후 완료
    LaunchedEffect(Unit) {
        delay(3000)
        onLoadingFinished()
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            // 헤더 영역
            Text(
                text = "시도 분석",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 24.dp)
            )
            // 상단 밑줄
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.2f))
                    .padding(top = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(60.dp))
            Text(
                text = "디디고가 자세를 분석하고 있어요",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(100.dp))

            // 스캐너 + 캐릭터 영역 (목업 구현)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(250.dp)
            ) {
                // TODO: 실제 옐로우 캐릭터(drawable/ic_loading_mascot)를 넣어야 함
                // 현재는 빈 회색 상자로 시뮬레이션
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .background(Color.DarkGray, shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
                ) {
                    Text("캐릭터 이미지 자리", color=Color.White, modifier = Modifier.align(Alignment.Center))
                }

                // 위아래 스캔 바
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

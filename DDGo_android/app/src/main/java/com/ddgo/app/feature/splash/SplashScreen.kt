package com.ddgo.app.feature.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ddgo.app.core.ui.theme.PretendardFamily

// 🚨 주의: 프로젝트 패키지명에 맞게 R 클래스를 꼭 import 해주세요!
// import com.ddgo.app.R

@Composable
fun SplashScreen(
    onNavigateToAuth: () -> Unit,
    onNavigateToMain: () -> Unit,
    viewModel: SplashViewModel = hiltViewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                is SplashNavigationEvent.NavigateToAuth -> onNavigateToAuth()
                is SplashNavigationEvent.NavigateToMain -> onNavigateToMain()
            }
        }
    }

    // 🎨 디자인 적용: 전체 화면을 하얗게 채우고, 내용물을 중앙에 배치
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color(0xFFFFFFFF)),
        contentAlignment = Alignment.Center
    ) {
        // 1. 뒤에 깔리는 보라색 타원 효과 이미지 (이미지 파일이 준비되었다면)
        Box(
            modifier = Modifier
                .width(180.dp) // 효과의 전체 너비
                .height(180.dp) // 효과의 전체 높이
                .offset(x = (-75).dp, y = (-50).dp) // 살짝 왼쪽 위로 비껴가게 배치
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            // 중심부 색상: Figma에서 찍힌 보라색/파란색 hex 코드를 넣으세요! (현재는 예시로 연보라색 세팅)
                            Color(0xA09D94FF), // 0x80은 투명도(Alpha) 50%를 의미합니다.
                            // 바깥쪽 색상: 완전 투명하게 만들어서 자연스럽게 사라지는(블러) 효과
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape // 동그란 형태로 그라데이션 적용
                )
        )

        // 2. 그 위로 올라가는 텍스트들
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "추락을 데이터로 바꾸다",
                style = TextStyle(
                    fontSize = 16.sp,
                    fontFamily = PretendardFamily,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF1E232C),
                    textAlign = TextAlign.Center,
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "DDgo",
                style = TextStyle(
                    fontSize = 64.sp,
                    fontFamily = PretendardFamily,
                    fontWeight = FontWeight(900),
                    color = Color(0xFF0D1013),
                    textAlign = TextAlign.Center,
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "디디고",
                style = TextStyle(
                    fontSize = 32.sp,
                    lineHeight = 16.sp,
                    fontFamily = PretendardFamily,
                    fontWeight = FontWeight(400),
                    color = Color(0xFF000000),
                    textAlign = TextAlign.Center,
                )
            )
        }
    }
}
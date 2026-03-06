package com.ddgo.app.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddgo.app.core.ui.theme.PretendardFamily

@Composable
fun AuthLandingScreen(onRegisterClick: () -> Unit, onLoginClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFB4C5FF),
                        Color.White
                    )
                )
            )
            .padding(horizontal = 24.dp, vertical = 40.dp)
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "추락을 데이터로 바꾸다", 
                fontSize = 16.sp,
                fontFamily = PretendardFamily
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "DDgo",
                style = TextStyle(
                    fontWeight = FontWeight.Bold, 
                    fontSize = 64.sp,
                    fontFamily = PretendardFamily
                )
            )
            Text(
                text = "디디고", 
                fontSize = 32.sp,
                fontFamily = PretendardFamily
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = onRegisterClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A3FF))
            ) {
                Text(
                    "시작하기", 
                    color = Color.White, 
                    fontWeight = FontWeight.Bold,
                    fontFamily = PretendardFamily
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "이미 계정이 있나요? ", 
                    color = Color.Gray,
                    fontFamily = PretendardFamily
                )
                Text(
                    text = "로그인",
                    color = Color(0xFF00A3FF),
                    modifier = Modifier.clickable { onLoginClick() },
                    textDecoration = TextDecoration.Underline,
                    fontFamily = PretendardFamily,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

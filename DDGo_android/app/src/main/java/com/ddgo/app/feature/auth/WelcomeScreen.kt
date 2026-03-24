package com.ddgo.app.feature.auth

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddgo.app.R
import com.ddgo.app.core.ui.components.SafeAreaScreen
import com.ddgo.app.core.ui.theme.PretendardFamily

@Composable
fun AuthLandingScreen(onRegisterClick: () -> Unit, onLoginClick: () -> Unit) {
    SafeAreaScreen(
        modifier = Modifier
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
            DdgoMascotBadge()
            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "추락의 데이터를 바꾸는",
                fontSize = 16.sp,
                fontFamily = PretendardFamily
            )
            Spacer(modifier = Modifier.height(10.dp))
            DdgoKoreanWordmark(fontSize = 60.sp)
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
                    text = "시작하기",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontFamily = PretendardFamily
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "이미 계정이 있나요? ",
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

@Composable
private fun DdgoMascotBadge(size: Dp = 132.dp) {
    Card(
        shape = RoundedCornerShape(36.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 18.dp)
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF4396FB), Color(0xFF876FFF))
                    ),
                    shape = RoundedCornerShape(36.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_ddgo_mascot),
                contentDescription = "디디 캐릭터",
                modifier = Modifier.size(size * 0.6f)
            )
        }
    }
}

package com.ddgo.app.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddgo.app.core.ui.components.keyboardAwareBottomPadding
import com.ddgo.app.core.ui.theme.PretendardFamily

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoginEmailScreen(viewModel: AuthViewModel, onNext: () -> Unit, onRegisterClick: () -> Unit = {}) {
    val isImeVisible = WindowInsets.isImeVisible

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 24.dp)
            .padding(top = 80.dp)
            .keyboardAwareBottomPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "추락을 데이터로 바꾸다",
                    style = TextStyle(
                        fontFamily = PretendardFamily,
                        fontSize = 16.sp,
                        color = Color(0xFF1E232C)
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "DDgo",
                    style = TextStyle(
                        fontFamily = PretendardFamily,
                        fontSize = 64.sp,
                        fontWeight = FontWeight(900),
                        color = Color(0xFF0D1013)
                    )
                )
            }

            Spacer(modifier = Modifier.height(60.dp))

            Text(
                text = "이메일",
                style = TextStyle(
                    fontFamily = PretendardFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1E232C)
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            TextField(
                value = viewModel.username,
                onValueChange = { viewModel.username = it },
                placeholder = { Text("이메일", color = Color(0xFF8391A1)) },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color(0xFF1DA1F2),
                    unfocusedIndicatorColor = Color(0xFF1DA1F2),
                )
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isImeVisible) {
                Button(
                    onClick = onNext,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DA1F2))
                ) {
                    Text(
                        "다음",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = PretendardFamily,
                        fontSize = 16.sp
                    )
                }
            } else {
                Button(
                    onClick = onNext,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFE812))
                ) {
                    Text(
                        "카카오로 3초만에 로그인",
                        color = Color.Black,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = PretendardFamily
                    )
                }
                
                Button(
                    onClick = onNext, 
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF121212))
                ) {
                    Text(
                        "Google로 로그인",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = PretendardFamily
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(onClick = onRegisterClick) {
                    Text(
                        "회원가입",
                        color = Color(0xFF1DA1F2),
                        fontFamily = PretendardFamily,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

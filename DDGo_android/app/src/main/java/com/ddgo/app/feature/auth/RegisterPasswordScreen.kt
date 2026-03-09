package com.ddgo.app.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddgo.app.core.ui.theme.PretendardFamily

@Composable
fun RegisterPasswordScreen(viewModel: AuthViewModel, onRegComplete: () -> Unit, onBack: () -> Unit = {}) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 24.dp)
            .padding(top = 40.dp, bottom = 40.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "뒤로가기")
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            Text(
                text = "비밀번호를 정확하게 입력해주세요",
                style = TextStyle(
                    fontFamily = PretendardFamily,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1E232C)
                )
            )

            Spacer(modifier = Modifier.height(60.dp))

            Text(
                text = "비밀번호",
                style = TextStyle(
                    fontFamily = PretendardFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1E232C)
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            TextField(
                value = viewModel.password,
                onValueChange = { viewModel.password = it },
                placeholder = { Text("비밀번호", color = Color(0xFF8391A1)) },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color(0xFF1DA1F2),
                    unfocusedIndicatorColor = Color(0xFF1DA1F2),
                ),
                trailingIcon = {
                    Text("~", color = Color(0xFF64788D))
                }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "✕ 영문/숫자/특수문자 2가지 이상 조합 (8~20자)",
                style = TextStyle(
                    color = Color(0xFFFF3B30),
                    fontSize = 12.sp,
                    fontFamily = PretendardFamily
                )
            )
        }

        Button(
            onClick = onRegComplete,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF64788D))
        ) {
            Text(
                "다음",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = PretendardFamily
            )
        }
    }
}

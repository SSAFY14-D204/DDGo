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
import com.ddgo.app.core.ui.components.SafeAreaScreen
import com.ddgo.app.core.ui.components.keyboardAwareBottomPadding
import com.ddgo.app.core.ui.theme.PretendardFamily

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoginEmailScreen(viewModel: AuthViewModel, onNext: () -> Unit, onRegisterClick: () -> Unit = {}) {
    val isImeVisible = WindowInsets.isImeVisible

    SafeAreaScreen(
        modifier = Modifier
            .background(Color.White)
            .padding(horizontal = 24.dp)
            .padding(top = 80.dp)
            .keyboardAwareBottomPadding(),
        applyBottomInset = false
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "\uCD94\uB77D\uC758 \uB370\uC774\uD130\uB97C \uBC14\uAFB8\uB294",
                    style = TextStyle(
                        fontFamily = PretendardFamily,
                        fontSize = 16.sp,
                        color = Color(0xFF1E232C)
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                DdgoKoreanWordmark(fontSize = 60.sp)
            }

            Spacer(modifier = Modifier.height(60.dp))

            Text(
                text = "\uC774\uBA54\uC77C",
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
                onValueChange = viewModel::updateUsername,
                placeholder = { Text("\uC774\uBA54\uC77C", color = Color(0xFF8391A1)) },
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
                        "\uB2E4\uC74C",
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
                        "\uCE74\uCE74\uC624\uB85C 3\uCD08\uB9CC\uC5D0 \uB85C\uADF8\uC778",
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
                        "Google\uB85C \uB85C\uADF8\uC778",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = PretendardFamily
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(onClick = onRegisterClick) {
                    Text(
                        "\uD68C\uC6D0\uAC00\uC785",
                        color = Color(0xFF1DA1F2),
                        fontFamily = PretendardFamily,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

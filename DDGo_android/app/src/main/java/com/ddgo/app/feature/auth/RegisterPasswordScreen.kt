package com.ddgo.app.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddgo.app.core.ui.components.SafeAreaScreen
import com.ddgo.app.core.ui.components.keyboardAwareBottomPadding
import com.ddgo.app.core.ui.theme.PretendardFamily

@Composable
fun RegisterPasswordScreen(viewModel: AuthViewModel, onRegComplete: () -> Unit, onBack: () -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsState()
    val errorMessage = viewModel.errorMessage

    LaunchedEffect(uiState) {
        when (uiState) {
            is AuthUiState.Success -> {
                viewModel.resetUiState()
                onRegComplete()
            }

            else -> {}
        }
    }

    SafeAreaScreen(
        modifier = Modifier
            .background(Color.White)
            .padding(horizontal = 24.dp)
            .padding(top = 40.dp)
            .keyboardAwareBottomPadding(),
        applyBottomInset = false
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "\uB4A4\uB85C\uAC00\uAE30")
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "\uBE44\uBC00\uBC88\uD638\uB97C \uC815\uD655\uD558\uAC8C \uC785\uB825\uD574\uC8FC\uC138\uC694",
                style = TextStyle(
                    fontFamily = PretendardFamily,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1E232C)
                )
            )

            Spacer(modifier = Modifier.height(60.dp))

            Text(
                text = "\uBE44\uBC00\uBC88\uD638",
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
                onValueChange = viewModel::updatePassword,
                placeholder = { Text("\uBE44\uBC00\uBC88\uD638", color = Color(0xFF8391A1)) },
                visualTransformation = PasswordVisualTransformation(),
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

            errorMessage?.let { message ->
                AuthInlineErrorMessage(message = message)
                Spacer(modifier = Modifier.height(8.dp))
            }

            Text(
                text = AuthStrings.RegisterPasswordRule,
                style = TextStyle(
                    color = Color(0xFFFF3B30),
                    fontSize = 12.sp,
                    fontFamily = PretendardFamily
                )
            )
        }

        Button(
            onClick = { viewModel.register() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF64788D)),
            enabled = uiState != AuthUiState.Loading && viewModel.password.isNotBlank()
        ) {
            if (uiState == AuthUiState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    "\uB2E4\uC74C",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = PretendardFamily
                )
            }
        }
    }
}

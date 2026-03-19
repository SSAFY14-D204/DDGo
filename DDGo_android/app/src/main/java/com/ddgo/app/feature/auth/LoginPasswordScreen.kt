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
import androidx.compose.runtime.remember
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
fun LoginPasswordScreen(viewModel: AuthViewModel, onLoginComplete: () -> Unit, onBack: () -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is AuthUiState.Success -> {
                viewModel.resetUiState()
                onLoginComplete()
            }

            is AuthUiState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.resetUiState()
            }

            else -> Unit
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        SafeAreaScreen(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(top = 40.dp)
                .keyboardAwareBottomPadding(),
            containerColor = Color.White,
            applyBottomInset = false
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "\uB4A4\uB85C\uAC00\uAE30")
                }

                Spacer(modifier = Modifier.height(20.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    DdgoKoreanWordmark(fontSize = 60.sp)
                }

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
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
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
            ) {
                Text(
                    text = "\uBE44\uBC00\uBC88\uD638\uB97C \uC78A\uC73C\uC168\uB098\uC694?",
                    style = TextStyle(color = Color(0xFF6C7B8A), fontSize = 14.sp, fontFamily = PretendardFamily)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.login() },
                    enabled = uiState !is AuthUiState.Loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF64788D))
                ) {
                    if (uiState is AuthUiState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            "\uB85C\uADF8\uC778",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = PretendardFamily
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 24.dp)
        )
    }
}

package com.ddgo.app.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddgo.app.core.ui.atom.DdgoFieldState
import com.ddgo.app.core.ui.atom.DdgoPrimaryButton
import com.ddgo.app.core.ui.atom.DdgoTextButton
import com.ddgo.app.core.ui.atom.DdgoTextButtonTone
import com.ddgo.app.core.ui.atom.DdgoTextField
import com.ddgo.app.core.ui.components.SafeAreaScreen
import com.ddgo.app.core.ui.components.keyboardAwareBottomPadding
import com.ddgo.app.core.ui.theme.PretendardFamily

@Composable
fun LoginPasswordScreen(
    viewModel: AuthViewModel,
    onLoginComplete: (AuthSuccessDestination) -> Unit,
    onBack: () -> Unit = {},
    onForgotPassword: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val errorMessage = viewModel.errorMessage
    var isPasswordVisible by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiState) {
        when (uiState) {
            is AuthUiState.Success -> {
                val destination = (uiState as AuthUiState.Success).destination
                viewModel.resetUiState()
                onLoginComplete(destination)
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
                AuthBackButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp))

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

                DdgoTextField(
                    value = viewModel.password,
                    onValueChange = viewModel::updatePassword,
                    placeholder = "\uBE44\uBC00\uBC88\uD638",
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (isPasswordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    state = if (errorMessage != null) DdgoFieldState.Error else DdgoFieldState.Default,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingContent = {
                        AuthPasswordTrailingActions(
                            value = viewModel.password,
                            isPasswordVisible = isPasswordVisible,
                            onClear = { viewModel.updatePassword("") },
                            onToggleVisibility = { isPasswordVisible = !isPasswordVisible }
                        )
                    }
                )

                errorMessage?.let { message ->
                    Spacer(modifier = Modifier.height(8.dp))
                    AuthInlineErrorMessage(message = message)
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DdgoTextButton(
                    text = AuthStrings.ForgotPasswordAction,
                    onClick = onForgotPassword,
                    tone = DdgoTextButtonTone.Neutral,
                    enabled = uiState !is AuthUiState.Loading
                )
                Spacer(modifier = Modifier.height(16.dp))
                DdgoPrimaryButton(
                    text = "\uB85C\uADF8\uC778",
                    onClick = { viewModel.login() },
                    enabled = uiState !is AuthUiState.Loading,
                    modifier = Modifier.fillMaxWidth(),
                    isLoading = uiState is AuthUiState.Loading
                )
            }
        }
    }
}

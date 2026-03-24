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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddgo.app.core.ui.atom.DdgoFieldState
import com.ddgo.app.core.ui.atom.DdgoPrimaryButton
import com.ddgo.app.core.ui.atom.DdgoTextField
import com.ddgo.app.core.ui.components.SafeAreaScreen
import com.ddgo.app.core.ui.components.keyboardAwareBottomPadding
import com.ddgo.app.core.ui.theme.PretendardFamily

@Composable
fun RegisterPasswordScreen(
    viewModel: AuthViewModel,
    onRegComplete: (AuthSuccessDestination) -> Unit,
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val errorMessage = viewModel.errorMessage
    var isPasswordVisible by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiState) {
        when (uiState) {
            is AuthUiState.Success -> {
                val destination = (uiState as AuthUiState.Success).destination
                viewModel.resetUiState()
                onRegComplete(destination)
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
            AuthBackButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp))

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

            DdgoTextField(
                value = viewModel.password,
                onValueChange = viewModel::updatePassword,
                placeholder = "\uBE44\uBC00\uBC88\uD638",
                visualTransformation = if (isPasswordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                modifier = Modifier.fillMaxWidth(),
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

        DdgoPrimaryButton(
            text = "\uB2E4\uC74C",
            onClick = { viewModel.register() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            enabled = uiState != AuthUiState.Loading && viewModel.password.isNotBlank(),
            isLoading = uiState == AuthUiState.Loading
        )
    }
}

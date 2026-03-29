package com.ddgo.app.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddgo.app.R
import com.ddgo.app.core.ui.atom.DdgoFieldState
import com.ddgo.app.core.ui.atom.DdgoPrimaryButton
import com.ddgo.app.core.ui.atom.DdgoTextButton
import com.ddgo.app.core.ui.atom.DdgoTextButtonTone
import com.ddgo.app.core.ui.atom.DdgoTextField
import com.ddgo.app.core.ui.components.SafeAreaScreen
import com.ddgo.app.core.ui.components.keyboardAwareBottomPadding
import com.ddgo.app.core.ui.theme.PretendardFamily
import kotlinx.coroutines.launch

private const val TAGLINE_TEXT = "\uCD94\uB77D\uC758 \uB370\uC774\uD130\uB97C \uBC14\uAFB8\uB294"
private const val EMAIL_LABEL = "\uC774\uBA54\uC77C"
private const val NEXT_LABEL = "\uB2E4\uC74C"
private const val KAKAO_LOGIN_LABEL = "\uCE74\uCE74\uC624\uB85C 3\uCD08\uB9CC\uC5D0 \uB85C\uADF8\uC778"
private const val GOOGLE_LOGIN_LABEL = "Google\uB85C \uB85C\uADF8\uC778"
private const val REGISTER_LABEL = "\uD68C\uC6D0\uAC00\uC785"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoginEmailScreen(
    viewModel: AuthViewModel,
    onNext: () -> Unit,
    onLoginComplete: (AuthSuccessDestination) -> Unit,
    onRegisterClick: () -> Unit = {}
) {
    val isImeVisible = WindowInsets.isImeVisible
    val uiState by viewModel.uiState.collectAsState()
    val errorMessage = viewModel.errorMessage
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.clearErrorState()
    }

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
                    text = TAGLINE_TEXT,
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
                text = EMAIL_LABEL,
                style = TextStyle(
                    fontFamily = PretendardFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1E232C)
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            DdgoTextField(
                value = viewModel.username,
                onValueChange = viewModel::updateUsername,
                placeholder = EMAIL_LABEL,
                modifier = Modifier.fillMaxWidth(),
                state = if (errorMessage != null) DdgoFieldState.Error else DdgoFieldState.Default,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isImeVisible) {
                DdgoPrimaryButton(
                    text = NEXT_LABEL,
                    onClick = {
                        if (viewModel.validateUsernameStep() == null) {
                            onNext()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                AuthActionButton(
                    text = KAKAO_LOGIN_LABEL,
                    onClick = {
                        startKakaoLogin(
                            context = context,
                            onSuccess = viewModel::loginWithKakaoAccessToken,
                            onError = viewModel::reportExternalLoginError
                        )
                    },
                    enabled = uiState !is AuthUiState.Loading,
                    isLoading = uiState is AuthUiState.Loading,
                    containerColor = Color(0xFFFFE812),
                    contentColor = Color.Black,
                    iconResId = R.drawable.ic_kakao_talk,
                    iconTint = Color.Black
                )

                AuthActionButton(
                    text = GOOGLE_LOGIN_LABEL,
                    onClick = {
                        coroutineScope.launch {
                            startGoogleLogin(context)
                                .onSuccess { result ->
                        viewModel.loginWithGoogleIdToken(
                            idToken = result.idToken
                        )
                                }
                                .onFailure { throwable ->
                                    viewModel.reportExternalLoginError(
                                        throwable.message ?: AuthStrings.GoogleLoginFailed
                                    )
                            }
                        }
                    },
                    enabled = uiState !is AuthUiState.Loading,
                    isLoading = uiState is AuthUiState.Loading,
                    containerColor = Color(0xFF121212),
                    contentColor = Color.White,
                    iconResId = R.drawable.ic_google
                )

                Spacer(modifier = Modifier.height(16.dp))

                DdgoTextButton(
                    text = REGISTER_LABEL,
                    onClick = onRegisterClick,
                    tone = DdgoTextButtonTone.Primary
                )
            }
        }
    }
}

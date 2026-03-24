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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddgo.app.core.ui.components.SafeAreaScreen
import com.ddgo.app.core.ui.components.keyboardAwareBottomPadding
import com.ddgo.app.core.ui.theme.PretendardFamily
import kotlinx.coroutines.launch

private const val TAGLINE_TEXT = "추락의 데이터를 바꾸는"
private const val EMAIL_LABEL = "이메일"
private const val NEXT_LABEL = "다음"
private const val KAKAO_LOGIN_LABEL = "카카오로 3초만에 로그인"
private const val GOOGLE_LOGIN_LABEL = "Google로 로그인"
private const val REGISTER_LABEL = "회원가입"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoginEmailScreen(
    viewModel: AuthViewModel,
    onNext: () -> Unit,
    onLoginComplete: () -> Unit,
    onRegisterClick: () -> Unit = {}
) {
    val isImeVisible = WindowInsets.isImeVisible
    val uiState by viewModel.uiState.collectAsState()
    val usernameFeedback = viewModel.loginUsernameFeedback
    val isCheckingLoginUsername = viewModel.isCheckingLoginUsername
    val globalError = viewModel.errorMessage
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.clearErrorState()
        viewModel.refreshLoginUsernameFeedback()
    }

    LaunchedEffect(uiState) {
        when (uiState) {
            is AuthUiState.Success -> {
                viewModel.resetUiState()
                onLoginComplete()
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

            TextField(
                value = viewModel.username,
                onValueChange = viewModel::updateLoginUsername,
                placeholder = {
                    Text(EMAIL_LABEL, color = Color(0xFF8391A1))
                },
                modifier = Modifier.fillMaxWidth(),
                isError = usernameFeedback?.tone == AuthFieldFeedbackTone.Error,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color(0xFF1DA1F2),
                    unfocusedIndicatorColor = Color(0xFF1DA1F2),
                    errorIndicatorColor = Color(0xFFD92D20)
                )
            )

            usernameFeedback?.let { feedback ->
                Spacer(modifier = Modifier.height(8.dp))
                AuthInlineFeedbackMessage(feedback = feedback)
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            globalError?.let { message ->
                AuthInlineErrorMessage(message = message)
            }

            if (isImeVisible) {
                Button(
                    onClick = {
                        viewModel.submitLoginUsername(onVerified = onNext)
                    },
                    enabled = viewModel.canProceedWithLoginUsername() && !isCheckingLoginUsername,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DA1F2))
                ) {
                    if (isCheckingLoginUsername) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = NEXT_LABEL,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = PretendardFamily,
                            fontSize = 16.sp
                        )
                    }
                }
            } else {
                Button(
                    onClick = {
                        startKakaoLogin(
                            context = context,
                            onSuccess = viewModel::loginWithKakaoAccessToken,
                            onError = viewModel::reportExternalLoginError
                        )
                    },
                    enabled = uiState !is AuthUiState.Loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFE812))
                ) {
                    if (uiState is AuthUiState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.Black,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = KAKAO_LOGIN_LABEL,
                            color = Color.Black,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = PretendardFamily
                        )
                    }
                }

                Button(
                    onClick = {
                        coroutineScope.launch {
                            startGoogleLogin(context)
                                .onSuccess { result ->
                                    viewModel.loginWithGoogleIdToken(
                                        idToken = result.idToken,
                                        displayName = result.displayName
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF121212))
                ) {
                    if (uiState is AuthUiState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = GOOGLE_LOGIN_LABEL,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = PretendardFamily
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(onClick = onRegisterClick) {
                    Text(
                        text = REGISTER_LABEL,
                        color = Color(0xFF1DA1F2),
                        fontFamily = PretendardFamily,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

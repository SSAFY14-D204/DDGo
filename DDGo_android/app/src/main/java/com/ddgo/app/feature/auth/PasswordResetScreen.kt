package com.ddgo.app.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
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
import com.ddgo.app.core.validation.AuthInputPolicy

@Composable
fun PasswordResetScreen(
    viewModel: AuthViewModel,
    onResetCompleted: () -> Unit,
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val errorMessage = viewModel.errorMessage
    val requestedEmail = viewModel.lastPasswordResetRequestedEmail
    val hasIncomingToken = viewModel.passwordResetTokenOrLink.isNotBlank()
    val canResend = requestedEmail != null
    val showConfirmationSection = requestedEmail != null || hasIncomingToken
    var isNewPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var isConfirmPasswordVisible by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiState) {
        if (uiState is AuthUiState.PasswordResetCompleted) {
            onResetCompleted()
        }
    }

    SafeAreaScreen(
        modifier = Modifier
            .background(Color.White)
            .padding(horizontal = 24.dp)
            .padding(top = 40.dp)
            .keyboardAwareBottomPadding(),
        containerColor = Color.White,
        applyBottomInset = false
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            AuthBackButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp))

            Spacer(modifier = Modifier.height(20.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DdgoKoreanWordmark(fontSize = 52.sp)
            }

            Spacer(modifier = Modifier.height(36.dp))

            Text(
                text = AuthStrings.PasswordResetTitle,
                style = TextStyle(
                    fontFamily = PretendardFamily,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E232C)
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = AuthStrings.PasswordResetDescription,
                style = TextStyle(
                    fontFamily = PretendardFamily,
                    fontSize = 14.sp,
                    color = Color(0xFF6C7B8A),
                    lineHeight = 22.sp
                )
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = AuthStrings.PasswordResetEmailLabel,
                style = TextStyle(
                    fontFamily = PretendardFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1E232C)
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            DdgoTextField(
                value = viewModel.passwordResetEmail,
                onValueChange = viewModel::updatePasswordResetEmail,
                placeholder = AuthStrings.PasswordResetEmailLabel,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                state = if (errorMessage != null) DdgoFieldState.Error else DdgoFieldState.Default
            )

            errorMessage?.let { message ->
                Spacer(modifier = Modifier.height(8.dp))
                AuthInlineErrorMessage(message = message)
            }

            Spacer(modifier = Modifier.height(20.dp))

            DdgoPrimaryButton(
                text = if (canResend) {
                    AuthStrings.PasswordResetResendAction
                } else {
                    AuthStrings.PasswordResetSendAction
                },
                onClick = viewModel::requestPasswordReset,
                enabled = !viewModel.isRequestingPasswordReset && !viewModel.isConfirmingPasswordReset,
                modifier = Modifier
                    .fillMaxWidth(),
                isLoading = viewModel.isRequestingPasswordReset
            )

            if (showConfirmationSection) {
                Spacer(modifier = Modifier.height(28.dp))

                requestedEmail?.let { email ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFFEAF6FF),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = AuthStrings.PasswordResetSentTitle,
                                style = TextStyle(
                                    fontFamily = PretendardFamily,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E232C)
                                )
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = AuthStrings.passwordResetSentDescription(email),
                                style = TextStyle(
                                    fontFamily = PretendardFamily,
                                    fontSize = 13.sp,
                                    color = Color(0xFF4D5D6C),
                                    lineHeight = 20.sp
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))
                }

                Text(
                    text = AuthStrings.PasswordResetTokenLabel,
                    style = TextStyle(
                        fontFamily = PretendardFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1E232C)
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                DdgoTextField(
                    value = viewModel.passwordResetTokenOrLink,
                    onValueChange = viewModel::updatePasswordResetTokenOrLink,
                    placeholder = AuthStrings.PasswordResetTokenPlaceholder,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = AuthStrings.PasswordResetLinkHint,
                    style = TextStyle(
                        fontFamily = PretendardFamily,
                        fontSize = 12.sp,
                        color = Color(0xFF6C7B8A),
                        lineHeight = 18.sp
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = AuthStrings.PasswordResetNewPasswordLabel,
                    style = TextStyle(
                        fontFamily = PretendardFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1E232C)
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                DdgoTextField(
                    value = viewModel.passwordResetNewPassword,
                    onValueChange = viewModel::updatePasswordResetNewPassword,
                    placeholder = AuthStrings.PasswordResetNewPasswordLabel,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (isNewPasswordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingContent = {
                        AuthPasswordTrailingActions(
                            value = viewModel.passwordResetNewPassword,
                            isPasswordVisible = isNewPasswordVisible,
                            onClear = { viewModel.updatePasswordResetNewPassword("") },
                            onToggleVisibility = { isNewPasswordVisible = !isNewPasswordVisible }
                        )
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = AuthStrings.PasswordResetConfirmPasswordLabel,
                    style = TextStyle(
                        fontFamily = PretendardFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1E232C)
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                DdgoTextField(
                    value = viewModel.passwordResetConfirmPassword,
                    onValueChange = viewModel::updatePasswordResetConfirmPassword,
                    placeholder = AuthStrings.PasswordResetConfirmPasswordLabel,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (isConfirmPasswordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingContent = {
                        AuthPasswordTrailingActions(
                            value = viewModel.passwordResetConfirmPassword,
                            isPasswordVisible = isConfirmPasswordVisible,
                            onClear = { viewModel.updatePasswordResetConfirmPassword("") },
                            onToggleVisibility = { isConfirmPasswordVisible = !isConfirmPasswordVisible }
                        )
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = AuthInputPolicy.buildChangePasswordGuide(),
                    style = TextStyle(
                        fontFamily = PretendardFamily,
                        fontSize = 12.sp,
                        color = Color(0xFF6C7B8A),
                        lineHeight = 18.sp
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                DdgoPrimaryButton(
                    text = AuthStrings.PasswordResetCompleteAction,
                    onClick = viewModel::confirmPasswordReset,
                    enabled = !viewModel.isRequestingPasswordReset && !viewModel.isConfirmingPasswordReset,
                    modifier = Modifier
                        .fillMaxWidth(),
                    isLoading = viewModel.isConfirmingPasswordReset
                )
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

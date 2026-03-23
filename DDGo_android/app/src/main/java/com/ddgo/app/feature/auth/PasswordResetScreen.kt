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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "\uB4A4\uB85C\uAC00\uAE30"
                )
            }

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

            TextField(
                value = viewModel.passwordResetEmail,
                onValueChange = viewModel::updatePasswordResetEmail,
                placeholder = {
                    Text(
                        text = AuthStrings.PasswordResetEmailLabel,
                        color = Color(0xFF8391A1)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color(0xFF1DA1F2),
                    unfocusedIndicatorColor = Color(0xFF1DA1F2)
                )
            )

            errorMessage?.let { message ->
                Spacer(modifier = Modifier.height(8.dp))
                AuthInlineErrorMessage(message = message)
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = viewModel::requestPasswordReset,
                enabled = !viewModel.isRequestingPasswordReset && !viewModel.isConfirmingPasswordReset,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DA1F2))
            ) {
                if (viewModel.isRequestingPasswordReset) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = if (canResend) {
                            AuthStrings.PasswordResetResendAction
                        } else {
                            AuthStrings.PasswordResetSendAction
                        },
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = PretendardFamily
                    )
                }
            }

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

                TextField(
                    value = viewModel.passwordResetTokenOrLink,
                    onValueChange = viewModel::updatePasswordResetTokenOrLink,
                    placeholder = {
                        Text(
                            text = AuthStrings.PasswordResetTokenPlaceholder,
                            color = Color(0xFF8391A1)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color(0xFF1DA1F2),
                        unfocusedIndicatorColor = Color(0xFF1DA1F2)
                    )
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

                TextField(
                    value = viewModel.passwordResetNewPassword,
                    onValueChange = viewModel::updatePasswordResetNewPassword,
                    placeholder = {
                        Text(
                            text = AuthStrings.PasswordResetNewPasswordLabel,
                            color = Color(0xFF8391A1)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color(0xFF1DA1F2),
                        unfocusedIndicatorColor = Color(0xFF1DA1F2)
                    )
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

                TextField(
                    value = viewModel.passwordResetConfirmPassword,
                    onValueChange = viewModel::updatePasswordResetConfirmPassword,
                    placeholder = {
                        Text(
                            text = AuthStrings.PasswordResetConfirmPasswordLabel,
                            color = Color(0xFF8391A1)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color(0xFF1DA1F2),
                        unfocusedIndicatorColor = Color(0xFF1DA1F2)
                    )
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

                Button(
                    onClick = viewModel::confirmPasswordReset,
                    enabled = !viewModel.isRequestingPasswordReset && !viewModel.isConfirmingPasswordReset,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF64788D))
                ) {
                    if (viewModel.isConfirmingPasswordReset) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = AuthStrings.PasswordResetCompleteAction,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = PretendardFamily
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

package com.ddgo.app.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ddgo.app.core.ui.components.SafeAreaScreen
import com.ddgo.app.core.ui.components.keyboardAwareBottomPadding

@Composable
fun PasswordResetScreen(
    viewModel: AuthViewModel,
    onResetCompleted: () -> Unit,
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val requestedEmail = viewModel.lastPasswordResetRequestedEmail
    val hasIncomingToken = viewModel.passwordResetTokenOrLink.isNotBlank()
    val stage = if (requestedEmail != null || hasIncomingToken) {
        PasswordResetStage.UpdatePassword
    } else {
        PasswordResetStage.RequestEmail
    }
    var isEditingLink by rememberSaveable(hasIncomingToken) {
        mutableStateOf(!hasIncomingToken)
    }

    LaunchedEffect(uiState) {
        if (uiState is AuthUiState.PasswordResetCompleted) {
            onResetCompleted()
        }
    }

    SafeAreaScreen(
        modifier = Modifier
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFF1F7FF),
                        Color.White
                    )
                )
            )
            .padding(horizontal = 24.dp)
            .padding(top = 24.dp)
            .keyboardAwareBottomPadding(),
        containerColor = Color.Transparent,
        applyBottomInset = false
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            PasswordResetBackButton(onBack = onBack)
            PasswordResetHeader(
                stage = stage,
                hasIncomingToken = hasIncomingToken
            )

            when (stage) {
                PasswordResetStage.RequestEmail -> {
                    PasswordResetRequestSection(
                        email = viewModel.passwordResetEmail,
                        errorMessage = viewModel.errorMessage,
                        isLoading = viewModel.isRequestingPasswordReset,
                        isEnabled = !viewModel.isRequestingPasswordReset && !viewModel.isConfirmingPasswordReset,
                        onEmailChange = viewModel::updatePasswordResetEmail,
                        onRequestReset = viewModel::requestPasswordReset
                    )
                }

                PasswordResetStage.UpdatePassword -> {
                    PasswordResetConfirmSection(
                        requestedEmail = requestedEmail,
                        hasIncomingToken = hasIncomingToken,
                        isEditingLink = isEditingLink,
                        tokenOrLink = viewModel.passwordResetTokenOrLink,
                        newPassword = viewModel.passwordResetNewPassword,
                        confirmPassword = viewModel.passwordResetConfirmPassword,
                        errorMessage = viewModel.errorMessage,
                        isRequesting = viewModel.isRequestingPasswordReset,
                        isConfirming = viewModel.isConfirmingPasswordReset,
                        onEditLinkModeChange = { isEditingLink = it },
                        onTokenOrLinkChange = viewModel::updatePasswordResetTokenOrLink,
                        onNewPasswordChange = viewModel::updatePasswordResetNewPassword,
                        onConfirmPasswordChange = viewModel::updatePasswordResetConfirmPassword,
                        onResendResetMail = viewModel::requestPasswordReset,
                        onEditEmail = viewModel::preparePasswordResetFlow,
                        onConfirmReset = viewModel::confirmPasswordReset
                    )
                }
            }
        }
    }
}

package com.ddgo.app.feature.auth

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun PasswordResetBackButton(onBack: () -> Unit) {
    PasswordResetBackIconButton(onBack = onBack)
}

@Composable
internal fun PasswordResetHeader(
    stage: PasswordResetStage,
    hasIncomingToken: Boolean
) {
    Spacer(modifier = androidx.compose.ui.Modifier.height(8.dp))

    PasswordResetHeroCard(
        stage = stage,
        hasIncomingToken = hasIncomingToken
    )

    Spacer(modifier = androidx.compose.ui.Modifier.height(20.dp))

    PasswordResetProgressRow(stage = stage)

    Spacer(modifier = androidx.compose.ui.Modifier.height(20.dp))
}

@Composable
internal fun PasswordResetRequestSection(
    email: String,
    errorMessage: String?,
    isLoading: Boolean,
    isEnabled: Boolean,
    onEmailChange: (String) -> Unit,
    onRequestReset: () -> Unit
) {
    PasswordResetStageCard(
        eyebrow = PasswordResetCopy.StepOneEyebrow,
        title = PasswordResetCopy.EmailStepTitle,
        subtitle = PasswordResetCopy.EmailStepSubtitle
    ) {
        PasswordResetInputField(
            value = email,
            onValueChange = onEmailChange,
            placeholder = PasswordResetCopy.EmailPlaceholder,
            keyboardType = KeyboardType.Email
        )

        errorMessage?.let { message ->
            Spacer(modifier = androidx.compose.ui.Modifier.height(10.dp))
            AuthInlineErrorMessage(message = message)
        }

        Spacer(modifier = androidx.compose.ui.Modifier.height(18.dp))

        PasswordResetPrimaryButton(
            text = PasswordResetCopy.SendResetMailAction,
            loading = isLoading,
            enabled = isEnabled,
            onClick = onRequestReset
        )
    }
}

@Composable
internal fun PasswordResetConfirmSection(
    requestedEmail: String?,
    hasIncomingToken: Boolean,
    isEditingLink: Boolean,
    tokenOrLink: String,
    newPassword: String,
    confirmPassword: String,
    errorMessage: String?,
    isRequesting: Boolean,
    isConfirming: Boolean,
    onEditLinkModeChange: (Boolean) -> Unit,
    onTokenOrLinkChange: (String) -> Unit,
    onNewPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onResendResetMail: () -> Unit,
    onEditEmail: () -> Unit,
    onConfirmReset: () -> Unit
) {
    PasswordResetMailStatusCard(
        requestedEmail = requestedEmail,
        hasIncomingToken = hasIncomingToken,
        canResend = requestedEmail != null,
        isLoading = isRequesting,
        onResend = onResendResetMail,
        onEditEmail = onEditEmail
    )

    Spacer(modifier = androidx.compose.ui.Modifier.height(16.dp))

    PasswordResetStageCard(
        eyebrow = PasswordResetCopy.StepTwoEyebrow,
        title = if (hasIncomingToken) {
            PasswordResetCopy.LinkedStepTitle
        } else {
            PasswordResetCopy.LinkStepTitle
        },
        subtitle = if (hasIncomingToken) {
            PasswordResetCopy.LinkedStepSubtitle
        } else {
            PasswordResetCopy.LinkStepSubtitle
        }
    ) {
        PasswordResetTokenInputArea(
            hasIncomingToken = hasIncomingToken,
            isEditingLink = isEditingLink,
            tokenOrLink = tokenOrLink,
            onEditLinkModeChange = onEditLinkModeChange,
            onTokenOrLinkChange = onTokenOrLinkChange
        )

        PasswordResetInputField(
            value = newPassword,
            onValueChange = onNewPasswordChange,
            placeholder = PasswordResetCopy.NewPasswordPlaceholder,
            keyboardType = KeyboardType.Password,
            isPassword = true
        )

        Spacer(modifier = androidx.compose.ui.Modifier.height(12.dp))

        PasswordResetInputField(
            value = confirmPassword,
            onValueChange = onConfirmPasswordChange,
            placeholder = PasswordResetCopy.ConfirmPasswordPlaceholder,
            keyboardType = KeyboardType.Password,
            isPassword = true
        )

        Spacer(modifier = androidx.compose.ui.Modifier.height(14.dp))

        PasswordResetPasswordGuide()

        errorMessage?.let { message ->
            Spacer(modifier = androidx.compose.ui.Modifier.height(12.dp))
            AuthInlineErrorMessage(message = message)
        }

        Spacer(modifier = androidx.compose.ui.Modifier.height(18.dp))

        PasswordResetPrimaryButton(
            text = PasswordResetCopy.CompleteResetAction,
            loading = isConfirming,
            enabled = !isRequesting && !isConfirming,
            onClick = onConfirmReset
        )
    }

    Spacer(modifier = androidx.compose.ui.Modifier.height(36.dp))
}

@Composable
private fun PasswordResetTokenInputArea(
    hasIncomingToken: Boolean,
    isEditingLink: Boolean,
    tokenOrLink: String,
    onEditLinkModeChange: (Boolean) -> Unit,
    onTokenOrLinkChange: (String) -> Unit
) {
    if (hasIncomingToken && !isEditingLink) {
        PasswordResetTokenDetectedCard(
            onEditLink = { onEditLinkModeChange(true) }
        )

        Spacer(modifier = androidx.compose.ui.Modifier.height(18.dp))
        return
    }

    PasswordResetInputField(
        value = tokenOrLink,
        onValueChange = onTokenOrLinkChange,
        placeholder = PasswordResetCopy.TokenPlaceholder,
        keyboardType = KeyboardType.Uri
    )

    if (hasIncomingToken) {
        Spacer(modifier = androidx.compose.ui.Modifier.height(8.dp))

        TextButton(
            onClick = { onEditLinkModeChange(false) },
            contentPadding = ButtonDefaults.TextButtonContentPadding
        ) {
            Text(
                text = PasswordResetCopy.AutoDetectModeAction,
                style = PasswordResetTextStyles.SecondaryAction
            )
        }
    }

    Spacer(modifier = androidx.compose.ui.Modifier.height(18.dp))
}

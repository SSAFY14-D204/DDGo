package com.ddgo.app.feature.profile.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.ddgo.app.feature.profile.ProfileStrings
import com.ddgo.app.feature.profile.model.ProfileActionTone
import com.ddgo.app.feature.profile.model.ProfilePasswordEditorUiState

/**
 * 비밀번호 변경 다이얼로그입니다.
 *
 * 역할:
 * - 현재 비밀번호 확인과 새 비밀번호 입력 단계를 한 화면에서 안정적으로 마무리합니다.
 * - 민감한 입력이므로 다른 편집 다이얼로그보다 더 차분한 톤으로 보여줍니다.
 */
@Composable
internal fun ProfilePasswordEditorDialog(
    state: ProfilePasswordEditorUiState,
    onCurrentPasswordChanged: (String) -> Unit,
    onNewPasswordChanged: (String) -> Unit,
    onConfirmPasswordChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ProfileDialogScaffold(
        icon = Icons.Rounded.Lock,
        title = state.title,
        description = state.description,
        confirmLabel = state.submitLabel,
        dismissLabel = ProfileStrings.ActionCancel,
        confirmTone = ProfileActionTone.Accent,
        message = state.errorMessage,
        isProcessing = state.isSaving,
        confirmEnabled = state.canSubmit,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        content = {
            ProfileDialogTextField(
                value = state.currentPasswordInput,
                onValueChange = onCurrentPasswordChanged,
                label = ProfileStrings.CurrentPasswordFieldLabel,
                enabled = !state.isSaving,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = PasswordVisualTransformation(),
                feedback = state.currentPasswordFeedback,
                modifier = Modifier.fillMaxWidth()
            )

            ProfileDialogTextField(
                value = state.newPasswordInput,
                onValueChange = onNewPasswordChanged,
                label = ProfileStrings.NewPasswordFieldLabel,
                enabled = !state.isSaving,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = PasswordVisualTransformation(),
                feedback = state.newPasswordFeedback,
                modifier = Modifier.fillMaxWidth()
            )

            ProfileDialogTextField(
                value = state.confirmPasswordInput,
                onValueChange = onConfirmPasswordChanged,
                label = ProfileStrings.ConfirmPasswordFieldLabel,
                enabled = !state.isSaving,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = PasswordVisualTransformation(),
                feedback = state.confirmPasswordFeedback,
                modifier = Modifier.fillMaxWidth()
            )
        }
    )
}

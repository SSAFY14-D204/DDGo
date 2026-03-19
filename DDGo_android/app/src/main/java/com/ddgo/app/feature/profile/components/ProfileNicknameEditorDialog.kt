package com.ddgo.app.feature.profile.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ddgo.app.feature.profile.ProfileStrings
import com.ddgo.app.feature.profile.model.ProfileActionTone
import com.ddgo.app.feature.profile.model.ProfileNicknameEditorUiState

/**
 * 닉네임 등록/변경 다이얼로그입니다.
 *
 * 역할:
 * - 초기 회원은 닉네임 생성 흐름으로, 기존 회원은 닉네임 수정 흐름으로 자연스럽게 이어집니다.
 * - 입력 UI는 다른 프로필 편집 다이얼로그와 동일한 톤으로 유지합니다.
 */
@Composable
internal fun ProfileNicknameEditorDialog(
    state: ProfileNicknameEditorUiState,
    onNicknameChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ProfileDialogScaffold(
        icon = Icons.Rounded.Edit,
        title = state.title,
        description = state.description,
        confirmLabel = state.submitLabel,
        dismissLabel = ProfileStrings.ActionCancel,
        confirmTone = ProfileActionTone.Accent,
        isProcessing = state.isSaving,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        content = {
            ProfileDialogTextField(
                value = state.nicknameInput,
                onValueChange = onNicknameChanged,
                label = ProfileStrings.NicknameFieldLabel,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth()
            )
        }
    )
}

package com.ddgo.app.feature.profile.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.runtime.Composable
import com.ddgo.app.feature.profile.model.ProfileActionTone

/**
 * 로그아웃, 회원 탈퇴처럼 확인이 필요한 액션에 쓰는 다이얼로그입니다.
 *
 * 역할:
 * - 사용자가 중요한 액션을 가볍게 넘기지 않도록 경고 톤을 분명하게 보여줍니다.
 * - 프로필 편집 다이얼로그와 동일한 골격을 사용해 화면 경험을 통일합니다.
 */
@Composable
internal fun ProfileActionConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    confirmTone: ProfileActionTone,
    isLoading: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ProfileDialogScaffold(
        icon = Icons.Rounded.Warning,
        title = title,
        description = message,
        confirmLabel = confirmLabel,
        dismissLabel = dismissLabel,
        confirmTone = confirmTone,
        isProcessing = isLoading,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

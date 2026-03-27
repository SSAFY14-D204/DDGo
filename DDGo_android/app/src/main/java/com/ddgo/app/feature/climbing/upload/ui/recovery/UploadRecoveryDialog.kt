package com.ddgo.app.feature.climbing.upload.ui.recovery

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

enum class UploadRecoveryDialogType {
    ClosedResult,
    RetryRequired,
    RestartRequired
}

data class UploadRecoveryDialogState(
    val type: UploadRecoveryDialogType,
    val title: String,
    val message: String,
    val confirmText: String = "확인",
    val dismissText: String? = null
)

fun uploadClosedResultRecoveryDialogState(
    challengeResultLabel: String? = null
): UploadRecoveryDialogState {
    val resultSuffix = challengeResultLabel
        ?.takeIf { it.isNotBlank() }
        ?.let { " ($it)" }
        .orEmpty()

    return UploadRecoveryDialogState(
        type = UploadRecoveryDialogType.ClosedResult,
        title = "진행 중이던 챌린지가 종료되었어요$resultSuffix",
        message = "앱을 다시 여는 동안 챌린지 상태가 변경되어 이전 화면으로 복구할 수 없어요. 새로 시작해 주세요.",
        confirmText = "새로 시작"
    )
}

fun uploadRecoveryRetryDialogState(
    reason: String? = null
): UploadRecoveryDialogState {
    val message = buildString {
        append("진행 중이던 화면을 확인하는 중 문제가 발생했어요.")
        if (!reason.isNullOrBlank()) {
            append('\n')
            append(reason)
        }
        append('\n')
        append("다시 시도해 주세요.")
    }

    return UploadRecoveryDialogState(
        type = UploadRecoveryDialogType.RetryRequired,
        title = "복구를 다시 시도할게요",
        message = message,
        confirmText = "다시 시도",
        dismissText = "닫기"
    )
}

fun uploadRecoveryRestartDialogState(
    reason: String? = null
): UploadRecoveryDialogState {
    val message = buildString {
        append("이전 화면에 필요한 상태를 충분히 복구하지 못했어요.")
        if (!reason.isNullOrBlank()) {
            append('\n')
            append(reason)
        }
        append('\n')
        append("현재 챌린지를 정리한 뒤 새로 시작할게요.")
    }

    return UploadRecoveryDialogState(
        type = UploadRecoveryDialogType.RestartRequired,
        title = "이전 화면을 그대로 복구하지 못했어요",
        message = message,
        confirmText = "새로 시작",
        dismissText = "닫기"
    )
}

@Composable
fun UploadRecoveryDialog(
    state: UploadRecoveryDialogState?,
    onConfirm: () -> Unit,
    onDismiss: (() -> Unit)? = null
) {
    if (state == null) return

    AlertDialog(
        onDismissRequest = onDismiss ?: onConfirm,
        title = {
            Text(
                text = state.title,
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Text(
                text = state.message,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors()
            ) {
                Text(text = state.confirmText)
            }
        },
        dismissButton = if (state.dismissText != null && onDismiss != null) {
            {
                TextButton(onClick = onDismiss) {
                    Text(text = state.dismissText)
                }
            }
        } else {
            null
        }
    )
}

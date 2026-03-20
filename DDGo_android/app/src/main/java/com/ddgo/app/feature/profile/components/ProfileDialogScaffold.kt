package com.ddgo.app.feature.profile.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ddgo.app.feature.profile.model.ProfileActionTone
import com.ddgo.app.feature.profile.style.ProfilePalette

/**
 * 프로필 기능에서 공통으로 사용하는 다이얼로그 골격입니다.
 *
 * 역할:
 * - 확인창, 닉네임 편집, 신체 정보 편집, 비밀번호 변경이 같은 제품 경험으로 보이도록
 *   공통 헤더와 액션 영역을 제공합니다.
 * - 위험 액션과 일반 편집 액션의 시각적 톤 차이도 한 곳에서 관리합니다.
 */
@Composable
internal fun ProfileDialogScaffold(
    icon: ImageVector,
    title: String,
    description: String,
    confirmLabel: String,
    dismissLabel: String,
    confirmTone: ProfileActionTone,
    message: String? = null,
    isProcessing: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    Dialog(
        onDismissRequest = {
            if (!isProcessing) onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 440.dp),
                shape = RoundedCornerShape(30.dp),
                color = ProfilePalette.Surface,
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    ProfileDialogHeader(
                        icon = icon,
                        title = title,
                        description = description,
                        tone = confirmTone
                    )

                    content?.let {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp), content = it)
                    }

                    message?.takeIf { it.isNotBlank() }?.let { errorMessage ->
                        Text(
                            text = errorMessage,
                            color = ProfilePalette.Danger,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    ProfileDialogActions(
                        confirmLabel = confirmLabel,
                        dismissLabel = dismissLabel,
                        confirmTone = confirmTone,
                        isProcessing = isProcessing,
                        onConfirm = onConfirm,
                        onDismiss = onDismiss
                    )
                }
            }
        }
    }
}

/**
 * 프로필 다이얼로그 전용 입력 필드입니다.
 *
 * 역할:
 * - 각 다이얼로그의 입력 필드 밀도와 색상 톤을 통일합니다.
 * - 길이가 다른 폼도 한 제품 안의 같은 컴포넌트처럼 보이게 만듭니다.
 */
@Composable
internal fun ProfileDialogTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label) },
        enabled = enabled,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        textStyle = TextStyle(
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = ProfilePalette.TextPrimary
        ),
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = ProfilePalette.TextPrimary,
            unfocusedTextColor = ProfilePalette.TextPrimary,
            disabledTextColor = ProfilePalette.TextSecondary,
            focusedContainerColor = ProfilePalette.SurfaceMuted,
            unfocusedContainerColor = ProfilePalette.SurfaceMuted,
            disabledContainerColor = ProfilePalette.SurfaceMuted,
            focusedBorderColor = ProfilePalette.AccentStrong,
            unfocusedBorderColor = ProfilePalette.Divider,
            disabledBorderColor = ProfilePalette.Divider,
            focusedLabelColor = ProfilePalette.AccentStrong,
            unfocusedLabelColor = ProfilePalette.TextSecondary,
            disabledLabelColor = ProfilePalette.TextHint,
            cursorColor = ProfilePalette.AccentStrong
        ),
        modifier = modifier
    )
}

/** 다이얼로그 내부 섹션 제목을 공통 스타일로 그립니다. */
@Composable
internal fun ProfileDialogFieldLabel(
    text: String
) {
    Text(
        text = text,
        color = ProfilePalette.TextSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun ProfileDialogHeader(
    icon: ImageVector,
    title: String,
    description: String,
    tone: ProfileActionTone
) {
    val badgeBackground = when (tone) {
        ProfileActionTone.Normal -> ProfilePalette.SurfaceMuted
        ProfileActionTone.Accent -> ProfilePalette.AccentSoft
        ProfileActionTone.Danger -> ProfilePalette.DangerSoft
    }
    val badgeTint = when (tone) {
        ProfileActionTone.Normal -> ProfilePalette.TextPrimary
        ProfileActionTone.Accent -> ProfilePalette.AccentStrong
        ProfileActionTone.Danger -> ProfilePalette.Danger
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = CircleShape,
                color = badgeBackground
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = badgeTint,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = title,
                color = ProfilePalette.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            if (description.isNotBlank()) {
                Text(
                    text = description,
                    color = ProfilePalette.TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 21.sp
                )
            }
        }
    }
}

@Composable
private fun ProfileDialogActions(
    confirmLabel: String,
    dismissLabel: String,
    confirmTone: ProfileActionTone,
    isProcessing: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val confirmContainerColor = when (confirmTone) {
        ProfileActionTone.Normal -> ProfilePalette.TextPrimary
        ProfileActionTone.Accent -> ProfilePalette.AccentStrong
        ProfileActionTone.Danger -> ProfilePalette.Danger
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onConfirm,
            enabled = !isProcessing,
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = confirmContainerColor,
                contentColor = ProfilePalette.Surface,
                disabledContainerColor = confirmContainerColor.copy(alpha = 0.45f),
                disabledContentColor = ProfilePalette.Surface.copy(alpha = 0.8f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = ProfilePalette.Surface,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = confirmLabel,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        TextButton(
            onClick = onDismiss,
            enabled = !isProcessing,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.textButtonColors(
                contentColor = ProfilePalette.TextSecondary,
                disabledContentColor = ProfilePalette.TextHint
            )
        ) {
            Text(
                text = dismissLabel,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

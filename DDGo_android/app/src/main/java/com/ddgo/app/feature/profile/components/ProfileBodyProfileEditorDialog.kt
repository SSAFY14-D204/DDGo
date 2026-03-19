package com.ddgo.app.feature.profile.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddgo.app.feature.profile.ProfileStrings
import com.ddgo.app.feature.profile.model.ProfileActionTone
import com.ddgo.app.feature.profile.model.ProfileBodyProfileEditorUiState
import com.ddgo.app.feature.profile.model.ProfileSexOption
import com.ddgo.app.feature.profile.style.ProfilePalette

/**
 * 신체 정보 입력/수정 다이얼로그입니다.
 *
 * 역할:
 * - 초기 입력과 수정 흐름을 같은 폼에서 처리하되 입력 밀도는 너무 높지 않게 유지합니다.
 * - 자주 수정하는 값은 빠르게 훑고 바꿀 수 있도록 행과 필드 구조를 정돈합니다.
 */
@Composable
internal fun ProfileBodyProfileEditorDialog(
    state: ProfileBodyProfileEditorUiState,
    onSexSelected: (ProfileSexOption) -> Unit,
    onHeightChanged: (String) -> Unit,
    onWeightChanged: (String) -> Unit,
    onWingspanChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ProfileDialogScaffold(
        icon = Icons.Rounded.Straighten,
        title = state.title,
        description = state.description,
        confirmLabel = state.submitLabel,
        dismissLabel = ProfileStrings.ActionCancel,
        confirmTone = ProfileActionTone.Accent,
        isProcessing = state.isSaving,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ProfileDialogFieldLabel(text = ProfileStrings.SexLabel)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ProfileSexOption.entries.forEach { option ->
                        ProfileSexChip(
                            modifier = Modifier.weight(1f),
                            label = option.label,
                            selected = state.sex == option,
                            enabled = !state.isSaving,
                            onClick = { onSexSelected(option) }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ProfileDialogTextField(
                    value = state.heightCmInput,
                    onValueChange = onHeightChanged,
                    label = "${ProfileStrings.BodyProfileFieldLabelHeight}(cm)",
                    enabled = !state.isSaving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )

                ProfileDialogTextField(
                    value = state.weightKgInput,
                    onValueChange = onWeightChanged,
                    label = "${ProfileStrings.BodyProfileFieldLabelWeight}(kg)",
                    enabled = !state.isSaving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }

            ProfileDialogTextField(
                value = state.wingspanCmInput,
                onValueChange = onWingspanChanged,
                label = "${ProfileStrings.BodyProfileFieldLabelWingspan}(cm)",
                enabled = !state.isSaving,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
        }
    )
}

/**
 * 성별 선택 칩입니다.
 *
 * 역할:
 * - 선택 상태를 색상과 점 표시로 동시에 보여줘서 빠르게 인지할 수 있게 합니다.
 * - 입력 다이얼로그의 다른 필드와 톤을 맞추되 버튼처럼 눌리는 느낌은 분명하게 줍니다.
 */
@Composable
private fun ProfileSexChip(
    modifier: Modifier = Modifier,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) ProfilePalette.AccentSoft else ProfilePalette.SurfaceMuted
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = if (selected) {
                        ProfilePalette.AccentStrong.copy(alpha = 0.22f)
                    } else {
                        ProfilePalette.Divider
                    },
                    shape = RoundedCornerShape(18.dp)
                )
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) ProfilePalette.AccentStrong else ProfilePalette.Divider
                    )
            )

            Text(
                text = label,
                color = if (selected) ProfilePalette.AccentStrong else ProfilePalette.TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

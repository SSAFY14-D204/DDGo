package com.ddgo.app.feature.profile.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ddgo.app.feature.profile.model.ProfileActionTone
import com.ddgo.app.feature.profile.model.ProfileActionType
import com.ddgo.app.feature.profile.model.ProfileInfoRowUiModel
import com.ddgo.app.feature.profile.model.ProfileInfoSectionUiModel
import com.ddgo.app.feature.profile.model.ProfileRowTrailing
import com.ddgo.app.feature.profile.model.ProfileSectionActionUiModel
import com.ddgo.app.feature.profile.style.ProfilePalette

/**
 * 계정/신체 정보/보안 섹션을 공통 목록 카드로 그립니다.
 *
 * 역할:
 * - 캘린더 상세 카드와 비슷한 표면, 라운드, border를 사용해 같은 앱처럼 보이게 합니다.
 * - 프로필의 현재 리스트 구조는 유지하면서 읽기 쉬운 밀도로 정돈합니다.
 */
@Composable
internal fun ProfileInfoSection(
    section: ProfileInfoSectionUiModel,
    actionsEnabled: Boolean = true,
    onActionClick: (ProfileActionType) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            title = section.title,
            headerAction = section.headerAction,
            actionsEnabled = actionsEnabled,
            onActionClick = onActionClick
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = ProfilePalette.Surface,
            border = BorderStroke(1.dp, ProfilePalette.Border),
            shadowElevation = 4.dp
        ) {
            Column {
                section.rows.forEachIndexed { index, row ->
                    InfoRow(
                        row = row,
                        actionsEnabled = actionsEnabled,
                        onClick = { row.actionType?.let(onActionClick) }
                    )

                    if (index != section.rows.lastIndex) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp)
                                .height(1.dp)
                                .background(ProfilePalette.Divider)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(
    row: ProfileInfoRowUiModel,
    actionsEnabled: Boolean,
    onClick: () -> Unit
) {
    val enabled = actionsEnabled && row.actionType != null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = row.title,
                color = ProfilePalette.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            if (!row.value.isNullOrBlank()) {
                Text(
                    text = row.value,
                    color = ProfilePalette.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        ProfileRowTrailingContent(trailing = row.trailing)
    }
}

@Composable
private fun SectionHeader(
    title: String,
    headerAction: ProfileSectionActionUiModel?,
    actionsEnabled: Boolean,
    onActionClick: (ProfileActionType) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProfileSectionTitle(title = title)

        if (headerAction != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        when (headerAction.tone) {
                            ProfileActionTone.Danger -> ProfilePalette.DangerSoft
                            ProfileActionTone.Accent -> ProfilePalette.AccentSoft
                            ProfileActionTone.Normal -> ProfilePalette.SurfaceMuted
                        }
                    )
                    .clickable(enabled = actionsEnabled) { onActionClick(headerAction.actionType) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = headerAction.label,
                    color = when (headerAction.tone) {
                        ProfileActionTone.Danger -> ProfilePalette.Danger
                        ProfileActionTone.Accent -> ProfilePalette.AccentStrong
                        ProfileActionTone.Normal -> ProfilePalette.TextSecondary
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ProfileRowTrailingContent(
    trailing: ProfileRowTrailing
) {
    when (trailing) {
        is ProfileRowTrailing.Action -> {
            val badgeTextColor = when (trailing.tone) {
                ProfileActionTone.Danger -> ProfilePalette.Danger
                ProfileActionTone.Accent -> ProfilePalette.AccentStrong
                ProfileActionTone.Normal -> ProfilePalette.TextSecondary
            }

            Box(modifier = Modifier.padding(start = 10.dp)) {
                ProfileCapsuleLabel(
                    text = trailing.label,
                    background = when (trailing.tone) {
                        ProfileActionTone.Danger -> ProfilePalette.DangerSoft
                        ProfileActionTone.Accent -> ProfilePalette.AccentSoft
                        ProfileActionTone.Normal -> ProfilePalette.SurfaceMuted
                    },
                    textColor = badgeTextColor
                )
            }
        }

        ProfileRowTrailing.Disclosure -> {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = ProfilePalette.TextHint,
                modifier = Modifier.padding(start = 10.dp)
            )
        }

        ProfileRowTrailing.None -> Unit
    }
}

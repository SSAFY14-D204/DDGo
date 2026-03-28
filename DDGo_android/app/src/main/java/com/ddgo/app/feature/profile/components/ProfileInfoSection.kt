package com.ddgo.app.feature.profile.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Wc
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ddgo.app.feature.profile.model.ProfileActionTone
import com.ddgo.app.feature.profile.model.ProfileActionType
import com.ddgo.app.feature.profile.model.ProfileInfoRowUiModel
import com.ddgo.app.feature.profile.model.ProfileInfoSectionUiModel
import com.ddgo.app.feature.profile.model.ProfileRowIcon
import com.ddgo.app.feature.profile.model.ProfileRowTrailing
import com.ddgo.app.feature.profile.model.ProfileSectionActionUiModel
import com.ddgo.app.feature.profile.style.ProfilePalette

@Composable
internal fun ProfileInfoSection(
    section: ProfileInfoSectionUiModel,
    actionsEnabled: Boolean = true,
    onActionClick: (ProfileActionType) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(
            title = section.title,
            headerAction = section.headerAction,
            actionsEnabled = actionsEnabled,
            onActionClick = onActionClick
        )

        Column {
            section.rows.forEachIndexed { index, row ->
                InfoRow(
                    row = row,
                    actionsEnabled = actionsEnabled,
                    onClick = { row.actionType?.let(onActionClick) }
                )

                if (index != section.rows.lastIndex) {
                    ProfileRowDivider()
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
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProfileRowIconBadge(icon = row.icon)

        Text(
            text = row.title,
            color = ProfilePalette.TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterEnd
        ) {
            ProfileRowTrailingContent(
                value = row.value,
                trailing = row.trailing
            )
        }
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
            Text(
                text = headerAction.label,
                color = actionToneColor(headerAction.tone),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = actionsEnabled) { onActionClick(headerAction.actionType) }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun ProfileRowTrailingContent(
    value: String?,
    trailing: ProfileRowTrailing
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!value.isNullOrBlank()) {
            Text(
                text = value,
                color = ProfilePalette.TextSecondary,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End
            )
        }

        when (trailing) {
            is ProfileRowTrailing.Action -> {
                Text(
                    text = trailing.label,
                    color = actionToneColor(trailing.tone),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            ProfileRowTrailing.Disclosure -> {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = ProfilePalette.TextHint
                )
            }

            ProfileRowTrailing.None -> Unit
        }
    }
}

@Composable
private fun ProfileRowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp)
            .height(1.dp)
            .background(ProfilePalette.Divider)
    )
}

@Composable
private fun ProfileRowIconBadge(icon: ProfileRowIcon) {
    val iconStyle = rememberProfileRowIconStyle(icon)

    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(iconStyle.background),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = iconStyle.imageVector,
            contentDescription = null,
            tint = iconStyle.tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

private data class ProfileRowIconStyle(
    val imageVector: ImageVector,
    val tint: Color,
    val background: Color
)

private fun rememberProfileRowIconStyle(icon: ProfileRowIcon): ProfileRowIconStyle {
    return when (icon) {
        ProfileRowIcon.Account -> ProfileRowIconStyle(
            imageVector = Icons.Rounded.Email,
            tint = ProfilePalette.AccentStrong,
            background = ProfilePalette.AccentSoft
        )

        ProfileRowIcon.Nickname -> ProfileRowIconStyle(
            imageVector = Icons.Rounded.Person,
            tint = ProfilePalette.AccentStrong,
            background = ProfilePalette.AccentSoft
        )

        ProfileRowIcon.Sex -> ProfileRowIconStyle(
            imageVector = Icons.Rounded.Wc,
            tint = ProfilePalette.TextSecondary,
            background = ProfilePalette.SurfaceMuted
        )

        ProfileRowIcon.Height -> ProfileRowIconStyle(
            imageVector = Icons.Rounded.Straighten,
            tint = ProfilePalette.AccentStrong,
            background = ProfilePalette.SurfaceMuted
        )

        ProfileRowIcon.Weight -> ProfileRowIconStyle(
            imageVector = Icons.Rounded.MonitorWeight,
            tint = ProfilePalette.AccentStrong,
            background = ProfilePalette.SurfaceMuted
        )

        ProfileRowIcon.Wingspan -> ProfileRowIconStyle(
            imageVector = Icons.Rounded.AccessibilityNew,
            tint = ProfilePalette.AccentStrong,
            background = ProfilePalette.SurfaceMuted
        )

        ProfileRowIcon.BodyProfile -> ProfileRowIconStyle(
            imageVector = Icons.Rounded.Edit,
            tint = ProfilePalette.AccentStrong,
            background = ProfilePalette.AccentSoft
        )

        ProfileRowIcon.Password -> ProfileRowIconStyle(
            imageVector = Icons.Rounded.Lock,
            tint = ProfilePalette.TextSecondary,
            background = ProfilePalette.SurfaceMuted
        )

        ProfileRowIcon.Logout -> ProfileRowIconStyle(
            imageVector = Icons.Rounded.PowerSettingsNew,
            tint = ProfilePalette.TextSecondary,
            background = ProfilePalette.SurfaceMuted
        )
    }
}

private fun actionToneColor(tone: ProfileActionTone): Color {
    return when (tone) {
        ProfileActionTone.Danger -> ProfilePalette.Danger
        ProfileActionTone.Accent -> ProfilePalette.AccentStrong
        ProfileActionTone.Normal -> ProfilePalette.TextSecondary
    }
}

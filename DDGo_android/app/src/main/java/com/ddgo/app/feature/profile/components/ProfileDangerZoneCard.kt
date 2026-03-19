package com.ddgo.app.feature.profile.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ddgo.app.feature.profile.ProfileStrings
import com.ddgo.app.feature.profile.model.ProfileActionType
import com.ddgo.app.feature.profile.model.ProfileDangerZoneUiModel
import com.ddgo.app.feature.profile.style.ProfilePalette

/**
 * 회원 탈퇴 영역입니다.
 *
 * 역할:
 * - 프로필의 다른 카드와 같은 골격을 유지하면서도 위험 액션이라는 점은 분명하게 보이게 합니다.
 * - 장문 설명은 다이얼로그에 넘기고, 화면에서는 짧고 또렷하게 액션만 보여줍니다.
 */
@Composable
internal fun ProfileDangerZoneCard(
    dangerZone: ProfileDangerZoneUiModel,
    actionEnabled: Boolean = true,
    onActionClick: (ProfileActionType) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ProfileSectionTitle(title = ProfileStrings.DangerZoneSectionTitle)

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = ProfilePalette.Surface,
            border = BorderStroke(1.dp, ProfilePalette.Border),
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = dangerZone.title,
                        color = ProfilePalette.Danger,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    if (!dangerZone.subtitle.isNullOrBlank()) {
                        Text(
                            text = dangerZone.subtitle,
                            color = ProfilePalette.TextSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(ProfilePalette.DangerSoft)
                        .clickable(enabled = actionEnabled) { onActionClick(dangerZone.actionType) }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = dangerZone.actionLabel,
                        color = ProfilePalette.Danger,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

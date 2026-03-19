package com.ddgo.app.feature.profile.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddgo.app.feature.profile.style.ProfilePalette

/**
 * 프로필 화면 배경에 들어가는 글로우 장식입니다.
 */
@Composable
internal fun ProfileGlow(
    modifier: Modifier = Modifier,
    xOffset: Dp = 0.dp,
    yOffset: Dp = 0.dp,
    colors: List<Color>
) {
    Box(
        modifier = modifier
            .offset(x = xOffset, y = yOffset)
            .size(220.dp)
            .clip(CircleShape)
            .background(Brush.radialGradient(colors = colors))
    )
}

/** 프로필 화면 상단 제목입니다. */
@Composable
internal fun ProfileTopBar(
    title: String
) {
    Text(
        text = title,
        color = ProfilePalette.TextPrimary,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold
    )
}

/** 각 섹션 상단 제목 행입니다. */
@Composable
internal fun ProfileSectionTitle(
    title: String,
    trailingText: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = ProfilePalette.TextPrimary,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        if (!trailingText.isNullOrBlank()) {
            Text(
                text = trailingText,
                color = ProfilePalette.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/** 우측 액션 칩이나 상태 라벨에 쓰는 공통 캡슐입니다. */
@Composable
internal fun ProfileCapsuleLabel(
    text: String,
    background: Color,
    textColor: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

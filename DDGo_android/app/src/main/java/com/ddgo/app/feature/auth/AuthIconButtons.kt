package com.ddgo.app.feature.auth

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ddgo.app.R
import com.ddgo.app.core.ui.tokens.DdgoColorTokens

@Composable
internal fun AuthBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            painter = painterResource(id = R.drawable.ic_back_arrow),
            contentDescription = "뒤로가기",
            tint = DdgoColorTokens.TextPrimary
        )
    }
}

@Composable
internal fun AuthPasswordTrailingActions(
    value: String,
    isPasswordVisible: Boolean,
    onClear: () -> Unit,
    onToggleVisibility: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (value.isNotEmpty()) {
            TintableIconButton(
                iconResId = R.drawable.ic_erase,
                contentDescription = "입력 지우기",
                onClick = onClear
            )
        }

        TintableIconButton(
            iconResId = if (isPasswordVisible) {
                R.drawable.ic_eye_open
            } else {
                R.drawable.ic_eye_closed
            },
            contentDescription = if (isPasswordVisible) {
                "비밀번호 숨기기"
            } else {
                "비밀번호 보기"
            },
            onClick = onToggleVisibility
        )
    }
}

@Composable
internal fun TintableIconButton(
    @DrawableRes iconResId: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = DdgoColorTokens.TextSecondary
) {
    Icon(
        painter = painterResource(id = iconResId),
        contentDescription = contentDescription,
        modifier = modifier
            .size(18.dp)
            .clickable(onClick = onClick),
        tint = tint
    )
}

@Composable
internal fun BrandIcon(
    @DrawableRes iconResId: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tint: Color? = null
) {
    Icon(
        painter = painterResource(id = iconResId),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint ?: Color.Unspecified
    )
}

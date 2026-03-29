package com.ddgo.app.feature.auth

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
            contentDescription = "Go back",
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
        modifier = modifier
            .width(54.dp)
            .padding(end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(18.dp),
            contentAlignment = Alignment.Center
        ) {
            if (value.isNotEmpty()) {
                TintableIconButton(
                    iconResId = R.drawable.ic_erase,
                    contentDescription = "Clear password",
                    onClick = onClear,
                    iconSize = 14.dp
                )
            } else {
                Spacer(modifier = Modifier.size(18.dp))
            }
        }

        Box(
            modifier = Modifier.size(18.dp),
            contentAlignment = Alignment.Center
        ) {
            TintableIconButton(
                iconResId = if (isPasswordVisible) {
                    R.drawable.ic_eye_open
                } else {
                    R.drawable.ic_eye_closed
                },
                contentDescription = if (isPasswordVisible) {
                    "Hide password"
                } else {
                    "Show password"
                },
                onClick = onToggleVisibility
            )
        }
    }
}

@Composable
internal fun TintableIconButton(
    @DrawableRes iconResId: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: androidx.compose.ui.unit.Dp = 18.dp,
    tint: Color = DdgoColorTokens.TextSecondary
) {
    Icon(
        painter = painterResource(id = iconResId),
        contentDescription = contentDescription,
        modifier = modifier
            .size(iconSize)
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

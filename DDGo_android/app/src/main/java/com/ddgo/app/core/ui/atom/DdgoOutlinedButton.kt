package com.ddgo.app.core.ui.atom

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ddgo.app.core.ui.tokens.DdgoColorTokens
import com.ddgo.app.core.ui.tokens.DdgoShapeTokens
import com.ddgo.app.core.ui.tokens.DdgoSizeTokens

@Composable
fun DdgoOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(DdgoSizeTokens.ButtonHeight),
        shape = DdgoShapeTokens.Button,
        border = BorderStroke(
            width = 1.dp,
            color = if (enabled) DdgoColorTokens.BrandBlue else DdgoColorTokens.Border
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = DdgoColorTokens.Surface,
            contentColor = DdgoColorTokens.BrandBlue,
            disabledContainerColor = DdgoColorTokens.SurfaceMuted,
            disabledContentColor = DdgoColorTokens.TextSecondary
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

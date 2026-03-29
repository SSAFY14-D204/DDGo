package com.ddgo.app.core.ui.atom

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.ddgo.app.core.ui.tokens.DdgoColorTokens

enum class DdgoTextButtonTone {
    Primary,
    Neutral,
    Danger
}

@Composable
fun DdgoTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: DdgoTextButtonTone = DdgoTextButtonTone.Primary,
    enabled: Boolean = true
) {
    val color = when (tone) {
        DdgoTextButtonTone.Primary -> DdgoColorTokens.BrandBlue
        DdgoTextButtonTone.Neutral -> DdgoColorTokens.TextSecondary
        DdgoTextButtonTone.Danger -> DdgoColorTokens.Error
    }

    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(
            contentColor = color,
            disabledContentColor = DdgoColorTokens.TextSecondary.copy(alpha = 0.6f)
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

package com.ddgo.app.core.ui.atom

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ddgo.app.core.ui.tokens.DdgoBrushTokens
import com.ddgo.app.core.ui.tokens.DdgoColorTokens
import com.ddgo.app.core.ui.tokens.DdgoElevationTokens
import com.ddgo.app.core.ui.tokens.DdgoShapeTokens
import com.ddgo.app.core.ui.tokens.DdgoSizeTokens

enum class DdgoPrimaryButtonVariant {
    Solid,
    EmphasisGradient
}

@Composable
fun DdgoPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: DdgoPrimaryButtonVariant = DdgoPrimaryButtonVariant.Solid,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    leadingIcon: ImageVector? = null,
    height: Dp = DdgoSizeTokens.ButtonHeight,
    textStyle: TextStyle = MaterialTheme.typography.titleMedium,
    textFontWeight: FontWeight = FontWeight.SemiBold
) {
    val buttonShape = DdgoShapeTokens.Button
    val isInteractive = enabled && !isLoading

    Button(
        onClick = onClick,
        enabled = isInteractive,
        modifier = modifier
            .height(height)
            .then(
                if (isInteractive) {
                    Modifier.shadow(
                        elevation = if (variant == DdgoPrimaryButtonVariant.EmphasisGradient) {
                            DdgoElevationTokens.EmphasisButton
                        } else {
                            DdgoElevationTokens.FilledButton
                        },
                        shape = buttonShape,
                        ambientColor = if (variant == DdgoPrimaryButtonVariant.EmphasisGradient) {
                            DdgoColorTokens.BrandGradientStart.copy(alpha = 0.18f)
                        } else {
                            DdgoColorTokens.BrandBlue.copy(alpha = 0.12f)
                        },
                        spotColor = if (variant == DdgoPrimaryButtonVariant.EmphasisGradient) {
                            DdgoColorTokens.BrandGradientEnd.copy(alpha = 0.18f)
                        } else {
                            DdgoColorTokens.BrandBlue.copy(alpha = 0.12f)
                        }
                    )
                } else {
                    Modifier
                }
            ),
        shape = buttonShape,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            contentColor = DdgoColorTokens.TextInverse,
            disabledContentColor = DdgoColorTokens.DisabledContent
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(buttonShape)
                .background(buttonBackground(variant = variant, enabled = enabled)),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = DdgoColorTokens.TextInverse,
                    strokeWidth = 2.dp
                )
            } else {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    leadingIcon?.let { icon ->
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = DdgoColorTokens.TextInverse,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Text(
                        text = text,
                        style = textStyle,
                        fontWeight = textFontWeight,
                        color = DdgoColorTokens.TextInverse
                    )
                }
            }
        }
    }
}

@Composable
private fun buttonBackground(
    variant: DdgoPrimaryButtonVariant,
    enabled: Boolean
): Brush {
    return when {
        !enabled -> Brush.horizontalGradient(
            colors = listOf(
                DdgoColorTokens.DisabledFill,
                DdgoColorTokens.DisabledFill
            )
        )

        variant == DdgoPrimaryButtonVariant.EmphasisGradient -> DdgoBrushTokens.PrimaryButtonGradient
        else -> Brush.horizontalGradient(
            colors = listOf(
                DdgoColorTokens.BrandBlue,
                DdgoColorTokens.BrandBlue
            )
        )
    }
}

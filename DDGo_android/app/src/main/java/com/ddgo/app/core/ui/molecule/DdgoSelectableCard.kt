package com.ddgo.app.core.ui.molecule

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ddgo.app.core.ui.tokens.DdgoColorTokens
import com.ddgo.app.core.ui.tokens.DdgoShapeTokens

@Composable
fun DdgoSelectableCard(
    title: String,
    subtitle: String,
    leadingIcon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = DdgoShapeTokens.Card,
        color = if (selected) DdgoColorTokens.SurfaceTint else DdgoColorTokens.Surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) DdgoColorTokens.BrandBlue else DdgoColorTokens.Border
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = DdgoShapeTokens.Pill,
                color = if (selected) {
                    DdgoColorTokens.BrandBlue.copy(alpha = 0.14f)
                } else {
                    DdgoColorTokens.SurfaceMuted
                }
            ) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(12.dp)
                        .size(22.dp),
                    tint = if (selected) DdgoColorTokens.BrandBlue else DdgoColorTokens.TextSecondary
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = DdgoColorTokens.TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = DdgoColorTokens.TextSecondary
                )
            }
        }
    }
}

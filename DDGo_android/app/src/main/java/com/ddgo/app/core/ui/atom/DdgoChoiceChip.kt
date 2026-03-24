package com.ddgo.app.core.ui.atom

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ddgo.app.core.ui.tokens.DdgoColorTokens
import com.ddgo.app.core.ui.tokens.DdgoShapeTokens

@Composable
fun DdgoChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Surface(
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
        color = if (selected) {
            DdgoColorTokens.SurfaceTint
        } else {
            DdgoColorTokens.Surface
        },
        shape = DdgoShapeTokens.Pill,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) DdgoColorTokens.BrandBlue else DdgoColorTokens.Border
        )
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) DdgoColorTokens.BrandBlue else DdgoColorTokens.TextPrimary
        )
    }
}

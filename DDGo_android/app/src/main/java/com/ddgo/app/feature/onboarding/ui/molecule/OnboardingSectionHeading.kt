package com.ddgo.app.feature.onboarding.ui.molecule

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddgo.app.core.ui.atom.DdgoChoiceChip
import com.ddgo.app.core.ui.tokens.DdgoColorTokens
import com.ddgo.app.core.ui.theme.PretendardFamily
import com.ddgo.app.feature.onboarding.ui.shared.tokens.OnboardingTokens

@Composable
fun OnboardingSectionHeading(
    eyebrow: String,
    title: String,
    description: String
) {
    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = eyebrow,
            color = DdgoColorTokens.BrandBlueStrong,
            fontFamily = PretendardFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
        Text(
            text = title,
            color = OnboardingTokens.Graphite,
            fontFamily = PretendardFamily,
            fontWeight = FontWeight.Black,
            fontSize = 32.sp,
            lineHeight = 40.sp
        )
        Text(
            text = description,
            color = OnboardingTokens.GraphiteMuted,
            fontFamily = PretendardFamily,
            fontSize = 15.sp,
            lineHeight = 24.sp
        )
    }
}

@Composable
fun OnboardingSectionLabel(text: String) {
    Text(
        text = text,
        color = OnboardingTokens.Graphite,
        fontFamily = PretendardFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp
    )
}

@Composable
fun OnboardingProgressBar(
    totalCount: Int,
    currentIndex: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(totalCount) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .then(
                        Modifier
                    )
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(999.dp),
                    color = if (index <= currentIndex) {
                        DdgoColorTokens.BrandBlue
                    } else {
                        OnboardingTokens.CardBorder
                    }
                ) {}
            }
        }
    }
}

@Composable
fun OnboardingStatusBanner(
    icon: ImageVector,
    text: String,
    background: Color,
    contentColor: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = background
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(10.dp))
            Text(
                text = text,
                color = contentColor,
                fontFamily = PretendardFamily,
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
        }
    }
}

data class OnboardingChoiceOption<T>(
    val value: T,
    val label: String
)

@Composable
fun <T> OnboardingChoiceGroup(
    options: List<OnboardingChoiceOption<T>>,
    selectedValue: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        options.forEach { option ->
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp),
                color = if (selectedValue == option.value) {
                    OnboardingTokens.SelectedFill
                } else {
                    OnboardingTokens.CardFill
                },
                border = BorderStroke(
                    width = 1.dp,
                    color = if (selectedValue == option.value) {
                        DdgoColorTokens.BrandBlue
                    } else {
                        OnboardingTokens.CardBorder
                    }
                ),
                onClick = { onSelect(option.value) }
            ) {
                Text(
                    text = option.label,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    textAlign = TextAlign.Center,
                    color = if (selectedValue == option.value) {
                        DdgoColorTokens.BrandBlueStrong
                    } else {
                        OnboardingTokens.GraphiteMuted
                    },
                    fontFamily = PretendardFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
fun OnboardingSummaryTag(
    text: String,
    background: Color,
    contentColor: Color
) {
    Surface(shape = RoundedCornerShape(999.dp), color = background) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = contentColor,
            fontFamily = PretendardFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    }
}

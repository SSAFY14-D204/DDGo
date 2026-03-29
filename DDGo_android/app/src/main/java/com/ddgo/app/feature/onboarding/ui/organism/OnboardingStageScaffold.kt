package com.ddgo.app.feature.onboarding.ui.organism

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.ddgo.app.core.ui.atom.DdgoPrimaryButton
import com.ddgo.app.core.ui.atom.DdgoPrimaryButtonVariant
import com.ddgo.app.core.ui.atom.DdgoTextButton
import com.ddgo.app.core.ui.atom.DdgoTextButtonTone
import com.ddgo.app.core.ui.tokens.DdgoColorTokens
import com.ddgo.app.core.ui.theme.PretendardFamily
import com.ddgo.app.feature.onboarding.OnboardingStage
import com.ddgo.app.feature.onboarding.ui.molecule.OnboardingProgressBar
import com.ddgo.app.feature.onboarding.ui.shared.tokens.OnboardingTokens

@Composable
fun OnboardingStageScaffold(
    currentStage: OnboardingStage,
    trackedStages: List<OnboardingStage>,
    currentStageIndex: Int,
    canContinue: Boolean,
    buttonLabel: String,
    helperText: String,
    isLoading: Boolean,
    onContinue: () -> Unit,
    canGoBack: Boolean,
    onBack: () -> Unit,
    showSkip: Boolean,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val scrollState = rememberScrollState()
    var bottomCtaHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val contentBottomPadding = with(density) { bottomCtaHeightPx.toDp() } + 24.dp
    val hasTopBar = currentStage != OnboardingStage.Hero
    val buttonVariant = if (currentStage == OnboardingStage.Hero || currentStage == OnboardingStage.Summary) {
        DdgoPrimaryButtonVariant.EmphasisGradient
    } else {
        DdgoPrimaryButtonVariant.Solid
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OnboardingTokens.Background)
    ) {
        LaunchedEffect(currentStage) {
            scrollState.scrollTo(0)
        }

        OnboardingBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp)
                .padding(top = 20.dp, bottom = contentBottomPadding)
        ) {
            if (hasTopBar) {
                OnboardingTopBar(
                    trackedStages = trackedStages,
                    currentStage = currentStage,
                    currentStageIndex = currentStageIndex,
                    canGoBack = canGoBack,
                    onBack = onBack,
                    showSkip = showSkip,
                    onSkip = onSkip
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(28.dp))
            }

            content()
        }

        OnboardingBottomCtaBar(
            buttonLabel = buttonLabel,
            helperText = helperText,
            canContinue = canContinue,
            isLoading = isLoading,
            buttonVariant = buttonVariant,
            onContinue = onContinue,
            onHeightMeasured = { bottomCtaHeightPx = it },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
fun OnboardingBottomCtaBar(
    buttonLabel: String,
    helperText: String,
    canContinue: Boolean,
    isLoading: Boolean,
    buttonVariant: DdgoPrimaryButtonVariant,
    onContinue: () -> Unit,
    onHeightMeasured: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { onHeightMeasured(it.height) }
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, OnboardingTokens.Background)
                )
            )
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 18.dp)
    ) {
        DdgoPrimaryButton(
            text = buttonLabel,
            onClick = onContinue,
            enabled = canContinue,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            variant = buttonVariant,
            isLoading = isLoading
        )

        Text(
            text = helperText,
            modifier = Modifier.fillMaxWidth(),
            color = OnboardingTokens.GraphiteMuted,
            fontFamily = PretendardFamily
        )
    }
}

@Composable
private fun BoxScope.OnboardingBackground() {
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 40.dp)
            .size(320.dp)
            .clip(CircleShape)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(DdgoColorTokens.BrandBlue.copy(alpha = 0.12f), Color.Transparent)
                )
            )
    )

    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = 140.dp, end = 16.dp)
            .size(180.dp)
            .clip(CircleShape)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(OnboardingTokens.HoldYellow.copy(alpha = 0.16f), Color.Transparent)
                )
            )
    )
}

@Composable
private fun OnboardingTopBar(
    trackedStages: List<OnboardingStage>,
    currentStage: OnboardingStage,
    currentStageIndex: Int,
    canGoBack: Boolean,
    onBack: () -> Unit,
    showSkip: Boolean,
    onSkip: () -> Unit
) {
    val currentProgressIndex = if (currentStage == OnboardingStage.Summary) {
        trackedStages.lastIndex
    } else {
        trackedStages.indexOf(currentStage).coerceAtLeast(currentStageIndex - 1).coerceAtLeast(0)
    }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (canGoBack) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowBack,
                        contentDescription = "뒤로가기",
                        tint = OnboardingTokens.Graphite
                    )
                }
            } else {
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(48.dp))
            }

            Text(
                text = "디디고",
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = OnboardingTokens.Graphite,
                fontFamily = PretendardFamily,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )

            if (showSkip) {
                DdgoTextButton(
                    text = "건너뛰기",
                    onClick = onSkip,
                    tone = DdgoTextButtonTone.Neutral
                )
            } else {
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(48.dp))
            }
        }

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(18.dp))

        OnboardingProgressBar(
            totalCount = trackedStages.size,
            currentIndex = currentProgressIndex
        )
    }
}

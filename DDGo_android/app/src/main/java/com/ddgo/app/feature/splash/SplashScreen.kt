package com.ddgo.app.feature.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp as lerpDp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ddgo.app.R
import com.ddgo.app.core.ui.theme.PretendardFamily
import com.ddgo.app.feature.onboarding.OnboardingMode
import kotlin.math.sqrt

private const val SplashIntroDurationMs = 1830
private const val SplashFinalHoldMs = 120L
private const val SplashFrameWidth = 375f
private const val SplashFrameHeight = 812f
private const val LogoStartX = 104f
private const val LogoStartY = 260f
private const val LogoStartWidth = 152.4289093017578f
private const val LogoStartHeight = 99.0002212524414f
private const val LogoFinalX = -598f
private const val LogoFinalY = -196.4852752685547f
private const val LogoFinalWidth = 1597.857177734375f
private const val LogoFinalScale = LogoFinalWidth / LogoStartWidth

private const val FigmaSpringStiffness = 129.5f
private const val FigmaSpringDamping = 13.33f
private const val FigmaSpringMass = 1f
private val FigmaSpringDampingRatio =
    FigmaSpringDamping / (2f * sqrt(FigmaSpringStiffness * FigmaSpringMass))

private val SplashWordmarkBlack = Color(0xFF111319)

@Composable
fun SplashScreen(
    onNavigateToWelcome: () -> Unit,
    onNavigateToLoginEmail: () -> Unit,
    onNavigateToMain: () -> Unit,
    onNavigateToOnboarding: (String, OnboardingMode) -> Unit,
    viewModel: SplashViewModel = hiltViewModel()
) {
    val introProgress = remember { Animatable(0f) }
    val logoExpandProgress = remember { Animatable(0f) }
    var pendingNavigation by remember { mutableStateOf<SplashNavigationEvent?>(null) }
    var animationFinished by remember { mutableStateOf(false) }
    var hasNavigated by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.navigationEvent.collect { event ->
            pendingNavigation = event
        }
    }

    LaunchedEffect(Unit) {
        introProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = SplashIntroDurationMs,
                easing = LinearEasing
            )
        )
        logoExpandProgress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = FigmaSpringDampingRatio,
                stiffness = FigmaSpringStiffness,
                visibilityThreshold = 0.001f
            )
        )
        kotlinx.coroutines.delay(SplashFinalHoldMs)
        animationFinished = true
    }

    LaunchedEffect(animationFinished, pendingNavigation, hasNavigated) {
        val destination = pendingNavigation
        if (!animationFinished || destination == null || hasNavigated) {
            return@LaunchedEffect
        }

        hasNavigated = true
        when (destination) {
            is SplashNavigationEvent.NavigateToWelcome -> onNavigateToWelcome()
            is SplashNavigationEvent.NavigateToLoginEmail -> onNavigateToLoginEmail()
            is SplashNavigationEvent.NavigateToMain -> onNavigateToMain()
            is SplashNavigationEvent.NavigateToOnboarding -> {
                onNavigateToOnboarding(destination.nextRoute, destination.mode)
            }
        }
    }

    SplashMotionScene(
        introProgress = introProgress.value,
        logoExpandProgress = logoExpandProgress.value
    )
}

@Composable
private fun SplashMotionScene(
    introProgress: Float,
    logoExpandProgress: Float
) {
    val logoPainter = painterResource(id = R.drawable.ic_ddgo_logo)
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        val logoReveal = windowProgress(introProgress, 0.67f, 0.74f)
        val startWidth = maxWidth * (LogoStartWidth / SplashFrameWidth)
        val startHeight = maxHeight * (LogoStartHeight / SplashFrameHeight)
        val startX = maxWidth * (LogoStartX / SplashFrameWidth)
        val startY = maxHeight * (LogoStartY / SplashFrameHeight)
        val finalX = maxWidth * (LogoFinalX / SplashFrameWidth)
        val finalY = maxHeight * (LogoFinalY / SplashFrameHeight)

        val baseLogoY = when {
            introProgress < 0.67f -> startY + 8.dp
            introProgress < 0.74f -> lerpDp(startY + 8.dp, startY, logoReveal)
            else -> startY
        }
        val expandProgressClamped = logoExpandProgress.coerceAtLeast(0f)
        val translationX = with(density) { lerpDp(0.dp, finalX - startX, expandProgressClamped).toPx() }
        val translationY = with(density) { lerpDp(0.dp, finalY - startY, expandProgressClamped).toPx() }
        val scale = lerpFloat(1f, LogoFinalScale, expandProgressClamped)
        val wordmarkTone = logoExpandProgress.coerceIn(0f, 1f)

        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = logoPainter,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = startX, y = baseLogoY)
                    .size(width = startWidth, height = startHeight)
                    .graphicsLayer {
                        alpha = logoReveal.coerceIn(0f, 1f)
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = scale
                        scaleY = scale
                        this.translationX = translationX
                        this.translationY = translationY
                    }
            )

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                DdgoWordmark(
                    introProgress = introProgress,
                    color = lerpColor(SplashWordmarkBlack, Color.White, wordmarkTone)
                )
            }
        }
    }
}

@Composable
private fun DdgoWordmark(
    introProgress: Float,
    color: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SplashSyllable(
            text = "디",
            color = color,
            alpha = windowProgress(introProgress, 0.27f, 0.39f),
            offsetX = lerpDp((-18).dp, 0.dp, windowProgress(introProgress, 0.27f, 0.52f)),
            offsetY = lerpDp((-10).dp, 0.dp, windowProgress(introProgress, 0.27f, 0.52f)),
            rotationZ = lerpFloat(-14f, 0f, windowProgress(introProgress, 0.27f, 0.52f)),
            scale = lerpFloat(0.92f, 1f, windowProgress(introProgress, 0.27f, 0.52f))
        )
        SplashSyllable(
            text = "디",
            color = color,
            alpha = windowProgress(introProgress, 0.35f, 0.47f),
            offsetX = 0.dp,
            offsetY = lerpDp(6.dp, 0.dp, windowProgress(introProgress, 0.35f, 0.55f)),
            rotationZ = lerpFloat(10f, 0f, windowProgress(introProgress, 0.35f, 0.55f)),
            scale = lerpFloat(0.94f, 1f, windowProgress(introProgress, 0.35f, 0.55f))
        )
        SplashSyllable(
            text = "고",
            color = color,
            alpha = windowProgress(introProgress, 0.43f, 0.55f),
            offsetX = lerpDp(18.dp, 0.dp, windowProgress(introProgress, 0.43f, 0.61f)),
            offsetY = lerpDp((-4).dp, 0.dp, windowProgress(introProgress, 0.43f, 0.61f)),
            rotationZ = lerpFloat(12f, 0f, windowProgress(introProgress, 0.43f, 0.61f)),
            scale = lerpFloat(0.94f, 1f, windowProgress(introProgress, 0.43f, 0.61f))
        )
    }
}

@Composable
private fun SplashSyllable(
    text: String,
    color: Color,
    alpha: Float,
    offsetX: Dp,
    offsetY: Dp,
    rotationZ: Float,
    scale: Float
) {
    Text(
        text = text,
        modifier = Modifier
            .offset(x = offsetX, y = offsetY)
            .graphicsLayer {
                this.alpha = alpha
                this.rotationZ = rotationZ
                scaleX = scale
                scaleY = scale
            },
        style = TextStyle(
            fontFamily = PretendardFamily,
            fontWeight = FontWeight.Black,
            fontSize = 54.sp,
            letterSpacing = (-1.5).sp,
            color = color
        )
    )
}

private fun windowProgress(
    progress: Float,
    start: Float,
    end: Float,
    easing: androidx.compose.animation.core.Easing = FastOutSlowInEasing
): Float {
    if (progress <= start) return 0f
    if (progress >= end) return 1f
    return easing.transform((progress - start) / (end - start))
}

private fun lerpFloat(start: Float, end: Float, progress: Float): Float {
    return start + ((end - start) * progress)
}

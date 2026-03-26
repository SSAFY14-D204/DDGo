package com.ddgo.app.feature.auth

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.ddgo.app.R
import com.ddgo.app.core.ui.atom.DdgoPrimaryButton
import com.ddgo.app.core.ui.components.SafeAreaScreen
import com.ddgo.app.core.ui.theme.PretendardFamily
import kotlin.math.abs
import kotlinx.coroutines.delay

private const val IntroSlideState = -1
private const val IntroHoldDurationMs = 1_500L
private const val FeatureAutoAdvanceDurationMs = 2_800L

private enum class WelcomeTransitionDirection {
    Forward,
    Backward
}

private data class WelcomeFeatureSlide(
    @DrawableRes val iconResId: Int,
    val headline: String,
    val highlight: String,
    val accentColor: Color
)

@Composable
fun AuthLandingScreen(
    viewModel: AuthViewModel,
    onRegisterClick: () -> Unit,
    onLoginClick: () -> Unit
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val swipeThresholdPx = with(density) { 56.dp.toPx() }
    val slides = remember {
        listOf(
            WelcomeFeatureSlide(
                iconResId = R.drawable.ic_records,
                headline = AuthStrings.WelcomeFeatureAnalysis,
                highlight = AuthStrings.WelcomeHighlightAnalysis,
                accentColor = Color(0xFF53A6FF)
            ),
            WelcomeFeatureSlide(
                iconResId = R.drawable.ic_climbing,
                headline = AuthStrings.WelcomeFeatureRealtime,
                highlight = AuthStrings.WelcomeHighlightRealtime,
                accentColor = Color(0xFF8A4E20)
            ),
            WelcomeFeatureSlide(
                iconResId = R.drawable.ic_record,
                headline = AuthStrings.WelcomeFeatureVideo,
                highlight = AuthStrings.WelcomeHighlightVideo,
                accentColor = Color(0xFFFF4D73)
            ),
            WelcomeFeatureSlide(
                iconResId = R.drawable.ic_calendar,
                headline = AuthStrings.WelcomeFeatureGrowth,
                highlight = AuthStrings.WelcomeHighlightGrowth,
                accentColor = Color(0xFF65B969)
            )
        )
    }

    var showIntro by rememberSaveable { mutableStateOf(true) }
    var currentSlideIndex by rememberSaveable { mutableIntStateOf(0) }
    var autoPlayNonce by remember { mutableIntStateOf(0) }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var transitionDirection by remember { mutableStateOf(WelcomeTransitionDirection.Forward) }

    LaunchedEffect(Unit) {
        viewModel.markWelcomeSeen()
        delay(IntroHoldDurationMs)
        showIntro = false
    }

    LaunchedEffect(showIntro, currentSlideIndex, autoPlayNonce, isDragging) {
        if (showIntro || isDragging) return@LaunchedEffect
        delay(FeatureAutoAdvanceDurationMs)
        transitionDirection = WelcomeTransitionDirection.Forward
        currentSlideIndex = (currentSlideIndex + 1) % slides.size
    }

    SafeAreaScreen(
        modifier = Modifier
            .background(Color.White)
            .padding(horizontal = 18.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(showIntro, currentSlideIndex, slides.size, swipeThresholdPx) {
                        if (showIntro) return@pointerInput

                        detectHorizontalDragGestures(
                            onDragStart = {
                                isDragging = true
                                dragDistance = 0f
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                dragDistance += dragAmount
                            },
                            onDragEnd = {
                                isDragging = false
                                if (abs(dragDistance) >= swipeThresholdPx) {
                                    val isNext = dragDistance < 0f
                                    transitionDirection = if (isNext) {
                                        WelcomeTransitionDirection.Forward
                                    } else {
                                        WelcomeTransitionDirection.Backward
                                    }
                                    currentSlideIndex = if (isNext) {
                                        (currentSlideIndex + 1) % slides.size
                                    } else {
                                        (currentSlideIndex - 1 + slides.size) % slides.size
                                    }
                                    autoPlayNonce += 1
                                }
                                dragDistance = 0f
                            },
                            onDragCancel = {
                                isDragging = false
                                dragDistance = 0f
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = if (showIntro) IntroSlideState else currentSlideIndex,
                    transitionSpec = {
                        if (initialState == IntroSlideState || targetState == IntroSlideState) {
                            fadeIn(animationSpec = tween(durationMillis = 300)) togetherWith
                                fadeOut(animationSpec = tween(durationMillis = 220))
                        } else {
                            val isForward = transitionDirection == WelcomeTransitionDirection.Forward
                            (
                                slideInHorizontally(
                                    animationSpec = tween(durationMillis = 420)
                                ) { fullWidth -> if (isForward) fullWidth else -fullWidth } +
                                    fadeIn(animationSpec = tween(durationMillis = 320))
                                ).togetherWith(
                                    slideOutHorizontally(
                                        animationSpec = tween(durationMillis = 320)
                                    ) { fullWidth -> if (isForward) -fullWidth else fullWidth } +
                                        fadeOut(animationSpec = tween(durationMillis = 220))
                                    )
                                .using(SizeTransform(clip = false))
                        }
                    },
                    label = "welcome_content"
                ) { targetState ->
                    if (targetState == IntroSlideState) {
                        WelcomeIntroContent()
                    } else {
                        WelcomeFeatureContent(
                            slide = slides[targetState],
                            activeIndex = targetState,
                            slideCount = slides.size
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = !showIntro,
                enter = fadeIn(animationSpec = tween(durationMillis = 220)),
                exit = fadeOut(animationSpec = tween(durationMillis = 150))
            ) {
                WelcomeActionSection(
                    onRegisterClick = onRegisterClick,
                    onLoginClick = onLoginClick
                )
            }
        }
    }
}

@Composable
private fun WelcomeIntroContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        DdgoMascotMark(
            modifier = Modifier
                .width(110.dp)
                .height(72.dp)
        )
        Spacer(modifier = Modifier.height(36.dp))
        Text(
            text = AuthStrings.WelcomeIntroTitle,
            style = TextStyle(
                fontFamily = PretendardFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 28.sp,
                lineHeight = 39.sp,
                letterSpacing = (-0.28).sp,
                color = Color(0xFF0B0B0E),
                textAlign = TextAlign.Center
            )
        )
    }
}

@Composable
private fun WelcomeFeatureContent(
    slide: WelcomeFeatureSlide,
    activeIndex: Int,
    slideCount: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(id = slide.iconResId),
            contentDescription = null,
            tint = slide.accentColor,
            modifier = Modifier.size(38.dp)
        )
        Spacer(modifier = Modifier.height(22.dp))
        Text(
            text = highlightedHeadline(
                text = slide.headline,
                highlight = slide.highlight,
                accentColor = slide.accentColor
            ),
            style = TextStyle(
                fontFamily = PretendardFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 28.sp,
                lineHeight = 39.sp,
                letterSpacing = (-0.28).sp,
                color = Color(0xFF0B0B0E),
                textAlign = TextAlign.Center
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 90.dp)
                .padding(horizontal = 18.dp)
        )
        Spacer(modifier = Modifier.height(22.dp))
        WelcomePhoneMockup()
        Spacer(modifier = Modifier.height(22.dp))
        WelcomeIndicator(
            activeIndex = activeIndex,
            slideCount = slideCount
        )
    }
}

@Composable
private fun WelcomePhoneMockup() {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .width(200.dp)
            .height(414.dp)
            .shadow(
                elevation = 28.dp,
                shape = RoundedCornerShape(48.dp),
                ambientColor = Color(0x1A6A707C),
                spotColor = Color(0x1A6A707C)
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.welcome_phone_screen),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(40.dp))
        )
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(R.raw.welcome_phone_shell)
                .decoderFactory(SvgDecoder.Factory())
                .build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun WelcomeIndicator(
    activeIndex: Int,
    slideCount: Int
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(slideCount) { index ->
            Box(
                modifier = Modifier
                    .width(if (index == activeIndex) 18.dp else 6.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(
                        if (index == activeIndex) Color(0xFF19191C) else Color(0xFFD5D5D5)
                    )
            )
        }
    }
}

@Composable
private fun WelcomeActionSection(
    onRegisterClick: () -> Unit,
    onLoginClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DdgoPrimaryButton(
            text = AuthStrings.WelcomeRegister,
            onClick = onRegisterClick,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = AuthStrings.WelcomeLoginQuestion,
                color = Color(0xFF505050),
                fontFamily = PretendardFamily,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = AuthStrings.WelcomeLoginAction,
                modifier = Modifier.clickable(onClick = onLoginClick),
                color = Color(0xFF53A6FF),
                fontFamily = PretendardFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
        }
    }
}

private fun highlightedHeadline(
    text: String,
    highlight: String,
    accentColor: Color
): AnnotatedString {
    val startIndex = text.indexOf(highlight)
    if (startIndex < 0) return AnnotatedString(text)

    return buildAnnotatedString {
        append(text)
        addStyle(
            style = SpanStyle(color = accentColor),
            start = startIndex,
            end = startIndex + highlight.length
        )
    }
}

@Composable
private fun DdgoMascotMark(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(R.raw.main_dd_icon)
            .decoderFactory(SvgDecoder.Factory())
            .build(),
        contentDescription = null,
        modifier = modifier
    )
}

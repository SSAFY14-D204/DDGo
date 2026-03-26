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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
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

private data class WelcomeLayoutProfile(
    val introTopPadding: Dp,
    val introLogoWidth: Dp,
    val introLogoHeight: Dp,
    val introLogoSpacing: Dp,
    val introTitleFontSize: TextUnit,
    val introTitleLineHeight: TextUnit,
    val featureTopPadding: Dp,
    val featureSpacing: Dp,
    val featureIconSize: Dp,
    val featureTitleFontSize: TextUnit,
    val featureTitleLineHeight: TextUnit,
    val featureTitleMinHeight: Dp,
    val phoneWidth: Dp,
    val phoneHeight: Dp,
    val phoneShadowElevation: Dp
)

@Composable
fun AuthLandingScreen(
    viewModel: AuthViewModel,
    onRegisterClick: () -> Unit,
    onLoginClick: () -> Unit
) {
    val density = LocalDensity.current
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
            BoxWithConstraints(
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
                val layoutProfile = remember(maxHeight, density.fontScale) {
                    resolveWelcomeLayoutProfile(
                        availableHeight = maxHeight,
                        fontScale = density.fontScale
                    )
                }

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
                        WelcomeIntroContent(layoutProfile = layoutProfile)
                    } else {
                        WelcomeFeatureContent(
                            slide = slides[targetState],
                            activeIndex = targetState,
                            slideCount = slides.size,
                            layoutProfile = layoutProfile
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
private fun WelcomeIntroContent(
    layoutProfile: WelcomeLayoutProfile
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = layoutProfile.introTopPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        DdgoMascotMark(
            modifier = Modifier
                .width(layoutProfile.introLogoWidth)
                .height(layoutProfile.introLogoHeight)
        )
        Spacer(modifier = Modifier.height(layoutProfile.introLogoSpacing))
        Text(
            text = AuthStrings.WelcomeIntroTitle,
            style = TextStyle(
                fontFamily = PretendardFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = layoutProfile.introTitleFontSize,
                lineHeight = layoutProfile.introTitleLineHeight,
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
    slideCount: Int,
    layoutProfile: WelcomeLayoutProfile
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = layoutProfile.featureTopPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(id = slide.iconResId),
            contentDescription = null,
            tint = slide.accentColor,
            modifier = Modifier.size(layoutProfile.featureIconSize)
        )
        Spacer(modifier = Modifier.height(layoutProfile.featureSpacing))
        Text(
            text = highlightedHeadline(
                text = slide.headline,
                highlight = slide.highlight,
                accentColor = slide.accentColor
            ),
            style = TextStyle(
                fontFamily = PretendardFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = layoutProfile.featureTitleFontSize,
                lineHeight = layoutProfile.featureTitleLineHeight,
                letterSpacing = (-0.28).sp,
                color = Color(0xFF0B0B0E),
                textAlign = TextAlign.Center
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = layoutProfile.featureTitleMinHeight)
                .padding(horizontal = 18.dp)
        )
        Spacer(modifier = Modifier.height(layoutProfile.featureSpacing))
        WelcomePhoneMockup(layoutProfile = layoutProfile)
        Spacer(modifier = Modifier.height(layoutProfile.featureSpacing))
        WelcomeIndicator(
            activeIndex = activeIndex,
            slideCount = slideCount
        )
    }
}

@Composable
private fun WelcomePhoneMockup(
    layoutProfile: WelcomeLayoutProfile
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .width(layoutProfile.phoneWidth)
            .height(layoutProfile.phoneHeight)
            .shadow(
                elevation = layoutProfile.phoneShadowElevation,
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

private fun resolveWelcomeLayoutProfile(
    availableHeight: Dp,
    fontScale: Float
): WelcomeLayoutProfile {
    return when {
        availableHeight < 520.dp || fontScale >= 1.32f -> WelcomeLayoutProfile(
            introTopPadding = 16.dp,
            introLogoWidth = 94.dp,
            introLogoHeight = 62.dp,
            introLogoSpacing = 24.dp,
            introTitleFontSize = 24.sp,
            introTitleLineHeight = 34.sp,
            featureTopPadding = 12.dp,
            featureSpacing = 12.dp,
            featureIconSize = 32.dp,
            featureTitleFontSize = 24.sp,
            featureTitleLineHeight = 34.sp,
            featureTitleMinHeight = 68.dp,
            phoneWidth = 154.dp,
            phoneHeight = 319.dp,
            phoneShadowElevation = 18.dp
        )

        availableHeight < 580.dp || fontScale >= 1.2f -> WelcomeLayoutProfile(
            introTopPadding = 18.dp,
            introLogoWidth = 100.dp,
            introLogoHeight = 66.dp,
            introLogoSpacing = 28.dp,
            introTitleFontSize = 26.sp,
            introTitleLineHeight = 36.sp,
            featureTopPadding = 14.dp,
            featureSpacing = 14.dp,
            featureIconSize = 34.dp,
            featureTitleFontSize = 26.sp,
            featureTitleLineHeight = 36.sp,
            featureTitleMinHeight = 72.dp,
            phoneWidth = 170.dp,
            phoneHeight = 352.dp,
            phoneShadowElevation = 22.dp
        )

        availableHeight < 640.dp || fontScale >= 1.1f -> WelcomeLayoutProfile(
            introTopPadding = 20.dp,
            introLogoWidth = 104.dp,
            introLogoHeight = 68.dp,
            introLogoSpacing = 32.dp,
            introTitleFontSize = 28.sp,
            introTitleLineHeight = 39.sp,
            featureTopPadding = 16.dp,
            featureSpacing = 18.dp,
            featureIconSize = 36.dp,
            featureTitleFontSize = 28.sp,
            featureTitleLineHeight = 39.sp,
            featureTitleMinHeight = 78.dp,
            phoneWidth = 186.dp,
            phoneHeight = 385.dp,
            phoneShadowElevation = 24.dp
        )

        else -> WelcomeLayoutProfile(
            introTopPadding = 24.dp,
            introLogoWidth = 110.dp,
            introLogoHeight = 72.dp,
            introLogoSpacing = 36.dp,
            introTitleFontSize = 28.sp,
            introTitleLineHeight = 39.sp,
            featureTopPadding = 20.dp,
            featureSpacing = 22.dp,
            featureIconSize = 38.dp,
            featureTitleFontSize = 28.sp,
            featureTitleLineHeight = 39.sp,
            featureTitleMinHeight = 78.dp,
            phoneWidth = 200.dp,
            phoneHeight = 414.dp,
            phoneShadowElevation = 28.dp
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

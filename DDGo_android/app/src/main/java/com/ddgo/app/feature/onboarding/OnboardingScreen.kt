package com.ddgo.app.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.ddgo.app.R
import com.ddgo.app.core.ui.components.SafeAreaScreen
import com.ddgo.app.core.ui.components.SvgAssetImage
import com.ddgo.app.domain.model.NearbyPlace
import com.ddgo.app.feature.profile.model.ProfileSexOption

enum class OnboardingStage {
    Hero,
    Gym,
    ClimbingProfile,
    Goal,
    BodyProfile,
    Summary
}

private enum class FigmaOnboardingStep {
    Sex,
    Intro,
    Height,
    Weight,
    Wingspan,
    Gym,
    Nickname,
    Complete
}

private enum class MeasurementRulerKind {
    Height,
    Wingspan,
    Weight
}

private data class MeasurementRulerConfig(
    val containerHeight: androidx.compose.ui.unit.Dp,
    val tickWidth: androidx.compose.ui.unit.Dp,
    val labelWidth: androidx.compose.ui.unit.Dp,
    val rulerTopPadding: androidx.compose.ui.unit.Dp,
    val minorTickHeight: androidx.compose.ui.unit.Dp,
    val majorTickHeight: androidx.compose.ui.unit.Dp,
    val labelTopPadding: androidx.compose.ui.unit.Dp,
    val majorEvery: Int,
    val labelEvery: Int,
    val minorTickColor: Color,
    val majorTickColor: Color,
    val labelColor: Color,
    val labelFormatter: (Int) -> String
)

private val OnboardingBlue = Color(0xFF53A6FF)
private val OnboardingBlack = Color(0xFF0B0B0E)
private val OnboardingGray = Color(0xFF505050)
private val OnboardingLightGray = Color(0xFF999999)
private val OnboardingMediumGray = Color(0xFF8C8C8C)
private val OnboardingWhiteGray = Color(0xFFF0F3F5)
private val OnboardingGreen = Color(0xFF65B969)
private val OnboardingPurple = Color(0xFF8458FF)
private val OnboardingIntroGlow = Brush.radialGradient(
    colors = listOf(
        Color(0x6653A6FF),
        Color(0x448458FF),
        Color.Transparent
    )
)
private val OnboardingResultGlow = Brush.linearGradient(
    colors = listOf(Color(0xFFB8D8FF), Color(0xFFE4D7FF))
)
private val OnboardingCtaGradient = Brush.linearGradient(
    colors = listOf(Color(0xFF42A7FF), Color(0xFF8458FF))
)

private const val INTRO_SCREEN_ASSET = "file:///android_asset/onboarding/onboarding1.png"
private const val SEX_LEFT_ASSET = "file:///android_asset/onboarding/sex_male.svg"
private const val SEX_RIGHT_ASSET = "file:///android_asset/onboarding/sex_female.svg"
private const val COMPLETE_GLOW_ASSET = "file:///android_asset/onboarding/complete_glow.svg"
private const val COMPLETE_FEMALE_ASSET = "file:///android_asset/onboarding/complete_female.svg"
private const val COMPLETE_MALE_ASSET = "file:///android_asset/onboarding/complete_male.svg"

@Composable
fun OnboardingScreen(
    sessionKey: String,
    initialStepKey: String? = null,
    showEntryGuide: Boolean,
    onExit: () -> Unit,
    onFinish: () -> Unit,
    viewModel: FigmaOnboardingViewModel = hiltViewModel()
) {
    val steps = remember { FigmaOnboardingStep.entries }
    val initialStepIndex = remember(initialStepKey, steps) {
        steps.indexOfFirst { it.name.equals(initialStepKey, ignoreCase = true) }
            .takeIf { it >= 0 }
            ?: 0
    }
    var currentStepIndex by rememberSaveable(sessionKey, initialStepKey) {
        mutableIntStateOf(initialStepIndex)
    }
    val currentStep = steps[currentStepIndex]

    LaunchedEffect(Unit) {
        viewModel.prepare()
    }

    fun moveNext() {
        if (currentStepIndex < steps.lastIndex) {
            currentStepIndex += 1
        }
    }

    fun moveBack() {
        if (currentStepIndex > 0) {
            currentStepIndex -= 1
        } else {
            onExit()
        }
    }

    BackHandler {
        moveBack()
    }

    SafeAreaScreen(
        modifier = Modifier.background(Color.White),
        applyBottomInset = false
    ) {
        when (currentStep) {
            FigmaOnboardingStep.Sex -> SexStepScreen(
                selectedSex = viewModel.sex,
                onBack = onExit,
                onSelect = { option ->
                    viewModel.selectSex(option)
                    moveNext()
                }
            )

            FigmaOnboardingStep.Intro -> IntroStepScreen(onContinue = ::moveNext)

            FigmaOnboardingStep.Height -> MeasurementStepScreen(
                title = "키가 몇인가요?",
                displayValueText = viewModel.heightCm.toString(),
                unit = "cm",
                tickRange = HEIGHT_RANGE,
                selectedTick = viewModel.heightCm,
                rulerKind = MeasurementRulerKind.Height,
                progressStep = 0,
                onBack = ::moveBack,
                onTickChange = viewModel::setHeight,
                onContinue = ::moveNext
            )

            FigmaOnboardingStep.Weight -> MeasurementStepScreen(
                title = "현재 몸무게가 몇 인가요?",
                displayValueText = viewModel.weightDisplayText(),
                unit = "kg",
                tickRange = WEIGHT_TENTHS_RANGE,
                selectedTick = viewModel.weightTenthsKg,
                rulerKind = MeasurementRulerKind.Weight,
                progressStep = 1,
                onBack = ::moveBack,
                onTickChange = viewModel::setWeightTenths,
                onContinue = ::moveNext
            )

            FigmaOnboardingStep.Wingspan -> MeasurementStepScreen(
                title = "윙스팬을 알려주세요",
                displayValueText = viewModel.wingspanCm.toString(),
                unit = "cm",
                tickRange = WINGSPAN_RANGE,
                selectedTick = viewModel.wingspanCm,
                rulerKind = MeasurementRulerKind.Wingspan,
                progressStep = 2,
                onBack = ::moveBack,
                onTickChange = viewModel::setWingspan,
                helperDescription = "양팔을 양옆으로 쫙 펼쳤을 때의 길이에요",
                quickActionLabel = "윙스팬을 몰라도 괜찮아요. 키와 동일하게 설정할게요.",
                onQuickAction = viewModel::applyHeightToWingspan,
                isLoading = viewModel.isSavingProfile,
                errorMessage = viewModel.profileErrorMessage,
                onContinue = {
                    viewModel.saveBodyProfile(onSuccess = ::moveNext)
                }
            )

            FigmaOnboardingStep.Gym -> GymStepScreen(
                query = viewModel.gymSearchQuery,
                searchState = viewModel.gymSearchUiState,
                resolveState = viewModel.gymResolveUiState,
                selectedPlaceId = viewModel.selectedNearbyPlace?.externalPlaceId,
                onBack = ::moveBack,
                onQueryChange = viewModel::updateGymSearchQuery,
                onSearch = viewModel::triggerGymSearch,
                onSelectPlace = viewModel::selectGymPlace,
                onContinue = ::moveNext
            )

            FigmaOnboardingStep.Nickname -> NicknameStepScreen(
                nickname = viewModel.nicknameInput,
                recommendedNickname = viewModel.recommendedNickname,
                feedback = viewModel.nicknameFeedback,
                showRecommendation = viewModel.shouldShowRecommendedNickname(),
                canContinue = viewModel.canContinueNickname(),
                isLoading = viewModel.isSavingNickname,
                onBack = ::moveBack,
                onNicknameChange = viewModel::updateNicknameInput,
                onRecommendationClick = viewModel::applyRecommendedNickname,
                onContinue = {
                    viewModel.saveNickname(onSuccess = ::moveNext)
                }
            )

            FigmaOnboardingStep.Complete -> CompleteStepScreen(
                sex = viewModel.sex ?: ProfileSexOption.Female,
                nickname = viewModel.completionNickname(),
                heightCm = viewModel.heightCm,
                weightText = viewModel.weightSummaryText(),
                wingspanCm = viewModel.wingspanCm,
                isLoading = viewModel.isCompletingFlow,
                onContinue = {
                    viewModel.completeOnboarding(
                        showEntryGuide = showEntryGuide,
                        onSuccess = onFinish
                    )
                }
            )
        }
    }
}

@Composable
private fun SexStepScreen(
    selectedSex: ProfileSexOption?,
    onBack: () -> Unit,
    onSelect: (ProfileSexOption) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        val scale = maxWidth / 412.dp
        val titleFontSize = 28.sp * scale
        val titleLineHeight = 36.sp * scale
        val iconSize = 24.dp * scale
        val density = LocalDensity.current
        val topInsetPx = WindowInsets.statusBars.getTop(density)
        val topInset = with(density) { topInsetPx.toDp() }

        fun figmaTop(y: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.Dp {
            return (y * scale) - topInset
        }

        Icon(
            painter = painterResource(id = R.drawable.ic_back_arrow),
            contentDescription = "뒤로가기",
            tint = OnboardingBlack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 22.dp * scale, top = figmaTop(78.dp))
                .size(iconSize)
                .clickable { onBack() }
        )

        Text(
            text = "성별이 어떻게 되시나요?",
            style = TextStyle(
                fontSize = titleFontSize,
                fontWeight = FontWeight.SemiBold,
                color = OnboardingBlack,
                lineHeight = titleLineHeight,
                textAlign = TextAlign.Center
            ),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = figmaTop(317.dp))
                .width(279.dp * scale)
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = figmaTop(446.dp)),
            horizontalArrangement = Arrangement.spacedBy(59.dp * scale)
        ) {
            SvgAssetImage(
                assetPath = SEX_LEFT_ASSET,
                contentDescription = "남성",
                modifier = Modifier
                    .size(width = 110.857.dp * scale, height = 72.dp * scale)
                    .clickable { onSelect(ProfileSexOption.Male) }
            )
            SvgAssetImage(
                assetPath = SEX_RIGHT_ASSET,
                contentDescription = "여성",
                modifier = Modifier
                    .size(width = 113.846.dp * scale, height = 72.dp * scale)
                    .clickable { onSelect(ProfileSexOption.Female) }
            )
        }
    }
}

@Composable
private fun IntroStepScreen(onContinue: () -> Unit) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        val scale = maxWidth / 412.dp

        AsyncImage(
            model = INTRO_SCREEN_ASSET,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(width = 412.dp * scale, height = 892.dp * scale)
        )

        Text(
            text = "이제 나에게 꼭 맞는\n클라이밍 분석을 받아볼 차례예요",
            style = TextStyle(
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 36.sp,
                color = Color.Transparent,
                textAlign = TextAlign.Center
            ),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 330.dp * scale)
                .width(412.dp * scale)
        )
        Text(
            text = "여러분의 클라이밍 목표를\n달성할 수 있도록\n몇 가지 질문을 준비했어요.",
            style = TextStyle(
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 28.sp,
                color = Color.Transparent,
                textAlign = TextAlign.Center
            ),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 454.dp * scale)
                .width(279.dp * scale)
        )
        SolidCtaButton(
            label = "좋아요!",
            onClick = onContinue,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    start = 15.dp * scale,
                    end = 15.dp * scale,
                    bottom = 45.dp * scale
                )
        )
    }
}

@Composable
private fun MeasurementStepScreen(
    title: String,
    displayValueText: String,
    unit: String,
    tickRange: IntRange,
    selectedTick: Int,
    rulerKind: MeasurementRulerKind,
    progressStep: Int,
    onBack: () -> Unit,
    onTickChange: (Int) -> Unit,
    onContinue: () -> Unit,
    helperTitle: String? = null,
    helperDescription: String? = null,
    quickActionLabel: String? = null,
    onQuickAction: (() -> Unit)? = null,
    isLoading: Boolean = false,
    errorMessage: String? = null
) {
    val isWingspan = rulerKind == MeasurementRulerKind.Wingspan
    val valueTopPadding = when (rulerKind) {
        MeasurementRulerKind.Height -> 156.dp
        MeasurementRulerKind.Weight -> 156.dp
        MeasurementRulerKind.Wingspan -> 156.dp
    }

    QuestionShell(
        title = title,
        progressStep = progressStep,
        buttonLabel = "다음",
        onBack = onBack,
        onContinue = onContinue,
        isLoading = isLoading,
        isContinueEnabled = !isLoading
    ) {
        if (isWingspan && !helperDescription.isNullOrBlank()) {
            Text(
                text = helperDescription,
                style = TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Normal,
                    lineHeight = 21.sp,
                    color = Color(0xFF8C8C8F)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, end = 12.dp),
                textAlign = TextAlign.Start
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(valueTopPadding))

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = displayValueText,
                    style = TextStyle(
                        fontSize = 57.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OnboardingBlack,
                        lineHeight = 60.sp
                    )
                )
                Text(
                    text = unit,
                    style = TextStyle(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Normal,
                        color = OnboardingGray
                    ),
                    modifier = Modifier.padding(start = 8.dp, bottom = 7.dp)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp)
            ) {
                val rulerWidth = when (rulerKind) {
                    MeasurementRulerKind.Height,
                    MeasurementRulerKind.Wingspan -> 500.dp
                    MeasurementRulerKind.Weight -> 500.dp
                }
                val indicatorHeight = when (rulerKind) {
                    MeasurementRulerKind.Height -> 90.dp
                    MeasurementRulerKind.Wingspan -> 90.dp
                    MeasurementRulerKind.Weight -> 90.dp
                }
                MeasurementRuler(
                    kind = rulerKind,
                    tickRange = tickRange,
                    selectedTick = selectedTick,
                    onTickChange = onTickChange,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .requiredWidth(rulerWidth)
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 0.dp)
                        .width(3.dp)
                        .height(indicatorHeight)
                        .background(OnboardingBlue, RoundedCornerShape(12.dp))
                )
            }

            if (!errorMessage.isNullOrBlank()) {
                Text(
                    text = errorMessage,
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFD95F5F),
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.padding(top = 18.dp)
                )
            }

            if (isWingspan && quickActionLabel != null && onQuickAction != null) {
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = quickActionLabel,
                    style = TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Normal,
                        lineHeight = 21.sp,
                        color = Color(0xFF8C8C8F)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onQuickAction() }
                        .padding(end = 12.dp, bottom = 14.dp),
                    textAlign = TextAlign.Start
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GymStepScreen(
    query: String,
    searchState: FigmaOnboardingGymSearchUiState,
    resolveState: FigmaOnboardingGymResolveUiState,
    selectedPlaceId: String?,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSelectPlace: (NearbyPlace) -> Unit,
    onContinue: () -> Unit
) {
    QuestionShell(
        title = "자주 가는 암장을 선택해주세요",
        progressStep = 3,
        buttonLabel = "건너뛰기",
        onBack = onBack,
        onContinue = onContinue,
        isContinueEnabled = true
    ) {
        Text(
            text = "홈짐은 언제든지 변경할 수 있어요!",
            style = TextStyle(
                fontSize = 18.sp,
                fontWeight = FontWeight.Normal,
                lineHeight = 19.sp,
                color = Color(0xFF8C8C8F)
            ),
            modifier = Modifier.padding(top = 8.dp)
        )

        UnderlineTextField(
            value = query,
            onValueChange = onQueryChange,
            hint = "",
            textAlign = TextAlign.Start,
            trailing = {
                FigmaSearchIcon(
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { onSearch() }
                )
            },
            modifier = Modifier.padding(top = 124.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Search
            ),
            keyboardActions = KeyboardActions(onSearch = { onSearch() })
        )

        when (resolveState) {
            is FigmaOnboardingGymResolveUiState.Success -> {
                Text(
                    text = "${resolveState.resolvedGym.gym.displayName}을 홈짐으로 저장할게요.",
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = OnboardingBlue
                    ),
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            is FigmaOnboardingGymResolveUiState.Error -> {
                Text(
                    text = resolveState.message,
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFD95F5F)
                    ),
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            FigmaOnboardingGymResolveUiState.Idle,
            FigmaOnboardingGymResolveUiState.Loading -> Unit
        }

        when (searchState) {
            FigmaOnboardingGymSearchUiState.Idle -> Unit
            FigmaOnboardingGymSearchUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = OnboardingBlue,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            is FigmaOnboardingGymSearchUiState.Error -> {
                Text(
                    text = searchState.message,
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFD95F5F)
                    ),
                    modifier = Modifier.padding(top = 28.dp)
                )
            }

            is FigmaOnboardingGymSearchUiState.Success -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    searchState.places.take(5).forEach { place ->
                        GymSearchRow(
                            place = place,
                            selected = place.externalPlaceId == selectedPlaceId,
                            onClick = { onSelectPlace(place) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NicknameStepScreen(
    nickname: String,
    recommendedNickname: String,
    feedback: OnboardingFieldFeedback?,
    showRecommendation: Boolean,
    canContinue: Boolean,
    isLoading: Boolean,
    onBack: () -> Unit,
    onNicknameChange: (String) -> Unit,
    onRecommendationClick: () -> Unit,
    onContinue: () -> Unit
) {
    QuestionShell(
        title = "닉네임을 입력해주세요",
        progressStep = 4,
        buttonLabel = "다음",
        onBack = onBack,
        onContinue = onContinue,
        isLoading = isLoading,
        isContinueEnabled = canContinue
    ) {
        UnderlineTextField(
            value = nickname,
            onValueChange = onNicknameChange,
            hint = recommendedNickname,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 114.dp),
            textStyle = TextStyle(
                fontSize = 28.sp,
                fontWeight = FontWeight.Normal,
                color = OnboardingBlack,
                textAlign = TextAlign.Center
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { onContinue() })
        )

        feedback?.let {
            Text(
                text = it.message,
                style = TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = when (it.tone) {
                        OnboardingFieldFeedbackTone.Neutral -> OnboardingGray
                        OnboardingFieldFeedbackTone.Success -> OnboardingGreen
                        OnboardingFieldFeedbackTone.Error -> Color(0xFFD95F5F)
                    },
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
            )
        }

        if (showRecommendation) {
            Surface(
                modifier = Modifier
                    .padding(top = 19.dp)
                    .height(26.dp)
                    .clip(RoundedCornerShape(36.dp))
                    .clickable { onRecommendationClick() },
                color = OnboardingWhiteGray,
                shape = RoundedCornerShape(36.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "추천",
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Light,
                            color = OnboardingMediumGray
                        )
                    )
                    Text(
                        text = recommendedNickname,
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Light,
                            color = OnboardingBlack
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun CompleteStepScreen(
    sex: ProfileSexOption,
    nickname: String,
    heightCm: Int,
    weightText: String,
    wingspanCm: Int,
    isLoading: Boolean,
    onContinue: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        val scale = maxWidth / 412.dp

        SvgAssetImage(
            assetPath = COMPLETE_GLOW_ASSET,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(x = (-3.74f).dp * scale, y = (-250.24f).dp * scale)
                .size(width = 470.214.dp * scale, height = 837.087.dp * scale)
                .graphicsLayer { rotationZ = 89.65f }
        )

        Text(
            text = "${nickname}님을 위한\n맞춤 분석 준비가 끝났어요!",
            style = TextStyle(
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 36.sp,
                color = OnboardingBlack,
                textAlign = TextAlign.Center
            ),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 134.dp * scale)
                .width(341.dp * scale)
        )

        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 267.dp * scale)
                .size(width = 363.dp * scale, height = 436.dp * scale),
            color = Color(0xFF0B0B0E),
            shape = RoundedCornerShape(30.dp * scale)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp * scale)
                        .size(width = 334.dp * scale, height = 307.dp * scale)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFFEAF2FF),
                                    Color.White,
                                    Color(0xFFD7E6FF)
                                ),
                                start = androidx.compose.ui.geometry.Offset(28f, 300f),
                                end = androidx.compose.ui.geometry.Offset(310f, 0f)
                            ),
                            shape = RoundedCornerShape(20.dp * scale)
                        )
                )

                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 29.dp * scale, top = 29.dp * scale),
                    color = Color(0xFF5D5D62),
                    shape = RoundedCornerShape(36.dp * scale)
                ) {
                    Text(
                        text = nickname,
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            letterSpacing = 0.48.sp
                        ),
                        modifier = Modifier.padding(
                            horizontal = 12.dp * scale,
                            vertical = 6.dp * scale
                        )
                    )
                }

                SvgAssetImage(
                    assetPath = when (sex) {
                        ProfileSexOption.Male -> COMPLETE_MALE_ASSET
                        ProfileSexOption.Female -> COMPLETE_FEMALE_ASSET
                    },
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 41.dp * scale)
                        .size(width = 234.dp * scale, height = 239.dp * scale)
                )

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(
                            start = 29.dp * scale,
                            end = 29.dp * scale,
                            bottom = 37.dp * scale
                        ),
                    horizontalArrangement = Arrangement.spacedBy(13.dp * scale)
                ) {
                    SummaryMetric(
                        label = "키",
                        value = "$heightCm cm",
                        modifier = Modifier.weight(1f)
                    )
                    SummaryMetric(
                        label = "몸무게",
                        value = "$weightText kg",
                        modifier = Modifier.weight(1f)
                    )
                    SummaryMetric(
                        label = "윙스팬",
                        value = "$wingspanCm cm",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        GradientCtaButton(
            label = "열심히 해볼게요!",
            isLoading = isLoading,
            onClick = onContinue,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    start = 15.dp * scale,
                    end = 15.dp * scale,
                    bottom = 45.dp * scale
                )
                .fillMaxWidth()
        )
    }
}

@Composable
private fun SelectionShell(
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 24.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .height(53.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_back_arrow),
                contentDescription = "뒤로가기",
                tint = OnboardingBlack,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(24.dp)
                    .clickable { onBack() }
            )
        }
        content()
    }
}

@Composable
private fun QuestionShell(
    title: String,
    progressStep: Int,
    buttonLabel: String,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    isContinueEnabled: Boolean,
    isLoading: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .height(53.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_back_arrow),
                contentDescription = "뒤로가기",
                tint = OnboardingBlack,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 24.dp)
                    .size(24.dp)
                    .clickable { onBack() }
            )
        }

        EqualProgressBar(
            currentStep = progressStep,
            totalSteps = 5
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .imePadding()
        ) {
            Text(
                text = title,
                style = TextStyle(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 36.sp,
                    color = OnboardingBlack
                ),
                modifier = Modifier.padding(top = 24.dp)
            )
            content()
        }

        SolidCtaButton(
            label = buttonLabel,
            enabled = isContinueEnabled,
            isLoading = isLoading,
            onClick = onContinue,
            modifier = Modifier.padding(start = 15.dp, end = 15.dp, bottom = 45.dp)
        )
    }
}

@Composable
private fun ResultShell(
    buttonLabel: String,
    isLoading: Boolean,
    onContinue: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 14.dp)
                .size(width = 470.dp, height = 360.dp)
                .graphicsLayer {
                    rotationZ = 14f
                    alpha = 0.85f
                }
                .background(
                    brush = OnboardingResultGlow,
                    shape = RoundedCornerShape(220.dp)
                )
                .blur(64.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            content()
            Spacer(modifier = Modifier.weight(1f))
            GradientCtaButton(
                label = buttonLabel,
                isLoading = isLoading,
                onClick = onContinue,
                modifier = Modifier
                    .padding(bottom = 45.dp)
                    .fillMaxWidth()
            )
        }
    }
}

@Composable
private fun EqualProgressBar(
    currentStep: Int,
    totalSteps: Int
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(Color(0xFFC5C7CC))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth((currentStep + 1) / totalSteps.toFloat())
                .height(4.dp)
                .background(OnboardingBlack)
        )
    }
}

@Composable
private fun SexChoice(
    assetModel: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(width = 132.dp, height = 110.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) OnboardingBlue else Color.Transparent,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = assetModel,
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun MeasurementRuler(
    kind: MeasurementRulerKind,
    tickRange: IntRange,
    selectedTick: Int,
    onTickChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier)
            .height(
                when (kind) {
                    MeasurementRulerKind.Height,
                    MeasurementRulerKind.Wingspan -> 142.dp
                    MeasurementRulerKind.Weight -> 142.dp
                }
            )
    ) {
        val config = remember(kind) {
            when (kind) {
                MeasurementRulerKind.Height -> MeasurementRulerConfig(
                    containerHeight = 142.dp,
                    tickWidth = 7.3.dp,
                    labelWidth = 36.dp,
                    rulerTopPadding = 32.dp,
                    minorTickHeight = 40.dp,
                    majorTickHeight = 56.dp,
                    labelTopPadding = 10.dp,
                    majorEvery = 5,
                    labelEvery = 5,
                    minorTickColor = Color(0xFFDCE2EA),
                    majorTickColor = Color(0xFFC2CAD6),
                    labelColor = Color(0xFFB8C3CF),
                    labelFormatter = { it.toString() }
                )

                MeasurementRulerKind.Wingspan -> MeasurementRulerConfig(
                    containerHeight = 142.dp,
                    tickWidth = 7.3.dp,
                    labelWidth = 36.dp,
                    rulerTopPadding = 32.dp,
                    minorTickHeight = 40.dp,
                    majorTickHeight = 56.dp,
                    labelTopPadding = 10.dp,
                    majorEvery = 5,
                    labelEvery = 5,
                    minorTickColor = Color(0xFFDCE2EA),
                    majorTickColor = Color(0xFFC2CAD6),
                    labelColor = Color(0xFFB8C3CF),
                    labelFormatter = { it.toString() }
                )

                MeasurementRulerKind.Weight -> MeasurementRulerConfig(
                    containerHeight = 142.dp,
                    tickWidth = 7.3.dp,
                    labelWidth = 36.dp,
                    rulerTopPadding = 32.dp,
                    minorTickHeight = 40.dp,
                    majorTickHeight = 56.dp,
                    labelTopPadding = 10.dp,
                    majorEvery = 10,
                    labelEvery = 10,
                    minorTickColor = Color(0xFFDCE2EA),
                    majorTickColor = Color(0xFFC2CAD6),
                    labelColor = Color(0xFFB8C3CF),
                    labelFormatter = { (it / 10).toString() }
                )
            }
        }
        val itemWidth = config.tickWidth
        val centerPadding = (maxWidth / 2) - (itemWidth / 2)
        val listState = rememberLazyListState()
        val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
        val rulerTicks = remember(tickRange) { tickRange.toList() }
        var hasEmittedHaptic by remember { mutableStateOf(false) }
        val centeredTick by remember(listState, tickRange, selectedTick) {
            derivedStateOf {
                val layoutInfo = listState.layoutInfo
                val visibleItems = layoutInfo.visibleItemsInfo
                if (visibleItems.isEmpty()) {
                    selectedTick
                } else {
                    val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                    val nearestItem = visibleItems.minByOrNull { itemInfo ->
                        kotlin.math.abs((itemInfo.offset + (itemInfo.size / 2)) - viewportCenter)
                    }
                    tickRange.first + (nearestItem?.index ?: 0)
                }
            }
        }

        LaunchedEffect(centerPadding, tickRange) {
            listState.scrollToItem((selectedTick - tickRange.first).coerceAtLeast(0))
        }

        LaunchedEffect(selectedTick) {
            if (!listState.isScrollInProgress) {
                listState.animateScrollToItem((selectedTick - tickRange.first).coerceAtLeast(0))
            }
        }

        LaunchedEffect(centeredTick) {
            if (hasEmittedHaptic) {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
            } else {
                hasEmittedHaptic = true
            }

            if (centeredTick != selectedTick) {
                onTickChange(centeredTick)
            }
        }

        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = centerPadding),
            flingBehavior = flingBehavior,
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(rulerTicks) { _, tick ->
                val tickHeight = if (tick % config.majorEvery == 0) {
                    config.majorTickHeight
                } else {
                    config.minorTickHeight
                }

                Column(
                    modifier = Modifier.width(itemWidth),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(config.rulerTopPadding))
                    Box(
                        modifier = Modifier.height(config.majorTickHeight),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .width(if (tick % config.majorEvery == 0) 2.dp else 1.dp)
                                .height(tickHeight)
                                .background(
                                    if (tick % config.majorEvery == 0) {
                                        config.majorTickColor
                                    } else {
                                        config.minorTickColor
                                    }
                                )
                        )
                    }
                    Spacer(modifier = Modifier.height(config.labelTopPadding))
                    if (tick % config.labelEvery == 0) {
                        Text(
                            text = config.labelFormatter(tick),
                            style = TextStyle(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal,
                                color = config.labelColor
                            ),
                            modifier = Modifier.requiredWidth(config.labelWidth),
                            maxLines = 1,
                            softWrap = false,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Spacer(modifier = Modifier.height(19.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun UnderlineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    textAlign: TextAlign,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = TextStyle(
        fontSize = 28.sp,
        fontWeight = FontWeight.Normal,
        color = OnboardingBlack,
        textAlign = TextAlign.Center
    ),
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
    trailing: (@Composable BoxScope.() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = textStyle,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            cursorBrush = SolidColor(OnboardingBlue),
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 2.dp)
        ) { innerTextField ->
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = if (textAlign == TextAlign.Center) {
                    Alignment.Center
                } else {
                    Alignment.CenterStart
                }
            ) {
                if (value.isBlank()) {
                    Text(
                        text = hint,
                        style = textStyle.copy(color = OnboardingLightGray)
                    )
                }
                innerTextField()
            }
        }

        trailing?.let {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 2.dp),
                content = it
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(1.dp)
                .background(OnboardingBlue)
        )
    }
}

@Composable
private fun GymSearchRow(
    place: NearbyPlace,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) Color(0xFFF3F8FF) else Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable { onClick() }
                .border(
                    width = 1.dp,
                    color = if (selected) OnboardingBlue else Color(0xFFE6E9EE),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Text(
                text = place.placeName,
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OnboardingBlack
                )
            )
            val secondary = place.roadAddressName ?: place.addressName
            if (!secondary.isNullOrBlank()) {
                Text(
                    text = secondary,
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        color = OnboardingLightGray
                    ),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun FigmaSearchIcon(
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.1f
        val circleRadius = size.minDimension * 0.26f
        val circleCenter = Offset(
            x = size.width * 0.42f,
            y = size.height * 0.42f
        )

        drawCircle(
            color = OnboardingBlack,
            radius = circleRadius,
            center = circleCenter,
            style = Stroke(width = strokeWidth)
        )
        drawLine(
            color = OnboardingBlack,
            start = Offset(size.width * 0.62f, size.height * 0.62f),
            end = Offset(size.width * 0.9f, size.height * 0.9f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun SummaryMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight.Light,
                color = OnboardingWhiteGray
            )
        )
        Text(
            text = value,
            style = TextStyle(
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        )
    }
}

@Composable
private fun SolidCtaButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                enabled = enabled && !isLoading,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        color = if (enabled) OnboardingBlue else OnboardingBlue.copy(alpha = 0.35f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = label,
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                )
            }
        }
    }
}

@Composable
private fun GradientCtaButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false
) {
    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(OnboardingCtaGradient)
            .clickable(
                enabled = !isLoading,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text = label,
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            )
        }
    }
}

package com.ddgo.app.feature.onboarding

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
    Body,
    Weight
}

private data class MeasurementRulerConfig(
    val tickWidth: androidx.compose.ui.unit.Dp,
    val topPadding: androidx.compose.ui.unit.Dp,
    val minorTickHeight: androidx.compose.ui.unit.Dp,
    val majorTickHeight: androidx.compose.ui.unit.Dp,
    val majorEvery: Int,
    val labelEvery: Int,
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
private val OnboardingGlow = Brush.radialGradient(
    colors = listOf(
        Color(0x6653A6FF),
        Color(0x338458FF),
        Color.Transparent
    )
)
private val OnboardingCtaGradient = Brush.linearGradient(
    colors = listOf(Color(0xFF42A7FF), Color(0xFF8458FF))
)

private const val SEX_LEFT_ASSET = "file:///android_asset/onboarding/sex_left.svg"
private const val SEX_RIGHT_ASSET = "file:///android_asset/onboarding/sex_right.svg"
private const val COMPLETE_CHARACTER_ASSET = "file:///android_asset/onboarding/complete_character.svg"
private const val GYM_SEARCH_CIRCLE_ASSET = "file:///android_asset/figma/gym_search_circle.svg"
private const val GYM_SEARCH_HANDLE_ASSET = "file:///android_asset/figma/gym_search_handle.png"

@Composable
fun OnboardingScreen(
    showEntryGuide: Boolean,
    onExit: () -> Unit,
    onFinish: () -> Unit,
    viewModel: FigmaOnboardingViewModel = hiltViewModel()
) {
    val steps = remember { FigmaOnboardingStep.entries }
    var currentStepIndex by rememberSaveable { mutableIntStateOf(0) }
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
                rulerKind = MeasurementRulerKind.Body,
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
                rulerKind = MeasurementRulerKind.Body,
                progressStep = 2,
                onBack = ::moveBack,
                onTickChange = viewModel::setWingspan,
                helperTitle = "양 팔 리치",
                helperDescription = "윙스팬을 몰라도 괜찮아요. 키와 동일하게 설정할게요.",
                quickActionLabel = "키와 동일하게",
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
                    .size(width = 111.dp * scale, height = 72.dp * scale)
                    .clickable { onSelect(ProfileSexOption.Male) }
            )
            SvgAssetImage(
                assetPath = SEX_RIGHT_ASSET,
                contentDescription = "여성",
                modifier = Modifier
                    .size(width = 111.dp * scale, height = 72.dp * scale)
                    .clickable { onSelect(ProfileSexOption.Female) }
            )
        }
    }
}

@Composable
private fun IntroStepScreen(onContinue: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 180.dp)
                .size(width = 280.dp, height = 260.dp)
                .background(brush = OnboardingGlow, shape = CircleShape)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "이제 나에게 꼭 맞는\n클라이밍 분석을 받아볼 차례예요",
                style = TextStyle(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 36.sp,
                    color = OnboardingBlack,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.padding(top = 278.dp)
            )
            Text(
                text = "여러분의 클라이밍 목표를\n달성할 수 있도록\n몇 가지 질문을 준비했어요.",
                style = TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 28.sp,
                    color = OnboardingGray,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.padding(top = 54.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            SolidCtaButton(
                label = "좋아요!",
                onClick = onContinue,
                modifier = Modifier.padding(bottom = 45.dp)
            )
        }
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
    QuestionShell(
        title = title,
        progressStep = progressStep,
        buttonLabel = "다음",
        onBack = onBack,
        onContinue = onContinue,
        isLoading = isLoading,
        isContinueEnabled = !isLoading
    ) {
        if (helperTitle != null || helperDescription != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                helperTitle?.let {
                    Text(
                        text = it,
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = OnboardingGray
                        )
                    )
                }
                helperDescription?.let {
                    Text(
                        text = it,
                        style = TextStyle(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal,
                            lineHeight = 22.sp,
                            color = OnboardingLightGray,
                            textAlign = TextAlign.Center
                        ),
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
                if (quickActionLabel != null && onQuickAction != null) {
                    Text(
                        text = quickActionLabel,
                        style = TextStyle(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = OnboardingBlue
                        ),
                        modifier = Modifier
                            .padding(top = 14.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onQuickAction() }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (helperTitle == null && helperDescription == null) 140.dp else 92.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = displayValueText,
                    style = TextStyle(
                        fontSize = 64.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OnboardingBlack,
                        lineHeight = 68.sp
                    )
                )
                Text(
                    text = unit,
                    style = TextStyle(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Normal,
                        color = OnboardingGray
                    ),
                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp)
            ) {
                MeasurementRuler(
                    kind = rulerKind,
                    tickRange = tickRange,
                    selectedTick = selectedTick,
                    onTickChange = onTickChange
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 9.dp)
                        .width(3.dp)
                        .height(154.dp)
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
                fontWeight = FontWeight.Medium,
                color = OnboardingGray
            ),
            modifier = Modifier.padding(top = 42.dp, start = 4.dp)
        )

        UnderlineTextField(
            value = query,
            onValueChange = onQueryChange,
            hint = "암장 이름으로 검색",
            textAlign = TextAlign.Start,
            trailing = {
                FigmaSearchIcon(
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { onSearch() }
                )
            },
            modifier = Modifier.padding(top = 48.dp),
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
                        .padding(top = 28.dp),
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
            modifier = Modifier.padding(top = 118.dp),
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
                    .padding(top = 26.dp)
            )
        }

        if (showRecommendation) {
            Surface(
                modifier = Modifier
                    .padding(top = 28.dp)
                    .clip(RoundedCornerShape(36.dp))
                    .clickable { onRecommendationClick() },
                color = OnboardingWhiteGray,
                shape = RoundedCornerShape(36.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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
    nickname: String,
    heightCm: Int,
    weightText: String,
    wingspanCm: Int,
    isLoading: Boolean,
    onContinue: () -> Unit
) {
    ResultShell(
        buttonLabel = "열심히 해볼게요!",
        isLoading = isLoading,
        onContinue = onContinue
    ) {
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
                .fillMaxWidth()
                .padding(top = 78.dp)
        )

        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 40.dp)
                .size(width = 334.dp, height = 307.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFFD0E0FF), Color.White, Color(0xFFD0E0FF))
                        ),
                        shape = RoundedCornerShape(18.dp)
                    )
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 10.dp, vertical = 12.dp)
                    .fillMaxSize(),
                shape = RoundedCornerShape(18.dp),
                color = Color.Transparent
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFF111319), Color(0xFF2A2F38))
                            ),
                            shape = RoundedCornerShape(18.dp)
                        )
                ) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 18.dp),
                        color = OnboardingGray,
                        shape = RoundedCornerShape(36.dp)
                    ) {
                        Text(
                            text = nickname,
                            style = TextStyle(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White,
                                letterSpacing = 0.48.sp
                            ),
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
                        )
                    }

                    SvgAssetImage(
                        assetPath = COMPLETE_CHARACTER_ASSET,
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(top = 8.dp)
                            .size(width = 188.dp, height = 218.dp)
                    )

                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(start = 18.dp, end = 18.dp, bottom = 18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SummaryMetric(label = "키", value = "$heightCm cm")
                        SummaryMetric(label = "몸무게", value = "$weightText kg")
                        SummaryMetric(label = "윙스팬", value = "$wingspanCm cm")
                    }
                }
            }
        }
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
                .size(width = 420.dp, height = 400.dp)
                .background(brush = OnboardingGlow, shape = CircleShape)
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
    onTickChange: (Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
    ) {
        val config = remember(kind) {
            when (kind) {
                MeasurementRulerKind.Body -> MeasurementRulerConfig(
                    tickWidth = 16.dp,
                    topPadding = 34.dp,
                    minorTickHeight = 52.dp,
                    majorTickHeight = 86.dp,
                    majorEvery = 5,
                    labelEvery = 5,
                    labelFormatter = { it.toString() }
                )

                MeasurementRulerKind.Weight -> MeasurementRulerConfig(
                    tickWidth = 8.dp,
                    topPadding = 40.dp,
                    minorTickHeight = 40.dp,
                    majorTickHeight = 56.dp,
                    majorEvery = 10,
                    labelEvery = 10,
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
                Column(
                    modifier = Modifier.width(itemWidth),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(config.topPadding))
                    Box(
                        modifier = Modifier
                            .width(if (tick % config.majorEvery == 0) 2.dp else 1.dp)
                            .height(
                                if (tick % config.majorEvery == 0) {
                                    config.majorTickHeight
                                } else {
                                    config.minorTickHeight
                                }
                            )
                            .background(
                                if (tick % config.majorEvery == 0) Color(0xFFC2CAD6) else Color(0xFFDCE2EA)
                            )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    if (tick % config.labelEvery == 0) {
                        Text(
                            text = config.labelFormatter(tick),
                            style = TextStyle(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Normal,
                                color = Color(0xFFB8C3CF)
                            )
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
            .height(66.dp)
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
                .padding(horizontal = 12.dp)
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
                    .padding(end = 8.dp),
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
    Box(modifier = modifier) {
        SvgAssetImage(
            assetPath = GYM_SEARCH_CIRCLE_ASSET,
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )
        AsyncImage(
            model = GYM_SEARCH_HANDLE_ASSET,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 2.dp)
                .size(width = 12.dp, height = 4.dp)
        )
    }
}

@Composable
private fun SummaryMetric(
    label: String,
    value: String
) {
    Column(
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

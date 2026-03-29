package com.ddgo.app.feature.onboarding.ui.organism

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.FmdGood
import androidx.compose.material.icons.rounded.Height
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddgo.app.R
import com.ddgo.app.core.ui.atom.DdgoOutlinedButton
import com.ddgo.app.core.ui.atom.DdgoPrimaryButton
import com.ddgo.app.core.ui.atom.DdgoTextButton
import com.ddgo.app.core.ui.atom.DdgoTextButtonTone
import com.ddgo.app.core.ui.atom.DdgoFieldState
import com.ddgo.app.core.ui.atom.DdgoTextField
import com.ddgo.app.core.ui.molecule.DdgoSelectableCard
import com.ddgo.app.core.ui.tokens.DdgoColorTokens
import com.ddgo.app.core.ui.theme.PretendardFamily
import com.ddgo.app.domain.model.NearbyPlace
import com.ddgo.app.feature.onboarding.OnboardingClimbingLevel
import com.ddgo.app.feature.onboarding.OnboardingClimbingStyle
import com.ddgo.app.feature.onboarding.OnboardingGoal
import com.ddgo.app.feature.onboarding.OnboardingGymResolveUiState
import com.ddgo.app.feature.onboarding.OnboardingGymSearchUiState
import com.ddgo.app.feature.onboarding.ui.molecule.OnboardingHeightRulerField
import com.ddgo.app.feature.onboarding.OnboardingMode
import com.ddgo.app.feature.onboarding.ui.molecule.OnboardingChoiceGroup
import com.ddgo.app.feature.onboarding.ui.molecule.OnboardingChoiceOption
import com.ddgo.app.feature.onboarding.ui.molecule.OnboardingSectionHeading
import com.ddgo.app.feature.onboarding.ui.molecule.OnboardingSectionLabel
import com.ddgo.app.feature.onboarding.ui.molecule.OnboardingStatusBanner
import com.ddgo.app.feature.onboarding.ui.molecule.OnboardingSummaryTag
import com.ddgo.app.feature.onboarding.ui.shared.tokens.OnboardingTokens
import com.ddgo.app.feature.profile.ProfileStrings
import com.ddgo.app.feature.profile.model.ProfileSexOption

@Composable
fun HeroStageSection() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = Color.White.copy(alpha = 0.9f),
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_ddgo_mark_fill),
                    contentDescription = null,
                    modifier = Modifier.size(width = 34.dp, height = 22.dp)
                )
                Text(
                    text = "디디고 ONBOARDING",
                    color = DdgoColorTokens.BrandBlueStrong,
                    fontFamily = PretendardFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Box(
            modifier = Modifier
                .size(300.dp)
                .clip(RoundedCornerShape(36.dp))
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.White, Color(0xFFF2F7FF))
                    )
                )
                .border(1.dp, OnboardingTokens.CardBorder, RoundedCornerShape(36.dp))
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(180.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(OnboardingTokens.ShadowBlue, Color.Transparent)
                        )
                    )
            )

            Image(
                painter = painterResource(id = R.drawable.ic_ddgo_mascot),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(112.dp)
            )

            FloatingInfoChip(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 18.dp, top = 28.dp),
                text = "암장 기록",
                background = OnboardingTokens.HoldYellow.copy(alpha = 0.2f),
                contentColor = OnboardingTokens.Graphite
            )

            FloatingInfoChip(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 72.dp, end = 16.dp),
                text = "무브 분석",
                background = DdgoColorTokens.BrandBlue.copy(alpha = 0.16f),
                contentColor = DdgoColorTokens.BrandBlueStrong
            )

            FloatingInfoChip(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 22.dp, bottom = 34.dp),
                text = "성장 추적",
                background = OnboardingTokens.HoldCoral.copy(alpha = 0.16f),
                contentColor = OnboardingTokens.CoralAccent
            )
        }

        Spacer(modifier = Modifier.height(34.dp))

        Text(
            text = buildAnnotatedString {
                append("내 몸과 무브에 맞는\n")
                withStyle(SpanStyle(color = DdgoColorTokens.BrandBlue, fontWeight = FontWeight.Black)) {
                    append("클라이밍 시작점")
                }
            },
            color = OnboardingTokens.Graphite,
            fontFamily = PretendardFamily,
            fontWeight = FontWeight.Black,
            fontSize = 34.sp,
            lineHeight = 42.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "자주 가는 암장, 현재 실력, 몸 기준점만 빠르게 알려주시면\n디디고가 첫 기록과 분석 흐름을 자연스럽게 이어드릴게요.",
            color = OnboardingTokens.GraphiteMuted,
            fontFamily = PretendardFamily,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun GymStageSection(
    gymSearchQuery: String,
    gymSearchUiState: OnboardingGymSearchUiState,
    gymResolveUiState: OnboardingGymResolveUiState,
    selectedNearbyPlaceId: String?,
    locationMessage: String?,
    isResolvingLocation: Boolean,
    onGymSearchQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onCurrentLocationSearch: () -> Unit,
    onSelectPlace: (NearbyPlace) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        OnboardingSectionHeading(
            eyebrow = "대표 암장",
            title = "먼저 자주 가는 암장을 알려주세요",
            description = "대표 암장을 알면 기록, 추천, 난이도 흐름을 더 자연스럽게 연결할 수 있어요."
        )

        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color.White,
            shadowElevation = 10.dp
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                DdgoTextField(
                    value = gymSearchQuery,
                    onValueChange = onGymSearchQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = "암장 이름으로 찾기",
                    leadingIcon = Icons.Rounded.Search,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Search,
                        keyboardType = KeyboardType.Text
                    ),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() })
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DdgoOutlinedButton(
                        text = "현재 위치로 찾기",
                        onClick = onCurrentLocationSearch,
                        modifier = Modifier.weight(1f)
                    )

                    DdgoPrimaryButton(
                        text = "검색",
                        onClick = onSearch,
                        modifier = Modifier.weight(0.42f)
                    )
                }
            }
        }

        if (isResolvingLocation) {
            OnboardingStatusBanner(
                icon = Icons.Rounded.Explore,
                text = "현재 위치를 확인하면서 가까운 암장을 불러오고 있어요.",
                background = OnboardingTokens.SuccessFill,
                contentColor = DdgoColorTokens.BrandBlueStrong
            )
        }

        if (!locationMessage.isNullOrBlank()) {
            OnboardingStatusBanner(
                icon = Icons.Rounded.FmdGood,
                text = locationMessage,
                background = OnboardingTokens.ErrorFill,
                contentColor = OnboardingTokens.ErrorAccent
            )
        }

        when (val resolveState = gymResolveUiState) {
            is OnboardingGymResolveUiState.Success -> {
                SelectedGymCard(
                    gymName = resolveState.resolvedGym.gym.displayName,
                    gradeCount = resolveState.resolvedGym.grades.size
                )
            }

            is OnboardingGymResolveUiState.Error -> {
                OnboardingStatusBanner(
                    icon = Icons.Rounded.FmdGood,
                    text = resolveState.message,
                    background = OnboardingTokens.ErrorFill,
                    contentColor = OnboardingTokens.ErrorAccent
                )
            }

            OnboardingGymResolveUiState.Idle,
            OnboardingGymResolveUiState.Loading -> Unit
        }

        when (val searchState = gymSearchUiState) {
            OnboardingGymSearchUiState.Idle -> EmptyGymState()
            OnboardingGymSearchUiState.Loading -> LoadingGymState()
            is OnboardingGymSearchUiState.Error -> OnboardingStatusBanner(
                icon = Icons.Rounded.Search,
                text = searchState.message,
                background = OnboardingTokens.ErrorFill,
                contentColor = OnboardingTokens.ErrorAccent
            )

            is OnboardingGymSearchUiState.Success -> GymSearchResultList(
                places = searchState.places,
                selectedExternalPlaceId = selectedNearbyPlaceId,
                onSelectPlace = onSelectPlace
            )
        }
    }
}

@Composable
fun ClimbingProfileStageSection(
    climbingLevel: OnboardingClimbingLevel?,
    climbingStyle: OnboardingClimbingStyle?,
    onSelectClimbingLevel: (OnboardingClimbingLevel) -> Unit,
    onSelectClimbingStyle: (OnboardingClimbingStyle) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        OnboardingSectionHeading(
            eyebrow = "등반 프로필",
            title = "현재 등반 프로필을 알려주세요",
            description = "질문은 짧게 받고, 첫 추천은 훨씬 선명하게 보여드릴게요."
        )

        OnboardingSectionLabel(text = "현재 레벨")

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OnboardingClimbingLevel.entries.forEach { option ->
                DdgoSelectableCard(
                    title = option.title,
                    subtitle = option.subtitle,
                    selected = climbingLevel == option,
                    onClick = { onSelectClimbingLevel(option) },
                    leadingIcon = Icons.Rounded.Route
                )
            }
        }

        OnboardingSectionLabel(text = "주로 타는 스타일")

        OnboardingChoiceGroup(
            options = OnboardingClimbingStyle.entries.map {
                OnboardingChoiceOption(value = it, label = it.title)
            },
            selectedValue = climbingStyle,
            onSelect = onSelectClimbingStyle
        )

        if (climbingStyle == null) {
            OnboardingStatusBanner(
                icon = Icons.Rounded.AutoAwesome,
                text = "선호 스타일은 나중에 바꿔도 괜찮아요. 지금은 현재 레벨만 골라도 계속할 수 있어요.",
                background = OnboardingTokens.CardFill,
                contentColor = OnboardingTokens.GraphiteMuted
            )
        }
    }
}

@Composable
fun GoalStageSection(
    selectedGoal: OnboardingGoal?,
    onSelectGoal: (OnboardingGoal) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        OnboardingSectionHeading(
            eyebrow = "목표",
            title = "이번 시즌 가장 바꾸고 싶은 건 무엇인가요?",
            description = "첫 기록 화면과 추천 카드가 이 목표를 기준으로 정렬돼요."
        )

        if (selectedGoal != null) {
            OnboardingStatusBanner(
                icon = goalIcon(selectedGoal),
                text = goalFeedback(selectedGoal),
                background = OnboardingTokens.SelectedFill,
                contentColor = DdgoColorTokens.BrandBlueStrong
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OnboardingGoal.entries.forEach { option ->
                DdgoSelectableCard(
                    title = option.title,
                    subtitle = option.subtitle,
                    selected = selectedGoal == option,
                    onClick = { onSelectGoal(option) },
                    leadingIcon = goalIcon(option)
                )
            }
        }
    }
}

@Composable
fun BodyProfileStageSection(
    sex: ProfileSexOption?,
    heightCmInput: String,
    weightKgInput: String,
    wingspanCmInput: String,
    profileErrorMessage: String?,
    isLoadingProfileDefaults: Boolean,
    isSubmittingProfile: Boolean,
    onSelectSex: (ProfileSexOption) -> Unit,
    onHeightChange: (String) -> Unit,
    onWeightChange: (String) -> Unit,
    onWingspanChange: (String) -> Unit,
    onApplyHeightToWingspan: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        OnboardingSectionHeading(
            eyebrow = "신체 프로필",
            title = "분석에 필요한 기본 신체 정보를 알려주세요",
            description = "키, 체중, 윙스팬은 리치와 무브 분석 정확도에 직접 영향을 줘요."
        )

        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color.White,
            shadowElevation = 10.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                if (isLoadingProfileDefaults) {
                    OnboardingStatusBanner(
                        icon = Icons.Rounded.AutoAwesome,
                        text = "기존 프로필을 불러와서 바로 이어서 입력할 수 있게 준비하고 있어요.",
                        background = OnboardingTokens.CardFill,
                        contentColor = OnboardingTokens.Graphite
                    )
                }

                OnboardingSectionLabel(text = ProfileStrings.SexLabel)

                OnboardingChoiceGroup(
                    options = ProfileSexOption.entries.map {
                        OnboardingChoiceOption(value = it, label = it.label)
                    },
                    selectedValue = sex,
                    onSelect = onSelectSex
                )

                OnboardingHeightRulerField(
                    value = heightCmInput,
                    onValueChange = onHeightChange,
                    enabled = !isSubmittingProfile,
                    initializeIfBlank = !isLoadingProfileDefaults
                )

                NumericInputField(
                    label = ProfileStrings.BodyProfileFieldLabelWeight,
                    value = weightKgInput,
                    onValueChange = onWeightChange,
                    unit = "kg",
                    icon = Icons.Rounded.MonitorWeight,
                    enabled = !isSubmittingProfile
                )

                NumericInputField(
                    label = ProfileStrings.BodyProfileFieldLabelWingspan,
                    value = wingspanCmInput,
                    onValueChange = onWingspanChange,
                    unit = "cm",
                    icon = Icons.Rounded.AccessibilityNew,
                    enabled = !isSubmittingProfile
                )

                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = OnboardingTokens.CardFill
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Straighten,
                            contentDescription = null,
                            tint = DdgoColorTokens.BrandBlue
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "윙스팬을 모르셔도 괜찮아요",
                                color = OnboardingTokens.Graphite,
                                fontFamily = PretendardFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "비워두면 키와 같은 값으로 먼저 시작할 수 있어요.",
                                color = OnboardingTokens.GraphiteMuted,
                                fontFamily = PretendardFamily,
                                fontSize = 13.sp,
                                lineHeight = 19.sp
                            )
                        }

                        if (heightCmInput.isNotBlank()) {
                            DdgoTextButton(
                                text = "키와 같게",
                                onClick = onApplyHeightToWingspan,
                                tone = DdgoTextButtonTone.Primary
                            )
                        }
                    }
                }

                if (!profileErrorMessage.isNullOrBlank()) {
                    OnboardingStatusBanner(
                        icon = Icons.Rounded.CheckCircle,
                        text = profileErrorMessage,
                        background = OnboardingTokens.ErrorFill,
                        contentColor = OnboardingTokens.ErrorAccent
                    )
                }
            }
        }
    }
}

@Composable
fun SummaryStageSection(
    mode: OnboardingMode,
    selectedGymTitle: String?,
    selectedGymGradeCount: Int?,
    onboardingGoal: OnboardingGoal?,
    climbingLevel: OnboardingClimbingLevel?,
    heightCmInput: String,
    wingspanCmInput: String
) {
    val gymTitle = selectedGymTitle ?: "대표 암장은 나중에 정해도 괜찮아요"
    val focusText = onboardingGoal?.focusLabel ?: "등반 흐름 정리"
    val levelText = climbingLevel?.summaryLabel ?: "첫 기록부터 자연스럽게"
    val bodyText = if (mode.includesProfileSetup && heightCmInput.isNotBlank()) {
        "키 ${heightCmInput}cm · 윙스팬 ${wingspanCmInput.ifBlank { heightCmInput }}cm"
    } else {
        "지금 단계에 맞는 온보딩만 먼저 적용"
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        OnboardingSectionHeading(
            eyebrow = "준비 완료",
            title = "디디고가 첫 등반 기준점을 준비했어요",
            description = "이제 기록과 분석이 내 암장, 내 목표, 내 몸 기준에 더 가깝게 시작돼요."
        )

        Surface(
            shape = RoundedCornerShape(32.dp),
            color = Color.White,
            shadowElevation = 12.dp
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFFEFF5FF), Color(0xFFFFF4E8))
                            )
                        )
                        .padding(horizontal = 24.dp, vertical = 24.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(116.dp)
                                .clip(CircleShape)
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_ddgo_mascot),
                                contentDescription = null,
                                modifier = Modifier.size(74.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        SummaryTagRow(
                            left = gymTitle,
                            center = focusText,
                            right = bodyText
                        )
                    }
                }

                Surface(
                    color = OnboardingTokens.ResultCardDark,
                    shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SummaryMetricColumn(
                            title = "대표 암장",
                            value = shortLabel(gymTitle)
                        )
                        SummaryMetricColumn(
                            title = "집중 포인트",
                            value = focusText
                        )
                        SummaryMetricColumn(
                            title = "다음 액션",
                            value = if (mode.includesProfileSetup) "첫 기록 시작" else "로그인 후 이어서"
                        )
                    }
                }
            }
        }

        SummaryBullet(
            icon = Icons.Rounded.FmdGood,
            title = "암장 기준점이 생겼어요",
            body = if (selectedGymGradeCount != null) {
                "대표 암장에 연결된 난이도 정보 ${selectedGymGradeCount}개를 바탕으로 추천이 이어져요."
            } else {
                "대표 암장을 정해두면 난이도와 기록 흐름을 같은 맥락에서 보기 쉬워져요."
            }
        )
        SummaryBullet(
            icon = Icons.Rounded.Flag,
            title = "목표가 홈 화면에 반영돼요",
            body = "$focusText 중심으로 첫 추천 카드와 액션 안내가 정렬될 거예요."
        )
        SummaryBullet(
            icon = Icons.Rounded.Timeline,
            title = "첫 기록부터 성장 흐름이 시작돼요",
            body = levelText
        )

        if (mode.includesProfileSetup) {
            SummaryBullet(
                icon = Icons.Rounded.CameraAlt,
                title = "다음은 기록과 분석이에요",
                body = "첫 영상이나 첫 등반 기록을 남기면 디디고가 훨씬 더 정교하게 맞춰질 거예요."
            )
        }
    }
}

@Composable
private fun FloatingInfoChip(
    modifier: Modifier = Modifier,
    text: String,
    background: Color,
    contentColor: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = background
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = contentColor,
            fontFamily = PretendardFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun NumericInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    unit: String,
    icon: ImageVector,
    enabled: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OnboardingSectionLabel(text = label)
        DdgoTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            state = if (enabled) DdgoFieldState.Default else DdgoFieldState.Disabled,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Next
            ),
            leadingIcon = icon,
            trailingText = unit
        )
    }
}

@Composable
private fun EmptyGymState() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Color.White,
        shadowElevation = 6.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(OnboardingTokens.CardFill),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_ddgo_mascot),
                    contentDescription = null,
                    modifier = Modifier.size(42.dp)
                )
            }

            Text(
                text = "지금 가는 암장부터 가볍게 골라볼까요?",
                color = OnboardingTokens.Graphite,
                fontFamily = PretendardFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                textAlign = TextAlign.Center
            )

            Text(
                text = "현재 위치로 찾기를 누르거나 암장 이름을 검색하면 대표 암장을 빠르게 정할 수 있어요.",
                color = OnboardingTokens.GraphiteMuted,
                fontFamily = PretendardFamily,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun LoadingGymState() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "암장을 찾고 있어요.",
                color = OnboardingTokens.GraphiteMuted,
                fontFamily = PretendardFamily,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun GymSearchResultList(
    places: List<NearbyPlace>,
    selectedExternalPlaceId: String?,
    onSelectPlace: (NearbyPlace) -> Unit
) {
    if (places.isEmpty()) {
        OnboardingStatusBanner(
            icon = Icons.Rounded.Search,
            text = "검색 결과가 없어요. 다른 지역명이나 암장 이름으로 다시 찾아보세요.",
            background = OnboardingTokens.ErrorFill,
            contentColor = OnboardingTokens.ErrorAccent
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        places.forEach { place ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = if (selectedExternalPlaceId == place.externalPlaceId) {
                    OnboardingTokens.SelectedFill
                } else {
                    Color.White
                },
                shadowElevation = 5.dp
            ) {
                Row(
                    modifier = Modifier
                        .clickable { onSelectPlace(place) }
                        .border(
                            width = 1.dp,
                            color = if (selectedExternalPlaceId == place.externalPlaceId) {
                                DdgoColorTokens.BrandBlue
                            } else {
                                OnboardingTokens.CardBorder
                            },
                            shape = RoundedCornerShape(22.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(OnboardingTokens.CardFill),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FmdGood,
                            contentDescription = null,
                            tint = DdgoColorTokens.BrandBlue
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = place.placeName,
                            color = OnboardingTokens.Graphite,
                            fontFamily = PretendardFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = place.roadAddressName ?: place.addressName ?: "주소 정보 없음",
                            color = OnboardingTokens.GraphiteMuted,
                            fontFamily = PretendardFamily,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = place.distanceMeters?.let(::formatDistanceLabel) ?: "선택",
                        color = if (selectedExternalPlaceId == place.externalPlaceId) {
                            DdgoColorTokens.BrandBlueStrong
                        } else {
                            OnboardingTokens.GraphiteMuted
                        },
                        fontFamily = PretendardFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedGymCard(
    gymName: String,
    gradeCount: Int
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = OnboardingTokens.SuccessFill
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp)) {
            Text(
                text = "대표 암장",
                color = DdgoColorTokens.BrandBlueStrong,
                fontFamily = PretendardFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = normalizeGymDisplayName(gymName),
                color = OnboardingTokens.Graphite,
                fontFamily = PretendardFamily,
                fontWeight = FontWeight.Black,
                fontSize = 20.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "디디고에 연결된 난이도 정보 ${gradeCount}개를 기준으로 기록과 추천을 이어갈 수 있어요.",
                color = OnboardingTokens.GraphiteMuted,
                fontFamily = PretendardFamily,
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun SummaryTagRow(left: String, center: String, right: String) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OnboardingSummaryTag(shortLabel(left), OnboardingTokens.HoldYellow.copy(alpha = 0.24f), OnboardingTokens.Graphite)
        OnboardingSummaryTag(center, DdgoColorTokens.BrandBlue.copy(alpha = 0.18f), DdgoColorTokens.BrandBlueStrong)
        OnboardingSummaryTag(shortLabel(right), OnboardingTokens.HoldCoral.copy(alpha = 0.18f), OnboardingTokens.CoralAccent)
    }
}

@Composable
private fun SummaryMetricColumn(title: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.7f),
            fontFamily = PretendardFamily,
            fontSize = 12.sp
        )
        Text(
            text = value,
            color = Color.White,
            fontFamily = PretendardFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SummaryBullet(icon: ImageVector, title: String, body: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(OnboardingTokens.CardFill),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = DdgoColorTokens.BrandBlue)
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                color = OnboardingTokens.Graphite,
                fontFamily = PretendardFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Text(
                text = body,
                color = OnboardingTokens.GraphiteMuted,
                fontFamily = PretendardFamily,
                fontSize = 14.sp,
                lineHeight = 21.sp
            )
        }
    }
}

private fun goalIcon(goal: OnboardingGoal): ImageVector {
    return when (goal) {
        OnboardingGoal.TopRate -> Icons.Rounded.Flag
        OnboardingGoal.Reach -> Icons.Rounded.Straighten
        OnboardingGoal.Endurance -> Icons.Rounded.Timeline
        OnboardingGoal.Safe -> Icons.Rounded.CheckCircle
        OnboardingGoal.Routine -> Icons.Rounded.AutoAwesome
    }
}

private fun goalFeedback(goal: OnboardingGoal): String {
    return when (goal) {
        OnboardingGoal.TopRate -> "좋아요. 완등률을 높이는 흐름으로 첫 추천 카드를 정리해둘게요."
        OnboardingGoal.Reach -> "좋아요. 리치와 윙스팬이 중요하니 몸 기준을 더 정확히 반영해볼게요."
        OnboardingGoal.Endurance -> "좋아요. 후반 집중력이 떨어지는 구간을 보는 데 더 도움이 될 거예요."
        OnboardingGoal.Safe -> "좋아요. 무리 없이 오래 탈 수 있는 방향으로 기록 흐름을 잡아볼게요."
        OnboardingGoal.Routine -> "좋아요. 기록이 꾸준히 쌓이도록 시작 진입을 더 가볍게 맞춰둘게요."
    }
}

private fun shortLabel(text: String): String {
    return if (text.length > 14) text.take(14) + "…" else text
}

private fun normalizeGymDisplayName(displayName: String): String {
    return displayName.replace(Regex("\\s*\\(\\d+\\)$"), "").trim()
}

private fun formatDistanceLabel(distanceMeters: Int): String {
    return if (distanceMeters >= 1000) {
        String.format("%.1fkm", distanceMeters / 1000f)
    } else {
        "${distanceMeters}m"
    }
}

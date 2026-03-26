package com.ddgo.app.feature.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.ddgo.app.R
import com.ddgo.app.core.datastore.MainEntryGuideStep
import com.ddgo.app.core.ui.components.SvgAssetImage
import com.ddgo.app.feature.calendar.CalendarViewModel
import com.ddgo.app.feature.analysis.AnalysisScreen
import com.ddgo.app.feature.calendar.CalendarScreen
import com.ddgo.app.feature.climbing.ClimbingMenuOverlay
import com.ddgo.app.feature.community.CommunityScreen
import com.ddgo.app.feature.profile.ProfileScreen
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.ddgo.app.navigation.PendingCommunityComposeRequest
import java.time.LocalDate

@Composable
fun MainScreen(
    onNavigateToUpload: () -> Unit,
    onNavigateToRecord: () -> Unit,
    onNavigateToCalendarDetail: (LocalDate) -> Unit,
    onNavigateToAuth: () -> Unit,
    calendarViewModel: CalendarViewModel,
    pendingCalendarChallengeId: Long? = null,
    onPendingCalendarChallengeHandled: () -> Unit = {},
    pendingAnalysisShareRequest: PendingCommunityComposeRequest? = null,
    onPendingAnalysisShareHandled: () -> Unit = {},
    onNavigateToDebug: () -> Unit = {},
    mainGuideViewModel: MainGuideViewModel = hiltViewModel()
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(MainTab.CALENDAR) }
    var lastActiveTab by rememberSaveable { mutableIntStateOf(MainTab.CALENDAR) }
    var analysisTargetChallengeId by rememberSaveable { mutableStateOf<Long?>(null) }
    val guideStep by mainGuideViewModel.guideStep.collectAsState()
    val isClimbing = selectedTab == MainTab.CLIMBING

    LaunchedEffect(pendingCalendarChallengeId) {
        if (pendingCalendarChallengeId == null) return@LaunchedEffect
        analysisTargetChallengeId = pendingCalendarChallengeId
        lastActiveTab = MainTab.ANALYSIS
        selectedTab = MainTab.ANALYSIS
        onPendingCalendarChallengeHandled()
    }

    LaunchedEffect(pendingAnalysisShareRequest?.requestId) {
        if (pendingAnalysisShareRequest == null) return@LaunchedEffect
        lastActiveTab = MainTab.COMMUNITY
        selectedTab = MainTab.COMMUNITY
    }

    LaunchedEffect(guideStep) {
        when (guideStep) {
            MainEntryGuideStep.MENU -> {
                if (selectedTab != MainTab.CLIMBING) {
                    selectedTab = MainTab.CLIMBING
                }
            }

            MainEntryGuideStep.FAB -> {
                if (selectedTab == MainTab.CLIMBING) {
                    selectedTab = lastActiveTab
                }
            }

            MainEntryGuideStep.NONE,
            MainEntryGuideStep.DONE -> Unit
        }
    }

    BackHandler(enabled = isClimbing) {
        if (guideStep == MainEntryGuideStep.MENU) {
            mainGuideViewModel.dismissMenuGuide()
        }
        selectedTab = lastActiveTab
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(MainZIndex.CONTENT)
        ) {
            when (lastActiveTab) {
                MainTab.CALENDAR -> CalendarScreen(
                    onDateSelected = onNavigateToCalendarDetail,
                    viewModel = calendarViewModel
                )
                MainTab.COMMUNITY -> CommunityScreen(
                    pendingAnalysisShareRequest = pendingAnalysisShareRequest,
                    onPendingAnalysisShareHandled = onPendingAnalysisShareHandled
                )
                MainTab.ANALYSIS -> AnalysisScreen(
                    externalChallengeId = analysisTargetChallengeId,
                    onExternalChallengeHandled = {
                        analysisTargetChallengeId = null
                    }
                )
                MainTab.PROFILE -> ProfileScreen(onNavigateToAuth = onNavigateToAuth)
            }
        }

        CustomBottomNavBarBase(
            selectedIndex = selectedTab,
            onTabSelected = { tab ->
                selectedTab = tab
                if (tab != MainTab.CLIMBING) {
                    lastActiveTab = tab
                }
            },
            onNavigateToDebug = onNavigateToDebug,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(MainZIndex.NAV_BAR)
        )

        if (isClimbing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .zIndex(MainZIndex.DIM_LAYER)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (guideStep == MainEntryGuideStep.MENU) {
                            mainGuideViewModel.dismissMenuGuide()
                        }
                        selectedTab = lastActiveTab
                    }
            )
        }

        if (guideStep == MainEntryGuideStep.FAB) {
            FabGuideOverlay(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(MainZIndex.GUIDE_OVERLAY),
                onFabClick = {
                    mainGuideViewModel.onFabGuideActivated()
                    selectedTab = MainTab.CLIMBING
                }
            )
        }

        if (guideStep == MainEntryGuideStep.MENU) {
            MenuGuideOverlay(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(MainZIndex.GUIDE_OVERLAY),
                onDismiss = {
                    mainGuideViewModel.dismissMenuGuide()
                    selectedTab = lastActiveTab
                }
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .zIndex(MainZIndex.FAB)
        ) {
            ClimbingFloatingButton(
                isSelected = isClimbing,
                onClick = {
                    if (guideStep == MainEntryGuideStep.FAB) {
                        mainGuideViewModel.onFabGuideActivated()
                    } else if (guideStep == MainEntryGuideStep.MENU && isClimbing) {
                        mainGuideViewModel.dismissMenuGuide()
                    }

                    if (isClimbing) {
                        selectedTab = lastActiveTab
                    } else {
                        selectedTab = MainTab.CLIMBING
                    }
                }
            )
        }

        if (isClimbing) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = MainChromeDefaults.MenuOverlayBottomPadding)
                    .zIndex(MainZIndex.MENU_OVERLAY)
            ) {
                ClimbingMenuOverlay(
                    onNavigateToUpload = {
                        if (guideStep == MainEntryGuideStep.MENU) {
                            mainGuideViewModel.dismissMenuGuide()
                        }
                        onNavigateToUpload()
                    },
                    onNavigateToRecord = {
                        if (guideStep == MainEntryGuideStep.MENU) {
                            mainGuideViewModel.dismissMenuGuide()
                        }
                        onNavigateToRecord()
                    },
                    onDismiss = {
                        if (guideStep == MainEntryGuideStep.MENU) {
                            mainGuideViewModel.dismissMenuGuide()
                        }
                        selectedTab = lastActiveTab
                    }
                )
            }
        }
    }
}

private object MainZIndex {
    const val CONTENT = 0f
    const val NAV_BAR = 100f
    const val DIM_LAYER = 200f
    const val GUIDE_OVERLAY = 350f
    const val FAB = 300f
    const val MENU_OVERLAY = 400f
}

private const val GUIDE_1_ARROW_ASSET = "file:///android_asset/figma/guide1_arrow.svg"
private const val GUIDE_1_FAB_ICON_ASSET = "file:///android_asset/figma/guide1_fab_icon.svg"
private const val GUIDE_2_ARROW_LEFT_ASSET = "file:///android_asset/figma/guide2_arrow_left.svg"
private const val GUIDE_2_ARROW_RIGHT_ASSET = "file:///android_asset/figma/guide2_arrow_right.svg"
private const val GUIDE_2_FAB_BG_ASSET = "file:///android_asset/figma/guide2_fab_bg.svg"
private const val GUIDE_2_CLOSE_ICON_ASSET = "file:///android_asset/figma/guide2_close_icon.svg"
private val GuideHandwritingFont = FontFamily(Font(R.font.memoment_kkukkukk))

@Composable
private fun FabGuideOverlay(
    modifier: Modifier = Modifier,
    onFabClick: () -> Unit
) {
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.58f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {}
    ) {
        Text(
            text = buildAnnotatedString {
                append("분석을 위해 ")
                withStyle(SpanStyle(color = Color(0xFF53A6FF))) {
                    append("버튼")
                }
                append("을 클릭해주세요!")
            },
            style = TextStyle(
                fontSize = 20.sp,
                fontFamily = GuideHandwritingFont,
                fontWeight = FontWeight.Normal,
                color = Color.White,
                textAlign = TextAlign.Center
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(x = 72.dp, y = (-188).dp)
        )

        SvgAssetImage(
            assetPath = GUIDE_1_ARROW_ASSET,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(x = 104.dp, y = (-112).dp)
                .size(width = 58.dp, height = 72.dp)
                .rotate(68.29f)
        )

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp)
                .size(width = 86.dp, height = 110.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onFabClick
                ),
            color = Color.White,
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SvgAssetImage(
                    assetPath = GUIDE_2_FAB_BG_ASSET,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .size(70.dp)
                )
                SvgAssetImage(
                    assetPath = GUIDE_1_FAB_ICON_ASSET,
                    contentDescription = null,
                    modifier = Modifier
                        .offset(y = (-61).dp)
                        .size(width = 29.dp, height = 36.dp)
                )
                Text(
                    text = "클라이밍",
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Light,
                        color = Color(0xFF505050),
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.offset(y = (-34).dp)
                )
            }
        }
    }
}

@Composable
private fun MenuGuideOverlay(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit
) {
    Box(modifier = modifier) {
        GuideCaption(
            lines = listOf("과거 영상 분석은", "영상 업로드에서"),
            accent = "영상 업로드",
            accentColor = Color(0xFFFF70A2),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, bottom = 292.dp)
        )

        SvgAssetImage(
            assetPath = GUIDE_2_ARROW_LEFT_ASSET,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 10.dp, bottom = 210.dp)
                .size(width = 102.dp, height = 49.dp)
                .scale(scaleX = 1f, scaleY = -1f)
                .rotate(66.56f)
        )

        GuideCaption(
            lines = listOf("암장 실시간 분석은", "실시간 기록에서"),
            accent = "실시간 기록",
            accentColor = Color(0xFF92F697),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = 236.dp)
        )

        SvgAssetImage(
            assetPath = GUIDE_2_ARROW_RIGHT_ASSET,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = 168.dp)
                .size(width = 121.dp, height = 58.dp)
                .rotate(101.33f)
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
                .size(86.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            SvgAssetImage(
                assetPath = GUIDE_2_FAB_BG_ASSET,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
            SvgAssetImage(
                assetPath = GUIDE_2_CLOSE_ICON_ASSET,
                contentDescription = null,
                modifier = Modifier.size(width = 32.dp, height = 31.dp)
            )
        }
    }
}

@Composable
private fun GuideCaption(
    lines: List<String>,
    accent: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Text(
        text = buildAnnotatedString {
            append(lines.first())
            append("\n")
            val secondLine = lines.last()
            val accentStart = secondLine.indexOf(accent)
            if (accentStart >= 0) {
                append(secondLine.substring(0, accentStart))
                withStyle(SpanStyle(color = accentColor)) {
                    append(accent)
                }
                append(secondLine.substring(accentStart + accent.length))
            } else {
                append(secondLine)
            }
        },
        style = TextStyle(
            fontSize = 20.sp,
            fontFamily = GuideHandwritingFont,
            fontWeight = FontWeight.Normal,
            color = Color.White,
            textAlign = TextAlign.Center
        ),
        modifier = modifier
    )
}

package com.ddgo.app.feature.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex
import com.ddgo.app.feature.calendar.CalendarViewModel
import com.ddgo.app.feature.analysis.AnalysisScreen
import com.ddgo.app.feature.calendar.CalendarScreen
import com.ddgo.app.feature.climbing.ClimbingMenuOverlay
import com.ddgo.app.feature.community.CommunityScreen
import com.ddgo.app.feature.profile.ProfileScreen
import com.ddgo.app.navigation.PendingCommunityComposeRequest
import java.time.LocalDate

private enum class PendingClimbingDestination {
    Upload,
    Record
}

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
    onNavigateToDebug: () -> Unit = {}
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(MainTab.CALENDAR) }
    var lastActiveTab by rememberSaveable { mutableIntStateOf(MainTab.CALENDAR) }
    var analysisTargetChallengeId by rememberSaveable { mutableStateOf<Long?>(null) }
    var pendingClimbingDestination by remember {
        mutableStateOf<PendingClimbingDestination?>(null)
    }
    val isClimbing = selectedTab == MainTab.CLIMBING

    LaunchedEffect(pendingClimbingDestination, isClimbing) {
        val destination = pendingClimbingDestination ?: return@LaunchedEffect
        if (isClimbing) return@LaunchedEffect

        // Let the menu overlay and its dim layer disappear before opening the next screen/picker.
        withFrameNanos { }

        when (destination) {
            PendingClimbingDestination.Upload -> onNavigateToUpload()
            PendingClimbingDestination.Record -> onNavigateToRecord()
        }
        pendingClimbingDestination = null
    }

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

    // Root bottom-nav tabs should return to Calendar before leaving the app.
    BackHandler(
        enabled = selectedTab == MainTab.COMMUNITY ||
            selectedTab == MainTab.ANALYSIS ||
            selectedTab == MainTab.PROFILE
    ) {
        selectedTab = MainTab.CALENDAR
        lastActiveTab = MainTab.CALENDAR
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
                    ) { selectedTab = lastActiveTab }
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
                        pendingClimbingDestination = PendingClimbingDestination.Upload
                        selectedTab = lastActiveTab
                    },
                    onNavigateToRecord = {
                        pendingClimbingDestination = PendingClimbingDestination.Record
                        selectedTab = lastActiveTab
                    },
                    onDismiss = {
                        if (pendingClimbingDestination == null) {
                            selectedTab = lastActiveTab
                        }
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
    const val FAB = 300f
    const val MENU_OVERLAY = 400f
}

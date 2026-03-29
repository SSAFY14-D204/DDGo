package com.ddgo.app.feature.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.ddgo.app.core.datastore.MainEntryGuideStep
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
    onNavigateToDebug: () -> Unit = {},
    previewGuideStep: MainEntryGuideStep? = null,
    onPreviewGuideFabClick: (() -> Unit)? = null,
    onPreviewGuideMenuDismiss: (() -> Unit)? = null,
    enableBackHandlers: Boolean = true,
    mainGuideViewModel: MainGuideViewModel = hiltViewModel()
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(MainTab.CALENDAR) }
    var lastActiveTab by rememberSaveable { mutableIntStateOf(MainTab.CALENDAR) }
    var analysisRootResetNonce by remember { mutableIntStateOf(0) }
    var communityRootResetNonce by remember { mutableIntStateOf(0) }
    var profileRootResetNonce by remember { mutableIntStateOf(0) }
    var analysisTargetChallengeId by rememberSaveable { mutableStateOf<Long?>(null) }
    var pendingAnalysisShareRequestState by remember {
        mutableStateOf<PendingCommunityComposeRequest?>(null)
    }
    var pendingClimbingDestination by remember {
        mutableStateOf<PendingClimbingDestination?>(null)
    }
    val effectiveGuideStep = previewGuideStep ?: MainEntryGuideStep.DONE
    val isStaticGuidePreview = previewGuideStep != null &&
        (onPreviewGuideFabClick != null || onPreviewGuideMenuDismiss != null)
    val isClimbing = selectedTab == MainTab.CLIMBING
    val activateFabGuide = remember(previewGuideStep) {
        {
            if (previewGuideStep == null) {
                mainGuideViewModel.onFabGuideActivated()
            }
        }
    }
    val dismissMenuGuide = remember(previewGuideStep) {
        {
            if (previewGuideStep == null) {
                mainGuideViewModel.dismissMenuGuide()
            }
        }
    }

    LaunchedEffect(pendingClimbingDestination, isClimbing) {
        val destination = pendingClimbingDestination ?: return@LaunchedEffect
        if (isClimbing) return@LaunchedEffect

        // Let the menu overlay and dim layer disappear before opening the next flow.
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
        pendingAnalysisShareRequestState = pendingAnalysisShareRequest
    }

    LaunchedEffect(pendingAnalysisShareRequestState?.requestId) {
        if (pendingAnalysisShareRequestState == null) return@LaunchedEffect
        lastActiveTab = MainTab.COMMUNITY
        selectedTab = MainTab.COMMUNITY
    }

    LaunchedEffect(effectiveGuideStep) {
        when (effectiveGuideStep) {
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

    BackHandler(enabled = enableBackHandlers && isClimbing) {
        if (effectiveGuideStep == MainEntryGuideStep.MENU) {
            dismissMenuGuide()
        }
        selectedTab = lastActiveTab
    }

    BackHandler(
        enabled = enableBackHandlers && (
            selectedTab == MainTab.COMMUNITY ||
            selectedTab == MainTab.ANALYSIS ||
            selectedTab == MainTab.PROFILE
            )
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
                    rootResetNonce = communityRootResetNonce,
                    pendingAnalysisShareRequest = pendingAnalysisShareRequestState,
                    onPendingAnalysisShareHandled = {
                        pendingAnalysisShareRequestState = null
                        onPendingAnalysisShareHandled()
                    }
                )
                MainTab.ANALYSIS -> AnalysisScreen(
                    rootResetNonce = analysisRootResetNonce,
                    externalChallengeId = analysisTargetChallengeId,
                    onExternalChallengeHandled = {
                        analysisTargetChallengeId = null
                    }
                )
                MainTab.PROFILE -> ProfileScreen(
                    rootResetNonce = profileRootResetNonce,
                    onNavigateToAuth = onNavigateToAuth
                )
            }
        }

        CustomBottomNavBarBase(
            selectedIndex = selectedTab,
            onTabSelected = { tab ->
                when (tab) {
                    MainTab.ANALYSIS -> {
                        analysisRootResetNonce += 1
                        analysisTargetChallengeId = null
                    }
                    MainTab.COMMUNITY -> {
                        communityRootResetNonce += 1
                        if (pendingAnalysisShareRequestState != null) {
                            pendingAnalysisShareRequestState = null
                            onPendingAnalysisShareHandled()
                        }
                    }
                    MainTab.PROFILE -> profileRootResetNonce += 1
                }
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
                        if (effectiveGuideStep == MainEntryGuideStep.MENU) {
                            dismissMenuGuide()
                        }
                        selectedTab = lastActiveTab
                    }
            )
        }

        if (effectiveGuideStep == MainEntryGuideStep.FAB) {
            FabGuideOverlay(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(MainZIndex.GUIDE_OVERLAY),
                onFabClick = {
                    if (isStaticGuidePreview) {
                        onPreviewGuideFabClick?.invoke()
                    } else {
                        activateFabGuide()
                        selectedTab = MainTab.CLIMBING
                    }
                }
            )
        }

        if (effectiveGuideStep == MainEntryGuideStep.MENU) {
            MenuGuideOverlay(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(MainZIndex.GUIDE_OVERLAY),
                onDismiss = {
                    if (isStaticGuidePreview) {
                        onPreviewGuideMenuDismiss?.invoke()
                    } else {
                        dismissMenuGuide()
                        selectedTab = lastActiveTab
                    }
                }
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .zIndex(
                    if (
                        effectiveGuideStep == MainEntryGuideStep.FAB ||
                        effectiveGuideStep == MainEntryGuideStep.MENU
                    ) {
                        MainZIndex.MENU_OVERLAY + 1f
                    } else {
                        MainZIndex.FAB
                    }
                )
        ) {
            ClimbingFloatingButton(
                isSelected = isClimbing,
                onClick = {
                    if (isStaticGuidePreview && effectiveGuideStep == MainEntryGuideStep.FAB) {
                        onPreviewGuideFabClick?.invoke()
                        return@ClimbingFloatingButton
                    }
                    if (isStaticGuidePreview && effectiveGuideStep == MainEntryGuideStep.MENU) {
                        onPreviewGuideMenuDismiss?.invoke()
                        return@ClimbingFloatingButton
                    }

                    if (effectiveGuideStep == MainEntryGuideStep.FAB) {
                        activateFabGuide()
                    } else if (effectiveGuideStep == MainEntryGuideStep.MENU && isClimbing) {
                        dismissMenuGuide()
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
                    .padding(
                        bottom = if (effectiveGuideStep == MainEntryGuideStep.MENU) {
                            137.dp
                        } else {
                            MainChromeDefaults.MenuOverlayBottomPadding
                        }
                    )
                    .zIndex(MainZIndex.MENU_OVERLAY)
            ) {
                ClimbingMenuOverlay(
                    onNavigateToUpload = {
                        if (!isStaticGuidePreview) {
                            if (effectiveGuideStep == MainEntryGuideStep.MENU) {
                                dismissMenuGuide()
                            }
                            pendingClimbingDestination = PendingClimbingDestination.Upload
                            selectedTab = lastActiveTab
                        }
                    },
                    onNavigateToRecord = {
                        if (!isStaticGuidePreview) {
                            if (effectiveGuideStep == MainEntryGuideStep.MENU) {
                                dismissMenuGuide()
                            }
                            pendingClimbingDestination = PendingClimbingDestination.Record
                            selectedTab = lastActiveTab
                        }
                    },
                    onDismiss = {
                        if (!isStaticGuidePreview) {
                            if (effectiveGuideStep == MainEntryGuideStep.MENU) {
                                dismissMenuGuide()
                            }
                            if (pendingClimbingDestination == null) {
                                selectedTab = lastActiveTab
                            }
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
    const val GUIDE_OVERLAY = 350f
    const val FAB = 300f
    const val MENU_OVERLAY = 400f
}

private const val GUIDE_1_OVERLAY_ASSET = "file:///android_asset/figma/onboarding_over1.png"
private const val GUIDE_2_OVERLAY_ASSET = "file:///android_asset/figma/onboarding_over2.png"

@Composable
private fun FabGuideOverlay(
    modifier: Modifier = Modifier,
    onFabClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {}
    ) {
        AsyncImage(
            model = GUIDE_1_OVERLAY_ASSET,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {}
        )
    }
}

@Composable
private fun MenuGuideOverlay(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit
) {
    Box(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    ) {
        AsyncImage(
            model = GUIDE_2_OVERLAY_ASSET,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize()
        )
    }
}

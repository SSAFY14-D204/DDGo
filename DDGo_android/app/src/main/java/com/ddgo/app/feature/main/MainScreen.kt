package com.ddgo.app.feature.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ddgo.app.feature.analysis.AnalysisScreen
import com.ddgo.app.feature.calendar.CalendarScreen
import com.ddgo.app.feature.climbing.ClimbingMenuOverlay
import com.ddgo.app.feature.community.CommunityScreen
import com.ddgo.app.feature.profile.ProfileScreen

@Composable
fun MainScreen(
    onNavigateToUpload: () -> Unit,
    onNavigateToRecord: () -> Unit,
    onNavigateToAuth: () -> Unit,
    onNavigateToDebug: () -> Unit = {}
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(MainTab.CALENDAR) }
    var lastActiveTab by rememberSaveable { mutableIntStateOf(MainTab.CALENDAR) }
    val isClimbing = selectedTab == MainTab.CLIMBING

    Box(modifier = Modifier.fillMaxSize()) {
        // [Z=0] 메인 콘텐츠 (이전 화면 맥락 유지)
        Scaffold(
            modifier = Modifier.fillMaxSize().zIndex(MainZIndex.CONTENT)
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(bottom = 60.dp)
            ) {
                when (lastActiveTab) {
                    MainTab.CALENDAR   -> CalendarScreen()
                    MainTab.COMMUNITY  -> CommunityScreen()
                    MainTab.ANALYSIS   -> AnalysisScreen()
                    MainTab.PROFILE    -> ProfileScreen(
                        onNavigateToAuth = onNavigateToAuth
                    )
                }
            }
        }

        // [Z=100] 하단 네비게이션 바 본체 (Dim 보다 아래 계층)
        CustomBottomNavBarBase(
            selectedIndex = selectedTab,
            onTabSelected = { tab ->
                selectedTab = tab
                if (tab != MainTab.CLIMBING) lastActiveTab = tab
            },
            onNavigateToDebug = onNavigateToDebug,
            modifier = Modifier.align(Alignment.BottomCenter).zIndex(MainZIndex.NAV_BAR)
        )

        // [Z=200] 유리판 Dim 레이어 (Nav Bar 본체를 덮음)
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

        // [Z=300] 중앙 FAB (유리판 위에 선명하게 뚫고 나옴)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(MainZIndex.FAB)
        ) {
            ClimbingFloatingButton(
                isSelected = isClimbing,
                onClick = {
                    if (isClimbing) selectedTab = lastActiveTab
                    else selectedTab = MainTab.CLIMBING
                }
            )
        }

        // [Z=400] 말풍선 메뉴 (최상단)
        if (isClimbing) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 110.dp)
                    .zIndex(MainZIndex.MENU_OVERLAY)
            ) {
                ClimbingMenuOverlay(
                    onNavigateToUpload = onNavigateToUpload,
                    onNavigateToRecord = onNavigateToRecord,
                    onDismiss = { selectedTab = lastActiveTab }
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

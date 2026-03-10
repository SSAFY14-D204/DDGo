package com.ddgo.app.feature.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ddgo.app.feature.analysis.AnalysisScreen
import com.ddgo.app.feature.calendar.CalendarScreen
import com.ddgo.app.feature.climbing.ClimbingScreen
import com.ddgo.app.feature.community.CommunityScreen
import com.ddgo.app.feature.profile.ProfileScreen

/**
 * 메인 화면 - CustomBottomNavigationBar를 공통 하단 바로 사용.
 * 초기 탭은 캘린더(index=0).
 */
@Composable
fun MainScreen() {
    // 탭 상태 (화면 회전 등에도 유지되도록 rememberSaveable)
    var selectedTab by rememberSaveable { mutableIntStateOf(MainTab.CALENDAR) }

    Scaffold(
        bottomBar = {
            CustomBottomNavigationBar(
                selectedIndex = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                MainTab.CALENDAR   -> CalendarScreen()
                MainTab.COMMUNITY  -> CommunityScreen()
                MainTab.CLIMBING   -> ClimbingScreen()
                MainTab.ANALYSIS   -> AnalysisScreen()
                MainTab.PROFILE    -> ProfileScreen()
            }
        }
    }
}

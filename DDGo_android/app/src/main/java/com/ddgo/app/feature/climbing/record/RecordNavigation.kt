package com.ddgo.app.feature.climbing.record

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.ddgo.app.navigation.ScreenRoutes

/**
 * 실시간 기록 플로우를 담당하는 서브 네비게이션 그래프.
 *
 * 플로우: 기록 메인 화면 → (추후 화면 추가)
 * 진입: ScreenRoutes.Climbing.Record.route
 * 시작 화면: ScreenRoutes.Climbing.Record.RECORD_MAIN
 */
fun NavGraphBuilder.recordGraph(
    navController: NavController
) {
    navigation(
        startDestination = ScreenRoutes.Climbing.Record.RECORD_MAIN,
        route = ScreenRoutes.Climbing.Record.route
    ) {
        composable(ScreenRoutes.Climbing.Record.RECORD_MAIN) {
            TempRecordScreen()
        }
    }
}

/**
 * 기록 그래프로 이동하는 NavController 확장 함수.
 */
fun NavController.navigateToRecord() {
    navigate(ScreenRoutes.Climbing.Record.route)
}

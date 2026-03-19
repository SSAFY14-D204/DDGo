package com.ddgo.app.feature.main

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.ddgo.app.feature.climbing.record.navigateToRecord
import com.ddgo.app.feature.climbing.record.recordGraph
import com.ddgo.app.feature.climbing.upload.uploadGraph
import com.ddgo.app.navigation.ScreenRoutes

/**
 * 메인 플로우 전체를 담당하는 서브 네비게이션 그래프.
 *
 * Splash/Auth 이후 진입하는 최상위 그래프로,
 * 탭 UI(MainScreen)와 하위 기능 그래프들(upload, record)을 포함합니다.
 *
 * 하위 구조:
 * main_graph
 * ├── main (MainScreen - 하단 탭 UI)
 * ├── uploadGraph → upload_graph
 * └── recordGraph → record_graph
 */
fun NavGraphBuilder.mainGraph(
    navController: NavController,
    onNavigateToAuth: () -> Unit,
    onNavigateToDebug: () -> Unit = {}
) {
    navigation(
        startDestination = ScreenRoutes.Main.route,
        route = ScreenRoutes.MainGraph.route
    ) {
        composable(route = ScreenRoutes.Main.route) {
            MainScreen(
                onNavigateToUpload = {
                    navController.navigate(ScreenRoutes.Climbing.Upload.route)
                },
                onNavigateToRecord = {
                    navController.navigateToRecord()
                },
                onNavigateToAuth = onNavigateToAuth,
                onNavigateToDebug = onNavigateToDebug
            )
        }

        // 영상 업로드 서브 그래프 (갤러리 → 업로드 → 결과)
        uploadGraph(navController = navController)

        // 실시간 기록 서브 그래프 (카메라 → 기록 → 결과)
        recordGraph(navController = navController)
    }
}

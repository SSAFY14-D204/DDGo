package com.ddgo.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ddgo.app.feature.auth.authGraph
import com.ddgo.app.feature.debug.debugGraph
import com.ddgo.app.feature.main.mainGraph
import com.ddgo.app.feature.splash.SplashScreen

/**
 * 앱 전체 네비게이션 진입점.
 *
 * 각 플로우는 별도 파일에서 정의된 그래프 함수로 관리됩니다:
 * - [authGraph]  : 인증 플로우 (AuthNavigation.kt)
 * - [mainGraph]  : 메인 플로우 (MainNavigation.kt)
 * - [debugGraph] : 디버그 플로우 (DebugNavigation.kt)
 */
@Composable
fun NavGraph() {
    val navController: NavHostController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = ScreenRoutes.Splash.route
    ) {
        // 스플래시
        composable(ScreenRoutes.Splash.route) {
            SplashScreen(
                onNavigateToAuth = {
                    navController.navigate(ScreenRoutes.Auth.route) {
                        popUpTo(ScreenRoutes.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToMain = {
                    navController.navigate(ScreenRoutes.MainGraph.route) {
                        popUpTo(ScreenRoutes.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        // 인증 플로우 (로그인, 회원가입)
        authGraph(
            navController = navController,
            onLoginSuccess = {
                navController.navigate(ScreenRoutes.MainGraph.route) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            }
        )

        // 메인 플로우 (탭 UI + 클라이밍 기능들)
        mainGraph(
            navController = navController,
            onNavigateToDebug = {
                navController.navigate("debug_main")
            }
        )

        // 디버그 플로우
        debugGraph(navController = navController)
    }
}

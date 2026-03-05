package com.ddgo.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ddgo.app.feature.auth.AuthScreen
import com.ddgo.app.feature.report.ReportScreen
import com.ddgo.app.feature.upload.UploadScreen

/**
 * 앱 전체 네비게이션 그래프.
 *
 * ── Compose Navigation 학습 포인트 ─────────────────────────────────
 * - rememberNavController(): NavController 생성 및 기억
 * - NavHost: 화면 컨테이너 (startDestination으로 시작 화면 지정)
 * - composable("route"): 특정 경로에 Composable 화면을 연결
 * - navController.navigate(): 화면 이동
 * - navController.popBackStack(): 이전 화면으로 돌아가기
 * ──────────────────────────────────────────────────────────────────
 *
 * 새 화면 추가 방법:
 *   1. ScreenRoutes.kt에 경로 추가
 *   2. 이 파일에 composable() 블록 추가
 */
@Composable
fun NavGraph(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = ScreenRoutes.Auth.route
    ) {
        composable(route = ScreenRoutes.Auth.route) {
            AuthScreen(
                onLoginSuccess = {
                    navController.navigate(ScreenRoutes.Upload.route) {
                        // 로그인 화면을 백스택에서 제거 (뒤로 가기로 돌아올 수 없음)
                        popUpTo(ScreenRoutes.Auth.route) { inclusive = true }
                    }
                }
            )
        }

        composable(route = ScreenRoutes.Upload.route) {
            UploadScreen(
                onAnalyzeDone = {
                    navController.navigate(ScreenRoutes.Report.route)
                }
            )
        }

        composable(route = ScreenRoutes.Report.route) {
            ReportScreen()
        }
    }
}

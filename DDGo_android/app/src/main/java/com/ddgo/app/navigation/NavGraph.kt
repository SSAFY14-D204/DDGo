package com.ddgo.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ddgo.app.feature.auth.authGraph
import com.ddgo.app.feature.debug.debugGraph
import com.ddgo.app.feature.main.MainScreen
import com.ddgo.app.feature.splash.SplashScreen

@Composable
fun NavGraph() {
    val navController: NavHostController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = ScreenRoutes.Splash.route
    ) {
        composable(ScreenRoutes.Splash.route) {
            SplashScreen(
                onNavigateToAuth = {
                    navController.navigate(ScreenRoutes.Auth.route) {
                        popUpTo(ScreenRoutes.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToMain = {
                    navController.navigate(ScreenRoutes.Main.route) {
                        popUpTo(ScreenRoutes.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        // ✅ viewModel을 넘기지 않고, authGraph 내부에서 스스로 관리하게 함
        authGraph(
            navController = navController,
            onLoginSuccess = {
                navController.navigate(ScreenRoutes.Main.route) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            }
        )

        composable(route = ScreenRoutes.Main.route) {
            MainScreen(
                onNavigateToDebug = { navController.navigate("debug_main") }
            )
        }

        // 디버깅 페이지 연결 //
        debugGraph(navController = navController)

    }
}

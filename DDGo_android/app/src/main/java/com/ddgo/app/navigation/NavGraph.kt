package com.ddgo.app.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ddgo.app.feature.auth.AuthViewModel
import com.ddgo.app.feature.auth.authGraph
import com.ddgo.app.feature.main.MainScreen
import com.ddgo.app.feature.report.ReportScreen
import com.ddgo.app.feature.splash.SplashScreen
import com.ddgo.app.feature.upload.UploadScreen

@Composable
fun NavGraph(
) {
    val navController: NavHostController = rememberNavController()
    val authViewModel: AuthViewModel = viewModel()

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

        authGraph(
            navController = navController,
            viewModel = authViewModel,
            onLoginSuccess = {
                navController.navigate(ScreenRoutes.Main.route) {
                    popUpTo(ScreenRoutes.Auth.route) { inclusive = true }
                }
            }
        )

        composable(route = ScreenRoutes.Main.route) {
            MainScreen()
        }
    }
}

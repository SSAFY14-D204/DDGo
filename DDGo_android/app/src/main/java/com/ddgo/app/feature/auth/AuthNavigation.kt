package com.ddgo.app.feature.auth

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.ddgo.app.navigation.ScreenRoutes

fun NavGraphBuilder.authGraph(navController: NavController, viewModel: AuthViewModel) {
    navigation(startDestination = ScreenRoutes.Auth.WELCOME, route = ScreenRoutes.Auth.route) {

        composable(ScreenRoutes.Auth.WELCOME) {
            AuthLandingScreen(
                onLoginClick = { navController.navigate(ScreenRoutes.Auth.LOGIN_EMAIL) },
                onRegisterClick = { navController.navigate(ScreenRoutes.Auth.REGISTER_EMAIL) }
            )
        }

        composable(ScreenRoutes.Auth.LOGIN_EMAIL) {
            LoginEmailScreen(
                viewModel,
                onNext = { navController.navigate(ScreenRoutes.Auth.LOGIN_PASSWORD) })
        }

        composable(ScreenRoutes.Auth.LOGIN_PASSWORD) {
            LoginPasswordScreen(viewModel, onLoginComplete = {
                navController.popBackStack(ScreenRoutes.Auth.LOGIN_EMAIL, false)
            }, onBack = { navController.popBackStack() })
        }

        composable(ScreenRoutes.Auth.REGISTER_EMAIL) {
            RegisterEmailScreen(viewModel, onNext = { navController.navigate(ScreenRoutes.Auth.REGISTER_PASSWORD) }, onBack = { navController.popBackStack() })
        }

        composable(ScreenRoutes.Auth.REGISTER_PASSWORD) {
            RegisterPasswordScreen(viewModel, onRegComplete = {
                navController.popBackStack(ScreenRoutes.Auth.LOGIN_EMAIL, false)
            }, onBack = { navController.popBackStack() })
        }

    }
}
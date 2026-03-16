package com.ddgo.app.feature.debug

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

fun NavGraphBuilder.debugGraph(navController: NavController) {
    composable("debug_main") {
        DebugPoseScreen(
            onNavigateToSplash = {
                navController.navigate(com.ddgo.app.navigation.ScreenRoutes.Splash.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
        )
    }
}

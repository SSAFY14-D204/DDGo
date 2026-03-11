package com.ddgo.app.feature.debug

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

fun NavGraphBuilder.debugGraph(navController: NavController) {
    composable("debug_main") {
        DebugPoseScreen()
    }
}

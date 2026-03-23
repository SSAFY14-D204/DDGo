package com.ddgo.app.feature.climbing.upload

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import com.ddgo.app.navigation.ScreenRoutes

@Composable
internal fun rememberSharedUploadViewModel(
    navController: NavController,
    backStackEntry: NavBackStackEntry
): UploadViewModel {
    val parentEntry = remember(backStackEntry) {
        navController.getBackStackEntry(ScreenRoutes.MainGraph.route)
    }
    return hiltViewModel(parentEntry)
}

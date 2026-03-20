package com.ddgo.app.feature.climbing.record

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.ddgo.app.feature.climbing.record.ui.RecordRoute
import com.ddgo.app.feature.climbing.shared.navigation.navigateToClimbingUpload
import com.ddgo.app.feature.climbing.shared.navigation.toClimbingUploadEntryArgs
import com.ddgo.app.navigation.ScreenRoutes

fun NavGraphBuilder.recordGraph(
    navController: NavController
) {
    navigation(
        startDestination = ScreenRoutes.Climbing.Record.RECORD_MAIN,
        route = ScreenRoutes.Climbing.Record.route
    ) {
        composable(ScreenRoutes.Climbing.Record.RECORD_MAIN) {
            RecordRoute(
                onNavigateBack = { navController.popBackStack() },
                onRecordedDraftReady = { draft ->
                    navController.navigateToClimbingUpload(draft.toClimbingUploadEntryArgs())
                }
            )
        }
    }
}

fun NavController.navigateToRecord() {
    navigate(ScreenRoutes.Climbing.Record.route)
}

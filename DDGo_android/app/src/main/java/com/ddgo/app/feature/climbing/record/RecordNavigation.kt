package com.ddgo.app.feature.climbing.record

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.ddgo.app.feature.climbing.record.ui.RecordRoute
import com.ddgo.app.feature.climbing.upload.ChallengeCreationUiState
import com.ddgo.app.feature.climbing.upload.GymResolveUiState
import com.ddgo.app.feature.climbing.upload.GymSearchUiState
import com.ddgo.app.feature.climbing.upload.UploadViewModel
import com.ddgo.app.feature.climbing.upload.rememberSharedUploadViewModel
import com.ddgo.app.feature.climbing.upload.navigateToRealtimeRecordedAttempt
import com.ddgo.app.navigation.ScreenRoutes

fun NavGraphBuilder.recordGraph(
    navController: NavController
) {
    navigation(
        startDestination = ScreenRoutes.Climbing.Record.RECORD_MAIN,
        route = ScreenRoutes.Climbing.Record.route
    ) {
        composable(ScreenRoutes.Climbing.Record.RECORD_MAIN) {
            val uploadViewModel: UploadViewModel =
                rememberSharedUploadViewModel(navController, it)
            val gymSearchUiState by uploadViewModel.gymSearchUiState.collectAsState(
                initial = GymSearchUiState.Idle
            )
            val gymResolveUiState by uploadViewModel.gymResolveUiState.collectAsState(
                initial = GymResolveUiState.Idle
            )
            val challengeCreationUiState by uploadViewModel.challengeCreationUiState.collectAsState(
                initial = ChallengeCreationUiState.Idle
            )
            val realtimeOverlayUiState = uploadViewModel.realtimeOverlayUiState.copy(
                gymSearchUiState = gymSearchUiState,
                gymResolveUiState = gymResolveUiState,
                challengeCreationUiState = challengeCreationUiState
            )

            RecordRoute(
                onNavigateBack = { navController.popBackStack() },
                realtimeOverlayUiState = realtimeOverlayUiState,
                realtimeAttemptActionState = uploadViewModel.realtimeAttemptActionState,
                onRecordedDraftReady = { draft ->
                    navController.navigateToRealtimeRecordedAttempt(
                        recordedVideoUri = draft.videoUri,
                        realtimeSessionId = draft.realtimeSessionId
                    )
                },
                onOpenGymList = uploadViewModel::openRealtimeGymList,
                onSearchNearbyGyms = uploadViewModel::searchNearbyPlaces,
                onSearchQueryChange = uploadViewModel::onRealtimeGymSearchQueryChanged,
                onSelectGym = uploadViewModel::onRealtimeNearbyPlaceSelected,
                onSelectDifficulty = uploadViewModel::onRealtimeGymGradeSelected,
                onSelectHoldColor = uploadViewModel::onRealtimeHoldColorSelected,
                onSetHoldColorSheetVisible = uploadViewModel::updateRealtimeHoldColorSheetVisible,
                onTapFinish = {
                    uploadViewModel.prepareFinalAnalysisLoading()
                    navController.navigate(ScreenRoutes.Climbing.Upload.REALTIME_ANALYSIS_LOADING) {
                        launchSingleTop = true
                    }
                },
                onTapRetake = uploadViewModel::prepareRealtimeRetake
            )
        }
    }
}

fun NavController.navigateToRecord() {
    navigate(ScreenRoutes.Climbing.Record.route)
}

package com.ddgo.app.feature.climbing.record

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.ddgo.app.feature.climbing.record.ui.RecordRoute
import com.ddgo.app.feature.climbing.upload.ChallengeCreateEntryStep
import com.ddgo.app.feature.climbing.upload.ChallengeCreatePresentationMode
import com.ddgo.app.feature.climbing.upload.ChallengeCreationUiState
import com.ddgo.app.feature.climbing.upload.ChallengeCreateScreen
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
            val challengeCreateCardContent: @Composable () -> Unit = remember(uploadViewModel) {
                @Composable {
                    ChallengeCreateScreen(
                        viewModel = uploadViewModel,
                        initialStep = ChallengeCreateEntryStep.LEVEL,
                        minimumStep = ChallengeCreateEntryStep.LEVEL,
                        presentationMode = ChallengeCreatePresentationMode.RealtimeEmbedded,
                        onNavigateBack = uploadViewModel::openRealtimeGymList
                    )
                }
            }

            RecordRoute(
                onNavigateBack = { navController.popBackStack() },
                realtimeOverlayUiState = realtimeOverlayUiState,
                onRecordedDraftReady = { draft ->
                    uploadViewModel.registerRealtimeRecordedAttempt(draft)
                    navController.navigateToRealtimeRecordedAttempt(
                        recordedVideoUri = draft.videoUri
                    )
                },
                onOpenGymList = uploadViewModel::openRealtimeGymList,
                onSearchNearbyGyms = uploadViewModel::searchNearbyPlaces,
                onSearchQueryChange = uploadViewModel::onRealtimeGymSearchQueryChanged,
                onSelectGym = uploadViewModel::onRealtimeNearbyPlaceSelected,
                onSelectHoldColor = uploadViewModel::onRealtimeHoldColorSelected,
                onSetHoldColorSheetVisible = uploadViewModel::updateRealtimeHoldColorSheetVisible,
                challengeCreateCardContent = challengeCreateCardContent
            )
        }
    }
}

fun NavController.navigateToRecord(clearRealtimeAttemptStack: Boolean = false) {
    if (clearRealtimeAttemptStack) {
        navigate(ScreenRoutes.Climbing.Record.RECORD_MAIN) {
            popUpTo(ScreenRoutes.Climbing.Record.RECORD_MAIN) {
                inclusive = true
            }
            launchSingleTop = true
        }
    } else {
        navigate(ScreenRoutes.Climbing.Record.route)
    }
}

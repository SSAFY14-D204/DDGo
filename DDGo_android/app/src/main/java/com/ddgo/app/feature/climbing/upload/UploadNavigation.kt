package com.ddgo.app.feature.climbing.upload

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.ddgo.app.feature.climbing.record.navigateToRecord
import com.ddgo.app.feature.climbing.upload.ui.analysis.route.ChallengeFinalAnalysisRoute
import com.ddgo.app.feature.climbing.upload.ui.analysis.route.FinalAnalysisRoute
import com.ddgo.app.navigation.ScreenRoutes

fun NavGraphBuilder.uploadGraph(
    navController: NavController
) {
    navigation(
        startDestination = ScreenRoutes.Climbing.Upload.ATTEMPT_UPLOAD,
        route = ScreenRoutes.Climbing.Upload.route
    ) {
        composable(ScreenRoutes.Climbing.Upload.REALTIME_SETUP) { backStackEntry ->
            val viewModel = rememberSharedUploadViewModel(navController, backStackEntry)

            LaunchedEffect(Unit) {
                viewModel.beginRealtimeChallengeUploadFlow()
                viewModel.setLocalAnalysisWithoutChallengeEnabled(false)
                navController.navigate(ScreenRoutes.Climbing.Record.route) {
                    popUpTo(ScreenRoutes.Climbing.Upload.REALTIME_SETUP) {
                        inclusive = true
                    }
                    launchSingleTop = true
                }
            }

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        composable(ScreenRoutes.Climbing.Upload.REALTIME_HOLD) { backStackEntry ->
            val viewModel = rememberSharedUploadViewModel(navController, backStackEntry)

            ChallengeHoldScreen(
                viewModel = viewModel,
                allowAdditionalUpload = false,
                onNavigateToAdditional = {
                    navController.navigate(ScreenRoutes.Climbing.Upload.CHALLENGE_COLOR)
                },
                onNavigateToHoldSelect = {
                    navController.navigate(ScreenRoutes.Climbing.Upload.REALTIME_HOLD_SELECT)
                }
            )
        }

        composable(ScreenRoutes.Climbing.Upload.REALTIME_HOLD_SELECT) { backStackEntry ->
            val viewModel = rememberSharedUploadViewModel(navController, backStackEntry)

            HoldSelectScreen(
                viewModel = viewModel,
                allowAdditionalUpload = false,
                onNavigateToNext = {
                    viewModel.prepareAttemptResultAnalysisLoading()
                    navController.navigate(ScreenRoutes.Climbing.Upload.REALTIME_ANALYSIS_LOADING) {
                        popUpTo(ScreenRoutes.Climbing.Upload.REALTIME_HOLD_SELECT) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(
            route = ScreenRoutes.Climbing.Upload.ATTEMPT_UPLOAD_WITH_ARGS,
            arguments = listOf(
                navArgument(ScreenRoutes.Climbing.Upload.ARG_RECORDED_VIDEO_URI) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val viewModel = rememberSharedUploadViewModel(navController, backStackEntry)
            val initialRecordedVideoUri = backStackEntry.arguments
                ?.getString(ScreenRoutes.Climbing.Upload.ARG_RECORDED_VIDEO_URI)
                ?.let(Uri::decode)

            AttemptUploadScreen(
                viewModel = viewModel,
                initialRecordedVideoUri = initialRecordedVideoUri,
                onNavigateToNext = {
                    navController.navigate(ScreenRoutes.Climbing.Upload.CHALLENGE_CREATE)
                }
            )
        }

        composable(
            route = ScreenRoutes.Climbing.Upload.REALTIME_RECORDED_ATTEMPT_WITH_ARGS,
            arguments = listOf(
                navArgument(ScreenRoutes.Climbing.Upload.ARG_RECORDED_VIDEO_URI) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val viewModel = rememberSharedUploadViewModel(navController, backStackEntry)
            val initialRecordedVideoUri = backStackEntry.arguments
                ?.getString(ScreenRoutes.Climbing.Upload.ARG_RECORDED_VIDEO_URI)
                ?.let(Uri::decode)

            LaunchedEffect(initialRecordedVideoUri) {
                val recordedUri = initialRecordedVideoUri?.takeIf { it.isNotBlank() }
                if (recordedUri == null) {
                    navController.popBackStack()
                    return@LaunchedEffect
                }
                viewModel.updateRealtimeVideoUri(uri = recordedUri)
                val nextRoute = if (viewModel.needsRealtimeHoldSelection()) {
                    ScreenRoutes.Climbing.Upload.REALTIME_HOLD
                } else {
                    viewModel.prepareAttemptResultAnalysisLoading()
                    ScreenRoutes.Climbing.Upload.REALTIME_ANALYSIS_LOADING
                }
                navController.navigate(nextRoute) {
                    popUpTo(
                        backStackEntry.destination.route
                            ?: ScreenRoutes.Climbing.Upload.REALTIME_RECORDED_ATTEMPT_WITH_ARGS
                    ) {
                        inclusive = true
                    }
                    launchSingleTop = true
                }
            }

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        composable(ScreenRoutes.Climbing.Upload.CHALLENGE_CREATE) { backStackEntry ->
            val viewModel = rememberSharedUploadViewModel(navController, backStackEntry)

            LaunchedEffect(Unit) {
                viewModel.setLocalAnalysisWithoutChallengeEnabled(false)
            }

            ChallengeCreateScreen(
                viewModel = viewModel,
                onNavigateToNext = {
                    navController.navigate(ScreenRoutes.Climbing.Upload.CHALLENGE_HOLD)
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(ScreenRoutes.Climbing.Upload.CHALLENGE_COLOR) { backStackEntry ->
            val viewModel = rememberSharedUploadViewModel(navController, backStackEntry)

            LaunchedEffect(Unit) {
                viewModel.setLocalAnalysisWithoutChallengeEnabled(true)
                viewModel.markHoldPrecomputeEligibleForCurrentSelection()
            }

            ChallengeCreateScreen(
                viewModel = viewModel,
                initialStep = ChallengeCreateEntryStep.COLOR,
                onNavigateToNext = {
                    navController.navigate(ScreenRoutes.Climbing.Upload.CHALLENGE_HOLD)
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // 2-2. 디버그용 이미지 선택 (베스트 프레임 선택 단계 우회)
        composable(ScreenRoutes.Climbing.Upload.DEV_IMAGE_PICKER) { backStackEntry ->
            val viewModel = rememberSharedUploadViewModel(navController, backStackEntry)

            DevImagePickScreen(
                viewModel = viewModel,
                onNavigateToNext = {
                    navController.navigate(ScreenRoutes.Climbing.Upload.CHALLENGE_HOLD) {
                        popUpTo(ScreenRoutes.Climbing.Upload.DEV_IMAGE_PICKER) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // 3. 홀드 탐지 대기 + 누락 홀드 추가
        composable(ScreenRoutes.Climbing.Upload.CHALLENGE_HOLD) { backStackEntry ->
            val viewModel = rememberSharedUploadViewModel(navController, backStackEntry)

            ChallengeHoldScreen(
                viewModel = viewModel,
                onNavigateToAdditional = {
                    navController.navigate(ScreenRoutes.Climbing.Upload.ADDITIONAL_UPLOAD)
                },
                onNavigateToHoldSelect = {
                    navController.navigate(ScreenRoutes.Climbing.Upload.HOLD_SELECT)
                }
            )
        }

        composable(ScreenRoutes.Climbing.Upload.ADDITIONAL_UPLOAD) { backStackEntry ->
            val viewModel = rememberSharedUploadViewModel(navController, backStackEntry)

            AdditionalUploadScreen(
                viewModel = viewModel,
                onNavigateToNext = {
                    val nextRoute = if (
                        viewModel.isAttemptOnlyUploadMode ||
                        viewModel.numberedHolds.isNotEmpty()
                    ) {
                        viewModel.prepareAttemptResultAnalysisLoading()
                        ScreenRoutes.Climbing.Upload.ANALYSIS_LOADING
                    } else {
                        ScreenRoutes.Climbing.Upload.HOLD_SELECT
                    }
                    navController.navigate(nextRoute)
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(ScreenRoutes.Climbing.Upload.HOLD_SELECT) { backStackEntry ->
            val viewModel = rememberSharedUploadViewModel(navController, backStackEntry)

            HoldSelectScreen(
                viewModel = viewModel,
                allowAdditionalUpload = true,
                onNavigateToAdditional = {
                    navController.navigate(ScreenRoutes.Climbing.Upload.ADDITIONAL_UPLOAD)
                },
                onNavigateToNext = {
                    viewModel.prepareAttemptResultAnalysisLoading()
                    navController.navigate(ScreenRoutes.Climbing.Upload.ANALYSIS_LOADING)
                }
            )
        }

        composable(ScreenRoutes.Climbing.Upload.REALTIME_ANALYSIS_LOADING) { backStackEntry ->
            val viewModel = rememberSharedUploadViewModel(navController, backStackEntry)

            AnalysisLoadingScreen(
                viewModel = viewModel,
                onLoadingFinished = {
                    when (viewModel.analysisLoadingPhase) {
                        AnalysisLoadingPhase.AttemptResultPreparation -> {
                            viewModel.prepareFinalAnalysisLoading()
                        }

                        AnalysisLoadingPhase.FinalAnalysisPreparation -> {
                            navController.navigate(ScreenRoutes.Climbing.Upload.FINAL_ANALYSIS) {
                                popUpTo(ScreenRoutes.Climbing.Upload.REALTIME_ANALYSIS_LOADING) {
                                    inclusive = true
                                }
                                launchSingleTop = true
                            }
                        }
                    }
                }
            )
        }

        composable(ScreenRoutes.Climbing.Upload.ANALYSIS_LOADING) { backStackEntry ->
            val viewModel = rememberSharedUploadViewModel(navController, backStackEntry)

            AnalysisLoadingScreen(
                viewModel = viewModel,
                onLoadingFinished = {
                    when (viewModel.analysisLoadingPhase) {
                        AnalysisLoadingPhase.AttemptResultPreparation -> {
                            viewModel.prepareFinalAnalysisLoading()
                        }

                        AnalysisLoadingPhase.FinalAnalysisPreparation -> {
                            navController.navigate(ScreenRoutes.Climbing.Upload.FINAL_ANALYSIS) {
                                popUpTo(ScreenRoutes.Climbing.Upload.ANALYSIS_LOADING) {
                                    inclusive = true
                                }
                                launchSingleTop = true
                            }
                        }
                    }
                }
            )
        }

        composable(ScreenRoutes.Climbing.Upload.REALTIME_ATTEMPT_RESULT) { backStackEntry ->
            val viewModel = rememberSharedUploadViewModel(navController, backStackEntry)

            AttemptResultScreen(
                viewModel = viewModel,
                isRealtimeAttemptFlow = true,
                onNavigateToCompare = {
                    viewModel.prepareFinalAnalysisLoading()
                    navController.navigate(ScreenRoutes.Climbing.Upload.REALTIME_ANALYSIS_LOADING) {
                        launchSingleTop = true
                    }
                },
                onNavigateToAddAttempt = {
                    viewModel.prepareRealtimeRetake()
                    navController.navigateToRecord(clearRealtimeAttemptStack = true)
                }
            )
        }

        composable(ScreenRoutes.Climbing.Upload.ATTEMPT_RESULT) { backStackEntry ->
            val viewModel = rememberSharedUploadViewModel(navController, backStackEntry)

            AttemptResultScreen(
                viewModel = viewModel,
                onNavigateToCompare = {
                    if (viewModel.isAttemptOnlyUploadMode) {
                        navController.navigate(ScreenRoutes.Climbing.Upload.FINAL_ANALYSIS)
                    } else {
                        viewModel.prepareFinalAnalysisLoading()
                        navController.navigate(ScreenRoutes.Climbing.Upload.ANALYSIS_LOADING)
                    }
                },
                onNavigateToAddAttempt = {
                    if (viewModel.enterAttemptOnlyUploadMode()) {
                        navController.navigate(ScreenRoutes.Climbing.Upload.ADDITIONAL_UPLOAD)
                    }
                }
            )
        }

        composable(ScreenRoutes.Climbing.Upload.HOLD_CONTACT_DEBUG) { backStackEntry ->
            val viewModel = rememberSharedUploadViewModel(navController, backStackEntry)

            HoldContactDebugScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(ScreenRoutes.Climbing.Upload.FINAL_ANALYSIS) { backStackEntry ->
            val viewModel = rememberSharedUploadViewModel(navController, backStackEntry)

            FinalAnalysisRoute(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToChallenge = {
                    navController.navigate(ScreenRoutes.Climbing.Upload.CHALLENGE_FINAL_ANALYSIS) {
                        launchSingleTop = true
                    }
                },
                onNavigateToMain = {
                    navController.navigate(ScreenRoutes.Main.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(ScreenRoutes.Climbing.Upload.CHALLENGE_FINAL_ANALYSIS) { backStackEntry ->
            val viewModel = rememberSharedUploadViewModel(navController, backStackEntry)

            ChallengeFinalAnalysisRoute(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToMain = {
                    navController.navigate(ScreenRoutes.Main.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}

fun NavController.navigateToUpload(
    recordedVideoUri: String? = null
) {
    val route = buildString {
        append(ScreenRoutes.Climbing.Upload.ATTEMPT_UPLOAD)
        if (!recordedVideoUri.isNullOrBlank()) {
            append("?")
            append(ScreenRoutes.Climbing.Upload.ARG_RECORDED_VIDEO_URI)
            append("=")
            append(Uri.encode(recordedVideoUri.orEmpty()))
        }
    }
    navigate(route)
}
fun NavController.navigateToRealtimeRecordedAttempt(
    recordedVideoUri: String? = null
) {
    val route = buildString {
        append(ScreenRoutes.Climbing.Upload.REALTIME_RECORDED_ATTEMPT)
        if (!recordedVideoUri.isNullOrBlank()) {
            append("?")
            append(ScreenRoutes.Climbing.Upload.ARG_RECORDED_VIDEO_URI)
            append("=")
            append(Uri.encode(recordedVideoUri.orEmpty()))
        }
    }
    navigate(route)
}



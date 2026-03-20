package com.ddgo.app.feature.climbing.upload

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.ddgo.app.feature.climbing.shared.navigation.ClimbingUploadEntryArgs
import com.ddgo.app.feature.climbing.shared.navigation.buildClimbingUploadRoute
import com.ddgo.app.feature.climbing.shared.navigation.toClimbingUploadEntryArgs
import com.ddgo.app.feature.climbing.upload.ui.analysis.route.FinalAnalysisRoute
import com.ddgo.app.navigation.ScreenRoutes

fun NavGraphBuilder.uploadGraph(
    navController: NavController
) {
    navigation(
        startDestination = ScreenRoutes.Climbing.Upload.ATTEMPT_UPLOAD,
        route = ScreenRoutes.Climbing.Upload.route
    ) {
        composable(
            route = ScreenRoutes.Climbing.Upload.ATTEMPT_UPLOAD_WITH_ARGS,
            arguments = listOf(
                navArgument(ScreenRoutes.Climbing.Upload.ARG_RECORDED_VIDEO_URI) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument(ScreenRoutes.Climbing.Upload.ARG_REALTIME_SESSION_ID) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(ScreenRoutes.Climbing.Upload.route)
            }
            val viewModel: UploadViewModel = hiltViewModel(parentEntry)
            val initialEntryArgs = backStackEntry.arguments.toClimbingUploadEntryArgs(
                recordedVideoUriArgName = ScreenRoutes.Climbing.Upload.ARG_RECORDED_VIDEO_URI,
                realtimeSessionIdArgName = ScreenRoutes.Climbing.Upload.ARG_REALTIME_SESSION_ID
            )

            AttemptUploadScreen(
                viewModel = viewModel,
                initialRecordedVideoUri = initialEntryArgs.recordedVideoUri,
                initialRealtimeSessionId = initialEntryArgs.realtimeSessionId,
                onNavigateToNext = {
                    navController.navigate(ScreenRoutes.Climbing.Upload.CHALLENGE_CREATE)
                }
            )
        }

        composable(ScreenRoutes.Climbing.Upload.CHALLENGE_CREATE) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(ScreenRoutes.Climbing.Upload.route)
            }
            val viewModel: UploadViewModel = hiltViewModel(parentEntry)

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
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(ScreenRoutes.Climbing.Upload.route)
            }
            val viewModel: UploadViewModel = hiltViewModel(parentEntry)

            LaunchedEffect(Unit) {
                viewModel.setLocalAnalysisWithoutChallengeEnabled(true)
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
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(ScreenRoutes.Climbing.Upload.route)
            }
            val viewModel: UploadViewModel = hiltViewModel(parentEntry)

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
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(ScreenRoutes.Climbing.Upload.route)
            }
            val viewModel: UploadViewModel = hiltViewModel(parentEntry)

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
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(ScreenRoutes.Climbing.Upload.route)
            }
            val viewModel: UploadViewModel = hiltViewModel(parentEntry)

            AdditionalUploadScreen(
                viewModel = viewModel,
                onNavigateToNext = {
                    val nextRoute = if (viewModel.isAttemptOnlyUploadMode) {
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
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(ScreenRoutes.Climbing.Upload.route)
            }
            val viewModel: UploadViewModel = hiltViewModel(parentEntry)

            HoldSelectScreen(
                viewModel = viewModel,
                onNavigateToNext = {
                    navController.navigate(ScreenRoutes.Climbing.Upload.ANALYSIS_LOADING)
                }
            )
        }

        composable(ScreenRoutes.Climbing.Upload.ANALYSIS_LOADING) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(ScreenRoutes.Climbing.Upload.route)
            }
            val viewModel: UploadViewModel = hiltViewModel(parentEntry)

            AnalysisLoadingScreen(
                viewModel = viewModel,
                onLoadingFinished = {
                    val popUpRoute = if (viewModel.isAttemptOnlyUploadMode) {
                        ScreenRoutes.Climbing.Upload.ADDITIONAL_UPLOAD
                    } else {
                        ScreenRoutes.Climbing.Upload.ATTEMPT_UPLOAD
                    }

                    navController.navigate(ScreenRoutes.Climbing.Upload.ATTEMPT_RESULT) {
                        popUpTo(popUpRoute) {
                            inclusive = true
                        }
                    }
                }
            )
        }

        composable(ScreenRoutes.Climbing.Upload.ATTEMPT_RESULT) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(ScreenRoutes.Climbing.Upload.route)
            }
            val viewModel: UploadViewModel = hiltViewModel(parentEntry)

            AttemptResultScreen(
                viewModel = viewModel,
                onNavigateToCompare = {
                    navController.navigate(ScreenRoutes.Climbing.Upload.FINAL_ANALYSIS)
                },
                onNavigateToAddAttempt = {
                    if (viewModel.enterAttemptOnlyUploadMode()) {
                        navController.navigate(ScreenRoutes.Climbing.Upload.ADDITIONAL_UPLOAD)
                    }
                }
            )
        }

        composable(ScreenRoutes.Climbing.Upload.HOLD_CONTACT_DEBUG) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(ScreenRoutes.Climbing.Upload.route)
            }
            val viewModel: UploadViewModel = hiltViewModel(parentEntry)

            HoldContactDebugScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(ScreenRoutes.Climbing.Upload.FINAL_ANALYSIS) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(ScreenRoutes.Climbing.Upload.route)
            }
            val viewModel: UploadViewModel = hiltViewModel(parentEntry)

            FinalAnalysisRoute(
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
    entryArgs: ClimbingUploadEntryArgs = ClimbingUploadEntryArgs()
) {
    navigate(
        buildClimbingUploadRoute(
            baseRoute = ScreenRoutes.Climbing.Upload.ATTEMPT_UPLOAD,
            recordedVideoUriArgName = ScreenRoutes.Climbing.Upload.ARG_RECORDED_VIDEO_URI,
            realtimeSessionIdArgName = ScreenRoutes.Climbing.Upload.ARG_REALTIME_SESSION_ID,
            entryArgs = entryArgs
        )
    )
}

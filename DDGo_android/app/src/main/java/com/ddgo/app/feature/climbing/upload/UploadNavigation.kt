package com.ddgo.app.feature.climbing.upload

import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.ddgo.app.feature.climbing.upload.ui.analysis.route.FinalAnalysisRoute
import com.ddgo.app.navigation.ScreenRoutes

/**
 * 영상 업로드 플로우를 담당하는 서브 네비게이션 그래프.
 *
 * 플로우:
 *   시도 업로드 → 챌린지 생성(이름→레벨→컬러) → 홀드 탐지+누락 추가 → 시작/끝 홀드 선택 → 결과 확인
 *
 * 진입: [ScreenRoutes.Climbing.Upload.route]
 * 시작 화면: [ScreenRoutes.Climbing.Upload.ATTEMPT_UPLOAD]
 *
 * 모든 화면이 동일한 UploadViewModel 인스턴스를 공유합니다 (uploadGraph 스코프).
 */
fun NavGraphBuilder.uploadGraph(
    navController: NavController
) {
    navigation(
        startDestination = ScreenRoutes.Climbing.Upload.ATTEMPT_UPLOAD,
        route = ScreenRoutes.Climbing.Upload.route
    ) {
        // 1. 영상 선택 화면
        composable(ScreenRoutes.Climbing.Upload.ATTEMPT_UPLOAD) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(ScreenRoutes.Climbing.Upload.route)
            }
            val viewModel: UploadViewModel = hiltViewModel(parentEntry)

            AttemptUploadScreen(
                viewModel = viewModel,
                onNavigateToNext = {
                    navController.navigate(ScreenRoutes.Climbing.Upload.CHALLENGE_CREATE)
                }
            )
        }

        // 2. 챌린지 생성 (이름 → 레벨 → 컬러 3단계 내부 처리)
        composable(ScreenRoutes.Climbing.Upload.CHALLENGE_CREATE) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(ScreenRoutes.Climbing.Upload.route)
            }
            val viewModel: UploadViewModel = hiltViewModel(parentEntry)

            LaunchedEffect(Unit) {
                viewModel.setLocalAnalysisWithoutChallengeEnabled(false)
            }

            ChallengeCreateScreen(
                viewModel        = viewModel,
                onNavigateToNext = {
                    navController.navigate(ScreenRoutes.Climbing.Upload.CHALLENGE_HOLD)
                },
                onNavigateBack   = { navController.popBackStack() }
            )
        }

        // 2-1. 색상 선택 바로 진입 (dev navigation 용)
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

        // 3. 홀드 탐지 대기 + 누락 홀드 추가
        composable(ScreenRoutes.Climbing.Upload.CHALLENGE_HOLD) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(ScreenRoutes.Climbing.Upload.route)
            }
            val viewModel: UploadViewModel = hiltViewModel(parentEntry)

            ChallengeHoldScreen(
                viewModel        = viewModel,
                onNavigateToAdditional = {
                    navController.navigate(ScreenRoutes.Climbing.Upload.ADDITIONAL_UPLOAD)
                },
                onNavigateToHoldSelect = {
                    navController.navigate(ScreenRoutes.Climbing.Upload.HOLD_SELECT)
                }
            )
        }

        // 추가 3-1. 추가 영상 다중 업로드 화면
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

        // 4. 시작/끝 홀드 선택
        composable(ScreenRoutes.Climbing.Upload.HOLD_SELECT) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(ScreenRoutes.Climbing.Upload.route)
            }
            val viewModel: UploadViewModel = hiltViewModel(parentEntry)

            HoldSelectScreen(
                viewModel        = viewModel,
                onNavigateToNext = {
                    navController.navigate(ScreenRoutes.Climbing.Upload.ANALYSIS_LOADING)
                }
            )
        }

        // 추가 3-2. 로딩 화면
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
                        // 결과 화면 진입 시 직전 업로드 스택을 정리해 뒤로 가기 동선을 단순하게 유지합니다.
                        popUpTo(popUpRoute) {
                            inclusive = true
                        }
                    }
                }
            )
        }

        // 5. 결과 화면
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

        // 5. 최종 분석 결과 화면
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

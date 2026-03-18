package com.ddgo.app.feature.climbing.upload

import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
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

            ChallengeCreateScreen(
                viewModel        = viewModel,
                onNavigateToNext = {
                    navController.navigate(ScreenRoutes.Climbing.Upload.CHALLENGE_HOLD)
                },
                onNavigateBack   = { navController.popBackStack() }
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
                    navController.navigate(ScreenRoutes.Climbing.Upload.HOLD_SELECT)
                }
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
                    navController.navigate(ScreenRoutes.Climbing.Upload.ATTEMPT_RESULT) {
                        // 결과 화면 진입 시 뒤로 가기 눌렀을 때 업로드 과정 전체를 생략하기 위한 팝업
                        popUpTo(ScreenRoutes.Climbing.Upload.ATTEMPT_UPLOAD) {
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
                }
            )
        }

        // 5. 최종 분석 결과 화면
        composable(ScreenRoutes.Climbing.Upload.FINAL_ANALYSIS) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(ScreenRoutes.Climbing.Upload.route)
            }
            val viewModel: UploadViewModel = hiltViewModel(parentEntry)

            FinalAnalysisScreen(
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

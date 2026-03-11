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
 * 플로우: 시도 업로드 → 챌린지 생성(이름→레벨→컬러) → 홀드 탐지 대기 → 결과 확인
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
        // 1. 영상 선택 화면 → 선택 즉시 다음 화면
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

        // 3. 홀드 탐지 대기 화면
        composable(ScreenRoutes.Climbing.Upload.CHALLENGE_HOLD) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(ScreenRoutes.Climbing.Upload.route)
            }
            val viewModel: UploadViewModel = hiltViewModel(parentEntry)

            ChallengeHoldScreen(
                viewModel        = viewModel,
                onNavigateToNext = {
                    navController.navigate(ScreenRoutes.Climbing.Upload.ATTEMPT_RESULT)
                }
            )
        }

        // 4. 결과 화면
        composable(ScreenRoutes.Climbing.Upload.ATTEMPT_RESULT) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(ScreenRoutes.Climbing.Upload.route)
            }
            val viewModel: UploadViewModel = hiltViewModel(parentEntry)

            AttemptResultScreen(viewModel = viewModel)
        }
    }
}

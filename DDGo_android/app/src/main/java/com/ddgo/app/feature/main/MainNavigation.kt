package com.ddgo.app.feature.main

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import androidx.navigation.navArgument
import com.ddgo.app.feature.calendar.CALENDAR_DETAIL_RESULT_CHALLENGE_ID
import com.ddgo.app.feature.calendar.calendarDetailRoute
import com.ddgo.app.feature.calendar.navigateToCalendarDetail
import com.ddgo.app.feature.calendar.rememberSharedCalendarViewModel
import com.ddgo.app.feature.climbing.record.recordGraph
import com.ddgo.app.feature.climbing.upload.navigateToUpload
import com.ddgo.app.feature.climbing.upload.uploadGraph
import com.ddgo.app.core.datastore.MainEntryGuideStep
import com.ddgo.app.navigation.PENDING_COMMUNITY_COMPOSE_REQUEST_KEY
import com.ddgo.app.navigation.toPendingCommunityComposeRequestOrNull
import com.ddgo.app.navigation.ScreenRoutes

/**
 * 메인 플로우 전체를 담당하는 서브 네비게이션 그래프.
 *
 * Splash/Auth 이후 진입하는 최상위 그래프로,
 * 탭 UI(MainScreen)와 하위 기능 그래프들(upload, record)을 포함합니다.
 *
 * 하위 구조:
 * main_graph
 * ├── main (MainScreen - 하단 탭 UI)
 * ├── uploadGraph → upload_graph
 * └── recordGraph → record_graph
 */
fun NavGraphBuilder.mainGraph(
    navController: NavController,
    onNavigateToAuth: () -> Unit,
    onNavigateToDebug: () -> Unit = {}
) {
    navigation(
        startDestination = ScreenRoutes.Main.route,
        route = ScreenRoutes.MainGraph.route
    ) {
        composable(
            route = ScreenRoutes.Main.ROUTE_WITH_ARG,
            arguments = listOf(
                navArgument(ScreenRoutes.Main.ARG_GUIDE_STEP) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val calendarViewModel = rememberSharedCalendarViewModel(navController, backStackEntry)
            val pendingCalendarChallengeId by backStackEntry.savedStateHandle
                .getStateFlow<Long?>(CALENDAR_DETAIL_RESULT_CHALLENGE_ID, null)
                .collectAsState()
            val pendingCommunityComposeRequestJson by backStackEntry.savedStateHandle
                .getStateFlow<String?>(PENDING_COMMUNITY_COMPOSE_REQUEST_KEY, null)
                .collectAsState()
            val pendingCommunityComposeRequest = pendingCommunityComposeRequestJson
                ?.toPendingCommunityComposeRequestOrNull()
            val previewGuideStep = backStackEntry.arguments
                ?.getString(ScreenRoutes.Main.ARG_GUIDE_STEP)
                ?.takeIf { it.isNotBlank() }
                ?.let(MainEntryGuideStep::fromStoredValue)

            MainScreen(
                onNavigateToUpload = {
                    navController.navigateToUpload(autoOpenPicker = true)
                },
                onNavigateToRecord = {
                    navController.navigate(ScreenRoutes.Climbing.Upload.REALTIME_SETUP)
                },
                onNavigateToCalendarDetail = navController::navigateToCalendarDetail,
                onNavigateToAuth = onNavigateToAuth,
                calendarViewModel = calendarViewModel,
                pendingCalendarChallengeId = pendingCalendarChallengeId,
                onPendingCalendarChallengeHandled = {
                    backStackEntry.savedStateHandle[CALENDAR_DETAIL_RESULT_CHALLENGE_ID] = null
                },
                pendingAnalysisShareRequest = pendingCommunityComposeRequest,
                onPendingAnalysisShareHandled = {
                    backStackEntry.savedStateHandle[PENDING_COMMUNITY_COMPOSE_REQUEST_KEY] = null
                },
                onNavigateToDebug = onNavigateToDebug,
                previewGuideStep = previewGuideStep
            )
        }

        // 영상 업로드 서브 그래프 (갤러리 → 업로드 → 결과)
        uploadGraph(navController = navController)

        // 실시간 기록 서브 그래프 (카메라 → 기록 → 결과)
        recordGraph(navController = navController)

        calendarDetailRoute(
            navController = navController,
            onEntrySelected = { challengeId ->
                navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.set(CALENDAR_DETAIL_RESULT_CHALLENGE_ID, challengeId)
                navController.popBackStack()
            }
        )
    }
}

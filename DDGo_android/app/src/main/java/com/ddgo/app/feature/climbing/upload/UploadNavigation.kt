package com.ddgo.app.feature.climbing.upload

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.ddgo.app.navigation.PENDING_COMMUNITY_COMPOSE_REQUEST_KEY
import com.ddgo.app.navigation.PendingCommunityComposeRequest
import com.ddgo.app.navigation.ScreenRoutes
import androidx.compose.runtime.Composable
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import com.ddgo.app.core.datastore.UploadRecoveryEntryIntent
import com.ddgo.app.core.ui.components.DarkSystemBarsEffect
import com.ddgo.app.feature.climbing.upload.ui.recovery.UploadRecoveryDialog
import com.ddgo.app.feature.climbing.upload.ui.recovery.UploadRecoveryDialogState
import com.ddgo.app.feature.climbing.upload.ui.recovery.uploadClosedResultRecoveryDialogState
import com.ddgo.app.feature.climbing.upload.ui.recovery.uploadRecoveryRestartDialogState
import com.ddgo.app.feature.climbing.upload.ui.recovery.uploadRecoveryRetryDialogState
import com.ddgo.app.navigation.toSavedStateValue
import kotlinx.coroutines.launch

private fun NavGraphBuilder.uploadComposable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit
) {
    composable(
        route = route,
        arguments = arguments
    ) { backStackEntry ->
        DarkSystemBarsEffect()
        content(backStackEntry)
    }
}

fun NavGraphBuilder.uploadGraph(
    navController: NavController
) {
    navigation(
        startDestination = ScreenRoutes.Climbing.Upload.ATTEMPT_UPLOAD,
        route = ScreenRoutes.Climbing.Upload.route
    ) {
        uploadComposable(ScreenRoutes.Climbing.Upload.REALTIME_SETUP) { backStackEntry ->
            val viewModel = rememberSharedUploadViewModel(navController, backStackEntry)
            val coroutineScope = rememberCoroutineScope()

            suspend fun prepareRealtimeEntry() {
                when (val result = viewModel.prepareUploadEntry(UploadRecoveryEntryIntent.REALTIME)) {
                    UploadEntryPreparationResult.NoRecovery -> {
                        if (!viewModel.beginRealtimeChallengeUploadFlow()) {
                            navController.popBackStack()
                            return
                        }
                        viewModel.setLocalAnalysisWithoutChallengeEnabled(false)
                        navController.navigate(ScreenRoutes.Climbing.Record.route) {
                            popUpTo(ScreenRoutes.Climbing.Upload.REALTIME_SETUP) {
                                inclusive = true
                            }
                            launchSingleTop = true
                        }
                    }

                    is UploadEntryPreparationResult.Recovered -> {
                        viewModel.setLocalAnalysisWithoutChallengeEnabled(false)
                        navController.navigate(result.target.toNavigationRoute()) {
                            popUpTo(ScreenRoutes.Climbing.Upload.REALTIME_SETUP) {
                                inclusive = true
                            }
                            launchSingleTop = true
                        }
                    }

                    UploadEntryPreparationResult.Blocked -> return
                }
            }

            LaunchedEffect(Unit) {
                prepareRealtimeEntry()
            }

            UploadRecoveryDialogLayer(
                dialogState = viewModel.pendingRecoveryPrompt.toDialogStateOrNull(),
                onConfirm = {
                    when (viewModel.pendingRecoveryPrompt?.type) {
                        UploadRecoveryPromptType.ClosedResult -> {
                            coroutineScope.launch {
                                viewModel.consumeRecoveryPrompt()
                                prepareRealtimeEntry()
                            }
                        }

                        UploadRecoveryPromptType.RetryRequired -> {
                            coroutineScope.launch {
                                viewModel.consumeRecoveryPrompt()
                                prepareRealtimeEntry()
                            }
                        }

                        UploadRecoveryPromptType.RestartRequired -> {
                            coroutineScope.launch {
                                viewModel.consumeRecoveryPrompt()
                                if (viewModel.restartAfterFailedRecovery(UploadRecoveryEntryIntent.REALTIME)) {
                                    prepareRealtimeEntry()
                                }
                            }
                        }

                        null -> Unit
                    }
                },
                onDismiss = {
                    when (viewModel.pendingRecoveryPrompt?.type) {
                        UploadRecoveryPromptType.ClosedResult -> {
                            coroutineScope.launch {
                                viewModel.consumeRecoveryPrompt()
                                prepareRealtimeEntry()
                            }
                        }

                        UploadRecoveryPromptType.RetryRequired -> {
                            viewModel.consumeRecoveryPrompt()
                            navController.popBackStack()
                        }

                        UploadRecoveryPromptType.RestartRequired -> {
                            viewModel.consumeRecoveryPrompt()
                            navController.popBackStack()
                        }

                        null -> Unit
                    }
                }
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        uploadComposable(ScreenRoutes.Climbing.Upload.REALTIME_HOLD) { backStackEntry ->
            val viewModel = rememberSharedUploadViewModel(navController, backStackEntry)

            LaunchedEffect(Unit) {
                viewModel.rememberRecoveryRoute(
                    route = UploadRecoveryRoute.REALTIME_HOLD,
                    entryIntent = UploadRecoveryEntryIntent.REALTIME
                )
            }

            ChallengeHoldScreen(
                viewModel = viewModel,
                allowAdditionalUpload = false,
                onNavigateToAdditional = {
                    navController.navigate(ScreenRoutes.Climbing.Upload.CHALLENGE_COLOR)
                },
                onNavigateToHoldSelect = {
                    navController.navigate(ScreenRoutes.Climbing.Upload.REALTIME_HOLD_SELECT)
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        uploadComposable(ScreenRoutes.Climbing.Upload.REALTIME_HOLD_SELECT) { backStackEntry ->
            val viewModel = rememberSharedUploadViewModel(navController, backStackEntry)

            LaunchedEffect(Unit) {
                viewModel.rememberRecoveryRoute(
                    route = UploadRecoveryRoute.REALTIME_HOLD_SELECT,
                    entryIntent = UploadRecoveryEntryIntent.REALTIME
                )
            }

            HoldSelectScreen(
                viewModel = viewModel,
                allowAdditionalUpload = false,
                onNavigateBack = { navController.popBackStack() },
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

        uploadComposable(
            route = ScreenRoutes.Climbing.Upload.ATTEMPT_UPLOAD_WITH_ARGS,
            arguments = listOf(
                navArgument(ScreenRoutes.Climbing.Upload.ARG_RECORDED_VIDEO_URI) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument(ScreenRoutes.Climbing.Upload.ARG_AUTO_OPEN_PICKER) {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStackEntry ->
            val viewModel = rememberSharedUploadViewModel(navController, backStackEntry)
            val coroutineScope = rememberCoroutineScope()
            val initialRecordedVideoUri = backStackEntry.arguments
                ?.getString(ScreenRoutes.Climbing.Upload.ARG_RECORDED_VIDEO_URI)
                ?.let(Uri::decode)
            val autoOpenPicker = backStackEntry.arguments
                ?.getBoolean(ScreenRoutes.Climbing.Upload.ARG_AUTO_OPEN_PICKER)
                ?: false
            var uploadEntryReady by rememberSaveable {
                mutableStateOf(false)
            }

            suspend fun prepareAttemptEntry() {
                when (val result = viewModel.prepareUploadEntry(UploadRecoveryEntryIntent.UPLOAD)) {
                    UploadEntryPreparationResult.NoRecovery -> {
                        uploadEntryReady = viewModel.beginNewChallengeUploadFlow()
                    }

                    is UploadEntryPreparationResult.Recovered -> {
                        val targetRoute = result.target.toNavigationRoute()
                        if (targetRoute == ScreenRoutes.Climbing.Upload.ATTEMPT_UPLOAD) {
                            uploadEntryReady = true
                        } else {
                            navController.navigate(targetRoute) {
                                popUpTo(ScreenRoutes.Climbing.Upload.ATTEMPT_UPLOAD) {
                                    inclusive = true
                                }
                                launchSingleTop = true
                            }
                        }
                    }

                    UploadEntryPreparationResult.Blocked -> {
                        uploadEntryReady = false
                    }
                }
            }

            LaunchedEffect(initialRecordedVideoUri, autoOpenPicker) {
                prepareAttemptEntry()
            }

            LaunchedEffect(uploadEntryReady) {
                if (uploadEntryReady) {
                    viewModel.rememberRecoveryRoute(
                        route = UploadRecoveryRoute.ATTEMPT_UPLOAD,
                        entryIntent = UploadRecoveryEntryIntent.UPLOAD
                    )
                }
            }

            UploadRecoveryDialogLayer(
                dialogState = viewModel.pendingRecoveryPrompt.toDialogStateOrNull(),
                onConfirm = {
                    when (viewModel.pendingRecoveryPrompt?.type) {
                        UploadRecoveryPromptType.ClosedResult -> {
                            coroutineScope.launch {
                                viewModel.consumeRecoveryPrompt()
                                viewModel.beginNewChallengeUploadFlow()
                                uploadEntryReady = true
                            }
                        }

                        UploadRecoveryPromptType.RetryRequired -> {
                            coroutineScope.launch {
                                viewModel.consumeRecoveryPrompt()
                                prepareAttemptEntry()
                            }
                        }

                        UploadRecoveryPromptType.RestartRequired -> {
                            coroutineScope.launch {
                                viewModel.consumeRecoveryPrompt()
                                if (viewModel.restartAfterFailedRecovery(UploadRecoveryEntryIntent.UPLOAD)) {
                                    uploadEntryReady = true
                                }
                            }
                        }

                        null -> Unit
                    }
                },
                onDismiss = {
                    when (viewModel.pendingRecoveryPrompt?.type) {
                        UploadRecoveryPromptType.RetryRequired -> {
                            viewModel.consumeRecoveryPrompt()
                            navController.popBackStack()
                        }

                        UploadRecoveryPromptType.ClosedResult -> {
                            viewModel.consumeRecoveryPrompt()
                            coroutineScope.launch {
                                viewModel.beginNewChallengeUploadFlow()
                                uploadEntryReady = true
                            }
                        }

                        UploadRecoveryPromptType.RestartRequired -> {
                            viewModel.consumeRecoveryPrompt()
                            navController.popBackStack()
                        }

                        null -> Unit
                    }
                }
            ) {
                if (uploadEntryReady) {
                    AttemptUploadScreen(
                        viewModel = viewModel,
                        initialRecordedVideoUri = initialRecordedVideoUri,
                        autoOpenPicker = autoOpenPicker,
                        prepareOnLaunch = false,
                        onNavigateToNext = {
                            navController.navigate(ScreenRoutes.Climbing.Upload.CHALLENGE_CREATE)
                        }
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }

        uploadComposable(
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

        uploadComposable(ScreenRoutes.Climbing.Upload.CHALLENGE_CREATE) { backStackEntry ->
            val viewModel = rememberSharedUploadViewModel(navController, backStackEntry)
            val initialStep = viewModel.recoveryCreateEntryStep

            LaunchedEffect(Unit) {
                viewModel.setLocalAnalysisWithoutChallengeEnabled(false)
                viewModel.rememberRecoveryRoute(
                    route = UploadRecoveryRoute.CHALLENGE_CREATE,
                    createStep = initialStep,
                    entryIntent = UploadRecoveryEntryIntent.UPLOAD
                )
            }

            ChallengeCreateScreen(
                viewModel = viewModel,
                initialStep = initialStep,
                minimumStep = ChallengeCreateEntryStep.GYM_NAME,
                onStepChanged = { step ->
                    viewModel.rememberRecoveryRoute(
                        route = UploadRecoveryRoute.CHALLENGE_CREATE,
                        createStep = step,
                        entryIntent = UploadRecoveryEntryIntent.UPLOAD
                    )
                },
                onNavigateToNext = {
                    navController.navigate(ScreenRoutes.Climbing.Upload.CHALLENGE_HOLD)
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        uploadComposable(ScreenRoutes.Climbing.Upload.CHALLENGE_COLOR) { backStackEntry ->
            val viewModel = rememberSharedUploadViewModel(navController, backStackEntry)

            LaunchedEffect(Unit) {
                viewModel.setLocalAnalysisWithoutChallengeEnabled(true)
                viewModel.markHoldPrecomputeEligibleForCurrentSelection()
                viewModel.rememberRecoveryRoute(
                    route = UploadRecoveryRoute.CHALLENGE_CREATE,
                    createStep = ChallengeCreateEntryStep.COLOR
                )
            }

            ChallengeCreateScreen(
                viewModel = viewModel,
                initialStep = ChallengeCreateEntryStep.COLOR,
                onStepChanged = { step ->
                    viewModel.rememberRecoveryRoute(
                        route = UploadRecoveryRoute.CHALLENGE_CREATE,
                        createStep = step
                    )
                },
                onNavigateToNext = {
                    navController.navigate(ScreenRoutes.Climbing.Upload.CHALLENGE_HOLD)
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // 2-2. 디버그용 이미지 선택 (베스트 프레임 선택 단계 우회)
        uploadComposable(ScreenRoutes.Climbing.Upload.DEV_IMAGE_PICKER) { backStackEntry ->
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
        uploadComposable(ScreenRoutes.Climbing.Upload.CHALLENGE_HOLD) { backStackEntry ->
            val viewModel = rememberSharedUploadViewModel(navController, backStackEntry)

            LaunchedEffect(Unit) {
                viewModel.rememberRecoveryRoute(route = UploadRecoveryRoute.CHALLENGE_HOLD)
            }

            ChallengeHoldScreen(
                viewModel = viewModel,
                onNavigateToAdditional = {
                    navController.navigate(ScreenRoutes.Climbing.Upload.ADDITIONAL_UPLOAD)
                },
                onNavigateToHoldSelect = {
                    navController.navigate(ScreenRoutes.Climbing.Upload.HOLD_SELECT)
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        uploadComposable(ScreenRoutes.Climbing.Upload.ADDITIONAL_UPLOAD) { backStackEntry ->
            val viewModel = rememberSharedUploadViewModel(navController, backStackEntry)

            LaunchedEffect(Unit) {
                viewModel.rememberRecoveryRoute(route = UploadRecoveryRoute.ADDITIONAL_UPLOAD)
            }

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

        uploadComposable(ScreenRoutes.Climbing.Upload.HOLD_SELECT) { backStackEntry ->
            val viewModel = rememberSharedUploadViewModel(navController, backStackEntry)

            LaunchedEffect(Unit) {
                viewModel.rememberRecoveryRoute(route = UploadRecoveryRoute.HOLD_SELECT)
            }

            HoldSelectScreen(
                viewModel = viewModel,
                allowAdditionalUpload = true,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAdditional = {
                    navController.navigate(ScreenRoutes.Climbing.Upload.ADDITIONAL_UPLOAD)
                },
                onNavigateToNext = {
                    viewModel.prepareAttemptResultAnalysisLoading()
                    navController.navigate(ScreenRoutes.Climbing.Upload.ANALYSIS_LOADING)
                }
            )
        }

        uploadComposable(ScreenRoutes.Climbing.Upload.REALTIME_ANALYSIS_LOADING) { backStackEntry ->
            val viewModel = rememberSharedUploadViewModel(navController, backStackEntry)
            val coroutineScope = rememberCoroutineScope()

            LaunchedEffect(Unit) {
                viewModel.rememberRecoveryRoute(
                    route = UploadRecoveryRoute.REALTIME_ANALYSIS_LOADING,
                    entryIntent = UploadRecoveryEntryIntent.REALTIME
                )
            }

            UploadRecoveryDialogLayer(
                dialogState = viewModel.pendingRecoveryPrompt.toDialogStateOrNull(),
                onConfirm = {
                    when (viewModel.pendingRecoveryPrompt?.type) {
                        UploadRecoveryPromptType.ClosedResult -> {
                            viewModel.consumeRecoveryPrompt()
                            navController.navigate(ScreenRoutes.Climbing.Upload.REALTIME_SETUP) {
                                popUpTo(ScreenRoutes.Climbing.Upload.REALTIME_ANALYSIS_LOADING) {
                                    inclusive = true
                                }
                                launchSingleTop = true
                            }
                        }

                        UploadRecoveryPromptType.RetryRequired -> {
                            coroutineScope.launch {
                                viewModel.consumeRecoveryPrompt()
                                viewModel.retryCurrentAnalysisLoadingPhase()
                            }
                        }

                        UploadRecoveryPromptType.RestartRequired -> {
                            coroutineScope.launch {
                                viewModel.consumeRecoveryPrompt()
                                if (viewModel.restartAfterFailedRecovery(UploadRecoveryEntryIntent.REALTIME)) {
                                    navController.navigate(ScreenRoutes.Climbing.Upload.REALTIME_SETUP) {
                                        popUpTo(ScreenRoutes.Climbing.Upload.REALTIME_ANALYSIS_LOADING) {
                                            inclusive = true
                                        }
                                        launchSingleTop = true
                                    }
                                }
                            }
                        }

                        null -> Unit
                    }
                },
                onDismiss = {
                    when (viewModel.pendingRecoveryPrompt?.type) {
                        UploadRecoveryPromptType.ClosedResult -> {
                            viewModel.consumeRecoveryPrompt()
                            navController.navigate(ScreenRoutes.Climbing.Upload.REALTIME_SETUP) {
                                popUpTo(ScreenRoutes.Climbing.Upload.REALTIME_ANALYSIS_LOADING) {
                                    inclusive = true
                                }
                                launchSingleTop = true
                            }
                        }

                        UploadRecoveryPromptType.RetryRequired -> {
                            viewModel.consumeRecoveryPrompt()
                            navController.popBackStack()
                        }

                        UploadRecoveryPromptType.RestartRequired -> {
                            viewModel.consumeRecoveryPrompt()
                            navController.popBackStack()
                        }

                        null -> Unit
                    }
                }
            ) {
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
        }

        uploadComposable(ScreenRoutes.Climbing.Upload.ANALYSIS_LOADING) { backStackEntry ->
            val viewModel = rememberSharedUploadViewModel(navController, backStackEntry)

            val coroutineScope = rememberCoroutineScope()

            LaunchedEffect(Unit) {
                viewModel.rememberRecoveryRoute(route = UploadRecoveryRoute.ANALYSIS_LOADING)
            }

            UploadRecoveryDialogLayer(
                dialogState = viewModel.pendingRecoveryPrompt.toDialogStateOrNull(),
                onConfirm = {
                    coroutineScope.launch {
                        viewModel.consumeRecoveryPrompt()
                        viewModel.beginNewChallengeUploadFlow()
                        navController.navigate(ScreenRoutes.Climbing.Upload.ATTEMPT_UPLOAD) {
                            popUpTo(ScreenRoutes.Climbing.Upload.ANALYSIS_LOADING) {
                                inclusive = true
                            }
                            launchSingleTop = true
                        }
                    }
                },
                onDismiss = {
                    coroutineScope.launch {
                        viewModel.consumeRecoveryPrompt()
                        viewModel.beginNewChallengeUploadFlow()
                        navController.navigate(ScreenRoutes.Climbing.Upload.ATTEMPT_UPLOAD) {
                            popUpTo(ScreenRoutes.Climbing.Upload.ANALYSIS_LOADING) {
                                inclusive = true
                            }
                            launchSingleTop = true
                        }
                    }
                }
            ) {
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
        }

        uploadComposable(ScreenRoutes.Climbing.Upload.REALTIME_ATTEMPT_RESULT) { backStackEntry ->
            val viewModel = rememberSharedUploadViewModel(navController, backStackEntry)

            LaunchedEffect(Unit) {
                viewModel.rememberRecoveryRoute(
                    route = UploadRecoveryRoute.REALTIME_ATTEMPT_RESULT,
                    entryIntent = UploadRecoveryEntryIntent.REALTIME
                )
            }

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

        uploadComposable(ScreenRoutes.Climbing.Upload.ATTEMPT_RESULT) { backStackEntry ->
            val viewModel = rememberSharedUploadViewModel(navController, backStackEntry)

            LaunchedEffect(Unit) {
                viewModel.rememberRecoveryRoute(route = UploadRecoveryRoute.ATTEMPT_RESULT)
            }

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

        uploadComposable(ScreenRoutes.Climbing.Upload.HOLD_CONTACT_DEBUG) { backStackEntry ->
            val viewModel = rememberSharedUploadViewModel(navController, backStackEntry)

            HoldContactDebugScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        uploadComposable(ScreenRoutes.Climbing.Upload.BATCH_AI_JSON_EXPORT) { backStackEntry ->
            val viewModel = rememberSharedUploadViewModel(navController, backStackEntry)

            BatchAiJsonExportScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        uploadComposable(ScreenRoutes.Climbing.Upload.FINAL_ANALYSIS) { backStackEntry ->
            val viewModel = rememberSharedUploadViewModel(navController, backStackEntry)

            LaunchedEffect(Unit) {
                viewModel.rememberRecoveryRoute(route = UploadRecoveryRoute.FINAL_ANALYSIS)
            }

            FinalAnalysisRoute(
                viewModel = viewModel,
                onNavigateToChallenge = {
                    navController.navigate(ScreenRoutes.Climbing.Upload.CHALLENGE_FINAL_ANALYSIS) {
                        launchSingleTop = true
                    }
                },
                onNavigateToMain = {
                    navController.navigate(ScreenRoutes.Main.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToCommunityCompose = { request ->
                    navController.navigateToCommunityCompose(request)
                }
            )
        }

        uploadComposable(ScreenRoutes.Climbing.Upload.CHALLENGE_FINAL_ANALYSIS) { backStackEntry ->
            val viewModel = rememberSharedUploadViewModel(navController, backStackEntry)

            LaunchedEffect(Unit) {
                viewModel.rememberRecoveryRoute(route = UploadRecoveryRoute.CHALLENGE_FINAL_ANALYSIS)
            }

            ChallengeFinalAnalysisRoute(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToMain = {
                    navController.navigate(ScreenRoutes.Main.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToCommunityCompose = { request ->
                    navController.navigateToCommunityCompose(request)
                }
            )
        }
    }
}

fun NavController.navigateToUpload(
    recordedVideoUri: String? = null,
    autoOpenPicker: Boolean = false
) {
    val queryParameters = buildList {
        if (!recordedVideoUri.isNullOrBlank()) {
            add(
                "${ScreenRoutes.Climbing.Upload.ARG_RECORDED_VIDEO_URI}=" +
                    Uri.encode(recordedVideoUri)
            )
        }
        if (autoOpenPicker) {
            add("${ScreenRoutes.Climbing.Upload.ARG_AUTO_OPEN_PICKER}=true")
        }
    }
    val route = buildString {
        append(ScreenRoutes.Climbing.Upload.ATTEMPT_UPLOAD)
        if (queryParameters.isNotEmpty()) {
            append("?")
            append(queryParameters.joinToString("&"))
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

private fun NavController.navigateToCommunityCompose(
    request: PendingCommunityComposeRequest
) {
    val mainEntry = getBackStackEntry(ScreenRoutes.Main.route)
    mainEntry.savedStateHandle[PENDING_COMMUNITY_COMPOSE_REQUEST_KEY] = request.toSavedStateValue()
    popBackStack(ScreenRoutes.Main.route, false)
}

private fun UploadRecoveryPrompt?.toDialogStateOrNull(): UploadRecoveryDialogState? {
    return when (this?.type) {
        UploadRecoveryPromptType.ClosedResult -> uploadClosedResultRecoveryDialogState(
            challengeResultLabel = challengeResult.toReadableRecoveryResultLabel()
        )

        UploadRecoveryPromptType.RetryRequired -> uploadRecoveryRetryDialogState(reason = reason)
        UploadRecoveryPromptType.RestartRequired ->
            uploadRecoveryRestartDialogState(reason = reason)
        null -> null
    }
}

private fun UploadRecoveryResumeTarget.toNavigationRoute(): String =
    when (route) {
        UploadRecoveryRoute.ATTEMPT_UPLOAD -> ScreenRoutes.Climbing.Upload.ATTEMPT_UPLOAD
        UploadRecoveryRoute.CHALLENGE_CREATE -> ScreenRoutes.Climbing.Upload.CHALLENGE_CREATE
        UploadRecoveryRoute.CHALLENGE_HOLD -> ScreenRoutes.Climbing.Upload.CHALLENGE_HOLD
        UploadRecoveryRoute.HOLD_SELECT -> ScreenRoutes.Climbing.Upload.HOLD_SELECT
        UploadRecoveryRoute.ADDITIONAL_UPLOAD -> ScreenRoutes.Climbing.Upload.ADDITIONAL_UPLOAD
        UploadRecoveryRoute.ANALYSIS_LOADING -> ScreenRoutes.Climbing.Upload.ANALYSIS_LOADING
        UploadRecoveryRoute.ATTEMPT_RESULT -> ScreenRoutes.Climbing.Upload.ATTEMPT_RESULT
        UploadRecoveryRoute.FINAL_ANALYSIS -> ScreenRoutes.Climbing.Upload.FINAL_ANALYSIS
        UploadRecoveryRoute.CHALLENGE_FINAL_ANALYSIS -> ScreenRoutes.Climbing.Upload.CHALLENGE_FINAL_ANALYSIS
        UploadRecoveryRoute.REALTIME_SETUP -> ScreenRoutes.Climbing.Record.route
        UploadRecoveryRoute.REALTIME_HOLD -> ScreenRoutes.Climbing.Upload.REALTIME_HOLD
        UploadRecoveryRoute.REALTIME_HOLD_SELECT -> ScreenRoutes.Climbing.Upload.REALTIME_HOLD_SELECT
        UploadRecoveryRoute.REALTIME_ANALYSIS_LOADING ->
            ScreenRoutes.Climbing.Upload.REALTIME_ANALYSIS_LOADING
        UploadRecoveryRoute.REALTIME_ATTEMPT_RESULT ->
            ScreenRoutes.Climbing.Upload.REALTIME_ATTEMPT_RESULT
    }

private fun String?.toReadableRecoveryResultLabel(): String? =
    when (this?.uppercase()) {
        "SUCCESS" -> "완등"
        "FAIL" -> "미완등"
        "UNKNOWN" -> "종료"
        else -> null
    }

private fun String?.toLegacyRecoveryResultLabel(): String? {
    return when (this?.uppercase()) {
        "SUCCESS" -> "완등"
        "FAIL" -> "미완등"
        "UNKNOWN" -> "종료"
        else -> null
    }
}

@Composable
fun UploadRecoveryDialogLayer(
    dialogState: UploadRecoveryDialogState?,
    onConfirm: () -> Unit,
    onDismiss: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    content()
    UploadRecoveryDialog(
        state = dialogState,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}



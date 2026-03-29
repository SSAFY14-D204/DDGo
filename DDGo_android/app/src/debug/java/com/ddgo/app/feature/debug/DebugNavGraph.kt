package com.ddgo.app.feature.debug

import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.composable
import com.ddgo.app.feature.climbing.upload.UploadViewModel
import com.ddgo.app.navigation.ScreenRoutes

internal const val PRE_POSE_LANDMARKER_ROUTE = "pre_pose_landmarker"
internal const val PRE_POSE_SMOOTH_FILTER_COMPARE_ROUTE = "pre_pose_smooth_filter_compare"

fun NavGraphBuilder.debugGraph(navController: NavController) {
    composable(ScreenRoutes.Debug.MAIN) {
        DebugPoseScreen(
            onNavigateToSplash = {
                navController.navigate(ScreenRoutes.Splash.route) {
                    popUpTo(0) { inclusive = true }
                }
            },
            onNavigateToPrePose = {
                navController.navigate(PRE_POSE_LANDMARKER_ROUTE)
            },
            onNavigateToSmoothFilter = {
                navController.navigate(PRE_POSE_SMOOTH_FILTER_COMPARE_ROUTE)
            },
            onNavigateToUploadPhysicsOverlay = {
                navController.navigate(ScreenRoutes.Debug.UPLOAD_PHYSICS_OVERLAY)
            }
        )
    }

    composable(PRE_POSE_LANDMARKER_ROUTE) {
        PrePoseLandmarkerScreen(
            onNavigateBack = {
                navController.popBackStack()
            },
            onNavigateToSmoothFilterCompare = {
                navController.navigate(PRE_POSE_SMOOTH_FILTER_COMPARE_ROUTE)
            }
        )
    }

    composable(PRE_POSE_SMOOTH_FILTER_COMPARE_ROUTE) { backStackEntry: NavBackStackEntry ->
        val prePoseEntry = remember(backStackEntry) {
            runCatching {
                navController.getBackStackEntry(PRE_POSE_LANDMARKER_ROUTE)
            }.getOrNull()
        }
        if (prePoseEntry != null) {
            val sharedViewModel: PrePoseLandmarkerViewModel = hiltViewModel(prePoseEntry)
            PrePoseLandmarkerSmoothFilterCompareScreen(
                viewModel = sharedViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        } else {
            PrePoseLandmarkerSmoothFilterCompareScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }

    composable(ScreenRoutes.Debug.UPLOAD_PHYSICS_OVERLAY) { backStackEntry: NavBackStackEntry ->
        val mainGraphEntry = remember(backStackEntry) {
            runCatching {
                navController.getBackStackEntry(ScreenRoutes.MainGraph.route)
            }.getOrNull()
        }
        val uploadViewModel = mainGraphEntry?.let { parentEntry ->
            hiltViewModel<UploadViewModel>(parentEntry)
        }

        UploadPhysicsOverlayDebugScreen(
            uploadViewModel = uploadViewModel,
            onNavigateBack = {
                navController.popBackStack()
            }
        )
    }
}

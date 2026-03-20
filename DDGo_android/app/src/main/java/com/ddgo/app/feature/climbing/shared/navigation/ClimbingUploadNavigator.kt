package com.ddgo.app.feature.climbing.shared.navigation

import androidx.navigation.NavController
import com.ddgo.app.navigation.ScreenRoutes

fun NavController.navigateToClimbingUpload(
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

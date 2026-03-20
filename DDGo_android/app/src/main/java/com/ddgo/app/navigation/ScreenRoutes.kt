package com.ddgo.app.navigation

sealed class ScreenRoutes(val route: String) {
    object Splash : ScreenRoutes("splash")

    object Auth : ScreenRoutes("auth_graph") {
        const val WELCOME = "welcome"
        const val LOGIN_EMAIL = "login_email"
        const val LOGIN_PASSWORD = "login_password"
        const val REGISTER_EMAIL = "register_email"
        const val REGISTER_PASSWORD = "register_password"
    }

    object Main : ScreenRoutes("main")

    object MainGraph : ScreenRoutes("main_graph")

    object Climbing : ScreenRoutes("climbing_graph") {
        object Upload : ScreenRoutes("upload_graph") {
            const val ATTEMPT_UPLOAD = "attempt_upload"
            const val ARG_RECORDED_VIDEO_URI = "recordedVideoUri"
            const val ARG_REALTIME_SESSION_ID = "realtimeSessionId"
            const val ATTEMPT_UPLOAD_WITH_ARGS =
                "$ATTEMPT_UPLOAD?$ARG_RECORDED_VIDEO_URI={$ARG_RECORDED_VIDEO_URI}&$ARG_REALTIME_SESSION_ID={$ARG_REALTIME_SESSION_ID}"

            const val CHALLENGE_CREATE = "challenge_create"
            const val CHALLENGE_COLOR = "challenge_color"
            const val DEV_IMAGE_PICKER = "dev_image_picker"
            const val CHALLENGE_HOLD = "challenge_hold"
            const val ADDITIONAL_UPLOAD = "additional_upload"
            const val HOLD_SELECT = "hold_select"
            const val ANALYSIS_LOADING = "analysis_loading"
            const val ATTEMPT_RESULT = "attempt_result"
            const val HOLD_CONTACT_DEBUG = "hold_contact_debug"
            const val FINAL_ANALYSIS = "final_analysis"
        }

        object Record : ScreenRoutes("record_graph") {
            const val RECORD_MAIN = "record_main"
        }
    }
}

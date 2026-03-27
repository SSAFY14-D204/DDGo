package com.ddgo.app.navigation

import android.net.Uri

sealed class ScreenRoutes(val route: String) {
    object Splash : ScreenRoutes("splash")

    object Onboarding : ScreenRoutes("onboarding") {
        const val ARG_NEXT_ROUTE = "nextRoute"
        const val ARG_SHOW_ENTRY_GUIDE = "showEntryGuide"
        const val ROUTE_WITH_ARG =
            "onboarding?$ARG_NEXT_ROUTE={$ARG_NEXT_ROUTE}&$ARG_SHOW_ENTRY_GUIDE={$ARG_SHOW_ENTRY_GUIDE}"

        fun createRoute(nextRoute: String, showEntryGuide: Boolean): String {
            return "$route?$ARG_NEXT_ROUTE=${Uri.encode(nextRoute)}&$ARG_SHOW_ENTRY_GUIDE=$showEntryGuide"
        }
    }

    object Auth : ScreenRoutes("auth_graph") {
        const val WELCOME = "welcome"
        const val LOGIN_EMAIL = "login_email"
        const val LOGIN_PASSWORD = "login_password"
        const val PASSWORD_RESET = "password_reset"
        const val ARG_PASSWORD_RESET_LINK = "passwordResetLink"
        const val PASSWORD_RESET_WITH_ARG =
            "$PASSWORD_RESET?$ARG_PASSWORD_RESET_LINK={$ARG_PASSWORD_RESET_LINK}"
        const val REGISTER_EMAIL = "register_email"
        const val REGISTER_PASSWORD = "register_password"
    }

    object Main : ScreenRoutes("main")

    object MainGraph : ScreenRoutes("main_graph")

    object CalendarDetail : ScreenRoutes("calendar_detail") {
        const val ARG_SELECTED_DATE = "selectedDate"
        val ROUTE_WITH_ARG = "$route?$ARG_SELECTED_DATE={$ARG_SELECTED_DATE}"
    }

    object Climbing : ScreenRoutes("climbing_graph") {
        object Upload : ScreenRoutes("upload_graph") {
            const val REALTIME_SETUP = "realtime_setup"
            const val REALTIME_HOLD = "realtime_hold"
            const val REALTIME_HOLD_SELECT = "realtime_hold_select"
            const val REALTIME_RECORDED_ATTEMPT = "realtime_recorded_attempt"
            const val REALTIME_ANALYSIS_LOADING = "realtime_analysis_loading"
            const val REALTIME_ATTEMPT_RESULT = "realtime_attempt_result"

            const val ATTEMPT_UPLOAD = "attempt_upload"
            const val ARG_RECORDED_VIDEO_URI = "recordedVideoUri"
            const val ARG_AUTO_OPEN_PICKER = "autoOpenPicker"
            const val REALTIME_RECORDED_ATTEMPT_WITH_ARGS =
                "$REALTIME_RECORDED_ATTEMPT?$ARG_RECORDED_VIDEO_URI={$ARG_RECORDED_VIDEO_URI}"
            const val ATTEMPT_UPLOAD_WITH_ARGS =
                "$ATTEMPT_UPLOAD?$ARG_RECORDED_VIDEO_URI={$ARG_RECORDED_VIDEO_URI}&$ARG_AUTO_OPEN_PICKER={$ARG_AUTO_OPEN_PICKER}"

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
            const val CHALLENGE_FINAL_ANALYSIS = "challenge_final_analysis"
        }

        object Record : ScreenRoutes("record_graph") {
            const val RECORD_MAIN = "record_main"
        }
    }
}

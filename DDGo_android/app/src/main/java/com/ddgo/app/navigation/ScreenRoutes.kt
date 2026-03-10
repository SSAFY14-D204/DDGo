package com.ddgo.app.navigation

/**
 * 앱 내 모든 화면의 라우트 경로를 정의합니다.
 *
 * sealed class 패턴을 사용해 타입 안전성을 보장합니다.
 * 화면을 추가할 때는 여기에 새 object를 추가하세요.
 */
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

    // 메인 하단 탭 라우트
    object Calendar : ScreenRoutes("calendar")
    object Community : ScreenRoutes("community")
    object Climbing : ScreenRoutes("climbing")
    object Analysis : ScreenRoutes("analysis")
    object Profile : ScreenRoutes("profile")

    // (레거시 - 추후 정리 예정)
    object Upload : ScreenRoutes("upload")
    object Report : ScreenRoutes("report")
}

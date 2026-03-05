package com.ddgo.app.navigation

/**
 * 앱 내 모든 화면의 라우트 경로를 정의합니다.
 *
 * sealed class 패턴을 사용해 타입 안전성을 보장합니다.
 * 화면을 추가할 때는 여기에 새 object를 추가하세요.
 */
sealed class ScreenRoutes(val route: String) {
    object Auth   : ScreenRoutes("auth")
    object Upload : ScreenRoutes("upload")
    object Report : ScreenRoutes("report")

    // 파라미터가 있는 화면 예시:
    // object ReportDetail : ScreenRoutes("report/{climbId}") {
    //     fun createRoute(climbId: String) = "report/$climbId"
    // }
}

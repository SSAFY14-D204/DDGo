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

    /** 메인 화면(탭 UI)의 route */
    object Main : ScreenRoutes("main")

    /**
     * 메인 플로우 전체를 감싸는 서브 그래프의 route.
     * Splash/Auth 이후 진입하며, Main 화면과 그 하위 기능 그래프들을 포함합니다.
     */
    object MainGraph : ScreenRoutes("main_graph")

    /**
     * 클라이밍 기능 전체 라우트.
     * Upload(영상 업로드)와 Record(실시간 기록) 두 개의 독립적인 서브 그래프로 구성됩니다.
     */
    object Climbing : ScreenRoutes("climbing_graph") {

        /** 영상 업로드 플로우 (갤러리 선택 → 챌린지 생성 → 홀드 선택 → 결과 확인) */
        object Upload : ScreenRoutes("upload_graph") {
            const val ATTEMPT_UPLOAD = "attempt_upload"
            const val CHALLENGE_CREATE = "challenge_create"
            const val CHALLENGE_HOLD = "challenge_hold"
            const val ATTEMPT_RESULT = "attempt_result"
        }

        /** 실시간 기록 플로우 (카메라 권한 → 기록 → 기록 결과) */
        object Record : ScreenRoutes("record_graph") {
            const val RECORD_MAIN = "record_main"
        }
    }
    }


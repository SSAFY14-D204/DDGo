package com.ddgo.app.core.datastore

/**
 * 인증 세션과 관련된 전역 이벤트입니다.
 *
 * 개별 화면이 아닌 앱 루트 내비게이션에서 감지해
 * 인증 화면으로 복귀시키는 용도로 사용합니다.
 */
sealed class AuthSessionEvent {
    /** 토큰 재발급에 실패해 현재 세션을 더 이상 유지할 수 없는 상태입니다. */
    data object SessionExpired : AuthSessionEvent()
}

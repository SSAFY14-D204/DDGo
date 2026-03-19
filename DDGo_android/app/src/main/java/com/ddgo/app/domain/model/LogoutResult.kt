package com.ddgo.app.domain.model

/**
 * 로그아웃 처리 결과를 표현하는 모델입니다.
 *
 * 역할:
 * - 서버 로그아웃 API까지 정상 확인됐는지,
 *   아니면 로컬 토큰 정리만 완료된 상태인지를 상위 계층에 전달합니다.
 * - UX는 동일하게 "로그인 화면으로 복귀"할 수 있어도,
 *   레이어 간 계약은 더 정직하게 유지하기 위해 사용합니다.
 */
sealed interface LogoutResult {

    /** 서버 로그아웃 API와 로컬 토큰 정리가 모두 정상 완료된 상태입니다. */
    data object ServerConfirmed : LogoutResult

    /**
     * 서버 확인에는 실패했지만, 이 기기의 토큰은 정리된 상태입니다.
     *
     * [reason]:
     * - 서버 응답 메시지 또는 예외 메시지를 필요 시 상위 계층에서 참고할 수 있도록 보관합니다.
     */
    data class LocalOnly(
        val reason: String? = null
    ) : LogoutResult
}

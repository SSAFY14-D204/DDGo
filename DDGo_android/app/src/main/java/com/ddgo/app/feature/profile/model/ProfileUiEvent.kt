package com.ddgo.app.feature.profile.model

/**
 * 프로필 화면에서 한 번만 처리해야 하는 단발성 이벤트입니다.
 *
 * 상태(State)와 분리해서, 화면 이동이나 스낵바 노출처럼 재구성 시 반복되면 안 되는
 * 동작만 별도로 전달합니다.
 */
sealed interface ProfileUiEvent {

    /** 로그아웃 완료 후 인증 화면으로 이동합니다. */
    data object NavigateToAuth : ProfileUiEvent

    /** 사용자에게 짧은 피드백 메시지를 보여줍니다. */
    data class ShowMessage(
        val message: String
    ) : ProfileUiEvent
}

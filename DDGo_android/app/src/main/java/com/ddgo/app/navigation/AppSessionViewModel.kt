package com.ddgo.app.navigation

import androidx.lifecycle.ViewModel
import com.ddgo.app.core.datastore.AuthSessionEvent
import com.ddgo.app.core.datastore.TokenDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharedFlow

/**
 * 앱 루트 내비게이션에서 인증 세션 변화를 감지하기 위한 ViewModel입니다.
 *
 * 역할:
 * - TokenDataStore가 발행한 세션 만료 이벤트를 NavGraph로 전달합니다.
 * - feature 화면들이 세션 만료 처리 책임을 직접 가지지 않도록 분리합니다.
 */
@HiltViewModel
class AppSessionViewModel @Inject constructor(
    tokenDataStore: TokenDataStore
) : ViewModel() {

    /** 토큰 만료로 인해 강제 로그아웃이 필요한 순간에 발행되는 이벤트입니다. */
    val authSessionEvent: SharedFlow<AuthSessionEvent> = tokenDataStore.authSessionEvent
}

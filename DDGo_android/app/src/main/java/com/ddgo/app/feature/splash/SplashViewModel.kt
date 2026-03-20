package com.ddgo.app.feature.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ddgo.app.core.datastore.TokenDataStore
import com.ddgo.app.core.network.JwtTokenParser
import com.ddgo.app.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val tokenDataStore: TokenDataStore,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _navigationEvent = MutableSharedFlow<SplashNavigationEvent>()
    val navigationEvent: SharedFlow<SplashNavigationEvent> = _navigationEvent.asSharedFlow()

    init {
        checkLoginStatus()
    }

    private fun checkLoginStatus() {
        viewModelScope.launch {
            delay(1500)

            val accessToken = tokenDataStore.accessToken.first()
            if (!accessToken.isNullOrEmpty() && !JwtTokenParser.isExpired(accessToken)) {
                // ✅ 액세스 토큰이 있으면 바로 메인으로
                _navigationEvent.emit(SplashNavigationEvent.NavigateToMain)
                return@launch
            }

            // 액세스 토큰이 없으면 refresh 시도 (최대 2회)
            val refreshToken = tokenDataStore.refreshToken.first()
            if (!refreshToken.isNullOrEmpty()) {
                val refreshSuccess = tryRefreshToken(refreshToken, maxRetries = 2)
                if (refreshSuccess) {
                    _navigationEvent.emit(SplashNavigationEvent.NavigateToMain)
                    return@launch
                }
            }

            // refresh도 실패 → auth 화면으로
            tokenDataStore.clearTokens()
            _navigationEvent.emit(SplashNavigationEvent.NavigateToAuth)
        }
    }

    /**
     * refreshToken으로 토큰 재발급 시도. 최대 [maxRetries]회 재시도.
     * @return 성공 시 true, 모두 실패 시 false
     */
    private suspend fun tryRefreshToken(refreshToken: String, maxRetries: Int): Boolean {
        repeat(maxRetries) { attempt ->
            val result = authRepository.refreshToken(refreshToken)
            if (result.isSuccess) return true
            // 재시도 전 약간 대기 (두 번째 시도)
            if (attempt < maxRetries - 1) delay(500)
        }
        return false
    }
}

sealed class SplashNavigationEvent {
    object NavigateToAuth : SplashNavigationEvent()
    object NavigateToMain : SplashNavigationEvent()
}

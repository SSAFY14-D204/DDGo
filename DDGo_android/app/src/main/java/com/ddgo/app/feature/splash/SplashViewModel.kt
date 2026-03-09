package com.ddgo.app.feature.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ddgo.app.core.datastore.TokenDataStore
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
    private val tokenDataStore: TokenDataStore
) : ViewModel() {

    private val _navigationEvent = MutableSharedFlow<SplashNavigationEvent>()
    val navigationEvent: SharedFlow<SplashNavigationEvent> = _navigationEvent.asSharedFlow()

    init {
        checkLoginStatus()
    }

    private fun checkLoginStatus() {
        viewModelScope.launch {
            // 스플래시 화면을 최소 1.5초 동안 보여줌
            delay(1500)
            
            val token = tokenDataStore.accessToken.first()
            if (token.isNullOrEmpty()) {
                _navigationEvent.emit(SplashNavigationEvent.NavigateToAuth)
            } else {
                _navigationEvent.emit(SplashNavigationEvent.NavigateToMain)
            }
        }
    }
}

sealed class SplashNavigationEvent {
    object NavigateToAuth : SplashNavigationEvent()
    object NavigateToMain : SplashNavigationEvent()
}

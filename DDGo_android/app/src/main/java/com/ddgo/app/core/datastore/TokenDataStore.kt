package com.ddgo.app.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

// Context 확장 프로퍼티: DataStore 싱글톤 생성
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ddgo_prefs")

/**
 * 사용자 토큰을 DataStore에 안전하게 저장/불러오는 클래스.
 *
 * SharedPreferences와 달리 DataStore는 코루틴 기반이며 타입 안전을 보장합니다.
 */
class TokenDataStore @Inject constructor(
    private val context: Context
) {
    companion object {
        private val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
    }

    /**
     * 세션 만료와 같은 전역 인증 이벤트를 전달합니다.
     *
     * refresh 토큰 재발급까지 실패하면 이 이벤트가 발행되고,
     * 루트 내비게이션이 이를 감지해 자동 로그아웃을 처리합니다.
     */
    private val _authSessionEvent = MutableSharedFlow<AuthSessionEvent>(extraBufferCapacity = 1)
    val authSessionEvent: SharedFlow<AuthSessionEvent> = _authSessionEvent.asSharedFlow()

    /** 저장된 accessToken을 Flow로 읽기 */
    val accessToken: Flow<String?> = context.dataStore.data
        .map { prefs -> prefs[KEY_ACCESS_TOKEN] }

    /** 저장된 refreshToken을 Flow로 읽기 */
    val refreshToken: Flow<String?> = context.dataStore.data
        .map { prefs -> prefs[KEY_REFRESH_TOKEN] }

    /** 토큰 저장 */
    suspend fun saveTokens(accessToken: String, refreshToken: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACCESS_TOKEN] = accessToken
            prefs[KEY_REFRESH_TOKEN] = refreshToken
        }
    }

    /** 로그아웃 시 토큰 삭제 */
    suspend fun clearTokens() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_ACCESS_TOKEN)
            prefs.remove(KEY_REFRESH_TOKEN)
        }
    }

    /**
     * 토큰을 비운 뒤 세션 만료 이벤트를 함께 발행합니다.
     *
     * 수동 로그아웃과 달리, 앱이 더 이상 유효한 세션을 유지할 수 없을 때만 사용합니다.
     */
    suspend fun clearTokensBySessionExpiry() {
        clearTokens()
        _authSessionEvent.emit(AuthSessionEvent.SessionExpired)
    }
}

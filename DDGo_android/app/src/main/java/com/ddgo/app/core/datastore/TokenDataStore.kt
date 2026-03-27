package com.ddgo.app.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map

/**
 * 앱 인증 토큰을 DataStore에 저장하고 읽어오는 저장소입니다.
 *
 * 세션 만료처럼 앱 전역에서 반응해야 하는 인증 이벤트도 함께 전달합니다.
 */
class TokenDataStore @Inject constructor(
    private val context: Context,
    private val uploadRecoveryDataStore: UploadRecoveryDataStore
) {
    private companion object {
        val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
        val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
    }

    private val _authSessionEvent = MutableSharedFlow<AuthSessionEvent>(extraBufferCapacity = 1)
    val authSessionEvent: SharedFlow<AuthSessionEvent> = _authSessionEvent.asSharedFlow()

    val accessToken: Flow<String?> = context.ddgoPreferencesDataStore.data
        .map { preferences -> preferences[KEY_ACCESS_TOKEN] }

    val refreshToken: Flow<String?> = context.ddgoPreferencesDataStore.data
        .map { preferences -> preferences[KEY_REFRESH_TOKEN] }

    suspend fun saveTokens(accessToken: String, refreshToken: String) {
        context.ddgoPreferencesDataStore.edit { preferences ->
            preferences[KEY_ACCESS_TOKEN] = accessToken
            preferences[KEY_REFRESH_TOKEN] = refreshToken
        }
    }

    suspend fun clearTokens() {
        context.ddgoPreferencesDataStore.edit { preferences ->
            preferences.remove(KEY_ACCESS_TOKEN)
            preferences.remove(KEY_REFRESH_TOKEN)
        }
        uploadRecoveryDataStore.clearAll()
    }

    suspend fun clearTokensBySessionExpiry() {
        clearTokens()
        _authSessionEvent.emit(AuthSessionEvent.SessionExpired)
    }
}

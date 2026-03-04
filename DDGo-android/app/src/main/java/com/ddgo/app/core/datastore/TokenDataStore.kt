package com.ddgo.app.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
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
}

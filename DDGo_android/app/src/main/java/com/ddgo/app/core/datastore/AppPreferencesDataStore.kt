package com.ddgo.app.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal val Context.ddgoPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "ddgo_prefs"
)

class OnboardingPreferenceDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }

    val hasCompletedOnboarding: Flow<Boolean> = context.ddgoPreferencesDataStore.data
        .map { preferences -> preferences[KEY_ONBOARDING_COMPLETED] ?: false }

    suspend fun setOnboardingCompleted(completed: Boolean = true) {
        context.ddgoPreferencesDataStore.edit { preferences ->
            preferences[KEY_ONBOARDING_COMPLETED] = completed
        }
    }
}

data class PreferredGymPreference(
    val gymId: Int,
    val gymName: String
)

class PreferredGymPreferenceDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        val KEY_PREFERRED_GYM_ID = intPreferencesKey("preferred_gym_id")
        val KEY_PREFERRED_GYM_NAME = stringPreferencesKey("preferred_gym_name")
    }

    val preferredGym: Flow<PreferredGymPreference?> = context.ddgoPreferencesDataStore.data
        .map { preferences ->
            val gymId = preferences[KEY_PREFERRED_GYM_ID]
            val gymName = preferences[KEY_PREFERRED_GYM_NAME]

            if (gymId == null || gymName.isNullOrBlank()) {
                null
            } else {
                PreferredGymPreference(
                    gymId = gymId,
                    gymName = gymName
                )
            }
        }

    suspend fun setPreferredGym(gymId: Int, gymName: String) {
        context.ddgoPreferencesDataStore.edit { preferences ->
            preferences[KEY_PREFERRED_GYM_ID] = gymId
            preferences[KEY_PREFERRED_GYM_NAME] = gymName
        }
    }

    suspend fun clearPreferredGym() {
        context.ddgoPreferencesDataStore.edit { preferences ->
            preferences.remove(KEY_PREFERRED_GYM_ID)
            preferences.remove(KEY_PREFERRED_GYM_NAME)
        }
    }
}

package com.dansheng.notifyenh.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

class AppPreferences(private val context: Context) {
    companion object {
        val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        val PERSISTENT_MODE_KEY = booleanPreferencesKey("persistent_mode")
        val RETENTION_DAYS_KEY = intPreferencesKey("retention_days")
        val LAST_SEEN_VERSION_KEY = intPreferencesKey("last_seen_version")
    }

    val themeModeFlow: Flow<ThemeMode> = context.dataStore.data
        .map { preferences ->
            val modeName = preferences[THEME_MODE_KEY] ?: ThemeMode.SYSTEM.name
            ThemeMode.valueOf(modeName)
        }

    val persistentModeFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PERSISTENT_MODE_KEY] ?: false
        }

    val retentionDaysFlow: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[RETENTION_DAYS_KEY] ?: 7
        }

    val lastSeenVersionFlow: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[LAST_SEEN_VERSION_KEY] ?: 0
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode.name
        }
    }

    suspend fun setPersistentMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PERSISTENT_MODE_KEY] = enabled
        }
    }

    suspend fun setRetentionDays(days: Int) {
        context.dataStore.edit { preferences ->
            preferences[RETENTION_DAYS_KEY] = days
        }
    }

    suspend fun setLastSeenVersion(version: Int) {
        context.dataStore.edit { preferences ->
            preferences[LAST_SEEN_VERSION_KEY] = version
        }
    }
}

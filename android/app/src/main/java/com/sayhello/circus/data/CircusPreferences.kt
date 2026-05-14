package com.sayhello.circus.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore("circus_prefs")

class CircusPreferences(private val context: Context) {

    private object Keys {
        val FirstLaunchAt = longPreferencesKey("first_launch_at")
        val ActiveCharacter = stringPreferencesKey("active_character")
        val ApiKey = stringPreferencesKey("anthropic_api_key")
        val ModelOverride = stringPreferencesKey("model_override")
    }

    suspend fun firstLaunchAt(): Long? =
        context.dataStore.data.first()[Keys.FirstLaunchAt]

    suspend fun setFirstLaunchAt(value: Long) {
        context.dataStore.edit { it[Keys.FirstLaunchAt] = value }
    }

    suspend fun resetProgression() {
        context.dataStore.edit { it.remove(Keys.FirstLaunchAt) }
    }

    suspend fun activeCharacterId(): String =
        context.dataStore.data.first()[Keys.ActiveCharacter] ?: "caine"

    suspend fun setActiveCharacter(id: String) {
        context.dataStore.edit { it[Keys.ActiveCharacter] = id }
    }

    suspend fun apiKey(): String? = context.dataStore.data.first()[Keys.ApiKey]

    suspend fun setApiKey(key: String) {
        context.dataStore.edit { it[Keys.ApiKey] = key }
    }

    suspend fun model(): String =
        context.dataStore.data.first()[Keys.ModelOverride] ?: "claude-opus-4-7"

    suspend fun setModel(model: String) {
        context.dataStore.edit { it[Keys.ModelOverride] = model }
    }

    suspend fun snapshot(): Map<Preferences.Key<*>, Any> =
        context.dataStore.data.first().asMap()
}

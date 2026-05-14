package com.sayhello.circus.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.consentStore by preferencesDataStore("roleplay_consent")

/**
 * Audit log of every in-character "permission" the user has granted. The
 * grants are PURELY narrative — they unlock new roleplay scenes inside the app
 * but do not authorise any real system action. The ledger exists so the user
 * can review what story-beats they've opened, and to make it obvious in code
 * review that these grants never escalate to host capabilities.
 */
class RoleplayConsentLedger(private val context: Context) {

    @Serializable
    data class Grant(
        val grantedAt: Long,
        val characterId: String,
        val scene: String,
        val sceneDescription: String,
    )

    private object Keys {
        val Log = stringPreferencesKey("grants_json")
    }

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun grants(): List<Grant> {
        val raw = context.consentStore.data.first()[Keys.Log] ?: return emptyList()
        return runCatching { json.decodeFromString<List<Grant>>(raw) }.getOrDefault(emptyList())
    }

    suspend fun record(grant: Grant) {
        val current = grants() + grant
        context.consentStore.edit {
            it[Keys.Log] = json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(Grant.serializer()),
                current,
            )
        }
    }
}

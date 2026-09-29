package com.sianalimalik.wearablesync

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "wearable_sync_settings")

private object Keys {
    val BASE_URL = stringPreferencesKey("base_url")
    val API_KEY = stringPreferencesKey("api_key")
    val AUTO_SYNC = booleanPreferencesKey("auto_sync")
}

// Settings are stored in plain DataStore, not EncryptedSharedPreferences: this is a
// single-owner personal-device app syncing your own wellness data, so the extra
// dependency/version risk of encrypted storage isn't worth it here.
class SyncSettings(private val context: Context) {
    val baseUrlFlow = context.dataStore.data.map { it[Keys.BASE_URL] ?: DEFAULT_BASE_URL }
    val apiKeyFlow = context.dataStore.data.map { it[Keys.API_KEY] ?: "" }
    val autoSyncFlow = context.dataStore.data.map { it[Keys.AUTO_SYNC] ?: false }

    suspend fun save(baseUrl: String, apiKey: String) {
        context.dataStore.edit {
            it[Keys.BASE_URL] = baseUrl.ifBlank { DEFAULT_BASE_URL }
            it[Keys.API_KEY] = apiKey
        }
    }

    suspend fun setAutoSync(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_SYNC] = enabled }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://sian-os.sianalimalik2418.workers.dev"
    }
}

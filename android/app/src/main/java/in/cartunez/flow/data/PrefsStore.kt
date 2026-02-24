package `in`.cartunez.flow.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore by preferencesDataStore("flow_prefs")

class PrefsStore(private val ctx: Context) {

    companion object {
        private val KEY_TOKEN     = stringPreferencesKey("token")
        private val KEY_USER_ID   = stringPreferencesKey("user_id")
        private val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        private val KEY_LAST_SYNC = stringPreferencesKey("last_sync")
    }

    suspend fun getToken(): String? =
        ctx.dataStore.data.map { it[KEY_TOKEN] }.first()

    suspend fun saveAuth(token: String, userId: String) {
        ctx.dataStore.edit {
            it[KEY_TOKEN]   = token
            it[KEY_USER_ID] = userId
        }
    }

    suspend fun getDeviceId(): String {
        val existing = ctx.dataStore.data.map { it[KEY_DEVICE_ID] }.first()
        if (existing != null) return existing
        val newId = UUID.randomUUID().toString()
        ctx.dataStore.edit { it[KEY_DEVICE_ID] = newId }
        return newId
    }

    suspend fun getLastSync(): String? =
        ctx.dataStore.data.map { it[KEY_LAST_SYNC] }.first()

    suspend fun saveLastSync(iso: String) {
        ctx.dataStore.edit { it[KEY_LAST_SYNC] = iso }
    }

    suspend fun clear() {
        ctx.dataStore.edit { it.clear() }
    }
}

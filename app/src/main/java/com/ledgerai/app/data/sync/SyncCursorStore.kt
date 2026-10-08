package com.ledgerai.app.data.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.syncDataStore: DataStore<Preferences> by preferencesDataStore("sync_cursor")

@Singleton
class SyncCursorStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val store = context.syncDataStore

    suspend fun lastSyncMs(): Long =
        store.data.map { it[KEY_LAST_SYNC_MS] ?: 0L }.first()

    suspend fun saveLastSyncMs(ms: Long) {
        store.edit { it[KEY_LAST_SYNC_MS] = ms }
    }

    companion object {
        private val KEY_LAST_SYNC_MS = longPreferencesKey("last_sync_ms")
    }
}

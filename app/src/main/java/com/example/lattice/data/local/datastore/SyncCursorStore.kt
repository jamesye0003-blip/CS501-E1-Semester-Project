package com.example.lattice.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

val Context.syncDataStore by preferencesDataStore(name = "sync_datastore")

class SyncCursorStore(private val context: Context) {

    private fun keyFor(localUserId: String) = longPreferencesKey("remoteSyncCursor_$localUserId")

    suspend fun getCursor(localUserId: String): Long {
        val prefs = context.syncDataStore.data.first()
        return prefs[keyFor(localUserId)] ?: 0L
    }

    suspend fun setCursor(localUserId: String, cursor: Long) {
        context.syncDataStore.edit { prefs ->
            prefs[keyFor(localUserId)] = cursor
        }
    }
}
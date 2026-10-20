package com.yourapp.iptv.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.yourapp.iptv.data.cache.ChannelSnapshotStore
import kotlinx.coroutines.flow.first

private val Context.channelSnapshotStore by preferencesDataStore(name = "channel_snapshot")

class ChannelSnapshotDataStore(private val store: DataStore<Preferences>) : ChannelSnapshotStore {
    constructor(context: Context) : this(context.applicationContext.channelSnapshotStore)
    private val key = stringPreferencesKey("source_fingerprint_v1")

    override suspend fun read(): String? = store.data.first()[key]

    override suspend fun write(fingerprint: String?) {
        store.edit { values ->
            if (fingerprint == null) values.remove(key) else values[key] = fingerprint
        }
    }
}
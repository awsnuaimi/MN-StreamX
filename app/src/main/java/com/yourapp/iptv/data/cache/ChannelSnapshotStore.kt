package com.yourapp.iptv.data.cache

/** The Room channel table is usable as a snapshot only when this stamp matches. */
interface ChannelSnapshotStore {
    suspend fun read(): String?
    suspend fun write(fingerprint: String?)

    companion object {
        val Disabled = object : ChannelSnapshotStore {
            override suspend fun read(): String? = null
            override suspend fun write(fingerprint: String?) = Unit
        }
    }
}
package com.yourapp.iptv.player.recovery

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn

/** A callback exists only while its player session is STARTED. No connectivity probes. */
internal class AndroidPlaybackNetwork(context: Context) {
    private val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    fun observe(): Flow<PlaybackConnection> = callbackFlow {
        val service = manager
        if (service == null) { trySend(PlaybackConnection.UNKNOWN); close(); return@callbackFlow }
        var current: Network? = null
        var isBlocked = false
        fun emitState() {
            trySend(if (current != null && !isBlocked) PlaybackConnection.ONLINE else PlaybackConnection.OFFLINE)
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                current = network; isBlocked = false; emitState()
            }
            override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
                if (network != current) return
                isBlocked = blocked; emitState()
            }
            override fun onLost(network: Network) {
                if (network != current) return
                current = null; isBlocked = false; emitState()
            }
        }
        try {
            service.registerDefaultNetworkCallback(callback, Handler(Looper.getMainLooper()))
        } catch (_: RuntimeException) {
            trySend(PlaybackConnection.UNKNOWN); close(); return@callbackFlow
        }
        // Presence of a default route is enough: LAN/VPN streams need no public-Internet probe.
        // Seed outside callback methods. Subsequent callbacks use only their supplied data.
        try {
            current = service.activeNetwork
            emitState()
        } catch (_: RuntimeException) { trySend(PlaybackConnection.UNKNOWN) }
        awaitClose { try { service.unregisterNetworkCallback(callback) } catch (_: RuntimeException) { } }
    }.distinctUntilChanged().flowOn(Dispatchers.Main.immediate)
}
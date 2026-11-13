package mnstudio.apps.mnstreamx.player.recovery

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn

/** Tracks the system default network only while the live player is started. */
internal class AndroidPlaybackNetwork(context: Context) {
    private val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    fun observe(): Flow<PlaybackConnection> = callbackFlow {
        val service = manager
        if (service == null) {
            trySend(PlaybackConnection.Unknown)
            close()
            return@callbackFlow
        }

        var current: Network? = null
        var blocked: Boolean? = null
        var validated = false

        fun publish(network: Network) {
            when (blocked) {
                true -> trySend(PlaybackConnection.Offline)
                false -> trySend(PlaybackConnection.Online(network.networkHandle, validated))
                null -> Unit
            }
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                current = network
                blocked = null
                validated = false
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                if (network != current) return
                validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                publish(network)
            }

            override fun onBlockedStatusChanged(network: Network, isBlocked: Boolean) {
                if (network != current) return
                blocked = isBlocked
                publish(network)
            }

            override fun onLost(network: Network) {
                if (network != current) return
                current = null
                blocked = null
                validated = false
                trySend(PlaybackConnection.Offline)
            }
        }

        try {
            service.registerDefaultNetworkCallback(callback, Handler(Looper.getMainLooper()))
        } catch (_: RuntimeException) {
            trySend(PlaybackConnection.Unknown)
            close()
            return@callbackFlow
        }

        try {
            if (service.activeNetwork == null) trySend(PlaybackConnection.Offline)
        } catch (_: RuntimeException) {
            trySend(PlaybackConnection.Unknown)
        }

        awaitClose {
            try {
                service.unregisterNetworkCallback(callback)
            } catch (_: RuntimeException) {
                // The system may already have removed the callback.
            }
        }
    }.distinctUntilChanged().flowOn(Dispatchers.Main.immediate)
}
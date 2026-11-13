package mnstudio.apps.mnstreamx.player.recovery

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities
import org.robolectric.util.ReflectionHelpers

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AndroidPlaybackNetworkTest {
    @Test fun replacementDefaultNetworkKeepsItsOwnIdentityAndIgnoresStaleCallbacks() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val application = RuntimeEnvironment.getApplication()
            val manager = application.getSystemService(ConnectivityManager::class.java)
            val shadow = shadowOf(manager)
            val states = mutableListOf<PlaybackConnection>()
            val collection = backgroundScope.launch { AndroidPlaybackNetwork(application).observe().collect { states.add(it) } }
            runCurrent()
            val callback = shadow.networkCallbacks.single()

            val wifi = network(101)
            val ethernet = network(102)
            callback.onAvailable(wifi)
            callback.onCapabilitiesChanged(wifi, capabilities(validated = true))
            callback.onBlockedStatusChanged(wifi, false)
            runCurrent()
            assertEquals(listOf(PlaybackConnection.Online(wifi.networkHandle, true)), states)

            callback.onAvailable(ethernet)
            callback.onCapabilitiesChanged(ethernet, capabilities(validated = true))
            callback.onLost(wifi)
            callback.onBlockedStatusChanged(wifi, true)
            runCurrent()
            assertEquals(listOf(PlaybackConnection.Online(wifi.networkHandle, true)), states)

            callback.onBlockedStatusChanged(ethernet, false)
            runCurrent()
            assertEquals(PlaybackConnection.Online(ethernet.networkHandle, true), states.last())
            assertEquals(2, states.size)

            collection.cancel()
            runCurrent()
            assertTrue(shadow.networkCallbacks.isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun blockedNetworkIsOfflineAndLocalUnvalidatedNetworkCanStillBeUsed() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val application = RuntimeEnvironment.getApplication()
            val shadow = shadowOf(application.getSystemService(ConnectivityManager::class.java))
            shadow.setActiveNetworkInfo(null)
            val states = mutableListOf<PlaybackConnection>()
            val collection = backgroundScope.launch { AndroidPlaybackNetwork(application).observe().collect { states.add(it) } }
            runCurrent()
            assertEquals(listOf(PlaybackConnection.Offline), states)
            val callback = shadow.networkCallbacks.single()

            val local = network(103)
            callback.onAvailable(local)
            callback.onCapabilitiesChanged(local, capabilities(validated = false))
            callback.onBlockedStatusChanged(local, true)
            runCurrent()
            assertEquals(listOf(PlaybackConnection.Offline), states)

            callback.onBlockedStatusChanged(local, false)
            runCurrent()
            callback.onCapabilitiesChanged(local, capabilities(validated = true))
            runCurrent()
            callback.onLost(local)
            runCurrent()
            assertEquals(listOf(
                PlaybackConnection.Offline,
                PlaybackConnection.Online(local.networkHandle, false),
                PlaybackConnection.Online(local.networkHandle, true),
                PlaybackConnection.Offline
            ), states)

            collection.cancel()
            runCurrent()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun network(id: Int): Network =
        ReflectionHelpers.callConstructor(Network::class.java, ReflectionHelpers.ClassParameter.from(Int::class.java, id))

    private fun capabilities(validated: Boolean): NetworkCapabilities {
        val capabilities = ShadowNetworkCapabilities.newInstance()
        if (validated) shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        return capabilities
    }
}
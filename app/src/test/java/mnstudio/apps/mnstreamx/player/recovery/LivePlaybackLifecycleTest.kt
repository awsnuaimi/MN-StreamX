package mnstudio.apps.mnstreamx.player.recovery

import android.app.Application
import android.media.AudioManager
import android.os.Looper
import androidx.media3.common.util.UnstableApi
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mnstudio.apps.mnstreamx.domain.model.Channel
import mnstudio.apps.mnstreamx.domain.model.LiveRecoveryPhase
import mnstudio.apps.mnstreamx.domain.model.LiveRecoveryState
import mnstudio.apps.mnstreamx.player.controller.PlayerController
import mnstudio.apps.mnstreamx.ui.component.player.PlayerPlaybackSession
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, manifest = Config.NONE)
@androidx.annotation.OptIn(UnstableApi::class)
class LivePlaybackLifecycleTest {
    @Test fun leavingForHomeAndReturningKeepsTheServerCooldown() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "60"))
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val fixture = Fixture(this, server)
            try {
                fixture.session.start()
                runCurrent()
                assertEquals("/live.ts", server.takeRequest(5, TimeUnit.SECONDS)?.path)
                fixture.awaitError()
                assertEquals(LiveRecoveryState(LiveRecoveryPhase.SCHEDULED, 1), fixture.states.last())
                assertEquals(1, fixture.networkObservers)

                fixture.session.stop()
                runCurrent()
                assertNull(fixture.session.player)
                assertEquals(0, fixture.networkObservers)
                fixture.elapse(15_000)
                assertNull(server.takeRequest(0, TimeUnit.MILLISECONDS))

                fixture.session.start()
                runCurrent()
                assertNotNull(fixture.session.player)
                assertEquals(1, fixture.networkObservers)
                fixture.elapse(44_000)
                assertNull(server.takeRequest(0, TimeUnit.MILLISECONDS))
                fixture.elapse(2_000)
                assertEquals("/live.ts", server.takeRequest(5, TimeUnit.SECONDS)?.path)
                assertEquals(LiveRecoveryState(LiveRecoveryPhase.CONNECTING, 1), fixture.states.last())
            } finally {
                fixture.session.stop()
            }
        }
    }

    @Test fun anotherChannelStartsImmediatelyAfterReturning() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "60"))
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val fixture = Fixture(this, server)
            try {
                fixture.session.start()
                runCurrent()
                assertEquals("/live.ts", server.takeRequest(5, TimeUnit.SECONDS)?.path)
                fixture.awaitError()
                fixture.session.stop()
                fixture.session.selectChannel(Channel("other", "Other channel", streamUrl = server.url("/other.ts").toString()), 0)

                fixture.session.start()
                runCurrent()
                assertEquals("/other.ts", server.takeRequest(5, TimeUnit.SECONDS)?.path)
                assertEquals(LiveRecoveryState(), fixture.states.last())
            } finally {
                fixture.session.stop()
            }
        }
    }

    private class Fixture(private val scope: TestScope, server: MockWebServer) {
        private val application = RuntimeEnvironment.getApplication()
        private val looper = shadowOf(Looper.getMainLooper())
        private val errors = mutableListOf<String>()
        val states = mutableListOf<LiveRecoveryState>()
        var networkObservers = 0
            private set
        val session = PlayerPlaybackSession(
            controller = PlayerController(application),
            initialChannel = Channel("live", "Live channel", streamUrl = server.url("/live.ts").toString()),
            scope = scope,
            network = flow {
                networkObservers++
                try {
                    emit(PlaybackConnection.Online(1))
                    awaitCancellation()
                } finally {
                    networkObservers--
                }
            },
            onError = { _, error -> if (error != null) errors.add(error) },
            onBuffering = { _, _ -> },
            isSelected = { true },
            onRecovery = { _, state -> states.add(state) }
        )

        init {
            shadowOf(application.getSystemService(AudioManager::class.java))
                .setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        }

        fun awaitError() {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (errors.isEmpty()) {
                assertTrue("The player did not report the source error", System.nanoTime() < deadline)
                looper.idleFor(10, TimeUnit.MILLISECONDS)
                Thread.sleep(10)
            }
            assertEquals(listOf("ERROR_CODE_IO_BAD_HTTP_STATUS"), errors)
        }

        fun elapse(ms: Long) {
            looper.idleFor(ms, TimeUnit.MILLISECONDS)
            scope.advanceTimeBy(ms)
            scope.runCurrent()
        }
    }
}
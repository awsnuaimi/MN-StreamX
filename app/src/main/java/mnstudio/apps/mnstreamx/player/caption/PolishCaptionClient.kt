package mnstudio.apps.mnstreamx.player.caption

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import mnstudio.apps.mnstreamx.data.caption.CaptionDeviceInfo
import mnstudio.apps.mnstreamx.domain.caption.*
import mnstudio.apps.mnstreamx.domain.model.*

internal class PolishCaptionClient(private val context: Context) : PolishCaptionRunner {
    private val armed = AtomicBoolean(false)
    private val sequence = AtomicInteger()
    private val memory = CaptionDeviceInfo(context, reserveSystemHeadroom = true)
    override fun arm() = !armed.getAndSet(true)

    override suspend fun run(input: CaptionAudioInput, settings: CaptionSettings, publish: (CaptionPipelineState) -> Unit) {
        if (!armed.get()) {
            publish(CaptionPipelineState(CaptionProgress(CaptionStatus.POLISH_PAUSED)))
            return
        }
        val result = try {
            val check = memory.assess(CaptionResourcePolicy.POLISH_VOSK_COLD)
            if (check.allowed) Link(input, settings.target.tag, sequence.incrementAndGet(), publish).execute()
            else CaptionPipelineState(CaptionProgress(check.failure, availableMb = check.availableMb,
                requiredMb = check.requiredMb, stage = check.stage, systemThresholdMb = check.systemThresholdMb))
        } catch (error: CancellationException) { throw error }
        catch (_: OutOfMemoryError) { CaptionPipelineState(CaptionProgress(CaptionStatus.LOW_MEMORY)) }
        catch (_: Exception) { stopped() }
        armed.set(false)
        CaptionDiagnosticJournal.record("CAPTION_POLISH_END", 0, "status=" + result.progress.status.name)
        publish(result)
    }

    private fun stopped() = CaptionPipelineState(CaptionProgress(CaptionStatus.POLISH_STOPPED))

    private inner class Link(
        private val input: CaptionAudioInput,
        private val target: String,
        private val id: Int,
        private val publish: (CaptionPipelineState) -> Unit
    ) : ServiceConnection {
        private val connected = CompletableDeferred<Unit>()
        private val ready = CompletableDeferred<Unit>()
        private val done = CompletableDeferred<CaptionPipelineState>()
        private val acks = Channel<Unit>(Channel.CONFLATED)
        private val closed = AtomicBoolean(false)
        @Volatile private var remote: Messenger? = null
        private val main = Handler(Looper.getMainLooper())
        private val death = IBinder.DeathRecipient { main.post { disconnected() } }
        private val local = Messenger(Handler(Looper.getMainLooper()) { message ->
            if (!closed.get() && !done.isCompleted && message.arg1 == id) {
                when (message.what) {
                    PolishCaptionWire.READY -> ready.complete(Unit)
                    PolishCaptionWire.LIMIT -> input.limitWindow(message.data.getInt("millis").coerceIn(2000, 5000))
                    PolishCaptionWire.ACK -> acks.trySend(Unit)
                    PolishCaptionWire.STATE -> PolishCaptionWire.read(message.data)?.let(publish)
                    PolishCaptionWire.DONE -> done.complete(PolishCaptionWire.read(message.data) ?: stopped())
                }
            }
            true
        })

        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            if (closed.get() || done.isCompleted || remote != null) return
            val peer = Messenger(binder)
            remote = peer
            try {
                binder.linkToDeath(death, 0)
                PolishCaptionWire.send(peer, PolishCaptionWire.START, id, local, Bundle().apply { putString("target", target) })
                connected.complete(Unit)
            } catch (_: RemoteException) { disconnected() }
        }
        override fun onServiceDisconnected(name: ComponentName) = disconnected()
        override fun onBindingDied(name: ComponentName) = disconnected()
        override fun onNullBinding(name: ComponentName) = disconnected()
        private fun disconnected() {
            done.complete(stopped())
            connected.complete(Unit)
        }

        suspend fun execute(): CaptionPipelineState = coroutineScope {
            val intent = Intent(context, PolishCaptionService::class.java)
            val flags = Context.BIND_AUTO_CREATE or Context.BIND_WAIVE_PRIORITY
            // Service callbacks and unbinding run on Main so registration and disposal cannot race.
            var bound = false
            try {
                withContext(Dispatchers.Main.immediate) { bound = context.bindService(intent, this@Link, flags) }
                if (!bound || withTimeoutOrNull(12_000) { connected.await() } == null) return@coroutineScope stopped()
                val audio = launch {
                    ready.await()
                    val peer = remote ?: return@launch
                    input.open().use { feed ->
                        for (window in feed.windows) {
                            if (window.samples.size > PolishCaptionWire.MAX_SAMPLES) continue
                            val data = Bundle().apply {
                                putShortArray("pcm", CaptionPcm16.shorts(window.samples, 0, window.samples.size))
                                putLong("start", window.startUs)
                                putLong("position", input.positionUs)
                            }
                            try { PolishCaptionWire.send(peer, PolishCaptionWire.AUDIO, id, local, data) }
                            catch (_: RemoteException) { disconnected(); return@launch }
                            acks.receive()
                        }
                        done.complete(stopped())
                    }
                }
                try { done.await() } finally { audio.cancelAndJoin() }
            } finally {
                withContext(NonCancellable + Dispatchers.Main.immediate) {
                    closed.set(true)
                    remote?.let { peer ->
                        try { PolishCaptionWire.send(peer, PolishCaptionWire.STOP, id, local) }
                        catch (_: RemoteException) { /* Already disconnected. */ }
                        try { peer.binder.unlinkToDeath(death, 0) }
                        catch (_: NoSuchElementException) { /* Binder died before registration. */ }
                    }
                    if (bound) context.unbindService(this@Link)
                }
            }
        }
    }
}
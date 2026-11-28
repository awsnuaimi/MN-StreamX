package mnstudio.apps.mnstreamx.player.caption

import android.app.Service
import android.content.ComponentCallbacks2
import android.content.Intent
import android.os.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import mnstudio.apps.mnstreamx.di.CaptionWorker
import mnstudio.apps.mnstreamx.domain.caption.*
import mnstudio.apps.mnstreamx.domain.model.*
import mnstudio.apps.mnstreamx.domain.repository.*
import javax.inject.Inject

@AndroidEntryPoint
class PolishCaptionService : Service() {
    @Inject @CaptionWorker lateinit var worker: CoroutineDispatcher
    @Inject lateinit var models: CaptionModels
    @Inject lateinit var speech: SpeechEngineFactory
    @Inject lateinit var translators: CaptionTranslatorFactory
    @Inject lateinit var device: CaptionDevice
    @Inject lateinit var storage: CaptionStorageCoordinator
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val main = Handler(Looper.getMainLooper())
    private val endpoint = Messenger(Handler(Looper.getMainLooper(), ::receive))
    private val pipeline by lazy {
        CaptionPipeline(scope, worker, models, speech, translators, device, storageCoordinator = storage)
    }
    private var active: Session? = null

    private inner class Session(val id: Int, val client: Messenger) : CaptionAudioInput {
        val frames = Channel<AudioWindow>(Channel.CONFLATED)
        val death = IBinder.DeathRecipient { main.post { if (active === this) stopSession() } }
        var observer: Job? = null
        @Volatile override var positionUs: Long = Long.MIN_VALUE
        override fun limitWindow(millis: Int) = reply(PolishCaptionWire.LIMIT,
            Bundle().apply { putInt("millis", millis) })
        override fun open(): CaptionFeed {
            reply(PolishCaptionWire.READY)
            return CaptionFeed(frames) { frames.close() }
        }
        fun reply(what: Int, data: Bundle = Bundle()) {
            try { PolishCaptionWire.send(client, what, id, data = data) }
            catch (_: RemoteException) { main.post { if (active === this) stopSession() } }
        }
    }

    override fun onBind(intent: Intent): IBinder = endpoint.binder

    private fun receive(message: Message): Boolean {
        val sender = message.replyTo ?: return true
        if (message.what == PolishCaptionWire.START) {
            val target = CaptionLanguage.fromTag(message.data.getString("target")) ?: return true
            // Superseded clients must also receive a terminal result.
            stopSession(CaptionStatus.POLISH_STOPPED)
            val session = Session(message.arg1, sender)
            try { sender.binder.linkToDeath(session.death, 0) } catch (_: RemoteException) { return true }
            active = session
            val settings = CaptionSettings(mode = CaptionMode.GENERATED, source = "pl", target = target,
                translationBackend = TranslationBackend.OFFLINE_POLISH_VOSK)
            val running = pipeline.start(session, settings)
            session.observer = scope.launch {
                pipeline.uiState.collect { session.reply(PolishCaptionWire.STATE, PolishCaptionWire.state(it)) }
            }
            scope.launch {
                running.join()
                if (active === session) {
                    session.reply(PolishCaptionWire.DONE, PolishCaptionWire.state(pipeline.uiState.value))
                    stopSession()
                }
            }
            return true
        }
        val session = active ?: return true
        if (message.arg1 != session.id || sender.binder != session.client.binder) return true
        when (message.what) {
            PolishCaptionWire.STOP -> stopSession()
            PolishCaptionWire.AUDIO -> {
                val pcm = message.data.getShortArray("pcm")
                if (pcm != null && pcm.size <= PolishCaptionWire.MAX_SAMPLES) {
                    session.positionUs = message.data.getLong("position")
                    val samples = FloatArray(pcm.size) { pcm[it] / 32768f }
                    session.frames.trySend(AudioWindow(samples, message.data.getLong("start")))
                }
                session.reply(PolishCaptionWire.ACK)
            }
        }
        return true
    }

    private fun stopSession(reason: CaptionStatus? = null) {
        val previous = active ?: return
        active = null
        if (reason != null) previous.reply(PolishCaptionWire.DONE,
            PolishCaptionWire.state(CaptionPipelineState(CaptionProgress(reason))))
        previous.observer?.cancel()
        previous.frames.close()
        try { previous.client.binder.unlinkToDeath(previous.death, 0) }
        catch (_: NoSuchElementException) { /* Parent already died. */ }
        pipeline.stop()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level == ComponentCallbacks2.TRIM_MEMORY_COMPLETE) stopSession(CaptionStatus.MEMORY_PRESSURE)
    }
    override fun onLowMemory() {
        super.onLowMemory()
        stopSession(CaptionStatus.MEMORY_PRESSURE)
    }
    override fun onUnbind(intent: Intent): Boolean { stopSession(); return false }
    override fun onDestroy() { stopSession(); scope.cancel(); super.onDestroy() }
}
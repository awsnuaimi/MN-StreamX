package mnstudio.apps.mnstreamx.data.caption

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import mnstudio.apps.mnstreamx.domain.caption.*
import mnstudio.apps.mnstreamx.domain.model.CaptionStage
import mnstudio.apps.mnstreamx.domain.repository.CaptionDevice
import mnstudio.apps.mnstreamx.domain.repository.CaptionTranslator
import java.io.File
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
/** All create/free operations belong to the serial caption worker. */
internal class BergamotCaptionTranslator(private val files: BergamotModelFiles,
    private val device: CaptionDevice, private val keepWarm: () -> Boolean) : CaptionTranslator {
    private val native by lazy { NativeBergamot() }
    private val lock = Any()
    private val cancelled = AtomicBoolean(false)
    private val folders = mutableMapOf<String, File>()
    private var route = emptyList<Pair<String, String>>()
    private var activePack: String? = null
    private var handle = 0L
    override suspend fun prepare(target: String, progress: (Int) -> Unit) = prepareRoute("en", target, progress)
    override suspend fun download(target: String, progress: (Int) -> Unit) = prepare(target, progress)
    override suspend fun prepareRoute(source: String, target: String, progress: (Int) -> Unit) {
        route = CaptionLanguageRoute.steps(if (source == "auto") "en" else source, target)
        for ((index, step) in route.withIndex()) {
            currentCoroutineContext().ensureActive()
            val pack = BergamotCatalog.pack(step.first, step.second)
            if (pack.id !in folders) folders[pack.id] = files.prepare(pack) { progress((index * 100 + it) / route.size) }
        }
        progress(100)
    }
    override suspend fun translate(text: String): String {
        currentCoroutineContext().ensureActive(); check(!cancelled.get())
        var output = text.trim().take(2000)
        val started = System.nanoTime()
        try {
            for (step in route) {
                currentCoroutineContext().ensureActive(); check(!cancelled.get())
                val pack = BergamotCatalog.pack(step.first, step.second)
                if (activePack != pack.id) {
                    releaseWorkingMemory()
                    val check = device.assess(CaptionResourcePolicy.NATIVE_TEXT_COLD)
                    if (!check.allowed) throw CaptionMemoryException(check)
                    val folder = checkNotNull(folders[pack.id])
                    val pointer = native.create(folder.resolve(pack.file("model")).absolutePath,
                        folder.resolve(pack.file("vocab")).absolutePath, folder.resolve(pack.file("lex")).absolutePath)
                    check(pointer != 0L)
                    synchronized(lock) { handle = pointer; activePack = pack.id; if (cancelled.get()) native.cancel(pointer) }
                }
                val elapsed = (System.nanoTime() - started) / 1000000
                if (elapsed >= 8000) throw TimeoutException()
                output = native.translate(handle, output, (8000 - elapsed).toInt()).trim().take(2000)
                currentCoroutineContext().ensureActive(); check(!cancelled.get())
                if (!keepWarm() || step != route.last()) releaseWorkingMemory()
                if (output.isBlank()) break
            }
            return output
        } catch (_: TimeoutException) {
            currentCoroutineContext().ensureActive()
            throw CaptionDeadlineException(CaptionStage.TRANSLATION, (System.nanoTime() - started) / 1000000, 8000)
        }
    }
    override fun releaseWorkingMemory() {
        val pointer = synchronized(lock) { handle.also { handle = 0L; activePack = null } }
        if (pointer != 0L) native.release(pointer)
    }
    override fun cancel() { cancelled.set(true); synchronized(lock) { if (handle != 0L) native.cancel(handle) } }
    override fun close() { cancel(); releaseWorkingMemory(); folders.clear() }
}
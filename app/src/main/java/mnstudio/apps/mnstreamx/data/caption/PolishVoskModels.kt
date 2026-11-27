package mnstudio.apps.mnstreamx.data.caption

import java.io.File
import java.io.IOException
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import mnstudio.apps.mnstreamx.domain.caption.CaptionLanguageRoute

internal class PolishVoskModels(private val files: VerifiedModelFiles) {
    private val lock = Mutex()

    suspend fun prepare(progress: (Int) -> Unit): File = lock.withLock {
        val root = files.prepare(bundle) { progress(it * 70 / 100) }
        withContext(Dispatchers.IO) {
            val context = currentCoroutineContext()
            val check = Runnable { context.ensureActive() }
            val staging = root.resolve("extracting")
            val target = root.resolve(FOLDER)
            if (staging.exists() && !staging.deleteRecursively()) throw IOException("Cannot clear partial model")
            ZipFile(root.resolve(bundle.assets.single().path)).use { zip ->
                val layout = VerifiedVoskArchive(zip, FOLDER)
                if (!layout.isInstalled(target, check)) {
                    CaptionModelIntegrity.requireSpace(root, layout.getBytes())
                    if (!staging.mkdir()) throw IOException("Cannot create speech model directory")
                    try {
                        layout.extract(staging, check)
                        context.ensureActive()
                        if (target.exists() && !target.deleteRecursively()) throw IOException("Cannot replace model")
                        if (!staging.renameTo(target)) throw IOException("Cannot publish Polish model")
                    } finally {
                        if (staging.exists()) staging.deleteRecursively()
                    }
                }
            }
            progress(100)
            target
        }
    }

    companion object {
        const val FOLDER = "vosk-model-small-pl-0.22"
        val bundle = ModelBundle("vosk-pl-small-v1", "https://alphacephei.com/vosk/models",
            listOf(ModelAsset("$FOLDER.zip", 52979372L,
                "c4cd16498ea544f446f9e9a55cbd602b71cfe5a2b6f2b0834d81e1b6fce15f0d")))

        fun downloadBytes(target: String): Long = bundle.bytes +
            CaptionLanguageRoute.steps("pl", target).sumOf { (from, to) -> BergamotCatalog.pack(from, to).bundle.bytes }
    }
}
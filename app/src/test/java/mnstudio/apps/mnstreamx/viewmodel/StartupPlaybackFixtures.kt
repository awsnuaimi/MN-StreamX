package mnstudio.apps.mnstreamx.viewmodel

import mnstudio.apps.mnstreamx.domain.repository.StartupPlaybackRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import java.io.IOException

internal class StartupPreference(enabled: Boolean = true) : StartupPlaybackRepository {
    val value = MutableStateFlow(enabled)
    var reads = 0
    var writes = 0
    var failRead = false
    var failWrite = false
    var readGate: CompletableDeferred<Unit>? = null
    var writeGate: CompletableDeferred<Unit>? = null
    override fun observe() = flow {
        reads++
        readGate?.await()
        if (failRead) throw IOException("Read unavailable")
        emit(value.value)
    }
    override suspend fun save(enabled: Boolean) {
        writes++
        writeGate?.await()
        if (failWrite) throw IOException("Write unavailable")
        value.value = enabled
    }
}
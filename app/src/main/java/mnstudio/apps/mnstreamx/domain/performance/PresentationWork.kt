package mnstudio.apps.mnstreamx.domain.performance

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex

/** Only list computation enters this gate. Network, video decoding and durable writes stay independent. */
internal class PresentationWork {
    private val gate = Mutex()
    suspend fun <T> run(dispatcher: CoroutineDispatcher, block: suspend () -> T): T {
        PerformanceJournal.measure(PerformanceStage.PRESENTATION_WAIT) { gate.lock() }
        try {
            return withContext(dispatcher) { PerformanceJournal.measure(PerformanceStage.PRESENTATION_WORK,block) }
        } finally { gate.unlock() }
    }
    companion object { val Shared = PresentationWork() }
}
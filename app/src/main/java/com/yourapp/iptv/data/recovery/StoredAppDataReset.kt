package com.yourapp.iptv.data.recovery

import com.yourapp.iptv.domain.recovery.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal interface AppResetJournal {
    fun pending(): Boolean
    fun mark()
}
internal fun interface AppDataEraser { fun request(): Boolean }

/** Android owns the complete erase; this coordinator never removes individual app data or keys. */
internal class StoredAppDataReset(private val journal: AppResetJournal, private val eraser: AppDataEraser,
    private val scope: CoroutineScope, private val io: CoroutineDispatcher = Dispatchers.IO) : AppDataReset {
    private val mutable = MutableStateFlow(AppResetState())
    override val state = mutable.asStateFlow()
    private val operations = Mutex()
    private var generation = 0L

    override suspend fun initialize() = withContext(io) { operations.withLock {
        if (state.value.phase != AppResetPhase.CHECKING && state.value.problem != AppResetProblem.JOURNAL_READ) return@withLock
        try {
            mutable.value = if (journal.pending()) AppResetState(AppResetPhase.RECOVERY_REQUIRED,AppResetProblem.INTERRUPTED)
                else AppResetState(AppResetPhase.READY)
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { mutable.value = AppResetState(AppResetPhase.RECOVERY_REQUIRED,AppResetProblem.JOURNAL_READ) }
    } }

    override suspend fun request() = withContext(io) {
        if (!operations.tryLock()) return@withContext
        try {
            if (!state.value.mayRequest) return@withContext
            val ticket = ++generation
            mutable.value = AppResetState(AppResetPhase.COMMITTING)
            // Once confirmed, caller cancellation must not interrupt the marker + system request.
            withContext(NonCancellable) committed@ {
                try { journal.mark() }
                catch (_: Exception) {
                    mutable.value = AppResetState(AppResetPhase.RECOVERY_REQUIRED,AppResetProblem.JOURNAL_WRITE)
                    return@committed
                }
                mutable.value = AppResetState(AppResetPhase.WAITING_FOR_ANDROID)
                val accepted = try { eraser.request() }
                catch (_: Exception) {
                    mutable.value = AppResetState(AppResetPhase.RECOVERY_REQUIRED,AppResetProblem.SYSTEM_UNAVAILABLE)
                    return@committed
                }
                if (!accepted) {
                    mutable.value = AppResetState(AppResetPhase.RECOVERY_REQUIRED,AppResetProblem.SYSTEM_REJECTED)
                } else scope.launch {
                    delay(15_000)
                    operations.withLock {
                        if (ticket == generation && state.value.phase == AppResetPhase.WAITING_FOR_ANDROID)
                            mutable.value = AppResetState(AppResetPhase.RECOVERY_REQUIRED,AppResetProblem.NOT_FINISHED)
                    }
                }
                Unit
            }
        } finally { operations.unlock() }
    }
}
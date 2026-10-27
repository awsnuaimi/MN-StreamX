package com.yourapp.iptv.data.datastore

import com.yourapp.iptv.domain.model.WatchHistoryEntry
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

internal sealed interface HistoryWrite {
    class Records(val batch: HistoryRecordBatch = HistoryRecordBatch()) : HistoryWrite
    class Edit(val source: String, val remove: String?, val reply: CompletableDeferred<Unit>) : HistoryWrite
}

/** Synchronous admission preserves event order; the conflated channel carries wake-ups only. */
internal class WatchHistoryWriter(scope: CoroutineScope, private val apply: suspend (HistoryWrite) -> Unit) {
    private val guard = Any()
    private val queue = ArrayDeque<HistoryWrite>()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var stopped: IOException? = null
    private var edits = 0
    private var inFlight: HistoryWrite? = null
    private val worker = scope.launch {
        try {
            for (ignored in wake) while (true) {
                val command = take() ?: break
                var failure: Throwable? = null
                try { currentCoroutineContext().ensureActive();apply(command) }
                catch (error: Throwable) { failure = error }
                finally { finish(command) }
                if (command is HistoryWrite.Edit) {
                    val replyFailure = if (failure is CancellationException) IOException("History writer stopped",failure) else failure
                    if (replyFailure == null) command.reply.complete(Unit) else command.reply.completeExceptionally(replyFailure)
                }
                if (failure is CancellationException) throw failure
                if (failure != null && failure !is Exception) throw failure
            }
        } finally { stop(IOException("History writer stopped")) }
    }
    init { worker.invokeOnCompletion { stop(IOException("History writer stopped",it)) } }

    fun record(source: String, entry: WatchHistoryEntry): Boolean {
        val accepted = synchronized(guard) {
            if (stopped != null) false
            else {
                val tail = queue.lastOrNull() as? HistoryWrite.Records
                if (tail != null) tail.batch.offer(source,entry)
                else HistoryWrite.Records().also { it.batch.offer(source,entry);queue.addLast(it) }.let { true }
            }
        }
        if (accepted) wake.trySend(Unit)
        return accepted
    }
    suspend fun edit(source: String, remove: String?) {
        currentCoroutineContext().ensureActive()
        // No parent Job: cancelling the caller does not retract an accepted durable edit.
        val reply = CompletableDeferred<Unit>()
        synchronized(guard) {
            stopped?.let { throw it }
            if (edits >= MAX_EDITS) throw IOException("History edit queue is full")
            edits++;queue.addLast(HistoryWrite.Edit(source,remove,reply))
        }
        wake.trySend(Unit)
        reply.await()
    }
    private fun take(): HistoryWrite? = synchronized(guard) {
        if (stopped != null || queue.isEmpty()) null else queue.removeFirst().also { inFlight = it }
    }
    private fun finish(command: HistoryWrite) = synchronized(guard) {
        if (inFlight === command) inFlight = null
        if (command is HistoryWrite.Edit && edits > 0) edits--
    }
    private fun stop(error: IOException) {
        val replies = synchronized(guard) {
            if (stopped != null) return
            stopped = error
            val all = queue.toList() + listOfNotNull(inFlight)
            queue.clear();inFlight = null;edits = 0
            all.filterIsInstance<HistoryWrite.Edit>().map { it.reply }
        }
        wake.close()
        replies.forEach { it.completeExceptionally(error) }
    }
    companion object {
        // Includes the executing edit. At most 9 queued record runs plus one executing run.
        const val MAX_EDITS = 8
    }
}
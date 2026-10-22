package com.yourapp.iptv.data.caption

import kotlinx.coroutines.CancellationException

/** Timer callbacks may terminate their own request only; handle disposal follows finish(). */
internal class CaptionCallControl {
    class Ticket internal constructor(internal val terminate: () -> Unit) {
        @Volatile var expired = false
            internal set
    }
    private val lock = Any()
    private var active: Ticket? = null
    private var cancelled = false

    fun begin(terminate: () -> Unit): Ticket = synchronized(lock) {
        if (cancelled) throw CancellationException("Caption translator stopped")
        check(active == null) { "Concurrent caption requests" }
        Ticket(terminate).also { active = it }
    }
    fun expire(ticket: Ticket) = synchronized(lock) {
        if (active === ticket && !cancelled) {
            ticket.expired = true
            ticket.terminate()
        }
    }
    fun checkRunning(ticket: Ticket) = synchronized(lock) {
        if (cancelled || active !== ticket) throw CancellationException("Caption request stopped")
    }
    fun finish(ticket: Ticket) = synchronized(lock) {
        if (active === ticket) active = null
    }
    fun cancel() = synchronized(lock) {
        if (!cancelled) {
            cancelled = true
            active?.terminate?.invoke()
        }
    }
}
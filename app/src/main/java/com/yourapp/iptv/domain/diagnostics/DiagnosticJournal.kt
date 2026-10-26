package com.yourapp.iptv.domain.diagnostics

import com.yourapp.iptv.domain.update.UpdateIssue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Bounded session journal. Only typed, non-secret fields cross this boundary. */
object DiagnosticJournal {
    const val LIMIT = 64
    private val mutable = MutableStateFlow<List<DiagnosticEvent>>(emptyList())
    val events = mutable.asStateFlow()
    private var sequence = 0L
    @Synchronized
    fun record(area: DiagnosticArea, issue: DiagnosticIssue, sourceKey: String? = null,
        code: Int? = null, httpStatus: Int? = null, update: UpdateIssue? = null) {
        val safeKey = sourceKey?.takeIf { it.length == 64 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }
        val event = DiagnosticEvent(++sequence,System.currentTimeMillis().coerceAtLeast(0),area,issue,safeKey,
            code?.takeIf { it in 0..1_000_000 },httpStatus?.takeIf { it in 100..599 },update)
        mutable.value = (listOf(event) + mutable.value).take(LIMIT)
    }
    @Synchronized fun clear() { mutable.value = emptyList() }
}
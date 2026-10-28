package com.yourapp.iptv.domain.model

data class EpgSchedule(val programs: List<Program>, val isStale: Boolean = false) {
    fun current(now: Long): Program? = programs
        .filter { it.startTime <= now && now < it.endTime }
        .maxByOrNull { it.startTime }

    fun next(now: Long): Program? {
        val current = current(now)
        return programs.asSequence()
            .filter { it.startTime > now && (current == null || it.startTime >= current.endTime) }
            .minByOrNull { it.startTime }
    }

    fun upcoming(now: Long, limit: Int = 3): List<Program> {
        val boundary = current(now)?.endTime ?: now
        return programs.asSequence().filter { it.startTime > now && it.startTime >= boundary && it.endTime > it.startTime }
            .sortedBy { it.startTime }.take(limit.coerceIn(0, 8)).toList()
    }

    fun progress(now: Long): Float = current(now)?.let {
        ((now - it.startTime).toDouble() / (it.endTime - it.startTime)).toFloat().coerceIn(0f, 1f)
    } ?: 0f
}
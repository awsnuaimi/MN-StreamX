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

    fun progress(now: Long): Float = current(now)?.let {
        ((now - it.startTime).toDouble() / (it.endTime - it.startTime)).toFloat().coerceIn(0f, 1f)
    } ?: 0f
}
package com.yourapp.iptv.domain.model

object EpgProgramSelection {
    fun key(program: Program): String = "${program.startTime}:${program.endTime}"

    fun resolve(programs: List<Program>, selectedKey: String?, now: Long): Program? {
        programs.firstOrNull { key(it) == selectedKey }?.let { return it }
        return programs.filter { it.startTime <= now && now < it.endTime }.maxByOrNull { it.startTime }
            ?: programs.filter { it.startTime > now }.minByOrNull { it.startTime }
            ?: programs.maxByOrNull { it.endTime }
    }
}
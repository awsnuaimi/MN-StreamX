package com.yourapp.iptv.domain.model

/** Android's previous process-exit report. It is diagnostic evidence, not a predicted cause. */
enum class CaptionExitKind { LOW_MEMORY, NATIVE_CRASH, JAVA_CRASH, ANR, SIGNAL, RESOURCE_LIMIT, OTHER }
data class CaptionExitReport(val kind: CaptionExitKind, val timestampMillis: Long, val statusCode: Int,
    val rssMb: Long)
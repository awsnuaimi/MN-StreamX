package com.yourapp.iptv.domain.model

enum class CaptionStage { SPEECH, TRANSLATION, THROUGHPUT, LAG }
data class CaptionTiming(val audioMillis: Long, val speechMillis: Long,
    val translationMillis: Long, val memoryMillis: Long, val totalMillis: Long)
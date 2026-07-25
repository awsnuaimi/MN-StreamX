package com.yourapp.iptv.domain.model

data class Program(
    val title: String,
    val startTime: Long, // وقت البدء (بالميلي ثانية)
    val endTime: Long,   // وقت الانتهاء (بالميلي ثانية)
    val description: String? = null
)
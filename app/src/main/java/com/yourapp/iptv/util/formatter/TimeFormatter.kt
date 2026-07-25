package com.yourapp.iptv.util.formatter

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeFormatter {
    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    private val fullFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    fun formatTime(timeInMillis: Long): String {
        return try {
            timeFormat.format(Date(timeInMillis))
        } catch (e: Exception) {
            "--:--"
        }
    }

    fun formatFull(timeInMillis: Long): String {
        return try {
            fullFormat.format(Date(timeInMillis))
        } catch (e: Exception) {
            "Unknown"
        }
    }
}
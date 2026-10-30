package com.yourapp.iptv.ui.screen.home

/** Two released physical presses, never auto-repeat. The first arrow is not delayed. */
internal class LiveTvUpShortcut(private val windowMillis: Long = 300L) {
    enum class Result { NORMAL, START, DOUBLE }
    private var started = -1L
    private var released = false
    private var device = 0

    fun down(time: Long, repeating: Boolean, deviceId: Int, canStart: Boolean = true): Result {
        if (repeating || time < 0) { reset(); return Result.NORMAL }
        val double = released && device == deviceId && started >= 0 && time - started in 0..windowMillis
        reset()
        if (double) return Result.DOUBLE
        if (!canStart) return Result.NORMAL
        started = time; device = deviceId
        return Result.START
    }

    fun up(time: Long, canceled: Boolean, deviceId: Int) {
        if (canceled || deviceId != device || started < 0 || time - started !in 0..windowMillis) reset()
        else released = true
    }

    fun reset() { started = -1; released = false }
}
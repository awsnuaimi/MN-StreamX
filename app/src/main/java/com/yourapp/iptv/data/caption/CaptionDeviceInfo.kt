package com.yourapp.iptv.data.caption

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.yourapp.iptv.domain.repository.CaptionDevice
import com.yourapp.iptv.domain.repository.CaptionDeviceStats

internal class CaptionDeviceInfo(context: Context) : CaptionDevice {
    private val memory = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    override fun snapshot(): CaptionDeviceStats {
        val info = ActivityManager.MemoryInfo().also(memory::getMemoryInfo)
        return CaptionDeviceStats(Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
            Runtime.getRuntime().availableProcessors(), info.totalMem / 1048576, info.availMem / 1048576)
    }
    override fun canRun(target: String): Boolean {
        if (Build.SUPPORTED_ABIS.none { it in setOf("arm64-v8a", "armeabi-v7a", "x86_64") }) return false
        val info = ActivityManager.MemoryInfo().also(memory::getMemoryInfo)
        val reserve = when (target) { "running" -> 96L; "en" -> 320L; else -> 576L } * 1024 * 1024
        return !info.lowMemory && info.availMem > maxOf(reserve, info.threshold + 64L * 1024 * 1024)
    }
}
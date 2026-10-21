package com.yourapp.iptv.data.caption

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.yourapp.iptv.domain.caption.CaptionResourceCheck
import com.yourapp.iptv.domain.caption.CaptionResourcePolicy
import com.yourapp.iptv.domain.model.CaptionStatus
import com.yourapp.iptv.domain.repository.CaptionDevice
import com.yourapp.iptv.domain.repository.CaptionDeviceStats

internal class CaptionDeviceInfo(context: Context) : CaptionDevice {
    private val memory = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val storage = context.noBackupFilesDir
    override fun snapshot(): CaptionDeviceStats {
        val info = ActivityManager.MemoryInfo().also(memory::getMemoryInfo)
        return CaptionDeviceStats(Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
            Runtime.getRuntime().availableProcessors(), info.totalMem / CaptionResourcePolicy.MIB,
            info.availMem / CaptionResourcePolicy.MIB, storage.usableSpace / CaptionResourcePolicy.MIB)
    }
    override fun supported() = Build.SUPPORTED_ABIS.any { it in setOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    override fun assess(target: String): CaptionResourceCheck {
        if (!supported()) return CaptionResourceCheck(false, CaptionStatus.UNAVAILABLE)
        val info = ActivityManager.MemoryInfo().also(memory::getMemoryInfo)
        return CaptionResourcePolicy.memory(target, info.availMem, info.threshold, info.lowMemory)
    }
    override fun canRun(target: String) = assess(target).allowed
}
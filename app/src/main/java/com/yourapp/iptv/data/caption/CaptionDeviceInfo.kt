package com.yourapp.iptv.data.caption

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import com.yourapp.iptv.domain.caption.CaptionResourceCheck
import com.yourapp.iptv.domain.caption.CaptionResourcePolicy
import com.yourapp.iptv.domain.model.CaptionStatus
import com.yourapp.iptv.domain.model.CaptionExitKind
import com.yourapp.iptv.domain.model.CaptionExitReport
import com.yourapp.iptv.domain.repository.CaptionDevice
import com.yourapp.iptv.domain.repository.CaptionDeviceStats

internal class CaptionDeviceInfo(context: Context) : CaptionDevice {
    private val memory = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val storage = context.noBackupFilesDir
    private val packageName = context.packageName
    private val lastExit by lazy { if (Build.VERSION.SDK_INT >= 30) previousExit() else null }
    override fun snapshot(): CaptionDeviceStats {
        val info = ActivityManager.MemoryInfo().also(memory::getMemoryInfo)
        return CaptionDeviceStats(Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
            Runtime.getRuntime().availableProcessors(), info.totalMem / CaptionResourcePolicy.MIB,
            info.availMem / CaptionResourcePolicy.MIB, storage.usableSpace / CaptionResourcePolicy.MIB, lastExit)
    }
    override fun supported() = Build.SUPPORTED_ABIS.any { it in setOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    override fun assess(target: String): CaptionResourceCheck {
        if (!supported()) return CaptionResourceCheck(false, CaptionStatus.UNAVAILABLE)
        val info = ActivityManager.MemoryInfo().also(memory::getMemoryInfo)
        return CaptionResourcePolicy.memory(target, info.availMem, info.threshold, info.lowMemory)
    }
    override fun canRun(target: String) = assess(target).allowed

    @androidx.annotation.RequiresApi(30)
    private fun previousExit(): CaptionExitReport? {
        return try {
            memory.getHistoricalProcessExitReasons(packageName, 0, 10)
                .filter { it.processName == packageName && exitKind(it.reason) != null }
                .maxByOrNull { it.timestamp }?.let { record ->
                    CaptionExitReport(checkNotNull(exitKind(record.reason)), record.timestamp,
                        record.status, record.rss.coerceAtLeast(0) / 1024)
                }
        } catch (_: Exception) { null }
    }

    internal companion object {
        fun exitKind(reason: Int): CaptionExitKind? = when (reason) {
            ApplicationExitInfo.REASON_LOW_MEMORY -> CaptionExitKind.LOW_MEMORY
            ApplicationExitInfo.REASON_CRASH_NATIVE -> CaptionExitKind.NATIVE_CRASH
            ApplicationExitInfo.REASON_CRASH -> CaptionExitKind.JAVA_CRASH
            ApplicationExitInfo.REASON_ANR -> CaptionExitKind.ANR
            // SIGKILL alone does not prove a low-memory kill on every Android TV.
            ApplicationExitInfo.REASON_SIGNALED -> CaptionExitKind.SIGNAL
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> CaptionExitKind.RESOURCE_LIMIT
            ApplicationExitInfo.REASON_OTHER -> CaptionExitKind.OTHER
            else -> null
        }
    }
}
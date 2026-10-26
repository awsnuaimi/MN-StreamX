package com.yourapp.iptv.data.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.media.MediaCodecList
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.StatFs
import com.yourapp.iptv.BuildConfig
import com.yourapp.iptv.domain.diagnostics.*

internal class AndroidDiagnosticSnapshot(private val context: Context) {
    fun read(): DiagnosticSnapshot {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val network = runCatching { manager?.activeNetwork }
        val capabilities = runCatching { network.getOrNull()?.let { manager?.getNetworkCapabilities(it) } }
        val caps = capabilities.getOrNull()
        val connection = when {
            manager == null || network.isFailure || capabilities.isFailure -> DiagnosticConnection.UNKNOWN
            network.getOrNull() == null -> DiagnosticConnection.NONE
            caps == null -> DiagnosticConnection.UNKNOWN
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> DiagnosticConnection.VPN
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> DiagnosticConnection.ETHERNET
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> DiagnosticConnection.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> DiagnosticConnection.CELLULAR
            else -> DiagnosticConnection.OTHER
        }
        val memory = runCatching {
            context.getSystemService(ActivityManager::class.java)?.let { activity -> ActivityManager.MemoryInfo().also(activity::getMemoryInfo) }
        }.getOrNull()
        val runtime = Runtime.getRuntime()
        val disk = runCatching { StatFs(context.filesDir.path) }.getOrNull()
        val codecs = runCatching {
            val types = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.filterNot { it.isEncoder }
                .flatMap { it.supportedTypes.toList() }.toSet()
            mapOf(DiagnosticCodec.AVC to "video/avc",DiagnosticCodec.HEVC to "video/hevc",DiagnosticCodec.AV1 to "video/av01")
                .filterValues { it in types }.keys
        }.getOrNull()
        return DiagnosticSnapshot(BuildConfig.VERSION_NAME,Build.VERSION.SDK_INT,Build.SUPPORTED_ABIS.toList(),connection,
            caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),runCatching { manager?.isActiveNetworkMetered }.getOrNull(),
            memory?.totalMem?.takeIf { it > 0 },memory?.availMem?.takeIf { it >= 0 },memory?.lowMemory,
            (runtime.totalMemory() - runtime.freeMemory()).coerceAtLeast(0),runtime.maxMemory(),
            disk?.availableBytes,disk?.totalBytes,codecs)
    }
}
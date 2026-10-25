package com.yourapp.iptv.data.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.yourapp.iptv.domain.update.AppUpdate
import com.yourapp.iptv.domain.update.UpdateFailure
import com.yourapp.iptv.domain.update.UpdateIssue
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

internal data class UpdateApkIdentity(
    val packageName: String,
    val version: Long,
    val certificates: Set<String>,
    val minSdk: Int = 29,
    val nativeAbis: Set<String> = emptySet()
)

internal object ApkUpdatePolicy {
    fun validate(installed: UpdateApkIdentity, candidate: UpdateApkIdentity,
        update: AppUpdate, sdk: Int, supportedAbis: List<String>) {
        if (installed.packageName != candidate.packageName) fail(UpdateIssue.PACKAGE_MISMATCH)
        if (candidate.version <= installed.version || candidate.version != update.versionCode) {
            fail(UpdateIssue.VERSION_MISMATCH)
        }
        if (installed.certificates.isEmpty() || installed.certificates != candidate.certificates) {
            fail(UpdateIssue.SIGNATURE_MISMATCH)
        }
        if (candidate.minSdk > sdk) fail(UpdateIssue.ANDROID_UNSUPPORTED)
        if (update.abi !in supportedAbis || candidate.nativeAbis != setOf(update.abi)) {
            fail(UpdateIssue.NO_COMPATIBLE_APK)
        }
    }
    private fun fail(issue: UpdateIssue): Nothing = throw UpdateFailure(issue)
}

/** Compares the archive with the installed app before granting its private URI to Android. */
@Suppress("DEPRECATION")
internal class ApkUpdateVerifier(private val context: Context) {
    private val packages get() = context.packageManager
    fun installedVersion(): Long = installed().longVersionCode

    fun verify(update: AppUpdate, file: File, active: () -> Unit = {}) {
        if (!UpdateDownload.matches(file, update, active)) throw UpdateFailure(UpdateIssue.INTEGRITY)
        val archive = packages.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: throw UpdateFailure(UpdateIssue.INTEGRITY)
        val abis = try {
            ZipFile(file).use { zip -> zip.entries().asSequence().mapNotNull { entry ->
                val parts = entry.name.split('/', limit = 3)
                parts.getOrNull(1)?.takeIf { parts.size == 3 && parts[0] == "lib" && !entry.isDirectory }
            }.toSet() }
        } catch (_: Exception) { throw UpdateFailure(UpdateIssue.INTEGRITY) }
        val minimum = archive.applicationInfo?.minSdkVersion
            ?: throw UpdateFailure(UpdateIssue.INTEGRITY)
        ApkUpdatePolicy.validate(identity(installed()), identity(archive).copy(minSdk = minimum, nativeAbis = abis),
            update, Build.VERSION.SDK_INT, Build.SUPPORTED_ABIS.toList())
    }

    private fun installed() = packages.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
    private fun identity(info: PackageInfo): UpdateApkIdentity {
        val signatures = info.signingInfo?.apkContentsSigners.orEmpty()
        return UpdateApkIdentity(info.packageName, info.longVersionCode, signatures.map {
            UpdateDownload.hex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray()))
        }.toSet())
    }
}
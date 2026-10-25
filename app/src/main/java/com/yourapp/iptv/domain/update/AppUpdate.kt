package com.yourapp.iptv.domain.update

import java.io.File

data class AppUpdate(
    val versionCode: Long,
    val versionName: String,
    val abi: String,
    val url: String,
    val bytes: Long,
    val sha256: String
) {
    companion object { const val MAX_BYTES = 512L * 1024 * 1024 }
}

enum class UpdateIssue {
    NOT_CONFIGURED, NO_RELEASE, ACCESS, NETWORK, RATE_LIMIT, INVALID_RELEASE,
    NO_COMPATIBLE_APK, STORAGE, INTEGRITY, PACKAGE_MISMATCH, SIGNATURE_MISMATCH,
    VERSION_MISMATCH, ANDROID_UNSUPPORTED, INSTALL_PERMISSION, INSTALL_UNAVAILABLE
}

class UpdateFailure(val issue: UpdateIssue) : Exception(issue.name)

interface AppUpdateRepository {
    suspend fun check(): AppUpdate?
    suspend fun download(update: AppUpdate, progress: (Int) -> Unit): File
    suspend fun verify(update: AppUpdate, file: File)
}
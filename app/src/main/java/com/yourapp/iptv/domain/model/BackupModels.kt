package com.yourapp.iptv.domain.model

enum class BackupMaintenance { STARTING, READY, RESTORING, RECOVERY_REQUIRED }
enum class BackupProblem { PASSWORD_RULES, PASSWORD_MISMATCH, AUTHENTICATION, INVALID, UNSUPPORTED, TOO_LARGE, STORAGE, PROFILE_LIMIT, ACCOUNT_LIMIT, RECOVERY, PICKER }
class BackupException(val problem: BackupProblem) : Exception("Backup operation failed: ${problem.name}")

data class BackupPreview(
    val token: String, val createdAtMillis: Long, val subscriptionNames: List<String>,
    val newSubscriptions: Int, val existingSubscriptions: Int, val favoriteCount: Int,
    val folderCount: Int, val historyCount: Int, val progressCount: Int, val profileCount: Int = 1
)
data class BackupRestoreOptions(val settings: Boolean = true, val folders: Boolean = true,
    val activateSavedSubscription: Boolean = true)

class BackupDocument(val name: String, val bytes: ByteArray) {
    override fun toString() = "BackupDocument(contents=redacted)"
}
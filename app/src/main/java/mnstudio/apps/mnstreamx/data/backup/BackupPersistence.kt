package mnstudio.apps.mnstreamx.data.backup

import mnstudio.apps.mnstreamx.domain.model.*

internal interface BackupPersistence {
    suspend fun prepare()
    suspend fun capture(): BackupSnapshot
    fun accounts(snapshot: BackupSnapshot): Subscriptions
    fun payload(snapshot: BackupSnapshot): BackupPayload
    fun restored(before: BackupSnapshot, saved: BackupPayload, options: BackupRestoreOptions): BackupSnapshot
    suspend fun apply(snapshot: BackupSnapshot)
}
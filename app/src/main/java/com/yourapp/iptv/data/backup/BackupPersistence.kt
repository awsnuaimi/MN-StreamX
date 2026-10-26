package com.yourapp.iptv.data.backup

import com.yourapp.iptv.domain.model.*

internal interface BackupPersistence {
    suspend fun prepare()
    suspend fun capture(): BackupSnapshot
    fun accounts(snapshot: BackupSnapshot): Subscriptions
    fun payload(snapshot: BackupSnapshot): BackupPayload
    fun restored(before: BackupSnapshot, saved: BackupPayload, options: BackupRestoreOptions): BackupSnapshot
    suspend fun apply(snapshot: BackupSnapshot)
}
package com.yourapp.iptv.domain.repository

import com.yourapp.iptv.domain.model.*
import kotlinx.coroutines.flow.StateFlow

interface BackupRepository {
    val maintenance: StateFlow<BackupMaintenance>
    suspend fun recover()
    suspend fun export(password: CharArray): ByteArray
    suspend fun inspect(bytes: ByteArray, password: CharArray): BackupPreview
    suspend fun restore(token: String, options: BackupRestoreOptions)
    fun discard(token: String)
}

interface BackupDocuments {
    suspend fun read(uri: String): BackupDocument
    suspend fun writeNew(uri: String, bytes: ByteArray)
}
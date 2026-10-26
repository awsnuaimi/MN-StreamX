package com.yourapp.iptv.data.backup

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.repository.BackupDocuments
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.*

internal class AndroidBackupDocuments(private val resolver: ContentResolver) : BackupDocuments {
    override suspend fun read(uri: String): BackupDocument = withContext(Dispatchers.IO) {
        try {
            val source = Uri.parse(uri)
            require(source.scheme == "content")
            val name = resolver.query(source, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0)?.take(160) else null
            } ?: "MN-StreamX.mnxb"
            val bytes = requireNotNull(resolver.openInputStream(source)).use { it.readBytesBounded(BackupLimits.FILE) }
            currentCoroutineContext().ensureActive()
            BackupDocument(name, bytes)
        } catch (e: CancellationException) { throw e }
        catch (e: BackupException) { throw e }
        catch (_: Exception) { throw BackupException(BackupProblem.STORAGE) }
    }
    override suspend fun writeNew(uri: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val target = Uri.parse(uri)
        try {
            require(target.scheme == "content" && bytes.size <= BackupLimits.FILE)
            requireNotNull(resolver.openOutputStream(target, "wt")).use { output ->
                for (start in bytes.indices step 16_384) {
                    currentCoroutineContext().ensureActive()
                    output.write(bytes, start, minOf(16_384, bytes.size - start))
                }
                output.flush()
            }
        } catch (error: Exception) {
            withContext(NonCancellable) { runCatching { resolver.delete(target, null, null) } }
            if (error is CancellationException) throw error
            throw BackupException(BackupProblem.STORAGE)
        }
    }
}

internal fun InputStream.readBytesBounded(limit: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(16_384)
    var emptyReads = 0
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        if (count == 0) {
            if (++emptyReads > 16) throw BackupException(BackupProblem.STORAGE)
            continue
        }
        emptyReads = 0
        if (output.size() > limit - count) throw BackupException(BackupProblem.TOO_LARGE)
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
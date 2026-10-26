package com.yourapp.iptv.data.backup

import com.google.gson.JsonParser
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.repository.BackupRepository
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class EncryptedBackupRepository(
    private val storage: BackupPersistence,
    private val journal: BackupJournal,
    private val cipher: BackupCipher = BackupCipher(),
    private val clock: () -> Long = System::nanoTime
) : BackupRepository {
    private data class Staged(val token: String, val time: Long, val payload: BackupPayload)
    private val staged = AtomicReference<Staged?>(null)
    private val operations = Mutex()
    private val mutable = MutableStateFlow(BackupMaintenance.STARTING)
    override val maintenance = mutable.asStateFlow()

    override suspend fun recover() = withContext(Dispatchers.IO) { operations.withLock {
        if (mutable.value == BackupMaintenance.READY) return@withLock
        mutable.value = BackupMaintenance.STARTING
        try {
            withContext(NonCancellable) { AppDataGate.write {
                journal.read()?.let { storage.apply(it); journal.clear() }
            } }
            mutable.value = BackupMaintenance.READY
        } catch (_: Exception) {
            mutable.value = BackupMaintenance.RECOVERY_REQUIRED
            throw BackupException(BackupProblem.RECOVERY)
        }
    } }

    override suspend fun export(password: CharArray): ByteArray {
        try { return withContext(Dispatchers.IO) { operations.withLock {
            ready(); BackupCipher.validatePassword(password)
            storage.prepare()
            val plain = AppDataGate.write { BackupPayloadCodec.encode(storage.payload(storage.capture())) }
            try { cipher.encrypt(plain, password) } finally { plain.fill(0) }
        } } } catch (e: CancellationException) { throw e }
        catch (e: BackupException) { throw e }
        catch (_: Exception) { throw BackupException(BackupProblem.STORAGE) }
        finally { password.fill('\u0000') }
    }

    override suspend fun inspect(bytes: ByteArray, password: CharArray): BackupPreview {
        var prepared: Staged? = null
        try { return withContext(Dispatchers.IO) { operations.withLock {
            ready()
            val plain = cipher.decrypt(bytes, password)
            val value = try { BackupPayloadCodec.decode(plain) } finally { plain.fill(0) }
            storage.prepare()
            val merge = AppDataGate.write { BackupMerge.accounts(storage.accounts(storage.capture()), value.subscriptions, true) }
            currentCoroutineContext().ensureActive()
            val token = UUID.randomUUID().toString()
            val candidate = Staged(token, clock(), value)
            prepared = candidate
            staged.set(candidate)
            fun count(area: BackupStore, array: String) = value.areas[area].orEmpty().values.sumOf {
                JsonParser.parseString(it.text).asJsonObject.getAsJsonArray(array).size()
            }
            BackupPreview(token, value.createdAt, BackupMerge.usefulEntries(value.subscriptions).map { it.name },
                merge.newCount, merge.existingCount, value.favorites.size, count(BackupStore.ORGANIZATION, "folders"),
                count(BackupStore.HISTORY, "rows"), count(BackupStore.VOD, "entries"))
        } } } catch (e: CancellationException) {
            prepared?.let { staged.compareAndSet(it, null) }
            throw e
        }
        catch (e: BackupException) { throw e }
        catch (_: Exception) { throw BackupException(BackupProblem.STORAGE) }
        finally { password.fill('\u0000') }
    }

    override suspend fun restore(token: String, options: BackupRestoreOptions) = withContext(Dispatchers.IO) { operations.withLock {
        ready()
        val imported = staged.get()?.takeIf { it.token == token && clock() - it.time < TimeUnit.MINUTES.toNanos(10) }
            ?: throw BackupException(BackupProblem.INVALID)
        storage.prepare()
        mutable.value = BackupMaintenance.RESTORING
        try {
            withContext(NonCancellable) { AppDataGate.write {
                if (journal.exists()) throw BackupException(BackupProblem.RECOVERY)
                val before = storage.capture()
                val after = storage.restored(before, imported.payload, options)
                journal.save(before)
                try {
                    storage.apply(after)
                    journal.clear()
                } catch (_: Exception) {
                    try { storage.apply(before); journal.clear() }
                    catch (_: Exception) {
                        mutable.value = BackupMaintenance.RECOVERY_REQUIRED
                        throw BackupException(BackupProblem.RECOVERY)
                    }
                    throw BackupException(BackupProblem.STORAGE)
                }
            } }
            staged.compareAndSet(imported, null)
        } catch (e: BackupException) {
            if (e.problem == BackupProblem.RECOVERY) mutable.value = BackupMaintenance.RECOVERY_REQUIRED
            throw e
        } catch (_: Exception) {
            if (journal.exists()) mutable.value = BackupMaintenance.RECOVERY_REQUIRED
            throw BackupException(if (mutable.value == BackupMaintenance.RECOVERY_REQUIRED) BackupProblem.RECOVERY else BackupProblem.STORAGE)
        } finally {
            if (mutable.value != BackupMaintenance.RECOVERY_REQUIRED) mutable.value = BackupMaintenance.READY
        }
        Unit
    } }

    override fun discard(token: String) { staged.get()?.takeIf { it.token == token }?.let { staged.compareAndSet(it, null) } }
    private fun ready() {
        if (mutable.value != BackupMaintenance.READY) throw BackupException(BackupProblem.RECOVERY)
    }
}
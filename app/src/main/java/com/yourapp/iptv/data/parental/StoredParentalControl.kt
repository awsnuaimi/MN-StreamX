package com.yourapp.iptv.data.parental

import com.yourapp.iptv.domain.parental.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serial disk mutations; foreground generation prevents a late PBKDF result from unlocking in background. */
internal class StoredParentalControl(private val storage: ParentalStorage, private val clock: ParentalClock,
    private val io: CoroutineDispatcher = Dispatchers.IO) : ParentalControl {
    private val mutable = MutableStateFlow(ParentalState())
    override val state = mutable.asStateFlow()
    private val operations = Mutex()
    private val gate = Any()
    private var foreground = false
    private var generation = 0L
    private var record: ParentalRecord? = null
    @Volatile private var deadline = 0L

    override fun foreground() { synchronized(gate) { foreground = true } }
    override fun background() { synchronized(gate) { foreground = false; lock() } }
    override fun lock() { synchronized(gate) {
        generation++
        mutable.value = mutable.value.copy(unlocked = false,epoch = mutable.value.epoch + 1)
    } }
    override fun remainingSeconds() = ((deadline - clock.elapsed()).coerceIn(0,1_800_000) + 999) / 1000

    override suspend fun initialize() = withContext(io) { operations.withLock {
        if (state.value.ready && !state.value.storageError) return@withLock
        try {
            var loaded = storage.read()
            if (loaded != null && loaded.cooldown > 0 && loaded.boot != clock.boot()) {
                loaded = ParentalRecord(loaded.salt,loaded.hash,loaded.rules,loaded.failures,
                    clock.elapsed() + loaded.cooldown,clock.boot(),loaded.cooldown)
                withContext(NonCancellable) { storage.write(loaded) }
            }
            record = loaded; deadline = loaded?.deadline ?: 0
            publish(unlocked = false)
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { synchronized(gate) {
            mutable.value = ParentalState(ready = true,configured = true,storageError = true,epoch = state.value.epoch + 1)
        } }
    } }

    override suspend fun create(pin: CharArray) = operation(pin) { token ->
        if (!usable() || record != null || !active(token)) return@operation ParentalResult.DENIED
        val salt = PinHash.salt()
        val value = ParentalRecord(salt,PinHash.derive(pin,salt),ParentalRules())
        if (!active(token)) return@operation ParentalResult.DENIED
        commit(value,token,unlock = true)
    }

    override suspend fun unlock(pin: CharArray) = operation(pin) { token ->
        val verified = verify(pin)
        if (verified != ParentalResult.OK) verified
        else {
            val old = record ?: return@operation ParentalResult.DENIED
            commit(ParentalRecord(old.salt,old.hash,old.rules),token,unlock = true)
        }
    }

    override suspend fun changePin(old: CharArray, replacement: CharArray?): ParentalResult = try {
        if (replacement != null && !PinHash.valid(replacement)) ParentalResult.INVALID_PIN
        else operation(old) { token ->
            val verified = verify(old)
            if (verified != ParentalResult.OK) verified
            else if (!active(token)) ParentalResult.DENIED
            else {
                val salt = replacement?.let { PinHash.salt() }
                val value = if (replacement == null) null else ParentalRecord(requireNotNull(salt),
                    PinHash.derive(replacement,salt),requireNotNull(record).rules)
                commit(value,token,unlock = false)
            }
        }
    } finally { old.fill('\u0000'); replacement?.fill('\u0000') }

    override suspend fun saveRules(rules: ParentalRules): ParentalResult = withContext(io) { operations.withLock {
        val token = synchronized(gate) { generation }
        val old = record
        if (!usable() || old == null || !state.value.unlocked || !active(token)) return@withLock ParentalResult.DENIED
        try { rules.validate() } catch (_: IllegalArgumentException) { return@withLock ParentalResult.INVALID_RULES }
        try { commit(ParentalRecord(old.salt,old.hash,rules),token,unlock = false) }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { storageFailure(); ParentalResult.STORAGE_ERROR }
    } }

    private suspend fun operation(pin: CharArray, action: suspend (Long) -> ParentalResult): ParentalResult = try {
        withContext(io) { operations.withLock {
            if (!PinHash.valid(pin)) return@withLock ParentalResult.INVALID_PIN
            val token = synchronized(gate) { generation }
            if (!active(token)) return@withLock ParentalResult.DENIED
            try { action(token) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { storageFailure(); ParentalResult.STORAGE_ERROR }
        } }
    } finally { pin.fill('\u0000') }

    private suspend fun verify(pin: CharArray): ParentalResult {
        val old = record
        if (!usable() || old == null) return ParentalResult.DENIED
        if (remainingSeconds() > 0) return ParentalResult.WAIT
        if (PinHash.matches(pin,old)) return ParentalResult.OK
        val failures = (old.failures + 1).coerceAtMost(10_000)
        val cooldown = if (failures < 5) 0 else (30_000L shl (failures - 5).coerceAtMost(6)).coerceAtMost(1_800_000L)
        val failed = ParentalRecord(old.salt,old.hash,old.rules,failures,
            if (cooldown == 0L) 0 else clock.elapsed() + cooldown,clock.boot(),cooldown)
        withContext(NonCancellable) { storage.write(failed); record = failed; deadline = failed.deadline; publish(false) }
        return if (cooldown > 0) ParentalResult.WAIT else ParentalResult.WRONG_PIN
    }

    private suspend fun commit(value: ParentalRecord?, token: Long, unlock: Boolean): ParentalResult = withContext(NonCancellable) {
        storage.write(value); record = value; deadline = value?.deadline ?: 0
        val granted = synchronized(gate) { publish(unlock && active(token)); !unlock || state.value.unlocked }
        if (granted) ParentalResult.OK else ParentalResult.DENIED
    }
    private fun usable() = state.value.ready && !state.value.storageError
    private fun storageFailure() = synchronized(gate) {
        generation++
        mutable.value = state.value.copy(storageError = true,unlocked = false,epoch = state.value.epoch + 1)
    }
    private fun active(token: Long) = synchronized(gate) { foreground && generation == token }
    private fun publish(unlocked: Boolean) = synchronized(gate) {
        val old = state.value
        mutable.value = ParentalState(ready = true,configured = record != null,unlocked = record != null && unlocked,
            rules = record?.rules ?: ParentalRules(),epoch = old.epoch + if (old.ready.not() || old.storageError ||
                old.unlocked != (record != null && unlocked) || old.configured != (record != null) || old.rules != record?.rules) 1 else 0)
    }
}
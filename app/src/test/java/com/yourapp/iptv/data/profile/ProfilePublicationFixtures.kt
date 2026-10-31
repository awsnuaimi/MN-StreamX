package com.yourapp.iptv.data.profile

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.yourapp.iptv.data.backup.AppDataGate
import com.yourapp.iptv.data.parental.*
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.profile.*
import java.io.Closeable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertTrue

internal class ProfileReadPause(val failure: Exception? = null) {
    val entered = CountDownLatch(1)
    private val released = CountDownLatch(1)
    fun hold() { entered.countDown();check(released.await(10,TimeUnit.SECONDS));failure?.let { throw it } }
    fun awaitEntry() { assertTrue("Profile observer did not reach the barrier",entered.await(5,TimeUnit.SECONDS)) }
    fun release() = released.countDown()
}

/** The observer has its own real scheduler; mutations run independently on Dispatchers.IO. */
internal class ProfilePublicationFixture(val disk: DataStore<Preferences> = ProfileMemory()) : Closeable {
    private val dispatcher = Executors.newSingleThreadExecutor { task -> Thread(task,"profile-publication-observer") }
        .asCoroutineDispatcher()
    private val owner = SupervisorJob()
    private val scope = CoroutineScope(owner + dispatcher)
    private data class TargetedRead(val pause: ProfileReadPause, val encryptedRecord: String?)
    private val pendingRead = AtomicReference<TargetedRead?>(null)
    private val pauses = ConcurrentLinkedQueue<ProfileReadPause>()
    val realCipher = DeviceProfileCipher { SecretKeySpec(ByteArray(32) { 6 },"AES") }
    @Volatile var decryptFailure: Exception? = null
    @Volatile var encryptInstead: String? = null
    private val cipher = object : ProfileCipher {
        override fun encrypt(value: String) = realCipher.encrypt(encryptInstead ?: value)
        override fun encryptNew(value: String) = realCipher.encryptNew(encryptInstead ?: value)
        override fun decrypt(value: String): String {
            if (Thread.currentThread().name.startsWith("profile-publication-observer")) {
                val pending = pendingRead.get()
                if (pending != null && (pending.encryptedRecord == null || pending.encryptedRecord == value) &&
                    pendingRead.compareAndSet(pending, null)) pending.pause.hold()
            }
            decryptFailure?.let { throw it }
            return realCipher.decrypt(value)
        }
    }
    val pin = StoredParentalControl(PinMemory(),PinClock())
    val profiles = StoredProfiles(disk,cipher,pin,scope,{ HomeAppearance() to PlayerAppearance() })
    val access = ProfileParentalControl(pin,profiles)

    suspend fun start(configurePin: Boolean = false) {
        access.foreground();access.initialize();drain()
        // A dispatcher barrier does not finish a suspended startup-cleanup transaction.
        // Let that transaction release its gate before tests freeze the observer thread.
        AppDataGate.write { };drain()
        if (configurePin) pin.create("123456".toCharArray())
    }
    fun pauseObserver(): ProfileReadPause = ProfileReadPause().also { pause ->
        pauses.add(pause);scope.launch { pause.hold() };pause.awaitEntry()
    }
    fun pauseNextRead(failure: Exception? = null, encryptedRecord: String? = null): ProfileReadPause =
        ProfileReadPause(failure).also { pause ->
            pauses.add(pause)
            check(pendingRead.compareAndSet(null, TargetedRead(pause, encryptedRecord)))
        }
    suspend fun drain() = withContext(dispatcher) { }
    suspend fun catalog() = ProfileCodec.decode(disk.data.first()[PROFILE_RECORD]?.let(realCipher::decrypt))
    suspend fun awaitCatalog(value: UserProfiles) = withTimeout(5_000) { profiles.state.first { it.catalog == value } }
    fun encoded(value: UserProfiles) = mutablePreferencesOf(PROFILE_RECORD to realCipher.encryptNew(ProfileCodec.encode(value)))
    override fun close() {
        pauses.forEach(ProfileReadPause::release)
        runBlocking { owner.cancelAndJoin() };dispatcher.close()
    }
}
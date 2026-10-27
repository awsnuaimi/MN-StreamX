package com.yourapp.iptv.data.profile

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.yourapp.iptv.data.parental.*
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.profile.*
import com.yourapp.iptv.domain.repository.*
import java.io.Closeable
import java.io.IOException
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal const val PROFILE_A = "11111111-1111-4111-8111-111111111111"
internal const val PROFILE_B = "22222222-2222-4222-8222-222222222222"
internal fun profileRow(id: String = PROFILE_A, kind: ProfileKind = ProfileKind.ADULT) = UserProfile(id,"Person ${id.take(8)}",kind,
    HomeAppearance(profileId = id),PlayerAppearance(profileId = id),rules = if (kind == ProfileKind.CHILD) UserProfiles.childRules() else com.yourapp.iptv.domain.parental.ParentalRules())
internal class ProfileMemory(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
    val values = MutableStateFlow(initial);var fail = false
    var before: suspend () -> Unit = {}
    private val mutex = Mutex()
    override val data: Flow<Preferences> = values
    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = mutex.withLock {
        before();if (fail) throw IOException("Synthetic profile failure")
        transform(values.value).also { values.value = it }
    }
}
internal class ProfilePeople(initial: UserProfiles = UserProfiles()) : ProfileRepository by ProfileRepository.Disabled {
    override val state = MutableStateFlow(ProfilesState(ready = true,catalog = initial))
}
internal class ProfileSettings : SettingsRepository {
    val values = MutableStateFlow(AppSettings(m3uUrl = "https://synthetic.invalid/list"))
    override fun observeSettings() = values
    override suspend fun saveSettings(settings: AppSettings) { values.value = settings }
}
internal class ProfileFixture(val disk: ProfileMemory = ProfileMemory()) : Closeable {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val pinDisk = PinMemory()
    val pin = StoredParentalControl(pinDisk,PinClock(),Dispatchers.Unconfined)
    val cipher = DeviceProfileCipher { SecretKeySpec(ByteArray(32) { 4 },"AES") }
    var failPurge = false;val purged = mutableListOf<String>()
    val repository = StoredProfiles(disk,cipher,pin,scope,
        seed = { HomeAppearance(language = HomeLanguage.GERMAN,theme = HomeTheme.RED) to PlayerAppearance() },
        purge = { if (failPurge) throw IOException();purged.add(it) })
    val access = ProfileParentalControl(pin,repository)
    suspend fun start(configure: Boolean = false) {
        access.foreground();access.initialize()
        if (configure) pin.create("123456".toCharArray())
    }
    suspend fun unlock() = pin.unlock("123456".toCharArray())
    fun last() = repository.state.value.catalog.entries.last()
    override fun close() { scope.cancel() }
}
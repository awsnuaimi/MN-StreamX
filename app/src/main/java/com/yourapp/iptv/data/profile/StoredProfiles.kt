package com.yourapp.iptv.data.profile

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.yourapp.iptv.data.backup.AppDataGate
import com.yourapp.iptv.data.security.*
import com.yourapp.iptv.domain.security.SecureStorageException
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.parental.*
import com.yourapp.iptv.domain.profile.*
import com.yourapp.iptv.domain.repository.ProfileRepository
import java.io.IOException
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal val Context.profilesStore by preferencesDataStore(name = "user_profiles")
internal val PROFILE_RECORD = stringPreferencesKey("profiles_encrypted_v1")

internal class StoredProfiles(private val store: DataStore<Preferences>, private val cipher: ProfileCipher,
    private val pin: ParentalControl, private val scope: CoroutineScope,
    private val seed: suspend () -> Pair<HomeAppearance,PlayerAppearance>,
    private val purge: suspend (String) -> Unit = {}) : ProfileRepository {
    private val mutable = MutableStateFlow(ProfilesState())
    override val state = mutable.asStateFlow()
    private val mutations = Mutex()
    private val initialization = Mutex()
    private var observer: Job? = null
    @Volatile private var observation = 0L
    private class Refused(val result: ProfileResult) : Exception("Profile operation refused")

    override suspend fun initialize() = withContext(Dispatchers.IO) { initialization.withLock {
        pin.initialize()
        if (observer?.isActive != true || state.value.failed) {
            val ticket = ++observation
            observer?.cancel();mutable.update { it.copy(ready = false,failed = false,storageFailure = null) }
            observer = scope.launch {
                try { store.data.collect { prefs ->
                    val next = decode(prefs)
                    mutable.update { old -> if (ticket != observation || old.failed) old
                        else old.copy(ready = true,storageFailure = null,catalog = next,
                            epoch = old.epoch + if (!old.ready || old.catalog.activeId != next.activeId) 1 else 0) }
                } } catch (error: CancellationException) { throw error }
                catch (error: Exception) { storageFailed(error,ticket) }
            }
        }
        state.first { it.ready }
        if (!state.value.failed) scope.launch { mutations.withLock {
            try { AppDataGate.write { clean(decode(store.data.first())) } }
            catch (error: CancellationException) { throw error }
            catch (error: SecureStorageException) { storageFailed(error) }
            catch (_: Exception) { }
        } }
    };Unit }

    override suspend fun create(name: String, kind: ProfileKind) = mutate(admin = true) { before ->
        val title = name.trim();requireName(title)
        if (before.entries.size >= UserProfiles.LIMIT) throw Refused(ProfileResult.LIMIT)
        if (kind == ProfileKind.CHILD && !pin.state.value.configured) throw Refused(ProfileResult.PIN_SETUP_REQUIRED)
        if (before.entries.any { it.name.equals(title,ignoreCase = true) }) throw Refused(ProfileResult.INVALID)
        val current = before.active
        val inherited = if (current.id == UserProfiles.DEFAULT_ID) seed()
            else requireNotNull(current.home) to requireNotNull(current.player)
        val id = generateSequence { UUID.randomUUID().toString() }.first { candidate ->
            candidate !in before.retired && before.entries.none { it.id == candidate }
        }
        before.copy(entries = before.entries + UserProfile(id,title,kind,
            inherited.first.copy(profileId = id),inherited.second.copy(profileId = id),
            rules = if (kind == ProfileKind.CHILD) UserProfiles.childRules() else ParentalRules()))
    }
    override suspend fun rename(id: String, name: String) = mutate(admin = true) { before ->
        val title = name.trim();requireName(title)
        if (before.entries.none { it.id == id }) throw Refused(ProfileResult.NOT_FOUND)
        if (before.entries.any { it.id != id && it.name.lowercase(Locale.ROOT) == title.lowercase(Locale.ROOT) }) throw Refused(ProfileResult.INVALID)
        before.copy(entries = before.entries.map { if (it.id == id) it.copy(name = title) else it })
    }
    override suspend fun remove(id: String) = mutate(admin = true) { before ->
        if (id == UserProfiles.DEFAULT_ID || id == before.activeId) throw Refused(ProfileResult.ACTIVE_PROFILE)
        if (before.entries.none { it.id == id }) throw Refused(ProfileResult.NOT_FOUND)
        before.copy(entries = before.entries.filterNot { it.id == id },retired = before.retired + id)
    }
    override suspend fun select(id: String): ProfileResult = withContext(Dispatchers.IO) { mutations.withLock {
        try { AppDataGate.write {
            if (!state.value.ready || state.value.failed) throw Refused(ProfileResult.STORAGE)
            val before = decode(store.data.first())
            val target = before.entries.firstOrNull { it.id == id } ?: throw Refused(ProfileResult.NOT_FOUND)
            if (id == before.activeId) return@write ProfileResult.OK
            val access = pin.state.value
            if (!access.ready || access.storageError) throw Refused(ProfileResult.STORAGE)
            if (target.kind == ProfileKind.CHILD && !access.configured) throw Refused(ProfileResult.PIN_SETUP_REQUIRED)
            if (access.configured && (before.active.kind == ProfileKind.CHILD || target.kind == ProfileKind.ADULT) && !access.unlocked)
                throw Refused(ProfileResult.PIN_REQUIRED)
            withContext(NonCancellable) {
                mutable.update { it.copy(switching = true,epoch = it.epoch + 1) }
                try {
                    if (target.kind == ProfileKind.ADULT && access.configured) {
                        if (!pin.rotateSession(access.epoch)) throw Refused(ProfileResult.PIN_REQUIRED)
                    } else pin.lock()
                    persist(before.copy(activeId = id,revision = before.revision + 1))
                    ProfileResult.OK
                } finally { mutable.update { it.copy(switching = false,epoch = it.epoch + 1) } }
            }
        } } catch (error: CancellationException) { throw error }
        catch (error: Refused) { error.result }
        catch (error: Exception) { storageFailed(error);ProfileResult.STORAGE }
    } }
    override suspend fun saveRules(id: String, value: ParentalRules) = mutate(admin = true) { before ->
        if (before.activeId != id) throw Refused(ProfileResult.DENIED)
        before.copy(entries = before.entries.map { row -> if (row.id != id) row else row.copy(rules =
            value.copy(sections = if (row.kind == ProfileKind.CHILD) value.sections + ParentalSection.SETTINGS else value.sections).validate()) })
    }
    override suspend fun saveHome(id: String, value: HomeAppearance) = savePersonal(id) { it.copy(home = value.normalized().copy(profileId = id)) }
    override suspend fun savePlayer(id: String, value: PlayerAppearance) = savePersonal(id) { it.copy(player = value.normalized().copy(profileId = id)) }
    override suspend fun saveStartup(id: String, value: Boolean) = savePersonal(id) { it.copy(resumeAtStartup = value) }
    private suspend fun savePersonal(id: String, change: (UserProfile) -> UserProfile) {
        val result = mutate(admin = false) { before ->
            if (id == UserProfiles.DEFAULT_ID || before.entries.none { it.id == id }) throw Refused(ProfileResult.NOT_FOUND)
            before.copy(entries = before.entries.map { if (it.id == id) change(it) else it })
        }
        if (result != ProfileResult.OK) throw IOException("Profile preferences unavailable")
    }
    private suspend fun mutate(admin: Boolean, change: suspend (UserProfiles) -> UserProfiles): ProfileResult = withContext(Dispatchers.IO) {
        mutations.withLock {
            try { AppDataGate.write {
                if (!state.value.ready || state.value.failed) throw Refused(ProfileResult.STORAGE)
                val access = pin.state.value
                if (admin && access.configured && !access.unlocked) throw Refused(ProfileResult.PIN_REQUIRED)
                if (admin && (!access.ready || access.storageError)) throw Refused(ProfileResult.STORAGE)
                val before = decode(store.data.first())
                val next = change(before).copy(revision = before.revision + 1).validate()
                if (admin && (pin.state.value.epoch != access.epoch || (access.configured && !pin.state.value.unlocked)))
                    throw Refused(ProfileResult.PIN_REQUIRED)
                withContext(NonCancellable) {
                    persist(next)
                    clean(next)
                }
                ProfileResult.OK
            } } catch (error: CancellationException) { throw error }
            catch (error: Refused) { error.result }
            catch (_: IllegalArgumentException) { ProfileResult.INVALID }
            catch (error: Exception) { storageFailed(error);ProfileResult.STORAGE }
        }
    }
    private suspend fun clean(catalog: UserProfiles) {
        val removed = mutableSetOf<String>()
        for (id in catalog.retired) {
            try { purge(id);removed.add(id) } catch (error: CancellationException) { throw error } catch (_: Exception) { }
        }
        if (removed.isNotEmpty()) persist(catalog.copy(retired = catalog.retired - removed,revision = catalog.revision + 1))
    }
    private suspend fun persist(value: UserProfiles) {
        val plain = ProfileCodec.encode(value)
        store.updateData { prefs ->
            val encoded = if (prefs[PROFILE_RECORD] == null) cipher.encryptNew(plain) else cipher.encrypt(plain)
            prefs.toMutablePreferences().apply { this[PROFILE_RECORD] = encoded }
        }
        val seen = withTimeout(10_000) { state.first { it.failed || it.catalog == value } }
        if (seen.failed) throw IOException("Profile record could not be read")
    }
    private fun decode(prefs: Preferences): UserProfiles {
        val plain = prefs[PROFILE_RECORD]?.let(cipher::decrypt)
        return decodeSecureRecord { ProfileCodec.decode(plain) }
    }
    private fun storageFailed(error: Exception, ticket: Long? = null) { mutable.update {
        if (ticket != null && ticket != observation) it
        else it.copy(ready = true,failed = true,switching = false,
            storageFailure = it.storageFailure ?: storageFailure(error),epoch = it.epoch + 1)
    } }
    private fun requireName(value: String) { require(value.isNotBlank() && value.length <= 32 && value.none(Char::isISOControl)) }
}
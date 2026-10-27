package com.yourapp.iptv.data.profile

import com.yourapp.iptv.domain.parental.*
import com.yourapp.iptv.domain.profile.*
import com.yourapp.iptv.domain.repository.ProfileRepository
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.*

/** The synchronous value getter is essential: repository guards never wait for a UI collector. */
internal class ProfileParentalControl(private val device: ParentalControl, private val profiles: ProfileRepository) : ParentalControl {
    override val canDisable get() = profiles.state.value.catalog.entries.none { it.kind == ProfileKind.CHILD }
    override val childProfile get() = profiles.state.value.catalog.active.kind == ProfileKind.CHILD
    override val state: StateFlow<ParentalState> = object : StateFlow<ParentalState> {
        override val value get() = project()
        override val replayCache get() = listOf(value)
        @OptIn(InternalCoroutinesApi::class)
        override suspend fun collect(collector: FlowCollector<ParentalState>): Nothing {
            combine(device.state,profiles.state) { _, _ -> project() }.distinctUntilChanged().collect { collector.emit(it) }
            error("Profile protection stream ended")
        }
    }
    private fun project(): ParentalState {
        val before = profiles.state.value
        val raw = device.state.value
        val people = profiles.state.value;val current = people.catalog.active
        // A device grant read during a profile transition is never a grant for the new identity.
        val changing = before !== people || people.switching
        val rules = if (current.id == UserProfiles.DEFAULT_ID) raw.rules else current.rules
        return raw.copy(profileId = current.id,unlocked = raw.unlocked && !changing,ready = raw.ready && people.ready && !changing,
            storageError = raw.storageError || people.failed || (current.kind == ProfileKind.CHILD && !raw.configured),
            rules = if (current.kind == ProfileKind.CHILD) rules.copy(sections = rules.sections + ParentalSection.SETTINGS) else rules,
            profileLocked = people.catalog.entries.size > 1 && current.kind == ProfileKind.ADULT && raw.configured,
            epoch = raw.epoch + people.epoch)
    }
    override suspend fun initialize() { device.initialize();profiles.initialize() }
    override fun foreground() = device.foreground()
    override fun background() = device.background()
    override fun lock() = device.lock()
    override fun remainingSeconds() = device.remainingSeconds()
    override fun rotateSession(expectedEpoch: Long) = state.value.epoch == expectedEpoch && device.rotateSession(device.state.value.epoch)
    override suspend fun unlock(pin: CharArray) = device.unlock(pin)
    override suspend fun create(pin: CharArray) = device.create(pin)
    override suspend fun changePin(old: CharArray, replacement: CharArray?): ParentalResult {
        if (replacement != null) return device.changePin(old,replacement)
        // Serialize disabling with child creation/selection; the raw PIN store never reenters this gate.
        return try { com.yourapp.iptv.data.backup.AppDataGate.write {
            if (profiles.state.value.catalog.entries.any { it.kind == ProfileKind.CHILD }) ParentalResult.DENIED
            else device.changePin(old,null)
        } } finally { old.fill('\u0000') }
    }
    override suspend fun saveRules(rules: ParentalRules): ParentalResult {
        val id = profiles.state.value.catalog.activeId
        if (id == UserProfiles.DEFAULT_ID) return device.saveRules(rules)
        val result = profiles.saveRules(id,rules)
        if (result == ProfileResult.OK) { device.lock();return ParentalResult.OK }
        return when (result) {
            ProfileResult.PIN_REQUIRED,ProfileResult.DENIED -> ParentalResult.DENIED
            ProfileResult.INVALID -> ParentalResult.INVALID_RULES
            else -> ParentalResult.STORAGE_ERROR
        }
    }
}
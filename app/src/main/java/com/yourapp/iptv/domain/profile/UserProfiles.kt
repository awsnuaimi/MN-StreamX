package com.yourapp.iptv.domain.profile

import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.parental.ParentalRules
import com.yourapp.iptv.domain.parental.ParentalSection
import java.util.UUID

enum class ProfileKind { ADULT, CHILD }

data class UserProfile(val id: String, val name: String, val kind: ProfileKind = ProfileKind.ADULT,
    val home: HomeAppearance? = null, val player: PlayerAppearance? = null,
    val resumeAtStartup: Boolean = false, val rules: ParentalRules = ParentalRules())

data class UserProfiles(val entries: List<UserProfile> = listOf(UserProfile(DEFAULT_ID,"")),
    val activeId: String = DEFAULT_ID, val revision: Long = 0, val retired: Set<String> = emptySet()) {
    val active: UserProfile get() = entries.first { it.id == activeId }
    fun validate(): UserProfiles {
        require(entries.size in 1..LIMIT && entries.map { it.id }.distinct().size == entries.size)
        require(entries.any { it.id == DEFAULT_ID && it.kind == ProfileKind.ADULT })
        require(entries.any { it.id == activeId } && revision >= 0 && revision < Long.MAX_VALUE)
        require(retired.size <= 64 && retired.all { validId(it) && it != DEFAULT_ID && entries.none { row -> row.id == it } })
        entries.forEach { profile ->
            require(validId(profile.id))
            require(profile.name.length <= 32 && profile.name == profile.name.trim() && profile.name.none(Char::isISOControl))
            require(profile.id == DEFAULT_ID || profile.name.isNotBlank())
            require(profile.id == DEFAULT_ID || (profile.home != null && profile.player != null))
            profile.rules.validate()
        }
        return this
    }
    companion object {
        const val DEFAULT_ID = "default"
        const val LIMIT = 8
        fun validId(value: String) = value == DEFAULT_ID || try { UUID.fromString(value).toString() == value }
            catch (_: IllegalArgumentException) { false }
        fun childRules() = ParentalRules(ParentalSection.entries.toSet())
    }
}

data class ProfilesState(val ready: Boolean = false, val failed: Boolean = false,
    val switching: Boolean = false, val catalog: UserProfiles = UserProfiles(), val epoch: Long = 0)

enum class ProfileResult { OK, PIN_REQUIRED, PIN_SETUP_REQUIRED, LIMIT, INVALID, NOT_FOUND, ACTIVE_PROFILE, STORAGE, DENIED }

/** Explicit personal namespaces leave channel-cache provenance and legacy default data unchanged. */
object ProfileScope {
    private val raw = Regex("[a-f0-9]{64}")
    fun of(account: AppSettings) = key(account.profileId,PlaylistCacheKey.of(account))
    fun key(profile: String, source: String): String {
        require(UserProfiles.validId(profile) && raw.matches(source))
        return if (profile == UserProfiles.DEFAULT_ID) source else "p1:$profile:$source"
    }
    fun profile(value: String): String? {
        if (raw.matches(value)) return UserProfiles.DEFAULT_ID
        val parts = value.split(':')
        return parts.takeIf { it.size == 3 && it[0] == "p1" && it[1] != UserProfiles.DEFAULT_ID &&
            UserProfiles.validId(it[1]) && raw.matches(it[2]) }?.get(1)
    }
    fun source(value: String): String? = if (profile(value) == null) null else value.substringAfterLast(':')
    fun valid(value: String) = profile(value) != null
    fun belongs(value: String, state: ProfilesState) = state.ready && !state.failed &&
        state.catalog.entries.any { it.id == profile(value) }
}
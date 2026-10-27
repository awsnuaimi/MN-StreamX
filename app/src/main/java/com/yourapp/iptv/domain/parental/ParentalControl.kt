package com.yourapp.iptv.domain.parental

import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.model.AppSettings
import com.yourapp.iptv.domain.model.VodKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ParentalSection { LIVE, MOVIES, SERIES, SETTINGS }

data class ParentalRules(
    val sections: Set<ParentalSection> = setOf(ParentalSection.SETTINGS),
    val groups: Map<String, Set<String>> = emptyMap()
) {
    fun validate(): ParentalRules {
        require(groups.size <= 16 && groups.values.sumOf { it.size } <= 512)
        require(groups.keys.all { it.matches(Regex("[a-f0-9]{64}")) })
        require(groups.values.all { names -> names.all { it.length <= 512 } })
        return this
    }
}

data class ParentalState(
    val ready: Boolean = false, val configured: Boolean = false,
    val unlocked: Boolean = false, val storageError: Boolean = false,
    val rules: ParentalRules = ParentalRules(), val epoch: Long = 0
) {
    val restricted: Boolean get() = configured && !unlocked
    val contentRestricted: Boolean get() = restricted &&
        (rules.sections.any { it != ParentalSection.SETTINGS } || rules.groups.values.any { it.isNotEmpty() })
    fun allows(section: ParentalSection): Boolean = ready && !storageError &&
        (!restricted || section !in rules.sections)
    fun allows(kind: VodKind) = allows(if (kind == VodKind.MOVIE) ParentalSection.MOVIES else ParentalSection.SERIES)
    fun allows(source: String, channel: Channel): Boolean = allows(ParentalSection.LIVE) &&
        (!restricted || channel.group.orEmpty() !in rules.groups[source].orEmpty())
}

enum class ParentalResult { OK, WRONG_PIN, INVALID_PIN, INVALID_RULES, WAIT, STORAGE_ERROR, DENIED }

interface ParentalControl {
    val state: StateFlow<ParentalState>
    suspend fun initialize()
    fun foreground()
    fun background()
    fun lock()
    fun remainingSeconds(): Long
    suspend fun unlock(pin: CharArray): ParentalResult
    suspend fun create(pin: CharArray): ParentalResult
    suspend fun changePin(old: CharArray, replacement: CharArray?): ParentalResult
    suspend fun saveRules(rules: ParentalRules): ParentalResult

    companion object {
        val Disabled = object : ParentalControl {
            override val state = MutableStateFlow(ParentalState(ready = true))
            override suspend fun initialize() = Unit
            override fun foreground() = Unit
            override fun background() = Unit
            override fun lock() = Unit
            override fun remainingSeconds() = 0L
            override suspend fun unlock(pin: CharArray) = ParentalResult.DENIED.also { pin.fill('\u0000') }
            override suspend fun create(pin: CharArray) = unlock(pin)
            override suspend fun changePin(old: CharArray, replacement: CharArray?) = unlock(old).also { replacement?.fill('\u0000') }
            override suspend fun saveRules(rules: ParentalRules) = ParentalResult.DENIED
        }
    }
}

class ParentalDenied : IllegalStateException("Parental access is locked")

/** Administrative catalog access is a domain contract, independent of Android storage. */
interface ParentalCatalog {
    suspend fun groups(source: AppSettings): List<String>
}
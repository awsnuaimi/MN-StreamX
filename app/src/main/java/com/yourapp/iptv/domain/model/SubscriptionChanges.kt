package com.yourapp.iptv.domain.model

import java.util.UUID

internal object SubscriptionChanges {
    fun save(previous: Subscriptions, id: String?, name: String, settings: AppSettings): Subscriptions {
        val title = name.trim()
        if (title.length > Subscriptions.NAME_LIMIT || (title.isBlank() && id != Subscriptions.DEFAULT_ID))
            fail(SubscriptionProblem.NAME)
        if (!settings.hasSelectedSource() || listOf(settings.m3uUrl, settings.xtreamServer,
                settings.xtreamUsername, settings.xtreamPassword, settings.epgUrl).any { it.length > 8192 })
            fail(SubscriptionProblem.INVALID)
        if (id != null && previous.entries.none { it.id == id }) fail(SubscriptionProblem.NOT_FOUND)
        if (id == null && previous.entries.size >= Subscriptions.LIMIT) fail(SubscriptionProblem.LIMIT)
        val fingerprint = PlaylistCacheKey.of(settings)
        if (previous.entries.any { it.id != id && it.settings.hasSelectedSource() && PlaylistCacheKey.of(it.settings) == fingerprint })
            fail(SubscriptionProblem.DUPLICATE)
        val value = Subscription(id ?: UUID.randomUUID().toString(), title, settings)
        return previous.copy(entries = if (id == null) previous.entries + value else
            previous.entries.map { if (it.id == id) value else it }, activeId = value.id)
    }

    fun activate(previous: Subscriptions, id: String): Subscriptions {
        if (previous.entries.none { it.id == id && it.settings.hasSelectedSource() }) fail(SubscriptionProblem.NOT_FOUND)
        return previous.copy(activeId = id)
    }

    fun remove(previous: Subscriptions, id: String): Subscriptions {
        if (id == previous.activeId) fail(SubscriptionProblem.ACTIVE_DELETE)
        if (previous.entries.none { it.id == id }) fail(SubscriptionProblem.NOT_FOUND)
        return previous.copy(entries = previous.entries.filterNot { it.id == id })
    }

    private fun fail(problem: SubscriptionProblem): Nothing = throw SubscriptionException(problem)
}
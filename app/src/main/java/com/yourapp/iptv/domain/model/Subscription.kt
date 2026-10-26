package com.yourapp.iptv.domain.model

/** Subscriptions are provider accounts, not user profiles sharing the same provider credentials. */
data class Subscription(val id: String, val name: String, val settings: AppSettings) {
    override fun toString() = "Subscription(id=$id, credentials=redacted)"
}

data class Subscriptions(
    val entries: List<Subscription>,
    val activeId: String,
    val legacySourceKey: String? = null
) {
    val active: Subscription get() = entries.first { it.id == activeId }
    companion object {
        const val DEFAULT_ID = "default"
        const val LIMIT = 16
        const val NAME_LIMIT = 40
    }
}

enum class SubscriptionProblem { NAME, DUPLICATE, LIMIT, NOT_FOUND, ACTIVE_DELETE, INVALID }
class SubscriptionException(val problem: SubscriptionProblem) : Exception(problem.name)
package com.yourapp.iptv.domain.model

import java.security.MessageDigest

/** Bounded, local evidence from explicit watch actions. No focus event trains this model. */
data class AdaptiveLiveChoice(val family: String, val score: Int)

data class AdaptiveLiveNavigation(
    val enabled: Boolean = true,
    // Insertion order is most recently used context first; choices use the same recency order.
    val contexts: Map<String, List<AdaptiveLiveChoice>> = emptyMap(),
    // Every observation/control advances the revision, including recency-only changes at score saturation.
    val epoch: Long = 0
) {
    fun validate(): AdaptiveLiveNavigation {
        require(epoch >= 0 && contexts.size <= MAX_CONTEXTS)
        contexts.forEach { (context, choices) ->
            require(digest.matches(context) && choices.isNotEmpty() && choices.size <= MAX_CHOICES)
            require(choices.map { it.family }.distinct().size == choices.size)
            require(choices.all { digest.matches(it.family) && it.score in 1..MAX_SCORE })
        }
        return this
    }

    fun record(context: String, family: String): AdaptiveLiveNavigation {
        require(digest.matches(context) && digest.matches(family))
        if (!enabled) return this
        val previous = contexts[context].orEmpty()
        val score = ((previous.firstOrNull { it.family == family }?.score ?: 0) + 2).coerceAtMost(MAX_SCORE)
        val choices = (listOf(AdaptiveLiveChoice(family, score)) + previous.filter { it.family != family }
            .map { it.copy(score = it.score - 1) }.filter { it.score > 0 }).take(MAX_CHOICES)
        val recent = linkedMapOf(context to choices)
        contexts.entries.asSequence().filter { it.key != context }.take(MAX_CONTEXTS - 1)
            .forEach { recent[it.key] = it.value }
        return copy(contexts = recent, epoch = nextEpoch()).validate()
    }

    /** Eligibility is resolved against the current visible projection, including parental rules. */
    fun entry(context: String?, eligible: (String) -> Boolean): String? {
        if (!enabled || context == null) return null
        return contexts[context].orEmpty().asSequence().filter { it.score >= MIN_SCORE && eligible(it.family) }
            .sortedByDescending { it.score }.firstOrNull()?.family
    }

    private fun nextEpoch() = if (epoch == Long.MAX_VALUE) 0 else epoch + 1
    fun clear() = copy(contexts = emptyMap(), epoch = nextEpoch())
    fun withEnabled(value: Boolean) = if (value == enabled) this else copy(enabled = value, epoch = nextEpoch())
    override fun toString() = "AdaptiveLiveNavigation(enabled=$enabled, contexts=${contexts.size})"

    companion object {
        const val MAX_CONTEXTS = 24
        const val MAX_CHOICES = 8
        const val MAX_SCORE = 12
        const val MIN_SCORE = 4
        private val digest = Regex("[a-f0-9]{64}")

        /** Search is temporary and never learned. Length prefixes distinguish arbitrary provider names. */
        fun context(group: String?, favorites: Boolean, query: String): String? {
            if (query.isNotBlank()) return null
            val source = "live-entry:1:$favorites:${group?.length ?: -1}:${group.orEmpty()}"
            return MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
    }
}
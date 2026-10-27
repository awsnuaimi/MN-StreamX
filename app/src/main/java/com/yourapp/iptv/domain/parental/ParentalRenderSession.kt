package com.yourapp.iptv.domain.parental

data class ParentalRenderFrame(val state: ParentalState, val owner: Long, val hidden: Boolean)

/** A previously authorized document/installer handoff may finish behind an opaque, input-blocking lock.
 * Its navigation owner is retained until reauthentication or explicit cancellation, never made public. */
class ParentalRenderSession {
    private var rendered: ParentalState? = null
    private var serial = 0L
    private var reset = 0L
    private var held = false
    fun frame(actual: ParentalState, external: Boolean, revision: Long): ParentalRenderFrame {
        val previous = rendered
        if (previous == null) { rendered = actual; reset = revision }
        else if (reset != revision || previous.configured != actual.configured || previous.rules != actual.rules || actual.storageError) {
            rendered = actual; serial++; held = false; reset = revision
        } else {
            if (external && previous.configured && previous.unlocked) held = true
            if (held) {
                if (actual.unlocked) { rendered = actual; if (!external) held = false }
            } else if (previous.epoch != actual.epoch) {
                // Without protection, ordinary background/resume preserves navigation.
                if (actual.configured) serial++
                rendered = actual
            }
        }
        return ParentalRenderFrame(requireNotNull(rendered),serial,held && !actual.unlocked)
    }
}
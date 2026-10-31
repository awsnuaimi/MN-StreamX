package com.yourapp.iptv.data.datastore

import com.yourapp.iptv.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class AdaptiveNavigationCodecTest {
    private val context = "a".repeat(64)
    private val family = "b".repeat(64)
    private val learned = LiveTvPreferences(adaptive = AdaptiveLiveNavigation().record(context, family)
        .record(context, family).record("c".repeat(64), family).withEnabled(false))

    @Test fun oldCanonicalRecordsKeepTheirBytesAndDefaultToEmptyEnabledLearning() {
        val old = """{"version":1,"sort":"PROVIDER","channels":[],"groups":[],"folders":[],"preferred":{}}"""
        assertEquals(LiveTvPreferences(), LiveTvPreferencesCodec.decode(old))
        assertEquals(old, LiveTvPreferencesCodec.encode(LiveTvPreferencesCodec.decode(old)))
    }
    @Test fun evidenceRecencyEpochAndDisabledStateSurviveRoundTrip() {
        val text = LiveTvPreferencesCodec.encode(learned)
        val decoded = LiveTvPreferencesCodec.decode(text)
        assertEquals(learned, decoded)
        assertEquals(learned.adaptive.contexts.keys.toList(), decoded.adaptive.contexts.keys.toList())
        assertEquals(text, LiveTvPreferencesCodec.encode(decoded))
        val reset = learned.copy(adaptive = learned.adaptive.clear())
        assertEquals(reset, LiveTvPreferencesCodec.decode(LiveTvPreferencesCodec.encode(reset)))
    }
    @Test fun wrongTypesInvalidScoresDuplicateContextsAndFamiliesFailClosed() {
        val valid = LiveTvPreferencesCodec.encode(learned)
        val row = """{"context":"$context","choices":[{"family":"$family","score":4}]}"""
        val prefix = """{"version":1,"sort":"PROVIDER","adaptive":{"enabled":true,"contexts":[$row,$row]}}"""
        val duplicateChoice = """{"version":1,"sort":"PROVIDER","adaptive":{"enabled":true,"contexts":[{"context":"$context","choices":[{"family":"$family","score":4},{"family":"$family","score":4}]}]}}"""
        for (bad in listOf(valid.replace("\"enabled\":false", "\"enabled\":\"false\""),
            valid.replace("\"score\":4", "\"score\":4.0"), valid.replace("\"score\":4", "\"score\":13"),
            valid.replace("\"score\":4", "\"score\":\"4\""), valid.replace("\"epoch\":${learned.adaptive.epoch}", "\"epoch\":-1"),
            valid.replace(context, "raw group"), prefix, duplicateChoice)) {
            assertThrows(IOException::class.java) { LiveTvPreferencesCodec.decode(bad) }
        }
    }
}
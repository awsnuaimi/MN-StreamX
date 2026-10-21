package com.yourapp.iptv.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.yourapp.iptv.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class CaptionDataStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun defaultIsOffAndWrongTypedValuesFailClosed() {
        assertEquals(CaptionMode.OFF, CaptionCodec.decode(emptyPreferences()).mode)
        val bad = mutablePreferencesOf(booleanPreferencesKey("mode") to true, intPreferencesKey("source") to 3)
        assertEquals(CaptionSettings(), CaptionCodec.decode(bad))
    }
    @Test fun codecRetainsUnrelatedKeysAndNormalizesUnsupportedSourceHints() {
        val values = mutablePreferencesOf(stringPreferencesKey("future") to "keep")
        CaptionCodec.encode(values, CaptionSettings(CaptionMode.GENERATED, CaptionLanguage.HINDI, "zz").normalized())
        assertEquals("keep", values[stringPreferencesKey("future")])
        assertEquals("auto", CaptionCodec.decode(values).source)
        assertEquals(CaptionLanguage.HINDI, CaptionCodec.decode(values).target)
    }
    @Test fun actualSettingsSurviveClosingAndReopeningTheStore() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val file = temporary.root.resolve("captions.preferences_pb")
        val wanted = CaptionSettings(CaptionMode.GENERATED, CaptionLanguage.POLISH, "de", CaptionTextSize.LARGE, false)
        var scope = CoroutineScope(SupervisorJob() + dispatcher)
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope) { file }
            CaptionDataStore(store).save(wanted); assertTrue(file.length() > 0)
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
        scope = CoroutineScope(SupervisorJob() + dispatcher)
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope) { file }
            assertEquals(wanted, CaptionDataStore(store).observe().first())
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
    }
}
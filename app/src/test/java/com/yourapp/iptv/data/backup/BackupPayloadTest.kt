package com.yourapp.iptv.data.backup

import com.google.gson.JsonParser
import com.yourapp.iptv.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class BackupPayloadTest {
    @Test fun portableBackupsPreserveTheOptionalSeriesParentForContinuedEpisodes() {
        val value = backupPayload()
        val source = PlaylistCacheKey.of(value.subscriptions.active.settings)
        val entry = VodProgressEntry(VodKind.SERIES,"10","Episode","mp4",60000,200000,100,"55")
        val stored = com.yourapp.iptv.data.datastore.VodProgressCodec.encode(listOf(entry))
        val updated = value.copy(areas = value.areas + (BackupStore.VOD to mapOf("vod_$source" to BackupValue(BackupValueType.STRING,stored))))
        val decoded = BackupPayloadCodec.decode(BackupPayloadCodec.encode(updated))
        assertEquals(listOf(entry),com.yourapp.iptv.data.datastore.VodProgressCodec.decode(decoded.areas.getValue(BackupStore.VOD).getValue("vod_$source").text))
    }
    @Test fun portablePayloadRoundTripsAndContainsOnlyExplicitlySupportedPreferences() {
        val value = backupPayload()
        assertEquals(value, BackupPayloadCodec.decode(BackupPayloadCodec.encode(value)))
        val areas = value.areas + (BackupStore.CAPTIONS to (value.areas.getValue(BackupStore.CAPTIONS) +
            ("cloud_api_key" to BackupValue(BackupValueType.STRING, "Synthetic secret"))))
        assertTrue(runCatching { BackupPayloadCodec.encode(value.copy(areas = areas)) }.isFailure)
    }
    @Test fun favoriteProvenanceAndDuplicateSourcesAreValidatedBeforeImport() {
        val value = backupPayload()
        assertTrue(runCatching { BackupPayloadCodec.encode(value.copy(favorites = listOf(BackupFavorite("b".repeat(64), "42")))) }.isFailure)
        val same = value.subscriptions.entries.single().copy(id = "12345678-1234-1234-1234-123456789abc", name = "Duplicate")
        assertTrue(runCatching { BackupPayloadCodec.encode(value.copy(subscriptions = value.subscriptions.copy(entries = value.subscriptions.entries + same))) }.isFailure)
    }
    @Test fun corruptHistoryCannotBeSilentlyImportedAsAnEmptyHistory() {
        val value = backupPayload()
        val key = PlaylistCacheKey.of(value.subscriptions.active.settings)
        val invalid = BackupValue(BackupValueType.STRING, """{"version":1,"rows":[{"id":"42","at":-1}]}""")
        assertTrue(runCatching { BackupPayloadCodec.encode(value.copy(areas = value.areas + (BackupStore.HISTORY to mapOf("history_$key" to invalid)))) }.isFailure)
    }
    @Test fun hostileNestingAndUnsupportedSchemaAreRejectedWithoutTraversingTheAppStores() {
        val nested = ("[".repeat(100) + "0" + "]".repeat(100)).toByteArray()
        assertTrue(runCatching { BackupPayloadCodec.decode(nested) }.exceptionOrNull() is BackupException)
        val root = JsonParser.parseString(String(BackupPayloadCodec.encode(backupPayload()))).asJsonObject
        root.addProperty("version", 999)
        assertEquals(BackupProblem.UNSUPPORTED, (runCatching { BackupPayloadCodec.decode(root.toString().toByteArray()) }.exceptionOrNull() as BackupException).problem)
    }
    @Test fun foreignStoreNamesAndScopedRecordsCannotBeInjected() {
        val value = backupPayload()
        val wrong = value.areas + (BackupStore.SEARCH to mapOf("search_${"b".repeat(64)}" to BackupValue(BackupValueType.STRING,
            """{"version":1,"queries":[]}""")))
        assertTrue(runCatching { BackupPayloadCodec.encode(value.copy(areas = wrong)) }.isFailure)
        val raw = String(BackupPayloadCodec.encode(value)).replace("\"HOME\"", "\"UNKNOWN_STORE\"")
        assertEquals(BackupProblem.INVALID, (runCatching { BackupPayloadCodec.decode(raw.toByteArray()) }.exceptionOrNull() as BackupException).problem)
    }
}
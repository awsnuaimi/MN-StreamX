package mnstudio.apps.mnstreamx.data.datastore

import mnstudio.apps.mnstreamx.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class VodEpisodeHistoryCodecTest {
    private val incomplete = VodProgressEntry(VodKind.SERIES,"22","Episode","mp4",30_000,120_000,20,"800")
    private val complete = VodCompletedEpisode("21","800",30)

    @Test fun legacyRecordsStillUseV1AndDecodeWithoutCompletion() {
        val encoded = VodProgressCodec.encode(listOf(incomplete))
        assertTrue(encoded.contains("\"version\":1"))
        assertEquals(listOf(incomplete),VodProgressCodec.decodeState(encoded).entries)
        assertTrue(VodProgressCodec.decodeState(encoded).completed.isEmpty())
    }

    @Test fun version2KeepsBookmarksAndCompletedEpisodesWithoutSensitiveData() {
        val history = VodProgressSnapshot(listOf(incomplete),listOf(complete))
        val encoded = VodProgressCodec.encodeState(history)
        assertTrue(encoded.contains("\"version\":2"))
        assertEquals(history,VodProgressCodec.decodeState(encoded))
        assertEquals(listOf(incomplete),VodProgressCodec.decode(encoded))
        assertFalse(encoded.contains("http"));assertFalse(encoded.contains("password"))
    }

    @Test fun corruptedV2AndMismatchedIdentitiesAreRejected() {
        assertEquals(VodProgressSnapshot(), VodProgressCodec.decodeState("{\"version\":2,\"entries\":[]}"))
        assertEquals(VodProgressSnapshot(),VodProgressCodec.decodeState(
            VodProgressCodec.encodeState(VodProgressSnapshot(emptyList(),listOf(complete)))
                .replace("\"series\":\"800\"","\"series\":\"../800\"")))
        assertTrue(runCatching { VodProgressCodec.encodeState(VodProgressSnapshot(
            listOf(incomplete),listOf(complete.copy(id = "22")))) }.isFailure)
    }
}
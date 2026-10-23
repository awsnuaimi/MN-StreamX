package com.yourapp.iptv.data.caption

import com.yourapp.iptv.domain.model.CaptionLanguage
import org.junit.Assert.*
import org.junit.Test

class CaptionTranslationCatalogTest {
    @Test fun everyNonEnglishTargetHasOnePinnedBilingualPackIncludingItsTargetTokenizer() {
        assertEquals(CaptionLanguage.entries.map { it.tag }.toSet() - "en", CaptionTranslationCatalog.bundles.keys)
        for ((tag, bundle) in CaptionTranslationCatalog.bundles) {
            assertTrue(bundle.id.contains("en-$tag"))
            assertTrue(bundle.baseUrl.substringAfterLast('/').matches(Regex("[a-f0-9]{40}")))
            assertEquals(bundle.assets.size, bundle.assets.map { it.path }.distinct().size)
            assertTrue(bundle.assets.map { it.path }.containsAll(listOf("source.spm", "target.spm", "vocab.json", "config.json")))
            assertTrue(bundle.assets.all { it.size > 0 && it.sha256.matches(Regex("[a-f0-9]{64}")) })
            assertNotNull(CaptionTranslationCatalog.encoder(bundle))
            assertNotNull(CaptionTranslationCatalog.decoder(bundle))
        }
    }
    @Test fun separateAndMergedDecoderExportsAreSelectedExplicitly() {
        assertNotNull(CaptionTranslationCatalog.cachedDecoder(CaptionTranslationCatalog.forTarget("pl")))
        for (tag in listOf("ar", "de", "hi")) {
            val bundle = CaptionTranslationCatalog.forTarget(tag)
            assertTrue(CaptionTranslationCatalog.decoder(bundle).path.contains("merged"))
            assertNull(CaptionTranslationCatalog.cachedDecoder(bundle))
        }
    }
    @Test(expected = IllegalArgumentException::class)
    fun unsupportedLanguagesNeverSilentlySelectAnotherVocabulary() {
        CaptionTranslationCatalog.forTarget("xx")
    }
}
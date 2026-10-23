package com.yourapp.iptv.data.caption

import org.junit.Assert.*
import org.junit.Test

class CaptionModelCatalogTest {
    @Test fun identicalLanguagesDoNotAdvertiseAnUnnecessaryTextModelDownload() {
        for (tag in listOf("ar", "en", "de", "pl", "hi")) {
            assertEquals(CaptionModelCatalog.speech.bytes, CaptionModelCatalog.totalBytes(tag, tag))
        }
    }
    @Test fun crossLanguageAndAutomaticModesKeepTheirRequiredTextModels() {
        val speech = CaptionModelCatalog.speech.bytes
        assertEquals(speech + CaptionTranslationCatalog.forTarget("de").bytes, CaptionModelCatalog.totalBytes("de", "ar"))
        assertEquals(speech + CaptionTranslationCatalog.forTarget("ar").bytes, CaptionModelCatalog.totalBytes("ar", "auto"))
        assertEquals(CaptionModelCatalog.speech.bytes, CaptionModelCatalog.totalBytes("en", "ar"))
    }
}
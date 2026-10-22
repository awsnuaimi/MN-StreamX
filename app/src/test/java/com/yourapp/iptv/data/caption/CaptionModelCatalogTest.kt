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
        val both = CaptionModelCatalog.speech.bytes + CaptionModelCatalog.translation.bytes
        assertEquals(both, CaptionModelCatalog.totalBytes("de", "ar"))
        assertEquals(both, CaptionModelCatalog.totalBytes("ar", "auto"))
        assertEquals(CaptionModelCatalog.speech.bytes, CaptionModelCatalog.totalBytes("en", "ar"))
    }
}
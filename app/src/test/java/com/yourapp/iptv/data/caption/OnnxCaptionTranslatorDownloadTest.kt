package com.yourapp.iptv.data.caption

import com.yourapp.iptv.domain.caption.CaptionResourcePolicy
import com.yourapp.iptv.domain.caption.CaptionStorageException
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OnnxCaptionTranslatorDownloadTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun prefetchCanReportLowStorageWithoutEverLoadingTheOnnxNativeRuntime() = runTest {
        val translator = OnnxCaptionTranslator(VerifiedModelFiles(temporary.root, OkHttpClient()) { 0L })
        try {
            translator.download("ar") { }
            fail("Disk shortage ignored")
        } catch (error: CaptionStorageException) {
            assertEquals(0L, error.availableBytes)
            assertEquals(CaptionTranslationCatalog.forTarget("ar").bytes + CaptionResourcePolicy.STORAGE_RESERVE, error.requiredBytes)
        } finally { translator.close() }
    }
    @Test fun englishNeedsNoTextModelDownloadOrOnnxSession() = runTest {
        val translator = OnnxCaptionTranslator(VerifiedModelFiles(temporary.root, OkHttpClient()) { 0L })
        try {
            translator.download("en") { fail("No text download expected") }
            translator.prepare("en") { fail("No text model expected") }
            assertEquals("hello", translator.translate("hello"))
            assertTrue(temporary.root.listFiles().orEmpty().isEmpty())
        } finally { translator.close() }
    }
}
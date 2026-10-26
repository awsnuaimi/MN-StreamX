package com.yourapp.iptv.diagnostics

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.yourapp.iptv.data.diagnostics.DiagnosticReports
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.android.controller.ContentProviderController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34],application = Application::class)
class DiagnosticSharingTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var provider: ContentProviderController<DiagnosticFileProvider>
    @Before fun setup() {
        val info = context.packageManager.getProviderInfo(ComponentName(context,DiagnosticFileProvider::class.java),PackageManager.GET_META_DATA)
        provider = Robolectric.buildContentProvider(DiagnosticFileProvider::class.java).create(info)
    }
    @After fun cleanup() { provider.shutdown() }
    @Suppress("DEPRECATION")
    @Test fun sharingUsesANarrowNonExportedProviderAndTemporaryReadPermissionOnly() {
        val file = DiagnosticReports(context.cacheDir.resolve("diagnostic-reports")).write("Synthetic safe report")
        val chooser = DiagnosticSharing(context).intent(file,"Fehlerdiagnose")
        assertEquals(Intent.ACTION_CHOOSER,chooser.action)
        val intent = requireNotNull(chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT))
        val uri = requireNotNull(intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM))
        assertEquals("${context.packageName}.diagnostics",uri.authority);assertEquals("content",uri.scheme)
        assertEquals("text/plain",intent.type);assertEquals(Intent.ACTION_SEND,intent.action)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(0,intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        assertEquals(uri,intent.clipData!!.getItemAt(0).uri)
        assertEquals("Synthetic safe report",context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() })
        val info = context.packageManager.getProviderInfo(ComponentName(context,DiagnosticFileProvider::class.java),0)
        assertFalse(info.exported);assertTrue(info.grantUriPermissions)
    }
    @Test fun subscriptionsUpdateApksAndArbitraryCacheFilesCannotBeSharedAsReports() {
        for (file in listOf(context.filesDir.resolve("mnstreamx-diagnostics-private.txt"),
            context.cacheDir.resolve("app-updates/mnstreamx-diagnostics-private.txt"),context.cacheDir.resolve("other/mnstreamx-diagnostics-private.txt"))) {
            file.parentFile!!.mkdirs();file.writeText("Private synthetic value")
            try { DiagnosticSharing(context).intent(file,"Test");fail("Shared unrelated data") }
            catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun snapshotCollectionDoesNotRequireAProviderLoginOrOpenAVideo() {
        val snapshot = com.yourapp.iptv.data.diagnostics.AndroidDiagnosticSnapshot(context).read()
        assertEquals(34,snapshot.sdk);assertTrue(snapshot.heapLimit > 0);assertTrue(snapshot.heapUsed >= 0)
    }
}
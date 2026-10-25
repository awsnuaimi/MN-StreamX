package com.yourapp.iptv.update

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.android.controller.ContentProviderController
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class UpdateInstallerTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var file: File
    private lateinit var provider: ContentProviderController<UpdateFileProvider>
    @Before fun setup() {
        // Each Robolectric test has a new cacheDir. Attach the provider for this context,
        // which also resets FileProvider's cached path strategy for the authority.
        val info = context.packageManager.getProviderInfo(
            ComponentName(context, UpdateFileProvider::class.java), PackageManager.GET_META_DATA)
        provider = Robolectric.buildContentProvider(UpdateFileProvider::class.java).create(info)
        val directory = File(context.cacheDir, "app-updates").apply { mkdirs() }
        file = File(directory, "synthetic.apk").apply { writeBytes(byteArrayOf(1, 2, 3)) }
    }
    @After fun cleanup() { file.parentFile?.deleteRecursively(); provider.shutdown() }

    @Test fun usesANonExportedNarrowProviderAndTemporarilyGrantsReadAccess() {
        val info = context.packageManager.getProviderInfo(ComponentName(context, UpdateFileProvider::class.java), 0)
        assertFalse(info.exported)
        assertTrue(info.grantUriPermissions)
        val intent = UpdateInstaller(context).installIntent(file)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("content", intent.data?.scheme)
        assertEquals("${context.packageName}.updates", intent.data?.authority)
        assertEquals("application/vnd.android.package-archive", intent.type)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(intent.data, intent.clipData?.getItemAt(0)?.uri)
        assertArrayEquals(file.readBytes(), context.contentResolver.openInputStream(intent.data!!)!!.use { it.readBytes() })
    }

    @Test fun cannotShareArbitraryPrivateFilesAsAnUpdate() {
        val outside = File(context.filesDir, "synthetic.apk").apply { writeText("private") }
        try {
            try { UpdateInstaller(context).installIntent(outside); fail("Shared another private directory") }
            catch (_: IllegalArgumentException) { }
        } finally { outside.delete() }
    }

    @Test fun installPermissionIsCheckedForThisApplication() {
        val installer = UpdateInstaller(context)
        shadowOf(context.packageManager).setCanRequestPackageInstalls(false)
        assertFalse(installer.allowed())
        shadowOf(context.packageManager).setCanRequestPackageInstalls(true)
        assertTrue(installer.allowed())
        assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, installer.permissionIntent().action)
        assertEquals("package:${context.packageName}", installer.permissionIntent().data.toString())
    }
}
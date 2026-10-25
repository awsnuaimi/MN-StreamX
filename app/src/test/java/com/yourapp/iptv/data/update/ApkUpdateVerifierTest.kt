package com.yourapp.iptv.data.update

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.Signature
import android.content.pm.SigningInfo
import androidx.test.core.app.ApplicationProvider
import com.yourapp.iptv.domain.update.AppUpdate
import com.yourapp.iptv.domain.update.UpdateFailure
import com.yourapp.iptv.domain.update.UpdateIssue
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowBuild
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Synthetic ZIP and PackageManager metadata; no real APK or private signing key is a fixture. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ApkUpdateVerifierTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var file: File
    private lateinit var update: AppUpdate
    private lateinit var candidate: PackageInfo
    @Before fun setup() {
        ShadowBuild.setSupportedAbis(arrayOf("arm64-v8a", "armeabi-v7a"))
        file = File.createTempFile("synthetic-update-", ".apk", context.cacheDir)
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("lib/arm64-v8a/libsynthetic.so")); zip.write(byteArrayOf(1, 2, 3)); zip.closeEntry()
        }
        update = AppUpdate(104201, "1.1.42.1", "arm64-v8a", "https://example.invalid/update.apk", file.length(),
            UpdateDownload.hex(MessageDigest.getInstance("SHA-256").digest(file.readBytes())))
        val packages = shadowOf(context.packageManager)
        packages.installPackage(info(104101, byteArrayOf(1, 2, 3)))
        candidate = info(104201, byteArrayOf(1, 2, 3))
        packages.setPackageArchiveInfo(file.absolutePath, candidate)
    }
    @After fun cleanup() { file.delete() }
    private fun info(version: Long, certificate: ByteArray) = PackageInfo().apply {
        packageName = context.packageName
        setLongVersionCode(version)
        applicationInfo = ApplicationInfo().apply { packageName = context.packageName; minSdkVersion = 29 }
        signingInfo = Shadow.newInstanceOf(SigningInfo::class.java).also {
            shadowOf(it).setSignatures(arrayOf(Signature(certificate)))
        }
    }
    @Test fun aMatchingPackageVersionCertificateAndAbiPassTheAndroidAdapter() {
        ApkUpdateVerifier(context).verify(update, file)
    }
    @Test fun changingTheArchiveCertificateIsRejected() {
        candidate.signingInfo = info(104201, byteArrayOf(9)).signingInfo
        fails(UpdateIssue.SIGNATURE_MISMATCH)
    }
    @Test fun aDifferentApplicationOrOlderVersionIsRejected() {
        candidate.packageName = "example.foreign.application"; fails(UpdateIssue.PACKAGE_MISMATCH)
        candidate.packageName = context.packageName; candidate.setLongVersionCode(104101)
        fails(UpdateIssue.VERSION_MISMATCH)
    }
    @Test fun aModifiedFileIsRejectedBeforeTrustingPackageMetadata() {
        file.appendText("modified"); fails(UpdateIssue.INTEGRITY)
    }
    @Test fun aMislabeledAbiOrUnsupportedAndroidVersionIsRejected() {
        try { ApkUpdateVerifier(context).verify(update.copy(abi = "armeabi-v7a"), file); fail("Wrong ABI") }
        catch (error: UpdateFailure) { assertEquals(UpdateIssue.NO_COMPATIBLE_APK, error.issue) }
        candidate.applicationInfo!!.minSdkVersion = 35; fails(UpdateIssue.ANDROID_UNSUPPORTED)
    }
    private fun fails(issue: UpdateIssue) {
        try { ApkUpdateVerifier(context).verify(update, file); fail("Expected $issue") }
        catch (error: UpdateFailure) { assertEquals(issue, error.issue) }
    }
}
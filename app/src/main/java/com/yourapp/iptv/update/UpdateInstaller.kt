package com.yourapp.iptv.update

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

class UpdateFileProvider : FileProvider()

/** Android owns the final confirmation and verifies the APK signature during installation. */
internal class UpdateInstaller(private val context: Context) {
    fun allowed(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun permissionIntent(): Intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}"))

    fun installIntent(file: File): Intent {
        require(file.isFile && file.extension == "apk" &&
            file.canonicalFile.parentFile == File(context.cacheDir, "app-updates").canonicalFile)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .apply { clipData = ClipData.newRawUri("MN StreamX update", uri) }
    }
}
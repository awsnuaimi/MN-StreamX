package com.yourapp.iptv.diagnostics

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.yourapp.iptv.domain.diagnostics.DiagnosticReport
import java.io.File

class DiagnosticFileProvider : FileProvider()

internal class DiagnosticSharing(private val context: Context) {
    fun intent(file: File, title: String): Intent {
        require(file.isFile && file.length() <= DiagnosticReport.MAX_BYTES &&
            file.name.startsWith("mnstreamx-diagnostics-") && file.extension == "txt" &&
            file.canonicalFile.parentFile == File(context.cacheDir,"diagnostic-reports").canonicalFile)
        val uri = FileProvider.getUriForFile(context,"${context.packageName}.diagnostics",file)
        val share = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM,uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply { clipData = ClipData.newRawUri("MN StreamX diagnostics",uri) }
        return Intent.createChooser(share,title)
    }
}
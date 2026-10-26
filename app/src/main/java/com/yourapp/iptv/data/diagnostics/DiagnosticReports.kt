package com.yourapp.iptv.data.diagnostics

import com.yourapp.iptv.domain.diagnostics.DiagnosticReport
import java.io.File
import java.util.UUID

internal class DiagnosticReports(private val directory: File) {
    @Synchronized fun write(text: String): File {
        val bytes = text.toByteArray(Charsets.UTF_8)
        require(bytes.size <= DiagnosticReport.MAX_BYTES)
        check(directory.mkdirs() || directory.isDirectory)
        val file = File(directory,"mnstreamx-diagnostics-${System.currentTimeMillis()}-${UUID.randomUUID()}.txt")
        try { file.outputStream().use { it.write(bytes) } }
        catch (error: Exception) { file.delete();throw error }
        directory.listFiles().orEmpty().filter { it.isFile && it.name.matches(OWNED) }
            .sortedByDescending { it.lastModified() }.filterNot { it == file }.drop(2).forEach { it.delete() }
        return file
    }
    private companion object { val OWNED = Regex("mnstreamx-diagnostics-[0-9]+-[0-9a-f-]{36}\\.txt") }
}
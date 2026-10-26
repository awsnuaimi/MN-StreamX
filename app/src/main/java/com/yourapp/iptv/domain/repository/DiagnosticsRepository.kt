package com.yourapp.iptv.domain.repository

import com.yourapp.iptv.domain.diagnostics.*
import com.yourapp.iptv.domain.model.AppSettings
import java.io.File

interface DiagnosticsRepository {
    suspend fun snapshot(): DiagnosticSnapshot
    fun sourceKind(source: AppSettings): DiagnosticSource
    suspend fun probe(source: AppSettings, onCheck: (DiagnosticCheck) -> Unit)
    suspend fun export(snapshot: DiagnosticSnapshot, source: DiagnosticSource, sourceKey: String?,
        checks: List<DiagnosticCheck>, events: List<DiagnosticEvent>, language: com.yourapp.iptv.domain.model.HomeLanguage): File
}
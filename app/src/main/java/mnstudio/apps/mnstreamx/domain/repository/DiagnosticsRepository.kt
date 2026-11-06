package mnstudio.apps.mnstreamx.domain.repository

import mnstudio.apps.mnstreamx.domain.diagnostics.*
import mnstudio.apps.mnstreamx.domain.model.AppSettings
import java.io.File

interface DiagnosticsRepository {
    suspend fun snapshot(): DiagnosticSnapshot
    fun sourceKind(source: AppSettings): DiagnosticSource
    suspend fun probe(source: AppSettings, onCheck: (DiagnosticCheck) -> Unit)
    suspend fun export(snapshot: DiagnosticSnapshot, source: DiagnosticSource, sourceKey: String?,
        checks: List<DiagnosticCheck>, events: List<DiagnosticEvent>, language: mnstudio.apps.mnstreamx.domain.model.HomeLanguage): File
}
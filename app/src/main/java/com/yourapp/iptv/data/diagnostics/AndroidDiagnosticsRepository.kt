package com.yourapp.iptv.data.diagnostics

import android.content.Context
import com.yourapp.iptv.data.network.HttpClient
import com.yourapp.iptv.domain.diagnostics.*
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.repository.DiagnosticsRepository
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class AndroidDiagnosticsRepository(context: Context) : DiagnosticsRepository {
    private val environment = AndroidDiagnosticSnapshot(context.applicationContext)
    private val probe = DiagnosticSourceProbe(DiagnosticTransport(HttpClient.create().newBuilder()
        .connectTimeout(6,TimeUnit.SECONDS).readTimeout(8,TimeUnit.SECONDS).callTimeout(10,TimeUnit.SECONDS).build()))
    private val reports = DiagnosticReports(File(context.applicationContext.cacheDir,"diagnostic-reports"))
    override suspend fun snapshot() = withContext(Dispatchers.IO) { environment.read() }
    override fun sourceKind(source: AppSettings) = probe.kind(source)
    override suspend fun probe(source: AppSettings,onCheck: (DiagnosticCheck) -> Unit) = probe.run(source,onCheck)
    override suspend fun export(snapshot: DiagnosticSnapshot, source: DiagnosticSource, sourceKey: String?,
        checks: List<DiagnosticCheck>, events: List<DiagnosticEvent>, language: HomeLanguage) = withContext(Dispatchers.IO) {
        reports.write(DiagnosticReport.build(snapshot,source,sourceKey,checks,events,language))
    }
}
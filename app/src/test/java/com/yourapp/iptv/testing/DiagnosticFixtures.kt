package com.yourapp.iptv.testing

import com.yourapp.iptv.domain.diagnostics.*

internal fun diagnosticSnapshot() = DiagnosticSnapshot("1.1.120.1",34,listOf("arm64-v8a"),DiagnosticConnection.ETHERNET,
    true,false,2L * 1024 * 1024 * 1024,512L * 1024 * 1024,false,64L * 1024 * 1024,256L * 1024 * 1024,
    400L * 1024 * 1024,16L * 1024 * 1024 * 1024,setOf(DiagnosticCodec.AVC,DiagnosticCodec.HEVC))
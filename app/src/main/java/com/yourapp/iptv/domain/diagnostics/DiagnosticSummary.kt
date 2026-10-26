package com.yourapp.iptv.domain.diagnostics

object DiagnosticSummary {
    private val version = Regex("[0-9]{1,9}(\\.[0-9]{1,9}){1,3}")
    private val abis = setOf("armeabi","armeabi-v7a","arm64-v8a","x86","x86_64","riscv64")
    fun rows(value: DiagnosticSnapshot, text: DiagnosticText): List<Pair<String,String>> {
        fun bool(value: Boolean?) = when (value) { true -> text.yes();false -> text.no();null -> text.unknown() }
        fun bytes(value: Long?) = value?.takeIf { it >= 0 }?.let { "${it / (1024 * 1024)} MiB" } ?: text.unknown()
        return listOf(
            text.text("إصدار التطبيق","App version","App-Version") to value.version.takeIf(version::matches).orEmpty().ifEmpty { text.unknown() },
            "Android SDK" to value.sdk.takeIf { it in 1..99 }?.toString().orEmpty().ifEmpty { text.unknown() },
            "ABI" to value.abis.filter { it in abis }.distinct().joinToString().ifEmpty { text.unknown() },
            text.text("نوع الاتصال","Connection type","Verbindungsart") to value.connection.name,
            text.text("إنترنت موثّق من أندرويد","Android validated Internet","Internetzugang durch Android bestätigt") to bool(value.validated),
            text.text("اتصال محسوب البيانات","Metered connection","Getaktete Verbindung") to bool(value.metered),
            text.text("RAM المتاحة / الإجمالية","Available / total RAM","Verfügbarer / gesamter RAM") to "${bytes(value.availableRam)} / ${bytes(value.totalRam)}",
            text.text("ذاكرة التطبيق / الحد","App heap / limit","App-Heap / Grenze") to "${bytes(value.heapUsed)} / ${bytes(value.heapLimit)}",
            text.text("إشارة انخفاض الذاكرة من النظام","System low-memory flag","Speicherwarnung des Systems") to bool(value.lowMemory),
            text.text("مساحة قسم بيانات التطبيق المتاحة / الإجمالية","Free / total app-data filesystem space","Freier / gesamter Speicher im App-Daten-Dateisystem") to "${bytes(value.diskAvailable)} / ${bytes(value.diskTotal)}",
            text.text("مفكّكات الفيديو المعلنة؛ ليست ضمانًا لكل ملف","Advertised video decoders; not a guarantee for every file","Gemeldete Videodecoder; keine Garantie für jede Datei") to
                (value.codecs?.sortedBy { it.ordinal }?.joinToString { it.name }?.ifEmpty { text.no() } ?: text.unknown())
        )
    }
    fun technical(event: DiagnosticEvent): String = listOfNotNull(
        event.code?.takeIf { it in 0..1_000_000 }?.let { "code=$it" },
        event.httpStatus?.takeIf { it in 100..599 }?.let { "HTTP $it" },event.update?.name).joinToString(" · ")
}
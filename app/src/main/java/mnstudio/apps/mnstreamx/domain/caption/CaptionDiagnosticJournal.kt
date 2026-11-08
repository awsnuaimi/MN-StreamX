package mnstudio.apps.mnstreamx.domain.caption

/** Bounded, content-free measurements for offline caption troubleshooting. */
object CaptionDiagnosticJournal {
    private const val LIMIT = 48
    private val entries = ArrayDeque<String>()
    @Synchronized fun record(stage: String, elapsedMs: Long, detail: String = "") {
        if (!stage.matches(Regex("[A-Z_]{2,32}"))) return
        val suffix = if (detail.matches(Regex("[a-zA-Z0-9_=, -]{0,64}"))) detail else ""
        if (entries.size == LIMIT) entries.removeFirst()
        entries.addLast("$stage | ${elapsedMs.coerceIn(0, 600_000)} ms" + if (suffix.isEmpty()) "" else " | $suffix")
    }
    @Synchronized fun snapshot(): List<String> = entries.toList()
    @Synchronized fun clear() = entries.clear()
}
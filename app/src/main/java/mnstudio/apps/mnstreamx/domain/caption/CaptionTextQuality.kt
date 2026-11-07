package mnstudio.apps.mnstreamx.domain.caption

/** Reject decoder loops within one utterance, never repeated dialogue across windows. */
internal object CaptionTextQuality {
    private val words = Regex("[\\p{L}\\p{N}]+")
    fun usable(text: String): Boolean {
        if (text.isBlank() || text.length > 2000 || text.contains("<|")) return false
        val tokens = words.findAll(text.lowercase(java.util.Locale.ROOT)).map { it.value }.toList()
        if (tokens.isEmpty()) return false
        // Short emphatic repetitions are legitimate speech. Require a sustained loop.
        for (width in 1..4) {
            val repeats = if (width == 1) 8 else 4
            if (tokens.size < width * repeats) continue
            for (start in 0..tokens.size - width * repeats) {
                if ((width until width * repeats).all { tokens[start + it] == tokens[start + it % width] })
                    return false
            }
        }
        return true
    }
}
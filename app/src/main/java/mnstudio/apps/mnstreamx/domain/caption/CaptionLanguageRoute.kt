package mnstudio.apps.mnstreamx.domain.caption
import mnstudio.apps.mnstreamx.domain.model.CaptionLanguage
object CaptionLanguageRoute {
    fun steps(source: String, target: String): List<Pair<String, String>> {
        require(CaptionLanguage.fromTag(source)?.tag == source && CaptionLanguage.fromTag(target)?.tag == target)
        return when {
            source == target -> emptyList()
            source == "en" || target == "en" -> listOf(source to target)
            else -> listOf(source to "en", "en" to target)
        }
    }
}
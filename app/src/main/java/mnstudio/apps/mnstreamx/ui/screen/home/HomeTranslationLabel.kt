package mnstudio.apps.mnstreamx.ui.screen.home

import mnstudio.apps.mnstreamx.domain.model.CaptionLanguage
import mnstudio.apps.mnstreamx.ui.presentation.HomeChannelPresentation
import mnstudio.apps.mnstreamx.ui.presentation.HomeTranslationKind
import mnstudio.apps.mnstreamx.ui.state.CaptionUiState

internal fun homeTranslationLabel(state: CaptionUiState, labels: HomeLabels): String {
    val target = when (state.settings.target) {
        CaptionLanguage.ARABIC -> labels.text("العربية", "Arabic", "Arabisch")
        CaptionLanguage.ENGLISH -> labels.text("الإنجليزية", "English", "Englisch")
        CaptionLanguage.GERMAN -> labels.text("الألمانية", "German", "Deutsch")
        CaptionLanguage.POLISH -> labels.text("البولندية", "Polish", "Polnisch")
        CaptionLanguage.HINDI -> labels.text("الهندية", "Hindi", "Hindi")
    }
    return when (HomeChannelPresentation.kind(state)) {
        HomeTranslationKind.UNKNOWN -> labels.text("الترجمة", "Translation", "Übersetzung")
        HomeTranslationKind.OFF -> labels.text("الترجمة: إيقاف", "Translation: Off", "Übersetzung: Aus")
        HomeTranslationKind.EMBEDDED -> labels.text("ترجمة المصدر", "Source subtitles", "Sender-Untertitel")
        HomeTranslationKind.SETUP -> labels.text("إعداد الترجمة", "Set up translation", "Übersetzung einrichten")
        HomeTranslationKind.GENERATED -> labels.text("الترجمة: $target", "Translation: $target", "Übersetzung: $target")
    }
}
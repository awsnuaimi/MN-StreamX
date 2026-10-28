package com.yourapp.iptv.ui.screen.home

import com.yourapp.iptv.domain.model.CaptionLanguage
import com.yourapp.iptv.ui.presentation.HomeChannelPresentation
import com.yourapp.iptv.ui.presentation.HomeTranslationKind
import com.yourapp.iptv.ui.state.CaptionUiState

/** This label is a playback preference, not a claim that a translation engine runs on Home. */
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
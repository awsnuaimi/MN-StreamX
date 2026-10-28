package com.yourapp.iptv.ui.presentation

import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.ui.state.CaptionUiState
import java.util.Locale

internal enum class HomeTranslationKind { UNKNOWN, OFF, EMBEDDED, GENERATED, SETUP }
internal data class HomeLanguageChannels(val channels: List<Channel>, val languageSpecific: Boolean) {
    override fun toString() = "HomeLanguageChannels(count=${channels.size}, specific=$languageSpecific)"
}

/** Provider-declared prefix only. Unknown names are not guessed to be a language or quality. */
internal object HomeChannelPresentation {
    private val prefix = Regex("^\\s*([A-Za-z]{2})\\s*[|:]")
    private val quality = Regex("(?:^|[\\s|:_-])(8K|4K|UHD|FHD|HD|SD)$", RegexOption.IGNORE_CASE)
    fun language(channel: Channel): String? = prefix.find(channel.name)?.groupValues?.get(1)?.lowercase(Locale.ROOT)
        ?.takeIf { CaptionLanguage.fromTag(it) != null }
    fun quality(channel: Channel): String? = quality.find(channel.name.trim())?.groupValues?.get(1)?.uppercase(Locale.ROOT)
    fun kind(state: CaptionUiState): HomeTranslationKind = when {
        !state.loaded || state.readFailed -> HomeTranslationKind.UNKNOWN
        state.settings.mode == CaptionMode.OFF -> HomeTranslationKind.OFF
        state.settings.mode == CaptionMode.EMBEDDED -> HomeTranslationKind.EMBEDDED
        state.settings.translationBackend == TranslationBackend.CLOUD && !state.cloudConfigured -> HomeTranslationKind.SETUP
        else -> HomeTranslationKind.GENERATED
    }
    fun badge(channel: Channel, state: CaptionUiState): String? {
        val source = language(channel)?.uppercase(Locale.ROOT) ?: return null
        val target = state.settings.target.tag.uppercase(Locale.ROOT)
        return if (kind(state) == HomeTranslationKind.GENERATED && source != target) "$source → $target" else source
    }
    fun select(catalog: List<Channel>, selection: HomeDashboardSelection, state: CaptionUiState,
        interfaceLanguage: HomeLanguage): HomeLanguageChannels {
        val target = if (state.loaded && !state.readFailed) state.settings.target.tag else when (interfaceLanguage) {
            HomeLanguage.ARABIC -> "ar"; HomeLanguage.ENGLISH -> "en"; HomeLanguage.GERMAN -> "de"
        }
        val translate = kind(state) == HomeTranslationKind.GENERATED
        fun priority() = selection.favorites.asSequence() + selection.recent.asSequence() + catalog.asSequence()
        fun eligible(channel: Channel) = language(channel)?.let { it == target || translate } ?: false
        val native = priority().firstOrNull { language(it) == target }
        val rows = (sequenceOfNotNull(native) + priority().filter(::eligible))
            .distinctBy { it.id to it.streamUrl }.take(12).toList()
        return if (rows.isNotEmpty()) HomeLanguageChannels(rows, true)
            else HomeLanguageChannels(priority().distinctBy { it.id to it.streamUrl }.take(12).toList(), false)
    }
    private fun <T : Any> sequenceOfNotNull(value: T?): Sequence<T> = if (value == null) emptySequence() else sequenceOf(value)
}
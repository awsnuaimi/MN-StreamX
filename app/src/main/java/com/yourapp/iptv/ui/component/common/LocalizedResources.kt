package com.yourapp.iptv.ui.component.common

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.yourapp.iptv.domain.model.HomeLanguage
import java.util.Locale

private val LocalResourceLanguage = staticCompositionLocalOf<HomeLanguage?> { null }

/** Retains the Activity in the context chain for dialogs and native player controls. */
// APK distribution includes every language; Play language splits are not used.
@SuppressLint("AppBundleLocaleChanges")
@Composable
internal fun LocalizedResources(language: HomeLanguage, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val original = LocalConfiguration.current
    val inherited = LocalResourceLanguage.current == language
    val locale = remember(language) { Locale.forLanguageTag(when (language) {
        HomeLanguage.ARABIC -> "ar"
        HomeLanguage.ENGLISH -> "en"
        HomeLanguage.GERMAN -> "de"
    }) }
    val config = remember(original, locale, inherited) {
        if (inherited) original else Configuration(original).apply {
            setLocale(locale); setLayoutDirection(locale)
        }
    }
    val localized = remember(context, config, inherited) {
        if (inherited) context else ContextThemeWrapper(context, context.theme).apply {
            applyOverrideConfiguration(config)
        }
    }
    CompositionLocalProvider(LocalResourceLanguage provides language,
        LocalContext provides localized, LocalConfiguration provides config,
        LocalLayoutDirection provides if (language == HomeLanguage.ARABIC) LayoutDirection.Rtl else LayoutDirection.Ltr,
        content = content)
}
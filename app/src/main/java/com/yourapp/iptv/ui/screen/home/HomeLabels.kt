package com.yourapp.iptv.ui.screen.home

import com.yourapp.iptv.domain.model.HomeLanguage

internal enum class HomeSection { DASHBOARD, LIVE, MOVIES, SERIES, FAVORITES, HISTORY, GUIDE, SEARCH, SETTINGS, APPEARANCE, ORGANIZE, REFRESH, MORE }
internal class HomeLabels(private val language: HomeLanguage) {
    fun text(ar: String, en: String, de: String) = when (language) {
        HomeLanguage.ARABIC -> ar
        HomeLanguage.ENGLISH -> en
        HomeLanguage.GERMAN -> de
    }
    fun section(value: HomeSection) = when (value) {
        HomeSection.DASHBOARD -> text("الرئيسية", "Home", "Startseite")
        HomeSection.LIVE -> text("البث الحي", "Live TV", "Live-TV")
        HomeSection.MOVIES -> text("الأفلام", "Movies", "Filme")
        HomeSection.SERIES -> text("المسلسلات", "Series", "Serien")
        HomeSection.FAVORITES -> text("المفضلة", "Favorites", "Favoriten")
        HomeSection.HISTORY -> text("شوهد مؤخرًا", "Recent", "Verlauf")
        HomeSection.GUIDE -> text("دليل البرامج", "TV guide", "Programm")
        HomeSection.SEARCH -> text("البحث", "Search", "Suche")
        HomeSection.SETTINGS -> text("الإعدادات", "Settings", "Einstellungen")
        HomeSection.APPEARANCE -> text("مظهر التطبيق", "App appearance", "App-Darstellung")
        HomeSection.ORGANIZE -> text("تنظيم القنوات", "Organize", "Ordnen")
        HomeSection.MORE -> text("المزيد", "More", "Mehr")
        HomeSection.REFRESH -> text("تحديث", "Refresh", "Aktualisieren")
    }
}
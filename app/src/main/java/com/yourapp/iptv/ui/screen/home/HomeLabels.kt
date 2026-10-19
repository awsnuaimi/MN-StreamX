package com.yourapp.iptv.ui.screen.home

import com.yourapp.iptv.domain.model.HomeLanguage

internal enum class HomeSection { LIVE, FAVORITES, GUIDE, SEARCH, SETTINGS, APPEARANCE, REFRESH }
internal class HomeLabels(private val language: HomeLanguage) {
    fun text(ar: String, en: String, de: String) = when (language) {
        HomeLanguage.ARABIC -> ar
        HomeLanguage.ENGLISH -> en
        HomeLanguage.GERMAN -> de
    }
    fun section(value: HomeSection) = when (value) {
        HomeSection.LIVE -> text("البث الحي", "Live TV", "Live-TV")
        HomeSection.FAVORITES -> text("المفضلة", "Favorites", "Favoriten")
        HomeSection.GUIDE -> text("دليل البرامج", "TV guide", "Programm")
        HomeSection.SEARCH -> text("البحث", "Search", "Suche")
        HomeSection.SETTINGS -> text("الإعدادات", "Settings", "Einstellungen")
        HomeSection.APPEARANCE -> text("مظهر الرئيسية", "Home appearance", "Startansicht")
        HomeSection.REFRESH -> text("تحديث", "Refresh", "Aktualisieren")
    }
}
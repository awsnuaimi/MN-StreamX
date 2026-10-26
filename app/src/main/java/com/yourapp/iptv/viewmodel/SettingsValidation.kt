package com.yourapp.iptv.viewmodel

import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.ui.state.SettingsIssue
import com.yourapp.iptv.util.validator.UrlValidator

internal object SettingsValidation {
    fun validate(settings: AppSettings): SettingsIssue? {
        val xtream = settings.source == PlaylistSource.XTREAM
        return when {
            !xtream && settings.m3uUrl.isBlank() -> SettingsIssue.M3U_REQUIRED
            !xtream && !UrlValidator.isValidUrl(settings.m3uUrl) -> SettingsIssue.M3U_INVALID
            xtream && (settings.xtreamServer.isBlank() || settings.xtreamUsername.isBlank() ||
                settings.xtreamPassword.isBlank()) -> SettingsIssue.XTREAM_REQUIRED
            xtream && !UrlValidator.isValidUrl(settings.xtreamServer) -> SettingsIssue.SERVER_INVALID
            settings.epgUrl.isNotBlank() && !UrlValidator.isValidUrl(settings.epgUrl) -> SettingsIssue.EPG_INVALID
            else -> null
        }
    }
    fun issue(error: Exception): SettingsIssue = when ((error as? SubscriptionException)?.problem) {
        SubscriptionProblem.NAME -> SettingsIssue.SUBSCRIPTION_NAME
        SubscriptionProblem.DUPLICATE -> SettingsIssue.SUBSCRIPTION_DUPLICATE
        SubscriptionProblem.LIMIT -> SettingsIssue.SUBSCRIPTION_LIMIT
        SubscriptionProblem.NOT_FOUND -> SettingsIssue.SUBSCRIPTION_MISSING
        SubscriptionProblem.ACTIVE_DELETE -> SettingsIssue.SUBSCRIPTION_ACTIVE_DELETE
        SubscriptionProblem.INVALID -> SettingsIssue.SUBSCRIPTION_INVALID
        null -> SettingsIssue.SAVE_FAILED
    }
}
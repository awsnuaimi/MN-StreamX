package com.yourapp.iptv.ui.screen.settings

import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.profile.*
import com.yourapp.iptv.ui.state.SettingsUiState
import org.junit.Assert.*
import org.junit.Test

class AdaptiveNavigationScopeTest {
    private val profile = "12345678-1234-1234-1234-123456789abc"
    private val source = AppSettings(m3uUrl = "https://synthetic.invalid/current.m3u")
    private val account = SettingsUiState(settings = AppSettings(m3uUrl = "https://synthetic.invalid/draft.m3u"),
        isLoading = false, subscriptions = listOf(Subscription("active", "Synthetic", source)), activeSubscriptionId = "active")
    private val people = ProfilesState(ready = true, catalog = UserProfiles(
        entries = UserProfiles().entries + UserProfile(profile, "Synthetic"), activeId = profile))

    @Test fun usesTheActiveProfileAndCommittedSubscriptionRatherThanTheDraftsDefaultProfile() {
        val scope = adaptiveSettingsScope(account, people)
        assertEquals(profile, ProfileScope.profile(scope))
        assertEquals(PlaylistCacheKey.of(source), ProfileScope.source(scope))
        assertNotEquals(PlaylistCacheKey.of(account.settings), ProfileScope.source(scope))
        val other = adaptiveSettingsScope(account, people.copy(catalog = people.catalog.copy(activeId = UserProfiles.DEFAULT_ID)))
        assertNotEquals(scope, other); assertEquals(UserProfiles.DEFAULT_ID, ProfileScope.profile(other))
    }
    @Test fun unavailableAccountsAndSwitchingOrFailedProfilesNeverExposeAUsableScope() {
        for (state in listOf(people.copy(ready = false), people.copy(failed = true), people.copy(switching = true)))
            assertEquals("", adaptiveSettingsScope(account, state))
        for (state in listOf(account.copy(isLoading = true), account.copy(isSaving = true), account.copy(loadFailed = true),
            account.copy(activeSubscriptionId = "missing"), account.copy(subscriptions = emptyList())))
            assertEquals("", adaptiveSettingsScope(state, people))
    }
}
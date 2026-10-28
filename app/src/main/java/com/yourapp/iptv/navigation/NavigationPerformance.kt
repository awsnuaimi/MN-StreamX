package com.yourapp.iptv.navigation

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import com.yourapp.iptv.domain.performance.*

/** Only destination templates are classified; argument values are never retained or reported. */
internal fun performanceScreen(route: String?): PerformanceScreen? = when (route) {
    Destinations.HOME -> PerformanceScreen.HOME
    Destinations.SEARCH -> PerformanceScreen.SEARCH
    Destinations.EPG -> PerformanceScreen.GUIDE
    Destinations.PLAYER,Destinations.HISTORY_PLAYER -> PerformanceScreen.LIVE_PLAYER
    "vod/{kind}" -> PerformanceScreen.VOD_LIST
    Destinations.VOD_PLAYER,Destinations.VOD_EPISODE_PLAYER -> PerformanceScreen.VOD_PLAYER
    Destinations.SETTINGS -> PerformanceScreen.SETTINGS
    "profiles?select={select}","profile_manager" -> PerformanceScreen.PROFILES
    "home_appearance",Destinations.PLAYER_APPEARANCE -> PerformanceScreen.APPEARANCE
    "organization" -> PerformanceScreen.ORGANIZATION
    Destinations.BACKUP -> PerformanceScreen.BACKUP
    Destinations.DIAGNOSTICS -> PerformanceScreen.DIAGNOSTICS
    Destinations.SUBSCRIPTIONS -> PerformanceScreen.SUBSCRIPTIONS
    "parental" -> PerformanceScreen.PROTECTION
    Destinations.HISTORY -> PerformanceScreen.HISTORY
    else -> null
}

@Composable
internal fun NavigationPerformanceEffect(nav: NavHostController) {
    val spans = remember(nav) { PerformanceSpans(PerformanceJournal.recorder) }
    var ticket by remember(nav) { mutableStateOf<Long?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(nav) {
        val listener = NavController.OnDestinationChangedListener { _,destination,_ ->
            spans.finish(ticket,PerformanceOutcome.CANCELLED)
            ticket = performanceScreen(destination.route)?.let { spans.start(PerformanceStage.NAV_COMPOSE,it) }
        }
        nav.addOnDestinationChangedListener(listener)
        onDispose { nav.removeOnDestinationChangedListener(listener);spans.finish(ticket,PerformanceOutcome.CANCELLED) }
    }
    LaunchedEffect(ticket) {
        val current = ticket ?: return@LaunchedEffect
        try {
            withFrameNanos { };withFrameNanos { }
            spans.finish(current,if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
                PerformanceOutcome.COMPLETE else PerformanceOutcome.CANCELLED)
        } finally { spans.finish(current,PerformanceOutcome.CANCELLED) }
    }
}
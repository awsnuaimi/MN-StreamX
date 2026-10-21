package com.yourapp.iptv.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import com.yourapp.iptv.viewmodel.StartupPlaybackViewModel

/** Activity handles real input; Navigation handles leaving Home without a key event. */
@Composable
internal fun StartupPlaybackGuard(nav: NavHostController, startup: StartupPlaybackViewModel) {
    LaunchedEffect(nav, startup) {
        nav.currentBackStackEntryFlow.collect { entry ->
            if (entry.destination.route != Destinations.HOME) startup.cancel()
        }
    }
}
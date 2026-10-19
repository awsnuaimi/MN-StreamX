package com.yourapp.iptv.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yourapp.iptv.ui.screen.home.HomeScreen
import com.yourapp.iptv.ui.screen.epg.EpgScreen
import com.yourapp.iptv.ui.screen.player.PlayerScreen
import com.yourapp.iptv.ui.screen.settings.SettingsScreen

@Composable
fun AppNavGraph() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Destinations.HOME) {
        composable(Destinations.HOME) { entry ->
            val returnedChannel by entry.savedStateHandle
                .getStateFlow<String?>("lastPlayedChannelId", null).collectAsStateWithLifecycle()
            HomeScreen(
                returnedChannelId = returnedChannel,
                onReturnHandled = { entry.savedStateHandle["lastPlayedChannelId"] = null },
                onSettings = { nav.navigate(Destinations.SETTINGS) { launchSingleTop = true } },
                onPlayChannel = { id, group, query, favoritesOnly ->
                    nav.navigate(playerRoute(id, group, query, favoritesOnly)) { launchSingleTop = true }
                },
                onOpenGuide = { id, group, query, favoritesOnly ->
                    nav.navigate(buildString {
                        append("epg?query=${Uri.encode(query)}&favoritesOnly=$favoritesOnly")
                        if (id != null) append("&channelId=${Uri.encode(id)}")
                        if (group != null) append("&group=${Uri.encode(group)}")
                    }) { launchSingleTop = true }
                }
            )
        }
        composable(Destinations.EPG, arguments = listOf(
            navArgument("channelId") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("group") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("query") { type = NavType.StringType; defaultValue = "" },
            navArgument("favoritesOnly") { type = NavType.BoolType; defaultValue = false }
        )) { entry ->
            val returnedChannel by entry.savedStateHandle
                .getStateFlow<String?>("lastPlayedChannelId", null).collectAsStateWithLifecycle()
            EpgScreen(
                returnedChannelId = returnedChannel,
                onReturnHandled = { entry.savedStateHandle["lastPlayedChannelId"] = null },
                onBack = { nav.popBackStack() },
                onPlayChannel = { id, group, query, favoritesOnly ->
                    nav.navigate(playerRoute(id, group, query, favoritesOnly)) { launchSingleTop = true }
                }
            )
        }
        composable(Destinations.SETTINGS) {
            SettingsScreen(
                onSettingsSaved = {
                    nav.navigate(Destinations.HOME) {
                        popUpTo(Destinations.HOME) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onBack = { nav.popBackStack() }
            )
        }
        composable(Destinations.PLAYER, arguments = listOf(
            navArgument("channelId") { type = NavType.StringType },
            navArgument("group") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("query") { type = NavType.StringType; defaultValue = "" },
            navArgument("favoritesOnly") { type = NavType.BoolType; defaultValue = false }
        )) {
            PlayerScreen(onBack = { channelId ->
                if (channelId != null) {
                    nav.previousBackStackEntry?.savedStateHandle?.set("lastPlayedChannelId", channelId)
                }
                nav.popBackStack()
            })
        }
    }
}

private fun playerRoute(id: String, group: String?, query: String, favoritesOnly: Boolean): String = buildString {
    append("player/${Uri.encode(id)}?query=${Uri.encode(query)}&favoritesOnly=$favoritesOnly")
    if (group != null) append("&group=${Uri.encode(group)}")
}
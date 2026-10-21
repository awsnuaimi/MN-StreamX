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
import com.yourapp.iptv.ui.screen.appearance.HomeAppearanceScreen
import com.yourapp.iptv.ui.screen.home.HomeScreen
import com.yourapp.iptv.ui.screen.epg.EpgScreen
import com.yourapp.iptv.ui.screen.player.PlayerScreen
import com.yourapp.iptv.ui.screen.organization.OrganizationScreen
import com.yourapp.iptv.ui.screen.settings.SettingsScreen
import com.yourapp.iptv.ui.screen.appearance.PlayerAppearanceScreen
import com.yourapp.iptv.ui.screen.history.WatchHistoryScreen

@Composable
fun AppNavGraph() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Destinations.HOME) {
        composable(Destinations.HOME) { entry ->
            val returnedChannel by entry.savedStateHandle
                .getStateFlow<String?>("lastPlayedChannelId", null).collectAsStateWithLifecycle()
            HomeScreen(
                onHistory = { nav.navigate(Destinations.HISTORY) { launchSingleTop = true } },
                onOrganization = { nav.navigate("organization") { launchSingleTop = true } },
                onHomeAppearance = { nav.navigate("home_appearance") { launchSingleTop = true } },
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
        composable("organization") { OrganizationScreen(onBack = { nav.popBackStack() }) }
        composable(Destinations.HISTORY) {
            WatchHistoryScreen(onBack = { nav.popBackStack() }, onPlay = { id, source ->
                nav.navigate(historyPlayerRoute(id, source)) { launchSingleTop = true }
            })
        }
        composable("home_appearance") { HomeAppearanceScreen(onBack = { nav.popBackStack() }) }
        composable(Destinations.PLAYER_APPEARANCE) {
            PlayerAppearanceScreen(onBack = { nav.popBackStack() })
        }
        composable(Destinations.SETTINGS) {
            SettingsScreen(
                onHomeAppearance = { nav.navigate("home_appearance") { launchSingleTop = true } },
                onPlayerAppearance = { nav.navigate(Destinations.PLAYER_APPEARANCE) { launchSingleTop = true } },
                onSettingsSaved = {
                    nav.navigate(Destinations.HOME) {
                        popUpTo(Destinations.HOME) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onBack = { nav.popBackStack() }
            )
        }
        for (playerDestination in listOf(Destinations.PLAYER, Destinations.HISTORY_PLAYER)) {
        composable(playerDestination, arguments = listOf(
            navArgument("channelId") { type = NavType.StringType },
            navArgument("group") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("query") { type = NavType.StringType; defaultValue = "" },
            navArgument("favoritesOnly") { type = NavType.BoolType; defaultValue = false },
            navArgument("sourceKey") { type = NavType.StringType; nullable = true; defaultValue = null }
        )) {
            PlayerScreen(onAppearance = { nav.navigate(Destinations.PLAYER_APPEARANCE) { launchSingleTop = true } },
                onOpenHistoryChannel = { id, source ->
                    nav.navigate(historyPlayerRoute(id, source)) {
                        popUpTo(playerDestination) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onGuide = { id, group, query, favoritesOnly ->
                    val previous = nav.previousBackStackEntry
                    if (id != null) previous?.savedStateHandle?.set("lastPlayedChannelId", id)
                    if (previous?.destination?.route == Destinations.EPG) nav.popBackStack()
                    else nav.navigate(playerGuideRoute(id, group, query, favoritesOnly)) {
                        popUpTo(playerDestination) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onBack = { channelId ->
                if (channelId != null) {
                    nav.previousBackStackEntry?.savedStateHandle?.set("lastPlayedChannelId", channelId)
                }
                nav.popBackStack()
            })
        }
        }
    }
}

private fun playerRoute(id: String, group: String?, query: String, favoritesOnly: Boolean): String = buildString {
    append("player/${Uri.encode(id)}?query=${Uri.encode(query)}&favoritesOnly=$favoritesOnly")
    if (group != null) append("&group=${Uri.encode(group)}")
}

private fun historyPlayerRoute(id: String, sourceKey: String) =
    "history-player/${Uri.encode(id)}?sourceKey=${Uri.encode(sourceKey)}"

private fun playerGuideRoute(id: String?, group: String?, query: String, favoritesOnly: Boolean): String = buildString {
    append("epg?query=${Uri.encode(query)}&favoritesOnly=$favoritesOnly")
    if (id != null) append("&channelId=${Uri.encode(id)}")
    if (group != null) append("&group=${Uri.encode(group)}")
}
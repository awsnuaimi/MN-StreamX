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
                onSettings = { nav.navigate(Destinations.SETTINGS) { launchSingleTop = true } },
                onPlayChannel = { id, group, query ->
                    val route = buildString {
                        append("player/${Uri.encode(id)}?query=${Uri.encode(query)}")
                        if (group != null) append("&group=${Uri.encode(group)}")
                    }
                    nav.navigate(route) { launchSingleTop = true }
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
            navArgument("query") { type = NavType.StringType; defaultValue = "" }
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
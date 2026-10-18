package com.yourapp.iptv.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
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
        composable(Destinations.HOME) {
            HomeScreen(
                onSettings = { nav.navigate(Destinations.SETTINGS) { launchSingleTop = true } },
                onPlayChannel = { id -> nav.navigate("player/${Uri.encode(id)}") { launchSingleTop = true } }
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
        composable(Destinations.PLAYER, arguments = listOf(navArgument("channelId") { type = NavType.StringType })) {
            PlayerScreen(onBack = { nav.popBackStack() })
        }
    }
}
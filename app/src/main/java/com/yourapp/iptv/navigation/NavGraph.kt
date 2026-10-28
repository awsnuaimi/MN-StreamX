package com.yourapp.iptv.navigation

import android.net.Uri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yourapp.iptv.viewmodel.StartupPlaybackViewModel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.yourapp.iptv.domain.parental.ParentalSection
import com.yourapp.iptv.ui.screen.parental.ParentalRouteGate
import com.yourapp.iptv.ui.screen.parental.ParentalSettingsScreen
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yourapp.iptv.ui.screen.appearance.HomeAppearanceScreen
import com.yourapp.iptv.ui.screen.home.HomeScreen
import com.yourapp.iptv.ui.screen.search.SearchScreen
import com.yourapp.iptv.ui.screen.epg.EpgScreen
import com.yourapp.iptv.ui.screen.player.PlayerScreen
import com.yourapp.iptv.ui.screen.organization.OrganizationScreen
import com.yourapp.iptv.ui.screen.settings.SettingsScreen
import com.yourapp.iptv.ui.screen.settings.SubscriptionsScreen
import com.yourapp.iptv.ui.screen.appearance.PlayerAppearanceScreen
import com.yourapp.iptv.ui.screen.history.WatchHistoryScreen
import com.yourapp.iptv.ui.screen.vod.VodScreen
import com.yourapp.iptv.ui.screen.vod.VodPlaybackScreen
import com.yourapp.iptv.domain.model.VodKind
import com.yourapp.iptv.ui.screen.backup.BackupScreen
import com.yourapp.iptv.ui.screen.diagnostics.DiagnosticsScreen

@Composable
fun AppNavGraph(startup: StartupPlaybackViewModel, initialRoute: String? = null, onInitialRouteHandled: () -> Unit = {}) {
    val nav = rememberNavController()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(initialRoute) {
        if ((initialRoute?.startsWith("profiles?select=") == true &&
                com.yourapp.iptv.domain.profile.UserProfiles.validId(initialRoute.removePrefix("profiles?select="))) || initialRoute in setOf("profiles","profile_manager","parental","settings","vod/movie","vod/series","organization","home_appearance",
                Destinations.PLAYER_APPEARANCE,Destinations.BACKUP,Destinations.DIAGNOSTICS,Destinations.SUBSCRIPTIONS,Destinations.HISTORY)) {
            nav.navigate(requireNotNull(initialRoute)) { launchSingleTop = true }
            onInitialRouteHandled()
        }
    }
    StartupPlaybackGuard(nav, startup)
    NavigationPerformanceEffect(nav)
    NavHost(navController = nav, startDestination = Destinations.HOME) {
        composable(Destinations.HOME) { entry ->
            val profiles: com.yourapp.iptv.viewmodel.ProfilesViewModel = androidx.hilt.navigation.compose.hiltViewModel()
            val people by profiles.people.collectAsStateWithLifecycle()
            val returnedChannel by entry.savedStateHandle
                .getStateFlow<String?>("lastPlayedChannelId", null).collectAsStateWithLifecycle()
            val openLive by entry.savedStateHandle
                .getStateFlow("openLiveFromSearch", false).collectAsStateWithLifecycle()
            HomeScreen(
                openLiveRequested = openLive,
                onOpenLiveHandled = { entry.savedStateHandle["openLiveFromSearch"] = false },
                onSearch = { nav.navigate(Destinations.SEARCH) { launchSingleTop = true } },
                onStartupReady = { sourceKey -> startup.onHomeReady(sourceKey) { id, source ->
                    if (nav.currentDestination?.route == Destinations.HOME &&
                        lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        nav.navigate(historyPlayerRoute(id, source)) { launchSingleTop = true }
                    }
                } },
                profileName = people.catalog.active.name,
                onProfiles = { nav.navigate("profiles") { launchSingleTop = true } },
                onHistory = { nav.navigate(Destinations.HISTORY) { launchSingleTop = true } },
                onMovies = { nav.navigate("vod/movie") { launchSingleTop = true } },
                onSeries = { nav.navigate("vod/series") { launchSingleTop = true } },
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
        composable(Destinations.SEARCH) {
            SearchScreen(onBack = { nav.popBackStack() },
                onSettings = { nav.navigate(Destinations.SETTINGS) { launchSingleTop = true } },
                onOpenLive = {
                    nav.getBackStackEntry(Destinations.HOME).savedStateHandle["openLiveFromSearch"] = true
                    nav.popBackStack(Destinations.HOME, false)
                },
                onPlayChannel = { id, source -> nav.navigate(historyPlayerRoute(id, source)) { launchSingleTop = true } },
                onPlayVod = { kind, id, ext, title, source, series ->
                    nav.navigate(vodPlaybackRoute(kind, id, ext, title, source, series)) { launchSingleTop = true }
                })
        }
        composable(Destinations.EPG, arguments = listOf(
            navArgument("channelId") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("group") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("query") { type = NavType.StringType; defaultValue = "" },
            navArgument("favoritesOnly") { type = NavType.BoolType; defaultValue = false }
        )) { entry ->
            val returnedChannel by entry.savedStateHandle
                .getStateFlow<String?>("lastPlayedChannelId", null).collectAsStateWithLifecycle()
            ParentalRouteGate(ParentalSection.LIVE,Destinations.HOME,{ nav.popBackStack() }) { EpgScreen(
                returnedChannelId = returnedChannel,
                onReturnHandled = { entry.savedStateHandle["lastPlayedChannelId"] = null },
                onBack = { nav.popBackStack() },
                onPlayChannel = { id, group, query, favoritesOnly ->
                    nav.navigate(playerRoute(id, group, query, favoritesOnly)) { launchSingleTop = true }
                }
            ) }
        }
        composable("profiles?select={select}",arguments = listOf(navArgument("select") { type = NavType.StringType;nullable = true;defaultValue = null })) { entry ->
            val selection by entry.savedStateHandle.getStateFlow<String?>("handledSelection",null).collectAsStateWithLifecycle()
            val requested = entry.arguments?.getString("select")?.takeUnless { it == selection }
            com.yourapp.iptv.ui.screen.profile.ProfileScreen(onBack = { nav.popBackStack() },
                onManage = { nav.navigate("profile_manager") { launchSingleTop = true } },onUsed = {
                    nav.popBackStack(Destinations.HOME,false)
                },selected = requested,onSelectionHandled = { entry.savedStateHandle["handledSelection"] = requested })
        }
        composable("profile_manager") { ParentalRouteGate(null,"profile_manager",{ nav.popBackStack() }) {
            com.yourapp.iptv.ui.screen.profile.ProfileManagerScreen(onBack = { nav.popBackStack() },
                onParental = { nav.navigate("parental") { launchSingleTop = true } })
        } }
        composable("parental") { ParentalRouteGate(null,"parental",{ nav.popBackStack() }) {
            ParentalSettingsScreen(onBack = { nav.popBackStack() })
        } }
        composable("vod/{kind}", arguments = listOf(navArgument("kind") { type = NavType.StringType })) { entry ->
            val kind = if (entry.arguments?.getString("kind") == "series") VodKind.SERIES else VodKind.MOVIE
            ParentalRouteGate(if (kind == VodKind.MOVIE) ParentalSection.MOVIES else ParentalSection.SERIES,
                if (kind == VodKind.MOVIE) "vod/movie" else "vod/series",{ nav.popBackStack() }) { VodScreen(kind, onBack = { nav.popBackStack() }, onPlay = { mediaKind, id, ext, title, source, series ->
                nav.navigate(vodPlaybackRoute(mediaKind, id, ext, title, source, series)) {
                    launchSingleTop = true
                }
            }) }
        }
        for (route in listOf(Destinations.VOD_PLAYER,Destinations.VOD_EPISODE_PLAYER)) {
        composable(route, arguments = listOf(
            navArgument("kind") { type = NavType.StringType },
            navArgument("id") { type = NavType.StringType },
            navArgument("ext") { type = NavType.StringType },
            navArgument("title") { type = NavType.StringType; defaultValue = "" },
            navArgument("sourceKey") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("seriesId") { type = NavType.StringType; nullable = true; defaultValue = null }
        )) { entry ->
            val movie = entry.arguments?.getString("kind") == "movie"
            ParentalRouteGate(if (movie) ParentalSection.MOVIES else ParentalSection.SERIES,
                if (movie) "vod/movie" else "vod/series",{ nav.popBackStack() }) {
                VodPlaybackScreen(onBack = { nav.popBackStack() })
            }
        }
        }
        composable("organization") { ParentalRouteGate(null,"organization",{ nav.popBackStack() }) { OrganizationScreen(onBack = { nav.popBackStack() }) } }
        composable(Destinations.HISTORY) {
            ParentalRouteGate(ParentalSection.LIVE,Destinations.HISTORY,{ nav.popBackStack() }) { WatchHistoryScreen(onBack = { nav.popBackStack() }, onPlay = { id, source ->
                nav.navigate(historyPlayerRoute(id, source)) { launchSingleTop = true }
            }) }
        }
        composable("home_appearance") { ParentalRouteGate(ParentalSection.SETTINGS,"home_appearance",{ nav.popBackStack() }) { HomeAppearanceScreen(onBack = { nav.popBackStack() }) } }
        composable(Destinations.PLAYER_APPEARANCE) {
            ParentalRouteGate(ParentalSection.SETTINGS,Destinations.PLAYER_APPEARANCE,{ nav.popBackStack() }) { PlayerAppearanceScreen(onBack = { nav.popBackStack() }) }
        }
        composable(Destinations.SETTINGS) {
            ParentalRouteGate(ParentalSection.SETTINGS,Destinations.SETTINGS,{ nav.popBackStack() }) { SettingsScreen(
                onParental = { nav.navigate("parental") { launchSingleTop = true } },
                onHomeAppearance = { nav.navigate("home_appearance") { launchSingleTop = true } },
                onPlayerAppearance = { nav.navigate(Destinations.PLAYER_APPEARANCE) { launchSingleTop = true } },
                onSubscriptions = { nav.navigate(Destinations.SUBSCRIPTIONS) { launchSingleTop = true } },
                onBackup = { nav.navigate(Destinations.BACKUP) { launchSingleTop = true } },
                onDiagnostics = { nav.navigate(Destinations.DIAGNOSTICS) { launchSingleTop = true } },
                onBack = { nav.popBackStack() }
            ) }
        }
        composable(Destinations.BACKUP) { ParentalRouteGate(null,Destinations.BACKUP,{ nav.popBackStack() }) { BackupScreen(onBack = { nav.popBackStack() }) } }
        composable(Destinations.DIAGNOSTICS) { ParentalRouteGate(ParentalSection.SETTINGS,Destinations.DIAGNOSTICS,{ nav.popBackStack() }) { DiagnosticsScreen(onBack = { nav.popBackStack() }) } }
        composable(Destinations.SUBSCRIPTIONS) {
            ParentalRouteGate(null,Destinations.SUBSCRIPTIONS,{ nav.popBackStack() }) { SubscriptionsScreen(onBack = { nav.popBackStack() }, onUsed = {
                nav.navigate(Destinations.HOME) {
                    popUpTo(Destinations.HOME) { inclusive = true }
                    launchSingleTop = true
                }
            }) }
        }
        for (playerDestination in listOf(Destinations.PLAYER, Destinations.HISTORY_PLAYER)) {
        composable(playerDestination, arguments = listOf(
            navArgument("channelId") { type = NavType.StringType },
            navArgument("group") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("query") { type = NavType.StringType; defaultValue = "" },
            navArgument("favoritesOnly") { type = NavType.BoolType; defaultValue = false },
            navArgument("sourceKey") { type = NavType.StringType; nullable = true; defaultValue = null }
        )) {
            ParentalRouteGate(ParentalSection.LIVE,Destinations.HOME,{ nav.popBackStack() }) { PlayerScreen(onAppearance = { nav.navigate(Destinations.PLAYER_APPEARANCE) { launchSingleTop = true } },
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
            }) }
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
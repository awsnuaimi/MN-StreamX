package com.yourapp.iptv.navigation

object Destinations {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val HISTORY = "history"
    const val PLAYER = "player/{channelId}?group={group}&query={query}&favoritesOnly={favoritesOnly}"
    const val HISTORY_PLAYER = "history-player/{channelId}?sourceKey={sourceKey}"
    const val EPG = "epg?channelId={channelId}&group={group}&query={query}&favoritesOnly={favoritesOnly}"
    const val PLAYER_APPEARANCE = "player-appearance"
}
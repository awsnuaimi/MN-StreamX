package com.yourapp.iptv.navigation

object Destinations {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val PLAYER = "player/{channelId}?group={group}&query={query}&favoritesOnly={favoritesOnly}"
    const val EPG = "epg?channelId={channelId}&group={group}&query={query}&favoritesOnly={favoritesOnly}"
}
package com.yourapp.iptv.navigation

object Destinations {
    const val DIAGNOSTICS = "diagnostics"
    const val BACKUP = "backup"
    const val SEARCH = "search"
    const val SUBSCRIPTIONS = "subscriptions"
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val HISTORY = "history"
    const val PLAYER = "player/{channelId}?group={group}&query={query}&favoritesOnly={favoritesOnly}"
    const val HISTORY_PLAYER = "history-player/{channelId}?sourceKey={sourceKey}"
    const val EPG = "epg?channelId={channelId}&group={group}&query={query}&favoritesOnly={favoritesOnly}"
    const val PLAYER_APPEARANCE = "player-appearance"
    const val VOD_PLAYER = "vod-play/{kind}/{id}/{ext}?title={title}&sourceKey={sourceKey}"
    const val VOD_EPISODE_PLAYER = "vod-episode/{kind}/{id}/{ext}?title={title}&sourceKey={sourceKey}&seriesId={seriesId}"
}
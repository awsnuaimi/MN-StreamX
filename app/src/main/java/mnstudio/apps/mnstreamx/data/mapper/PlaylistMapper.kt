package mnstudio.apps.mnstreamx.data.mapper

import mnstudio.apps.mnstreamx.domain.model.Channel
import mnstudio.apps.mnstreamx.domain.model.Playlist

fun List<Channel>.toPlaylist(name: String = "My Playlist"): Playlist {
    return Playlist(
        name = name,
        channels = this
    )
}
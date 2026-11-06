package mnstudio.apps.mnstreamx.domain.usecase

import mnstudio.apps.mnstreamx.domain.model.Playlist
import mnstudio.apps.mnstreamx.domain.repository.PlaylistRepository

class LoadPlaylistUseCase(
    private val playlistRepository: PlaylistRepository
) {
    suspend operator fun invoke(url: String): Playlist {
        return playlistRepository.loadPlaylist(url)
    }
}
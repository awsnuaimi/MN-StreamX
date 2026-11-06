package mnstudio.apps.mnstreamx.ui.state

import mnstudio.apps.mnstreamx.domain.model.PlayerAppearance

data class PlayerAppearanceUiState(
    val appearance: PlayerAppearance = PlayerAppearance(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val loadFailed: Boolean = false,
    val errorMessage: String? = null
)
package com.yourapp.iptv.ui.state

data class FavoriteUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
) {
    val canToggle: Boolean get() = !isLoading && !isSaving
}
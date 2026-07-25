package com.yourapp.iptv.domain.model

sealed class AppError {
    data object Network : AppError()
    data object InvalidUrl : AppError()
    data object Unauthorized : AppError()
    data object PlaylistNotFound : AppError()
    data class Unknown(val message: String) : AppError()
}
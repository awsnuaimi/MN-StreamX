package com.yourapp.iptv.data.network

import com.yourapp.iptv.domain.model.AppError

sealed class ApiResult<out T> {
    data class Success<out T>(val data: T) : ApiResult<T>()
    data class Error(val error: AppError) : ApiResult<Nothing>()
    data object Loading : ApiResult<Nothing>()
}
package com.invoiceapp.network

sealed class ApiResponse<T> {
    data class Success<T>(val data: T) : ApiResponse<T>()
    data class Error<T>(val message: String, val code: Int? = null) : ApiResponse<T>()
    data class Loading<T>(val isLoading: Boolean) : ApiResponse<T>()
}
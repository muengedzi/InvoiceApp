package com.invoiceapp.models

data class User(
    val id: Int,
    val email: String,
    val full_name: String,
    val role: String,
    val business_name: String? = null
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class LoginResponse(
    val success: Boolean,
    val user: User,
    val message: String? = null
)

data class RegisterRequest(
    val full_name: String,
    val email: String,
    val password: String,
    val business_name: String? = null
)

data class RegisterResponse(
    val success: Boolean,
    val message: String
)
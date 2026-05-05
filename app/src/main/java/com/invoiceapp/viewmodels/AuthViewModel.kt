// viewmodels/AuthViewModel.kt
package com.invoiceapp.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.invoiceapp.models.LoginRequest
import com.invoiceapp.models.RegisterRequest
import com.invoiceapp.network.ApiClient
import com.invoiceapp.network.ApiResponse
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {
    private val _loginResult = MutableLiveData<ApiResponse<Any>>()
    val loginResult: LiveData<ApiResponse<Any>> = _loginResult

    private val _registerResult = MutableLiveData<ApiResponse<Any>>()
    val registerResult: LiveData<ApiResponse<Any>> = _registerResult

    fun login(email: String, password: String) {
        _loginResult.value = ApiResponse.Loading(true)
        viewModelScope.launch {
            try {
                val response = ApiClient.apiService.login(LoginRequest(email, password))
                if (response.isSuccessful && response.body()?.success == true) {
                    _loginResult.value = ApiResponse.Success(response.body()!!)
                } else {
                    _loginResult.value = ApiResponse.Error(response.body()?.message ?: "Login failed")
                }
            } catch (e: Exception) {
                _loginResult.value = ApiResponse.Error(e.message ?: "Network error")
            }
        }
    }

    fun register(fullName: String, email: String, password: String, businessName: String?) {
        _registerResult.value = ApiResponse.Loading(true)
        viewModelScope.launch {
            try {
                val response = ApiClient.apiService.register(RegisterRequest(fullName, email, password, businessName))
                if (response.isSuccessful && response.body()?.success == true) {
                    _registerResult.value = ApiResponse.Success(response.body()!!)
                } else {
                    _registerResult.value = ApiResponse.Error(response.body()?.message ?: "Registration failed")
                }
            } catch (e: Exception) {
                _registerResult.value = ApiResponse.Error(e.message ?: "Network error")
            }
        }
    }
}
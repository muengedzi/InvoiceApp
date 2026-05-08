package com.invoiceapp.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.invoiceapp.models.CompanySettings
import com.invoiceapp.network.ApiResponse
import com.invoiceapp.network.RetrofitClient
import kotlinx.coroutines.launch

class SettingsViewModel : ViewModel() {
    private val _settings = MutableLiveData<ApiResponse<CompanySettings>>()
    val settings: LiveData<ApiResponse<CompanySettings>> = _settings

    private val _updateResult = MutableLiveData<ApiResponse<Any>>()
    val updateResult: LiveData<ApiResponse<Any>> = _updateResult

    fun loadSettings() {
        _settings.value = ApiResponse.Loading(true)
        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.getCompanySettings()
                if (response.isSuccessful && response.body() != null) {
                    _settings.value = ApiResponse.Success(response.body()!!)
                } else {
                    _settings.value = ApiResponse.Error("Failed to load settings")
                }
            } catch (e: Exception) {
                _settings.value = ApiResponse.Error(e.message ?: "Network error")
            }
        }
    }

    fun updateSettings(settings: CompanySettings) {
        _updateResult.value = ApiResponse.Loading(true)
        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.updateCompanySettings(settings)
                if (response.isSuccessful && response.body() != null) {
                    _updateResult.value = ApiResponse.Success(response.body()!!)
                } else {
                    _updateResult.value = ApiResponse.Error("Failed to update settings")
                }
            } catch (e: Exception) {
                _updateResult.value = ApiResponse.Error(e.message ?: "Network error")
            }
        }
    }
}
package com.invoiceapp.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.invoiceapp.models.DashboardStats
import com.invoiceapp.network.ApiClient
import com.invoiceapp.network.ApiResponse
import kotlinx.coroutines.launch

class DashboardViewModel : ViewModel() {
    private val _dashboardStats = MutableLiveData<ApiResponse<DashboardStats>>()
    val dashboardStats: LiveData<ApiResponse<DashboardStats>> = _dashboardStats

    fun loadDashboardStats() {
        _dashboardStats.value = ApiResponse.Loading(true)
        viewModelScope.launch {
            try {
                val response = ApiClient.apiService.getDashboardStats()
                if (response.isSuccessful && response.body() != null) {
                    _dashboardStats.value = ApiResponse.Success(response.body()!!)
                } else {
                    _dashboardStats.value = ApiResponse.Error("Failed to load dashboard data")
                }
            } catch (e: Exception) {
                _dashboardStats.value = ApiResponse.Error(e.message ?: "Network error")
            }
        }
    }
}
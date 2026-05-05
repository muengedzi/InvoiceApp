package com.invoiceapp.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.invoiceapp.models.ProfitLossReport
import com.invoiceapp.network.ApiClient
import com.invoiceapp.network.ApiResponse
import kotlinx.coroutines.launch

class ReportViewModel : ViewModel() {
    private val _profitLossReport = MutableLiveData<ApiResponse<ProfitLossReport>>()
    val profitLossReport: LiveData<ApiResponse<ProfitLossReport>> = _profitLossReport

    fun loadProfitLossReport(startDate: String, endDate: String) {
        _profitLossReport.value = ApiResponse.Loading(true)
        viewModelScope.launch {
            try {
                val response = ApiClient.apiService.getProfitLossReport(startDate, endDate)
                if (response.isSuccessful && response.body() != null) {
                    _profitLossReport.value = ApiResponse.Success(response.body()!!)
                } else {
                    _profitLossReport.value = ApiResponse.Error("Failed to load report data")
                }
            } catch (e: Exception) {
                _profitLossReport.value = ApiResponse.Error(e.message ?: "Network error")
            }
        }
    }
}
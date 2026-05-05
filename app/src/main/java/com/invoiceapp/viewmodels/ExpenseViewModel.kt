package com.invoiceapp.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.invoiceapp.models.Expense
import com.invoiceapp.models.ExpenseRequest
import com.invoiceapp.network.ApiClient
import com.invoiceapp.network.ApiResponse
import kotlinx.coroutines.launch

class ExpenseViewModel : ViewModel() {
    private val _expenses = MutableLiveData<ApiResponse<List<Expense>>>()
    val expenses: LiveData<ApiResponse<List<Expense>>> = _expenses

    private val _createResult = MutableLiveData<ApiResponse<Any>>()
    val createResult: LiveData<ApiResponse<Any>> = _createResult

    fun loadExpenses(category: String? = null) {
        _expenses.value = ApiResponse.Loading(true)
        viewModelScope.launch {
            try {
                val response = ApiClient.apiService.getExpenses(1, 100, category)
                if (response.isSuccessful && response.body() != null) {
                    _expenses.value = ApiResponse.Success(response.body()!!.expenses)
                } else {
                    _expenses.value = ApiResponse.Error("Failed to load expenses")
                }
            } catch (e: Exception) {
                _expenses.value = ApiResponse.Error(e.message ?: "Network error")
            }
        }
    }

    fun createExpense(request: ExpenseRequest) {
        _createResult.value = ApiResponse.Loading(true)
        viewModelScope.launch {
            try {
                val response = ApiClient.apiService.createExpense(request)
                if (response.isSuccessful && response.body() != null) {
                    _createResult.value = ApiResponse.Success(response.body()!!)
                    loadExpenses() // Refresh list
                } else {
                    _createResult.value = ApiResponse.Error("Failed to create expense")
                }
            } catch (e: Exception) {
                _createResult.value = ApiResponse.Error(e.message ?: "Network error")
            }
        }
    }
}
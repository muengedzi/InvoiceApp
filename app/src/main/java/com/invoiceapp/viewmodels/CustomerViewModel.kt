package com.invoiceapp.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.invoiceapp.models.Customer
import com.invoiceapp.network.ApiClient
import com.invoiceapp.network.ApiResponse
import kotlinx.coroutines.launch

class CustomerViewModel : ViewModel() {
    private val _customers = MutableLiveData<ApiResponse<List<Customer>>>()
    val customers: LiveData<ApiResponse<List<Customer>>> = _customers

    private val _createResult = MutableLiveData<ApiResponse<Any>>()
    val createResult: LiveData<ApiResponse<Any>> = _createResult

    private val _deleteResult = MutableLiveData<ApiResponse<Any>>()
    val deleteResult: LiveData<ApiResponse<Any>> = _deleteResult

    fun loadCustomers(search: String? = null) {
        _customers.value = ApiResponse.Loading(true)
        viewModelScope.launch {
            try {
                val response = ApiClient.apiService.getCustomers(1, 100, search)
                if (response.isSuccessful && response.body() != null) {
                    _customers.value = ApiResponse.Success(response.body()!!.customers)
                } else {
                    _customers.value = ApiResponse.Error("Failed to load customers")
                }
            } catch (e: Exception) {
                _customers.value = ApiResponse.Error(e.message ?: "Network error")
            }
        }
    }

    fun createCustomer(request: com.invoiceapp.models.CustomerRequest) {
        _createResult.value = ApiResponse.Loading(true)
        viewModelScope.launch {
            try {
                val response = ApiClient.apiService.createCustomer(request)
                if (response.isSuccessful && response.body() != null) {
                    _createResult.value = ApiResponse.Success(response.body()!!)
                    loadCustomers()
                } else {
                    _createResult.value = ApiResponse.Error("Failed to create customer")
                }
            } catch (e: Exception) {
                _createResult.value = ApiResponse.Error(e.message ?: "Network error")
            }
        }
    }

    fun deleteCustomer(customerId: Int) {
        _deleteResult.value = ApiResponse.Loading(true)
        viewModelScope.launch {
            try {
                val response = ApiClient.apiService.deleteCustomer(customerId)
                if (response.isSuccessful && response.body() != null) {
                    _deleteResult.value = ApiResponse.Success(response.body()!!)
                    loadCustomers()
                } else {
                    _deleteResult.value = ApiResponse.Error("Failed to delete customer")
                }
            } catch (e: Exception) {
                _deleteResult.value = ApiResponse.Error(e.message ?: "Network error")
            }
        }
    }
}
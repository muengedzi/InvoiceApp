// viewmodels/InvoiceViewModel.kt
package com.invoiceapp.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.invoiceapp.models.Invoice
import com.invoiceapp.models.InvoiceRequest
import com.invoiceapp.models.PaymentRequest
import com.invoiceapp.network.ApiClient
import com.invoiceapp.network.ApiResponse
import kotlinx.coroutines.launch

class InvoiceViewModel : ViewModel() {
    private val _invoices = MutableLiveData<ApiResponse<List<Invoice>>>()
    val invoices: LiveData<ApiResponse<List<Invoice>>> = _invoices

    private val _invoiceDetail = MutableLiveData<ApiResponse<Invoice>>()
    val invoiceDetail: LiveData<ApiResponse<Invoice>> = _invoiceDetail

    private val _createResult = MutableLiveData<ApiResponse<Any>>()
    val createResult: LiveData<ApiResponse<Any>> = _createResult

    private val _paymentResult = MutableLiveData<ApiResponse<Any>>()
    val paymentResult: LiveData<ApiResponse<Any>> = _paymentResult

    private val _pdfResult = MutableLiveData<ApiResponse<ByteArray>>()
    val pdfResult: LiveData<ApiResponse<ByteArray>> = _pdfResult

    fun loadInvoices(page: Int = 1, status: String? = null) {
        _invoices.value = ApiResponse.Loading(true)
        viewModelScope.launch {
            try {
                val response = ApiClient.apiService.getInvoices(page, 20, status)
                if (response.isSuccessful && response.body() != null) {
                    _invoices.value = ApiResponse.Success(response.body()!!.invoices)
                } else {
                    _invoices.value = ApiResponse.Error("Failed to load invoices")
                }
            } catch (e: Exception) {
                _invoices.value = ApiResponse.Error(e.message ?: "Network error")
            }
        }
    }

    fun getInvoiceDetail(invoiceId: Int) {
        _invoiceDetail.value = ApiResponse.Loading(true)
        viewModelScope.launch {
            try {
                val response = ApiClient.apiService.getInvoice(invoiceId)
                if (response.isSuccessful && response.body() != null) {
                    _invoiceDetail.value = ApiResponse.Success(response.body()!!)
                } else {
                    _invoiceDetail.value = ApiResponse.Error("Failed to load invoice details")
                }
            } catch (e: Exception) {
                _invoiceDetail.value = ApiResponse.Error(e.message ?: "Network error")
            }
        }
    }

    fun createInvoice(request: InvoiceRequest) {
        _createResult.value = ApiResponse.Loading(true)
        viewModelScope.launch {
            try {
                val response = ApiClient.apiService.createInvoice(request)
                if (response.isSuccessful && response.body() != null) {
                    _createResult.value = ApiResponse.Success(response.body()!!)
                } else {
                    _createResult.value = ApiResponse.Error("Failed to create invoice")
                }
            } catch (e: Exception) {
                _createResult.value = ApiResponse.Error(e.message ?: "Network error")
            }
        }
    }

    fun recordPayment(invoiceId: Int, request: PaymentRequest) {
        _paymentResult.value = ApiResponse.Loading(true)
        viewModelScope.launch {
            try {
                val response = ApiClient.apiService.recordPayment(invoiceId, request)
                if (response.isSuccessful && response.body() != null) {
                    _paymentResult.value = ApiResponse.Success(response.body()!!)
                } else {
                    _paymentResult.value = ApiResponse.Error("Failed to record payment")
                }
            } catch (e: Exception) {
                _paymentResult.value = ApiResponse.Error(e.message ?: "Network error")
            }
        }
    }

    fun downloadInvoicePdf(invoiceId: Int) {
        _pdfResult.value = ApiResponse.Loading(true)
        viewModelScope.launch {
            try {
                val response = ApiClient.apiService.downloadInvoicePdf(invoiceId)
                if (response.isSuccessful && response.body() != null) {
                    _pdfResult.value = ApiResponse.Success(response.body()!!.bytes())
                } else {
                    _pdfResult.value = ApiResponse.Error("Failed to download PDF")
                }
            } catch (e: Exception) {
                _pdfResult.value = ApiResponse.Error(e.message ?: "Network error")
            }
        }
    }
}
package com.invoiceapp.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.invoiceapp.activities.InvoiceDetailActivity
import com.invoiceapp.models.Invoice
import com.invoiceapp.models.InvoiceRequest
import com.invoiceapp.models.Payment
import com.invoiceapp.models.PaymentListResponse
import com.invoiceapp.models.PaymentRequest
import com.invoiceapp.network.ApiResponse
import com.invoiceapp.network.RetrofitClient
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class InvoiceViewModel : ViewModel() {
    private val _invoices = MutableLiveData<ApiResponse<List<Invoice>>>()
    val invoices: LiveData<ApiResponse<List<Invoice>>> = _invoices

    private val _invoiceDetail = MutableLiveData<ApiResponse<Invoice>>()
    val invoiceDetail: LiveData<ApiResponse<Invoice>> = _invoiceDetail

    private val _createResult = MutableLiveData<ApiResponse<Any>>()
    val createResult: LiveData<ApiResponse<Any>> = _createResult

    private val _paymentResult = MutableLiveData<ApiResponse<Any>>()
    val paymentResult: LiveData<ApiResponse<Any>> = _paymentResult

    private val _reversePaymentResult = MutableLiveData<ApiResponse<Any>>()
    val reversePaymentResult: LiveData<ApiResponse<Any>> = _reversePaymentResult

    private val _reapplyPaymentResult = MutableLiveData<ApiResponse<Any>>()
    val reapplyPaymentResult: LiveData<ApiResponse<Any>> = _reapplyPaymentResult

    private val _deletePaymentResult = MutableLiveData<ApiResponse<Any>>()
    val deletePaymentResult: LiveData<ApiResponse<Any>> = _deletePaymentResult

    private val _payments = MutableLiveData<ApiResponse<PaymentListResponse>>()
    val payments: LiveData<ApiResponse<PaymentListResponse>> = _payments

    private val _sendResult = MutableLiveData<ApiResponse<Any>>()
    val sendResult: LiveData<ApiResponse<Any>> = _sendResult

    private val _pdfResult = MutableLiveData<ApiResponse<File>>()
    val pdfResult: LiveData<ApiResponse<File>> = _pdfResult

    fun loadInvoices(page: Int = 1, status: String? = null) {
        loadInvoicesWithFilters(page, status, null, null, null)
    }

    fun loadInvoicesWithFilters(
        page: Int = 1,
        status: String? = null,
        customerId: Int? = null,
        dateFrom: String? = null,
        dateTo: String? = null
    ) {
        _invoices.value = ApiResponse.Loading(true)
        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.getInvoices(
                    page = page,
                    perPage = 20,
                    status = status,
                    customerId = customerId,
                    dateFrom = dateFrom,
                    dateTo = dateTo
                )
                if (response.isSuccessful && response.body() != null) {
                    val invoiceResponse = response.body()!!
                    _invoices.value = ApiResponse.Success(invoiceResponse.invoices ?: emptyList())
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
                val response = RetrofitClient.apiService.getInvoice(invoiceId)
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
                val response = RetrofitClient.apiService.createInvoice(request)
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

    fun createInvoiceAsDraft(request: InvoiceRequest) {
        _createResult.value = ApiResponse.Loading(true)
        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.createInvoiceAsDraft(request)
                if (response.isSuccessful && response.body() != null) {
                    _createResult.value = ApiResponse.Success(response.body()!!)
                } else {
                    _createResult.value = ApiResponse.Error("Failed to save draft")
                }
            } catch (e: Exception) {
                _createResult.value = ApiResponse.Error(e.message ?: "Network error")
            }
        }
    }

    fun updateInvoice(id: Int, request: InvoiceRequest) {
        _createResult.value = ApiResponse.Loading(true)
        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.updateInvoice(id, request)
                if (response.isSuccessful && response.body() != null) {
                    _createResult.value = ApiResponse.Success(response.body()!!)
                } else {
                    _createResult.value = ApiResponse.Error("Failed to update invoice: ${response.message()}")
                }
            } catch (e: Exception) {
                _createResult.value = ApiResponse.Error(e.message ?: "Network error")
            }
        }
    }

    fun updateInvoiceAsDraft(id: Int, request: InvoiceRequest) {
        _createResult.value = ApiResponse.Loading(true)
        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.updateInvoiceAsDraft(id, request)
                if (response.isSuccessful && response.body() != null) {
                    _createResult.value = ApiResponse.Success(response.body()!!)
                } else {
                    _createResult.value = ApiResponse.Error("Failed to update draft")
                }
            } catch (e: Exception) {
                _createResult.value = ApiResponse.Error(e.message ?: "Network error")
            }
        }
    }

    fun sendInvoice(id: Int, request: InvoiceRequest? = null) {
        _sendResult.value = ApiResponse.Loading(true)
        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.sendInvoice(id, request ?: emptyMap<String, Any>())
                if (response.isSuccessful && response.body() != null) {
                    _sendResult.value = ApiResponse.Success(response.body()!!)
                } else {
                    _sendResult.value = ApiResponse.Error("Failed to send invoice")
                }
            } catch (e: Exception) {
                _sendResult.value = ApiResponse.Error(e.message ?: "Network error")
            }
        }
    }

    fun deleteInvoice(id: Int) {
        _createResult.value = ApiResponse.Loading(true)
        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.deleteInvoice(id)
                if (response.isSuccessful && response.body() != null) {
                    _createResult.value = ApiResponse.Success(response.body()!!)
                } else {
                    _createResult.value = ApiResponse.Error("Failed to delete invoice")
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
                val response = RetrofitClient.apiService.recordPayment(invoiceId, request)
                if (response.isSuccessful && response.body() != null) {
                    _paymentResult.value = ApiResponse.Success(response.body()!!)
                    // Refresh invoice details after payment
                    getInvoiceDetail(invoiceId)
                } else {
                    _paymentResult.value = ApiResponse.Error("Failed to record payment")
                }
            } catch (e: Exception) {
                _paymentResult.value = ApiResponse.Error(e.message ?: "Network error")
            }
        }
    }

    fun reversePayment(paymentId: Int, reason: String = "") {
        _reversePaymentResult.value = ApiResponse.Loading(true)
        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.reversePayment(paymentId, mapOf("reason" to reason))
                if (response.isSuccessful && response.body() != null) {
                    _reversePaymentResult.value = ApiResponse.Success(response.body()!!)
                } else {
                    _reversePaymentResult.value = ApiResponse.Error("Failed to reverse payment")
                }
            } catch (e: Exception) {
                _reversePaymentResult.value = ApiResponse.Error(e.message ?: "Network error")
            }
        }
    }

    fun reapplyPayment(paymentId: Int) {
        _reapplyPaymentResult.value = ApiResponse.Loading(true)
        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.reapplyPayment(paymentId)
                if (response.isSuccessful && response.body() != null) {
                    _reapplyPaymentResult.value = ApiResponse.Success(response.body()!!)
                } else {
                    _reapplyPaymentResult.value = ApiResponse.Error("Failed to reapply payment")
                }
            } catch (e: Exception) {
                _reapplyPaymentResult.value = ApiResponse.Error(e.message ?: "Network error")
            }
        }
    }

    fun deletePayment(paymentId: Int) {
        _deletePaymentResult.value = ApiResponse.Loading(true)
        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.deletePayment(paymentId)
                if (response.isSuccessful && response.body() != null) {
                    _deletePaymentResult.value = ApiResponse.Success(response.body()!!)
                } else {
                    _deletePaymentResult.value = ApiResponse.Error("Failed to delete payment")
                }
            } catch (e: Exception) {
                _deletePaymentResult.value = ApiResponse.Error(e.message ?: "Network error")
            }
        }
    }

    fun getInvoicePayments(invoiceId: Int) {
        _payments.value = ApiResponse.Loading(true)
        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.getInvoicePayments(invoiceId)
                if (response.isSuccessful && response.body() != null) {
                    _payments.value = ApiResponse.Success(response.body()!!)
                } else {
                    _payments.value = ApiResponse.Error("Failed to load payments")
                }
            } catch (e: Exception) {
                _payments.value = ApiResponse.Error(e.message ?: "Network error")
            }
        }
    }

    fun downloadInvoicePdf(invoiceId1: InvoiceDetailActivity, invoiceId: Int) {
        _pdfResult.value = ApiResponse.Loading(true)
        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.downloadInvoicePdf(invoiceId)
                if (response.isSuccessful && response.body() != null) {
                    val pdfBytes = response.body()!!.bytes()
                    val pdfFile = File(invoiceId1.cacheDir, "invoice_${invoiceId}_${System.currentTimeMillis()}.pdf")
                    FileOutputStream(pdfFile).use { outputStream ->
                        outputStream.write(pdfBytes)
                    }
                    _pdfResult.value = ApiResponse.Success(pdfFile)
                } else {
                    val errorMessage = response.errorBody()?.string()
                        ?.takeIf { it.isNotBlank() }
                        ?: response.message()
                        ?: "Failed to download PDF"
                    _pdfResult.value = ApiResponse.Error(errorMessage)
                }
            } catch (e: Exception) {
                _pdfResult.value = ApiResponse.Error(e.message ?: "Network error")
            }
        }
    }
}
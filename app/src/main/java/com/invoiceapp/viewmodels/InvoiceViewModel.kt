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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

    // Pagination state
    private var currentPage = 1
    private var isLoadingMore = false
    private var hasMorePages = true
    private var allInvoices = mutableListOf<Invoice>()
    private var totalInvoices = 0

    // Cache for invoice details to prevent redundant network calls
    private val invoiceDetailCache = mutableMapOf<Int, Invoice>()

    fun loadInvoices(page: Int = 1, status: String? = null, resetList: Boolean = true) {
        if (resetList) {
            // Reset pagination state
            currentPage = 1
            hasMorePages = true
            isLoadingMore = false
            allInvoices.clear()
        }

        _invoices.value = ApiResponse.Loading(true)
        loadInvoicesWithFilters(page, status, null, null, null, null, resetList)
    }

    fun loadInvoicesWithFilters(
        page: Int = 1,
        status: String? = null,
        customerId: Int? = null,
        dateFrom: String? = null,
        dateTo: String? = null,
        search: String? = null,
        resetList: Boolean = true,
        perPage: Int = 20
    ) {
        if (resetList) {
            currentPage = page
            allInvoices.clear()
            hasMorePages = true
            isLoadingMore = false
            _invoices.value = ApiResponse.Loading(true)
        } else if (isLoadingMore) {
            return // Already loading more
        }

        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.getInvoices(
                    page = page,
                    perPage = perPage,
                    status = status,
                    customerId = customerId,
                    dateFrom = dateFrom,
                    dateTo = dateTo,
                    search = search
                )

                if (response.isSuccessful && response.body() != null) {
                    val invoiceResponse = response.body()!!
                    val newInvoices = invoiceResponse.invoices ?: emptyList()
                    totalInvoices = invoiceResponse.total
                    hasMorePages = newInvoices.size == 500 || allInvoices.size + newInvoices.size < totalInvoices

                    if (resetList) {
                        allInvoices.clear()
                        allInvoices.addAll(newInvoices)
                    } else {
                        allInvoices.addAll(newInvoices)
                    }

                    _invoices.value = ApiResponse.Success(allInvoices.toList())
                    currentPage = page
                    isLoadingMore = false
                } else {
                    _invoices.value = ApiResponse.Error("Failed to load invoices")
                }
            } catch (e: Exception) {
                _invoices.value = ApiResponse.Error(e.message ?: "Network error")
                isLoadingMore = false
            }
        }
    }

    // Load more invoices for pagination (scroll to load)
    fun loadMoreInvoices(status: String? = null, customerId: Int? = null, search: String? = null) {
        if (!hasMorePages || isLoadingMore) return

        isLoadingMore = true
        val nextPage = currentPage + 1

        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.getInvoices(
                    page = nextPage,
                    perPage = 500,
                    status = status,
                    customerId = customerId,
                    search = search
                )

                if (response.isSuccessful && response.body() != null) {
                    val invoiceResponse = response.body()!!
                    val newInvoices = invoiceResponse.invoices ?: emptyList()

                    if (newInvoices.isNotEmpty()) {
                        allInvoices.addAll(newInvoices)
                        _invoices.value = ApiResponse.Success(allInvoices.toList())
                        currentPage = nextPage
                    }

                    hasMorePages = newInvoices.size == 500 && allInvoices.size < totalInvoices
                }
            } catch (e: Exception) {
                // Don't show error for pagination failures to avoid disrupting UX
                e.printStackTrace()
            } finally {
                isLoadingMore = false
            }
        }
    }

    fun getInvoiceDetail(invoiceId: Int, useCache: Boolean = true) {
        // Check cache first for performance
        if (useCache && invoiceDetailCache.containsKey(invoiceId)) {
            _invoiceDetail.value = ApiResponse.Success(invoiceDetailCache[invoiceId]!!)
            return
        }

        _invoiceDetail.value = ApiResponse.Loading(true)
        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.getInvoice(invoiceId)
                if (response.isSuccessful && response.body() != null) {
                    val invoice = response.body()!!
                    // Cache the result
                    invoiceDetailCache[invoiceId] = invoice
                    _invoiceDetail.value = ApiResponse.Success(invoice)
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
                    // Clear cache after creating new invoice
                    clearCache()
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
                    clearCache()
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
                    // Invalidate cache for this invoice
                    invoiceDetailCache.remove(id)
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
                    invoiceDetailCache.remove(id)
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
                    invoiceDetailCache.remove(id)
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
                    // Remove from cache and list
                    invoiceDetailCache.remove(id)
                    allInvoices.removeAll { it.id == id }
                    _invoices.value = ApiResponse.Success(allInvoices.toList())
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
                    // Invalidate cache to refresh data
                    invoiceDetailCache.remove(invoiceId)
                    getInvoiceDetail(invoiceId, useCache = false)
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

    fun getInvoicePayments(invoiceId: Int, refresh: Boolean = false) {
        // If not refreshing and we have cached payments, don't reload
        if (!refresh && _payments.value is ApiResponse.Success) {
            return
        }

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

    // Helper method to refresh invoices list
    fun refreshInvoices(status: String? = null, customerId: Int? = null, search: String? = null) {
        loadInvoicesWithFilters(1, status, customerId, null, null, search, resetList = true)
    }

    // Clear cache when logging out or when data becomes stale
    fun clearCache() {
        invoiceDetailCache.clear()
    }

    // Get current state for UI
    fun hasMoreItems(): Boolean = hasMorePages
    fun isLoadingMore(): Boolean = isLoadingMore
    fun getTotalInvoices(): Int = totalInvoices
    fun getLoadedInvoiceCount(): Int = allInvoices.size

    // Load all invoices (best for reports/export)
    suspend fun loadAllInvoices(): List<Invoice> = coroutineScope {
        val allResults = mutableListOf<Invoice>()
        var currentPage = 1
        var hasMore = true

        while (hasMore) {
            try {
                val response = RetrofitClient.apiService.getInvoices(
                    page = currentPage,
                    perPage = 500
                )
                if (response.isSuccessful && response.body() != null) {
                    val invoices = response.body()!!.invoices ?: emptyList()
                    allResults.addAll(invoices)
                    hasMore = invoices.size == 500
                    currentPage++
                } else {
                    hasMore = false
                }
            } catch (e: Exception) {
                hasMore = false
            }
        }

        allResults
    }
}
package com.invoiceapp.network

import com.invoiceapp.models.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // ==================== AUTH ENDPOINTS ====================
    @POST("/api/register")
    suspend fun register(@Body request: RegisterRequest): Response<RegisterResponse>

    @POST("/api/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("/api/logout")
    suspend fun logout(): Response<Map<String, Any>>

    // ==================== CUSTOMER ENDPOINTS ====================
    @GET("/api/customers")
    suspend fun getCustomers(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20,
        @Query("search") search: String? = null
    ): Response<CustomerListResponse>

    @POST("/api/customers")
    suspend fun createCustomer(@Body request: CustomerRequest): Response<Map<String, Any>>

    @PUT("/api/customers/{id}")
    suspend fun updateCustomer(
        @Path("id") id: Int,
        @Body request: CustomerRequest
    ): Response<Map<String, Any>>

    @DELETE("/api/customers/{id}")
    suspend fun deleteCustomer(@Path("id") id: Int): Response<Map<String, Any>>

    // ==================== INVOICE ENDPOINTS ====================
    @GET("/api/invoices")
    suspend fun getInvoices(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20,
        @Query("status") status: String? = null
    ): Response<InvoiceListResponse>

    @GET("/api/invoices/{id}")
    suspend fun getInvoice(@Path("id") id: Int): Response<Invoice>

    @POST("/api/invoices")
    suspend fun createInvoice(@Body request: InvoiceRequest): Response<Map<String, Any>>

    @PUT("/api/invoices/{id}")
    suspend fun updateInvoice(
        @Path("id") id: Int,
        @Body request: InvoiceRequest
    ): Response<Map<String, Any>>

    @DELETE("/api/invoices/{id}")
    suspend fun deleteInvoice(@Path("id") id: Int): Response<Map<String, Any>>

    @POST("/api/invoices/draft")
    suspend fun createInvoiceAsDraft(@Body request: InvoiceRequest): Response<Map<String, Any>>

    @PUT("/api/invoices/{id}/draft")
    suspend fun updateInvoiceAsDraft(
        @Path("id") id: Int,
        @Body request: InvoiceRequest
    ): Response<Map<String, Any>>

    @POST("/api/invoices/{id}/send")
    suspend fun sendInvoice(
        @Path("id") id: Int,
        @Body request: Any
    ): Response<Map<String, Any>>

    // ==================== PAYMENT ENDPOINTS ====================
    @POST("/api/invoices/{id}/payments")
    suspend fun recordPayment(
        @Path("id") id: Int,
        @Body request: PaymentRequest
    ): Response<Map<String, Any>>

    // ==================== EXPENSE ENDPOINTS ====================
    @GET("/api/expenses")
    suspend fun getExpenses(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
        @Query("category") category: String? = null
    ): Response<ExpenseListResponse>

    @POST("/api/expenses")
    suspend fun createExpense(@Body request: ExpenseRequest): Response<Map<String, Any>>

    @GET("/api/expenses/{id}")
    suspend fun getExpense(@Path("id") id: Int): Response<Expense>

    @PUT("/api/expenses/{id}")
    suspend fun updateExpense(
        @Path("id") id: Int,
        @Body request: ExpenseRequest
    ): Response<Map<String, Any>>

    @DELETE("/api/expenses/{id}")
    suspend fun deleteExpense(@Path("id") id: Int): Response<Map<String, Any>>

    // ==================== BUDGET ENDPOINTS ====================
    @GET("/api/budgets")
    suspend fun getBudgets(
        @Query("month") month: Int,
        @Query("year") year: Int
    ): Response<BudgetResponse>

    @POST("/api/budgets")
    suspend fun saveBudget(@Body request: BudgetRequest): Response<Map<String, Any>>

    // ==================== DASHBOARD ENDPOINTS ====================
    @GET("/api/dashboard/stats")
    suspend fun getDashboardStats(): Response<DashboardStats>

    // ==================== REPORT ENDPOINTS ====================
    @GET("/api/reports/profit-loss")
    suspend fun getProfitLossReport(
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String
    ): Response<ProfitLossReport>

    // ==================== PDF ENDPOINTS ====================
    @GET("/api/invoices/{id}/pdf")
    @Streaming
    suspend fun downloadInvoicePdf(@Path("id") id: Int): Response<okhttp3.ResponseBody>

    // ==================== COMPANY SETTINGS ====================
    @GET("/api/company-settings")
    suspend fun getCompanySettings(): Response<CompanySettings>

    @PUT("/api/company-settings")
    suspend fun updateCompanySettings(@Body request: CompanySettings): Response<Map<String, Any>>

    // Add this to ApiService.kt
    @POST("/api/verify-email")
    suspend fun verifyEmail(@Body token: Map<String, String>): Response<Map<String, Any>>

    // ==================== INVOICE ITEMS MASTER ENDPOINTS ====================
    @GET("/api/invoice-items")
    suspend fun getInvoiceItems(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
        @Query("search") search: String? = null,
        @Query("category_id") categoryId: Int? = null
    ): Response<InvoiceItemMasterListResponse>

    @POST("/api/invoice-items")
    suspend fun createInvoiceItem(@Body request: InvoiceItemMasterRequest): Response<Map<String, Any>>

    @PUT("/api/invoice-items/{id}")
    suspend fun updateInvoiceItem(
        @Path("id") id: Int,
        @Body request: InvoiceItemMasterRequest
    ): Response<Map<String, Any>>

    @DELETE("/api/invoice-items/{id}")
    suspend fun deleteInvoiceItem(@Path("id") id: Int): Response<Map<String, Any>>

    // ==================== INVOICE ITEM CATEGORIES ENDPOINTS ====================
    @GET("/api/invoice-item-categories")
    suspend fun getInvoiceItemCategories(): Response<List<InvoiceItemCategory>>

    @POST("/api/invoice-item-categories")
    suspend fun createInvoiceItemCategory(@Body request: Map<String, String>): Response<Map<String, Any>>

    @PUT("/api/invoice-item-categories/{id}")
    suspend fun updateInvoiceItemCategory(
        @Path("id") id: Int,
        @Body request: Map<String, String>
    ): Response<Map<String, Any>>

    @DELETE("/api/invoice-item-categories/{id}")
    suspend fun deleteInvoiceItemCategory(@Path("id") id: Int): Response<Map<String, Any>>

    // ==================== INVOICE COMMUNICATION ENDPOINTS ====================
    @GET("/api/invoices/{id}/communications")
    suspend fun getInvoiceCommunications(@Path("id") id: Int): Response<List<InvoiceCommunication>>

    @POST("/api/invoices/send-email")
    suspend fun sendInvoiceEmail(@Body request: SendInvoiceEmailRequest): Response<Map<String, Any>>

    @POST("/api/invoices/send-reply")
    suspend fun sendReply(@Body request: SendReplyRequest): Response<Map<String, Any>>

    @GET("/api/invoices/communications/summary")
    suspend fun getCommunicationSummary(): Response<InvoiceCommunicationSummary>

    @GET("/api/invoices/follow-ups/pending")
    suspend fun getPendingFollowUps(): Response<List<InvoiceFollowUp>>

    @POST("/api/invoices/follow-ups/{id}/mark-sent")
    suspend fun markFollowUpSent(@Path("id") id: Int): Response<Map<String, Any>>

    @POST("/api/invoices/follow-ups/{id}/complete")
    suspend fun completeFollowUp(@Path("id") id: Int): Response<Map<String, Any>>

    @GET("/api/customers/{id}/communication-thread")
    suspend fun getCustomerCommunicationThread(@Path("id") id: Int): Response<CustomerCommunicationThread>
}

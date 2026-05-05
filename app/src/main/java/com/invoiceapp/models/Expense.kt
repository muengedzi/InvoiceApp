package com.invoiceapp.models

data class Expense(
    val id: Int,
    val expense_date: String,
    val category: String,
    val amount: Double,
    val description: String? = null,
    val vendor_name: String? = null,
    val payment_method: String? = null,
    val receipt_image_url: String? = null,
    val created_at: String? = null
)

data class ExpenseRequest(
    val expense_date: String,
    val category: String,
    val amount: Double,
    val description: String? = null,
    val vendor_name: String? = null,
    val payment_method: String? = null,
    val is_recurring: Boolean = false,
    val recurring_period: String? = null
)

data class ExpenseListResponse(
    val expenses: List<Expense>,
    val total: Int,
    val page: Int,
    val per_page: Int,
    val total_pages: Int
)
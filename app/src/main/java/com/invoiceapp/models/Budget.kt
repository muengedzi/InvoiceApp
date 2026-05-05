package com.invoiceapp.models

data class Budget(
    val id: Int,
    val category: String,
    val budget_amount: Double,
    val actual_amount: Double,
    val notifications_enabled: Boolean
)

data class BudgetRequest(
    val category: String,
    val budget_month: Int,
    val budget_year: Int,
    val budget_amount: Double,
    val notifications_enabled: Boolean = true
)

data class BudgetResponse(
    val budgets: List<Budget>,
    val actual_expenses: Map<String, Double>,
    val month: Int,
    val year: Int
)
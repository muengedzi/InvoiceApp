package com.invoiceapp.models

data class DashboardStats(
    val total_revenue: Double,
    val pending_amount: Double,
    val total_expenses: Double,
    val monthly_revenue: Double,
    val monthly_expenses: Double,
    val monthly_profit: Double,
    val invoice_counts: Map<String, Int>,
    val recent_invoices: List<RecentInvoice>,
    val recent_expenses: List<RecentExpense>
)

data class RecentInvoice(
    val id: Int,
    val invoice_number: String,
    val invoice_date: String,
    val total_amount: Double,
    val status: String,
    val customer_name: String
)

data class RecentExpense(
    val id: Int,
    val expense_date: String,
    val category: String,
    val amount: Double,
    val description: String
)

data class ProfitLossReport(
    val start_date: String,
    val end_date: String,
    val total_revenue: Double,
    val total_expenses: Double,
    val net_profit: Double,
    val revenue_by_month: List<MonthlyRevenue>,
    val expenses_by_category: List<CategoryExpense>
)

data class MonthlyRevenue(
    val year: Int,
    val month: Int,
    val revenue: Double
)

data class CategoryExpense(
    val category: String,
    val amount: Double
)
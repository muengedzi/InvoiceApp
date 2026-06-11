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
    val recent_expenses: List<RecentExpense>,
    // New fields for counts
    val paid_count: Int = 0,
    val paid_amount: Double = 0.0,
    val overdue_count: Int = 0,
    val overdue_amount: Double = 0.0,
    val pending_count: Int = 0,
    val recurring_count: Int = 0,
    val recurring_total_amount: Double = 0.0
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
    val expense_date: String?,
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
    val expenses_by_category: List<CategoryExpense>,
    val invoice_counts_by_month: List<MonthlyInvoiceStatusCount> = emptyList()
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

data class MonthlyInvoiceStatusCount(
    val year: Int,
    val month: Int,
    val paid: Int = 0,
    val pending: Int = 0,
    val quotation: Int = 0,
    val draft: Int = 0,
    val overdue: Int = 0,
    val total: Int = 0
)

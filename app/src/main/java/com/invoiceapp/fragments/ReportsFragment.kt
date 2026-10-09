package com.invoiceapp.fragments

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.color.MaterialColors
import com.invoiceapp.adapters.ReportDetailAdapter
import com.invoiceapp.databinding.FragmentReportsBinding
import com.invoiceapp.network.RetrofitClient
import com.invoiceapp.utils.SessionManager
import kotlinx.coroutines.launch
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class ReportsFragment : Fragment() {

    private var _binding: FragmentReportsBinding? = null
    private val binding get() = _binding!!
    private lateinit var sessionManager: SessionManager
    private lateinit var reportDetailAdapter: ReportDetailAdapter
    private var startDate: Date = Date()
    private var endDate: Date = Date()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayDateFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)
    private val monthYearFormat = SimpleDateFormat("MMM yyyy", Locale.US)
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "ZA")).apply {
        currency = java.util.Currency.getInstance("ZAR")
    }

    // State for exporting report
    private var currentReportType: Int = -1
    private var currentReportData: Map<String, Any>? = null
    private var currentStartDateStr: String = ""
    private var currentEndDateStr: String = ""

    companion object {
        private const val REPORT_SALES = 0
        private const val REPORT_EXPENSES = 1
        private const val REPORT_PROFIT_LOSS = 2
        private const val REPORT_TAX = 3
        private const val REPORT_OUTSTANDING = 4
        private const val REPORT_INVOICE_STATUS = 5
        private const val REPORT_ITEM_SALES = 6
        private const val REPORT_CUSTOMER = 7

        private const val PERIOD_LAST_30_DAYS = 0
        private const val PERIOD_LAST_7_DAYS = 1
        private const val PERIOD_THIS_MONTH = 2
        private const val PERIOD_LAST_3_MONTHS = 3
        private const val PERIOD_LAST_6_MONTHS = 4
        private const val PERIOD_LAST_12_MONTHS = 5
        private const val PERIOD_CUSTOM = 6
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReportsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sessionManager = SessionManager(requireContext())

        setupSpinners()
        setupDatePickers()
        setupClickListeners()
        setupRecyclerView()
        setupChart()

        // Set default date range to last 30 days
        endDate = Date()
        startDate = Date(endDate.time - 30L * 24 * 60 * 60 * 1000)
        updateDateButtonTexts()
    }

    private fun setupSpinners() {
        // Report Types
        val reportTypes = arrayOf(
            "Sales / Invoice Summary",
            "Expense Report",
            "Profit & Loss Report",
            "Tax Report",
            "Outstanding Receivables",
            "Invoice Status Report",
            "Item / Service Sales Report",
            "Customer Report"
        )
        val reportAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, reportTypes)
        reportAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerReportType.adapter = reportAdapter

        // Periods
        val periods = arrayOf(
            "Last 30 Days",
            "Last 7 Days",
            "This Month",
            "Last 3 Months",
            "Last 6 Months",
            "Last 12 Months",
            "Custom Range"
        )
        val periodAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, periods)
        periodAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerPeriod.adapter = periodAdapter

        binding.spinnerPeriod.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                binding.layoutCustomDate.visibility = if (position == PERIOD_CUSTOM) View.VISIBLE else View.GONE
                if (position != PERIOD_CUSTOM) {
                    updatePeriodText(position)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun updatePeriodText(period: Int) {
        val periodText = when (period) {
            PERIOD_LAST_30_DAYS -> "Last 30 Days"
            PERIOD_LAST_7_DAYS -> "Last 7 Days"
            PERIOD_THIS_MONTH -> "This Month"
            PERIOD_LAST_3_MONTHS -> "Last 3 Months"
            PERIOD_LAST_6_MONTHS -> "Last 6 Months"
            PERIOD_LAST_12_MONTHS -> "Last 12 Months"
            else -> ""
        }
        binding.tvPeriod.text = periodText
    }

    private fun setupDatePickers() {
        // Start date picker - using clickable card layout
        binding.btnStartDate.setOnClickListener {
            showDatePicker(true)
        }

        // End date picker
        binding.btnEndDate.setOnClickListener {
            showDatePicker(false)
        }
    }

    private fun setupClickListeners() {
        binding.btnGenerateReport.setOnClickListener {
            generateReport()
        }
        binding.btnExport.setOnClickListener {
            exportReport()
        }
    }

    private fun setupRecyclerView() {
        reportDetailAdapter = ReportDetailAdapter()
        binding.recyclerViewDetails.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewDetails.adapter = reportDetailAdapter
    }

    private fun setupChart() {
        binding.barChart.apply {
            description.isEnabled = false
            setTouchEnabled(true)
            setDragEnabled(true)
            setScaleEnabled(true)
            setPinchZoom(true)
            legend.isEnabled = true
            legend.textSize = 11f
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.granularity = 1f
            xAxis.labelRotationAngle = -45f
            xAxis.textSize = 10f
            axisLeft.isEnabled = true
            axisLeft.textSize = 10f
            axisRight.isEnabled = false
        }

        binding.pieChart.apply {
            description.isEnabled = false
            setUsePercentValues(true)
            isDrawHoleEnabled = true
            setHoleRadius(42f)
            setTransparentCircleRadius(48f)
            setEntryLabelTextSize(11f)
            legend.isEnabled = true
            legend.textSize = 11f
        }
    }

    private fun showDatePicker(isStartDate: Boolean) {
        val calendar = Calendar.getInstance()
        val currentDate = if (isStartDate) startDate else endDate
        calendar.time = currentDate

        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                val selectedDate = Calendar.getInstance().apply {
                    set(year, month, day)
                }.time
                if (isStartDate) {
                    startDate = selectedDate
                } else {
                    endDate = selectedDate
                }
                updateDateButtonTexts()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun updateDateButtonTexts() {
        binding.tvStartDate.text = displayDateFormat.format(startDate)
        binding.tvEndDate.text = displayDateFormat.format(endDate)
    }

    private fun generateReport() {
        val reportType = binding.spinnerReportType.selectedItemPosition
        val period = binding.spinnerPeriod.selectedItemPosition

        val startDateStr = if (period == PERIOD_CUSTOM) {
            dateFormat.format(startDate)
        } else {
            getStartDateForPeriod(period)
        }

        val endDateStr = if (period == PERIOD_CUSTOM) {
            dateFormat.format(endDate)
        } else {
            dateFormat.format(Date())
        }

        showLoading(true)
        binding.cardReportContent.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val response = when (reportType) {
                    REPORT_SALES -> RetrofitClient.apiService.getSalesReport(startDateStr, endDateStr, getPeriodGroupBy(period))
                    REPORT_EXPENSES -> RetrofitClient.apiService.getExpenseReport(startDateStr, endDateStr)
                    REPORT_PROFIT_LOSS -> RetrofitClient.apiService.getProfitLossReport(startDateStr, endDateStr)
                    REPORT_TAX -> RetrofitClient.apiService.getTaxReport(startDateStr, endDateStr)
                    REPORT_OUTSTANDING -> RetrofitClient.apiService.getOutstandingReport()
                    REPORT_INVOICE_STATUS -> RetrofitClient.apiService.getInvoiceStatusReport(startDateStr, endDateStr)
                    REPORT_ITEM_SALES -> RetrofitClient.apiService.getItemSalesReport(startDateStr, endDateStr)
                    REPORT_CUSTOMER -> RetrofitClient.apiService.getCustomerReport(startDateStr, endDateStr)
                    else -> return@launch
                }

                if (response.isSuccessful && response.body() != null) {
                    val data = response.body()!!
                    currentReportType = reportType
                    currentReportData = data
                    currentStartDateStr = startDateStr
                    currentEndDateStr = endDateStr
                    displayReport(reportType, data, startDateStr, endDateStr)
                    binding.btnExport.visibility = View.VISIBLE
                } else {
                    showError("Failed to load report")
                }
            } catch (e: Exception) {
                showError(e.message ?: "Network error")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun showError(message: String) {
        showLoading(false)
        currentReportType = -1
        currentReportData = null
        currentStartDateStr = ""
        currentEndDateStr = ""
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
        binding.cardReportContent.visibility = View.GONE
        binding.btnExport.visibility = View.GONE
    }

    private fun getStartDateForPeriod(period: Int): String {
        val calendar = Calendar.getInstance()
        when (period) {
            PERIOD_LAST_30_DAYS -> calendar.add(Calendar.DAY_OF_MONTH, -30)
            PERIOD_LAST_7_DAYS -> calendar.add(Calendar.DAY_OF_MONTH, -7)
            PERIOD_THIS_MONTH -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
            }
            PERIOD_LAST_3_MONTHS -> calendar.add(Calendar.MONTH, -3)
            PERIOD_LAST_6_MONTHS -> calendar.add(Calendar.MONTH, -6)
            PERIOD_LAST_12_MONTHS -> calendar.add(Calendar.YEAR, -1)
        }
        return dateFormat.format(calendar.time)
    }

    private fun getPeriodGroupBy(period: Int): String {
        return when (period) {
            PERIOD_LAST_7_DAYS -> "day"
            PERIOD_LAST_30_DAYS -> "day"
            else -> "month"
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun displayReport(reportType: Int, data: Any, startDateStr: String, endDateStr: String) {
        binding.cardReportContent.visibility = View.VISIBLE
        binding.tvError.visibility = View.GONE
        resetReportContent()

        val startDateObj = dateFormat.parse(startDateStr)
        val endDateObj = dateFormat.parse(endDateStr)
        val periodText = "${displayDateFormat.format(startDateObj)} - ${displayDateFormat.format(endDateObj)}"
        binding.tvReportPeriod.text = periodText

        when (reportType) {
            REPORT_SALES -> displaySalesReport(data as Map<String, Any>)
            REPORT_EXPENSES -> displayExpenseReport(data as Map<String, Any>)
            REPORT_PROFIT_LOSS -> displayProfitLossReport(data as Map<String, Any>)
            REPORT_TAX -> displayTaxReport(data as Map<String, Any>)
            REPORT_OUTSTANDING -> displayOutstandingReport(data as Map<String, Any>)
            REPORT_INVOICE_STATUS -> displayInvoiceStatusReport(data as Map<String, Any>)
            REPORT_ITEM_SALES -> displayItemSalesReport(data as Map<String, Any>)
            REPORT_CUSTOMER -> displayCustomerReport(data as Map<String, Any>)
        }
    }

    private fun displaySalesReport(data: Map<String, Any>) {
        binding.tvReportTitle.text = "Sales / Invoice Summary Report"

        val summary = data["summary"] as? Map<String, Any>
        val dataList = data["data"] as? List<Map<String, Any>> ?: emptyList()
        val monthlyActivity = data["monthly_activity"] as? List<Map<String, Any>> ?: emptyList()
        val revenueWindows = data["revenue_windows"] as? Map<String, Any>

        summary?.let {
            val totalRevenue = (it["total_revenue"] as? Number)?.toDouble() ?: 0.0
            val totalTax = (it["total_tax"] as? Number)?.toDouble() ?: 0.0
            val totalInvoices = (it["total_invoices"] as? Number)?.toInt() ?: 0
            binding.tvReportRevenueLabel.text = "Made"
            binding.tvReportExpensesLabel.text = "Tax Due"
            binding.tvReportNetProfitLabel.text = "Invoices"
            binding.tvReportTotalRevenue.text = currencyFormat.format(totalRevenue)
            binding.tvReportTotalExpenses.text = currencyFormat.format(totalTax)
            binding.tvReportTotalExpenses.setTextColor(android.graphics.Color.parseColor("#FF9800"))
            binding.tvReportNetProfit.text = totalInvoices.toString()
            binding.tvReportNetProfit.setTextColor(android.graphics.Color.parseColor("#2196F3"))
        }

        revenueWindows?.let {
            binding.tvRevenueWindows.visibility = View.VISIBLE
            binding.tvRevenueWindows.text = listOf(
                "Last 30 days: ${currencyFormat.format(((it["last_30_days"] as? Number)?.toDouble() ?: 0.0))}",
                "Quarter: ${currencyFormat.format(((it["last_quarter"] as? Number)?.toDouble() ?: 0.0))}",
                "Six months: ${currencyFormat.format(((it["last_six_months"] as? Number)?.toDouble() ?: 0.0))}",
                "Year: ${currencyFormat.format(((it["last_year"] as? Number)?.toDouble() ?: 0.0))}"
            ).joinToString("\n")
        }

        val entries = mutableListOf<BarEntry>()
        val labels = mutableListOf<String>()
        val chartList = monthlyActivity.takeIf { it.isNotEmpty() } ?: dataList

        chartList.forEachIndexed { index, item ->
            val amount = ((item["revenue"] ?: item["total_amount"]) as? Number)?.toFloat() ?: 0f
            entries.add(BarEntry(index.toFloat(), amount))
            val label = item["period"] as? String ?: formatMonthLabel(item)
            labels.add(label.take(12))
        }

        if (entries.isNotEmpty()) {
            updateChart(entries, labels, "Money Made")
        }

        reportDetailAdapter.submitList(monthlyActivity.takeIf { it.isNotEmpty() } ?: dataList)
    }

    private fun displayExpenseReport(data: Map<String, Any>) {
        binding.tvReportTitle.text = "Expense Report"

        val summary = data["summary"] as? Map<String, Any>
        val dataList = data["expenses_by_category"] as? List<Map<String, Any>> ?: emptyList()

        summary?.let {
            val totalExpenses = (it["total_expenses"] as? Number)?.toDouble() ?: 0.0
            binding.tvReportTotalExpenses.text = currencyFormat.format(totalExpenses)
            binding.tvReportNetProfit.text = currencyFormat.format(-totalExpenses)
            binding.tvReportNetProfit.setTextColor(android.graphics.Color.parseColor("#F44336"))
        }

        val chartList = (data["expenses_by_month"] as? List<Map<String, Any>>)
            ?.takeIf { it.isNotEmpty() }
            ?: dataList

        val entries = mutableListOf<BarEntry>()
        val labels = mutableListOf<String>()

        chartList.forEachIndexed { index, item ->
            val amount = (item["amount"] as? Number)?.toFloat() ?: 0f
            entries.add(BarEntry(index.toFloat(), amount))
            val year = (item["year"] as? Number)?.toInt()
            val month = (item["month"] as? Number)?.toInt()
            val label = if (year != null && month != null) {
                val calendar = Calendar.getInstance()
                calendar.set(year, (month ?: 1) - 1, 1)
                monthYearFormat.format(calendar.time)
            } else {
                item["category"] as? String ?: ""
            }
            labels.add(label.take(12))
        }

        if (entries.isNotEmpty()) {
            updateChart(entries, labels, "Expenses")
        }

        updatePieChart(
            dataList.map { (it["amount"] as? Number)?.toFloat() ?: 0f },
            dataList.map { it["category"] as? String ?: "Uncategorized" },
            "Expenses by Category"
        )

        reportDetailAdapter.submitList(dataList)
    }

    private fun displayProfitLossReport(data: Map<String, Any>) {
        binding.tvReportTitle.text = "Profit & Loss Report"

        val totalRevenue = (data["total_revenue"] as? Number)?.toDouble() ?: 0.0
        val totalExpenses = (data["total_expenses"] as? Number)?.toDouble() ?: 0.0
        val netProfit = (data["net_profit"] as? Number)?.toDouble() ?: 0.0

        binding.tvReportTotalRevenue.text = currencyFormat.format(totalRevenue)
        binding.tvReportTotalExpenses.text = currencyFormat.format(totalExpenses)
        binding.tvReportNetProfit.text = currencyFormat.format(netProfit)

        // Set color for net profit/loss
        if (netProfit >= 0) {
            binding.tvReportNetProfit.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
        } else {
            binding.tvReportNetProfit.setTextColor(android.graphics.Color.parseColor("#F44336"))
        }

        // Revenue by month chart
        val revenueByMonth = data["revenue_by_month"] as? List<Map<String, Any>> ?: emptyList()
        val entries = mutableListOf<BarEntry>()
        val labels = mutableListOf<String>()

        revenueByMonth.forEachIndexed { index, item ->
            val revenue = (item["revenue"] as? Number)?.toFloat() ?: 0f
            entries.add(BarEntry(index.toFloat(), revenue))
            val year = item["year"] as? Int ?: 0
            val month = item["month"] as? Int ?: 1
            val calendar = Calendar.getInstance()
            calendar.set(year, month - 1, 1)
            labels.add(monthYearFormat.format(calendar.time))
        }

        if (entries.isNotEmpty()) {
            updateChart(entries, labels, "Monthly Revenue")
        }

        updatePieChart(
            listOf(totalRevenue.toFloat(), totalExpenses.toFloat()).filter { it > 0f },
            listOf("Revenue", "Expenses").filterIndexed { index, _ ->
                listOf(totalRevenue.toFloat(), totalExpenses.toFloat())[index] > 0f
            },
            "Revenue vs Expenses"
        )

        reportDetailAdapter.submitList(revenueByMonth)
    }

    private fun displayTaxReport(data: Map<String, Any>) {
        binding.tvReportTitle.text = "Tax Report"

        val summary = data["summary"] as? Map<String, Any>
        val taxRates = data["tax_rates"] as? List<Map<String, Any>> ?: emptyList()
        val taxByMonth = data["tax_by_month"] as? List<Map<String, Any>> ?: emptyList()

        summary?.let {
            val totalTax = (it["total_tax_collected"] as? Number)?.toDouble() ?: 0.0
            binding.tvReportRevenueLabel.text = "Tax Due"
            binding.tvReportExpensesLabel.text = "Expenses"
            binding.tvReportNetProfitLabel.text = "Net Profit"
            binding.tvReportTotalRevenue.text = currencyFormat.format(totalTax)
            binding.tvReportTotalRevenue.setTextColor(android.graphics.Color.parseColor("#FF9800"))
        }

        val entries = mutableListOf<BarEntry>()
        val labels = mutableListOf<String>()
        val chartList = taxByMonth.takeIf { it.isNotEmpty() } ?: taxRates

        chartList.forEachIndexed { index, item ->
            val taxAmount = ((item["tax_liability"] ?: item["tax_collected"]) as? Number)?.toFloat() ?: 0f
            entries.add(BarEntry(index.toFloat(), taxAmount))
            labels.add((item["period"] as? String) ?: "${item["tax_rate"]}%")
        }

        if (entries.isNotEmpty()) {
            updateChart(entries, labels, "Tax Liability")
        }

        updatePieChart(
            taxRates.map { ((it["tax_collected"] ?: it["tax_liability"]) as? Number)?.toFloat() ?: 0f },
            taxRates.map { "${it["tax_rate"]}%" },
            "Tax by Rate"
        )

        reportDetailAdapter.submitList(taxByMonth.takeIf { it.isNotEmpty() } ?: taxRates)
    }

    private fun displayOutstandingReport(data: Map<String, Any>) {
        binding.tvReportTitle.text = "Outstanding Receivables Report"

        val totalOutstanding = (data["total_outstanding"] as? Number)?.toDouble() ?: 0.0
        val outstandingInvoices = data["outstanding_invoices"] as? List<Map<String, Any>> ?: emptyList()

        binding.tvReportTotalRevenue.text = currencyFormat.format(totalOutstanding)
        binding.tvReportTotalRevenue.setTextColor(android.graphics.Color.parseColor("#F44336"))
        binding.tvReportNetProfit.text = currencyFormat.format(totalOutstanding)
        binding.tvReportNetProfit.setTextColor(android.graphics.Color.parseColor("#F44336"))

        updatePieChart(
            outstandingInvoices.take(8).map { (it["balance_due"] as? Number)?.toFloat() ?: 0f },
            outstandingInvoices.take(8).map {
                (it["customer_name"] as? String) ?: (it["invoice_number"] as? String) ?: "Invoice"
            },
            "Outstanding by Customer"
        )

        reportDetailAdapter.submitList(outstandingInvoices)
    }

    private fun displayInvoiceStatusReport(data: Map<String, Any>) {
        binding.tvReportTitle.text = "Invoice Status Report"

        val statusBreakdown = data["status_breakdown"] as? List<Map<String, Any>> ?: emptyList()
        val monthlyStatus = data["monthly_status"] as? List<Map<String, Any>> ?: emptyList()

        val entries = mutableListOf<BarEntry>()
        val labels = mutableListOf<String>()
        val chartList = monthlyStatus.takeIf { it.isNotEmpty() } ?: statusBreakdown

        chartList.forEachIndexed { index, item ->
            val count = ((item["invoices_created"] ?: item["count"]) as? Number)?.toFloat() ?: 0f
            entries.add(BarEntry(index.toFloat(), count))
            labels.add((item["period"] ?: item["status"]) as? String ?: "")
        }

        if (entries.isNotEmpty()) {
            updateCountChart(entries, labels, if (monthlyStatus.isNotEmpty()) "Invoices Created by Month" else "Invoices by Status")
        }

        updatePieChart(
            statusBreakdown.map { (it["count"] as? Number)?.toFloat() ?: 0f },
            statusBreakdown.map { it["status"] as? String ?: "Unknown" },
            "Invoice Status Split"
        )

        reportDetailAdapter.submitList(monthlyStatus.takeIf { it.isNotEmpty() } ?: statusBreakdown)
    }

    private fun displayItemSalesReport(data: Map<String, Any>) {
        binding.tvReportTitle.text = "Item / Service Sales Report"

        val items = data["items"] as? List<Map<String, Any>> ?: emptyList()

        val entries = mutableListOf<BarEntry>()
        val labels = mutableListOf<String>()

        items.take(10).forEachIndexed { index, item ->
            val totalSales = (item["total_sales"] as? Number)?.toFloat() ?: 0f
            entries.add(BarEntry(index.toFloat(), totalSales))
            val itemName = item["item_name"] as? String ?: ""
            labels.add(itemName.take(15))
        }

        if (entries.isNotEmpty()) {
            updateChart(entries, labels, "Top Selling Items")
        }

        updatePieChart(
            items.take(8).map { (it["total_sales"] as? Number)?.toFloat() ?: 0f },
            items.take(8).map { it["item_name"] as? String ?: "Item" },
            "Top Items Share"
        )

        reportDetailAdapter.submitList(items)
    }

    private fun displayCustomerReport(data: Map<String, Any>) {
        binding.tvReportTitle.text = "Customer Report"

        val customers = data["customers"] as? List<Map<String, Any>> ?: emptyList()

        val entries = mutableListOf<BarEntry>()
        val labels = mutableListOf<String>()

        customers.take(10).forEachIndexed { index, item ->
            val totalSpent = (item["total_spent"] as? Number)?.toFloat() ?: 0f
            entries.add(BarEntry(index.toFloat(), totalSpent))
            val customerName = item["customer_name"] as? String ?: ""
            labels.add(customerName.take(15))
        }

        if (entries.isNotEmpty()) {
            updateChart(entries, labels, "Top Customers by Spending")
        }

        updatePieChart(
            customers.take(8).map { (it["total_spent"] as? Number)?.toFloat() ?: 0f },
            customers.take(8).map { it["customer_name"] as? String ?: "Customer" },
            "Customer Revenue Share"
        )

        reportDetailAdapter.submitList(customers)
    }

    private fun updateChart(entries: List<BarEntry>, labels: List<String>, title: String) {
        val dataSet = BarDataSet(entries, title).apply {
            color = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorSecondary)
            valueTextSize = 10f
            setDrawValues(true)
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return currencyFormat.format(value.toDouble())
                }
            }
        }

        val barData = BarData(dataSet)
        barData.barWidth = 0.8f

        binding.barChart.data = barData
        binding.barChart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        binding.barChart.xAxis.labelCount = labels.size.coerceAtMost(6)
        binding.barChart.invalidate()
    }

    private fun updateCountChart(entries: List<BarEntry>, labels: List<String>, title: String) {
        val dataSet = BarDataSet(entries, title).apply {
            color = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorPrimary)
            valueTextSize = 10f
            setDrawValues(true)
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return value.toInt().toString()
                }
            }
        }

        val barData = BarData(dataSet)
        barData.barWidth = 0.8f

        binding.barChart.data = barData
        binding.barChart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        binding.barChart.xAxis.labelCount = labels.size.coerceAtMost(6)
        binding.barChart.invalidate()
    }

    private fun updatePieChart(values: List<Float>, labels: List<String>, title: String) {
        val entries = values.zip(labels)
            .filter { it.first > 0f }
            .map { PieEntry(it.first, it.second.take(18)) }

        if (entries.isEmpty()) {
            binding.cardPieChart.visibility = View.GONE
            binding.tvPieChartTitle.visibility = View.GONE
            binding.pieChart.clear()
            return
        }

        val colors = listOf(
            android.graphics.Color.parseColor("#4CAF50"),
            android.graphics.Color.parseColor("#2196F3"),
            android.graphics.Color.parseColor("#FF9800"),
            android.graphics.Color.parseColor("#9C27B0"),
            android.graphics.Color.parseColor("#F44336"),
            android.graphics.Color.parseColor("#00ACC1"),
            android.graphics.Color.parseColor("#607D8B"),
            android.graphics.Color.parseColor("#795548")
        )

        val dataSet = PieDataSet(entries, title).apply {
            this.colors = colors
            valueTextSize = 11f
            valueFormatter = PercentFormatter(binding.pieChart)
            sliceSpace = 2f
            setDrawValues(true)
        }

        binding.tvPieChartTitle.text = title
        binding.tvPieChartTitle.visibility = View.VISIBLE
        binding.cardPieChart.visibility = View.VISIBLE
        binding.pieChart.data = PieData(dataSet)
        binding.pieChart.centerText = title
        binding.pieChart.setCenterTextSize(12f)
        binding.pieChart.animateY(700)
        binding.pieChart.invalidate()
    }

    private fun formatMonthLabel(item: Map<String, Any>): String {
        val year = (item["year"] as? Number)?.toInt()
        val month = (item["month"] as? Number)?.toInt()
        if (year != null && month != null) {
            val calendar = Calendar.getInstance()
            calendar.set(year, month - 1, 1)
            return monthYearFormat.format(calendar.time)
        }
        return item["period"] as? String ?: ""
    }

    private fun resetReportContent() {
        binding.tvReportRevenueLabel.text = "Revenue"
        binding.tvReportExpensesLabel.text = "Expenses"
        binding.tvReportNetProfitLabel.text = "Net Profit"
        binding.tvReportTotalRevenue.text = currencyFormat.format(0)
        binding.tvReportTotalRevenue.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
        binding.tvReportTotalExpenses.text = currencyFormat.format(0)
        binding.tvReportTotalExpenses.setTextColor(android.graphics.Color.parseColor("#F44336"))
        binding.tvReportNetProfit.text = currencyFormat.format(0)
        binding.tvReportNetProfit.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
        binding.tvRevenueWindows.visibility = View.GONE
        binding.tvRevenueWindows.text = ""
        reportDetailAdapter.submitList(emptyList())
        binding.barChart.clear()
        binding.barChart.invalidate()
        binding.pieChart.clear()
        binding.cardPieChart.visibility = View.GONE
        binding.tvPieChartTitle.visibility = View.GONE
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnGenerateReport.isEnabled = !show
        binding.btnGenerateReport.text = if (show) "Generating..." else "Generate Report"
    }

    private fun exportReport() {
        val data = currentReportData
        if (data == null) {
            Toast.makeText(requireContext(), "No report data available to export", Toast.LENGTH_SHORT).show()
            return
        }

        val reportType = currentReportType
        val startDate = currentStartDateStr
        val endDate = currentEndDateStr

        val (reportNameSlug, reportTitle, csvContent) = when (reportType) {
            REPORT_SALES -> Triple("sales", "Sales / Invoice Summary Report", buildSalesReportCsv(data, startDate, endDate))
            REPORT_EXPENSES -> Triple("expenses", "Expense Report", buildExpenseReportCsv(data, startDate, endDate))
            REPORT_PROFIT_LOSS -> Triple("profit_loss", "Profit & Loss Report", buildProfitLossReportCsv(data, startDate, endDate))
            REPORT_TAX -> Triple("tax", "Tax Report", buildTaxReportCsv(data, startDate, endDate))
            REPORT_OUTSTANDING -> Triple("outstanding", "Outstanding Receivables Report", buildOutstandingReportCsv(data, startDate, endDate))
            REPORT_INVOICE_STATUS -> Triple("invoice_status", "Invoice Status Report", buildInvoiceStatusReportCsv(data, startDate, endDate))
            REPORT_ITEM_SALES -> Triple("item_sales", "Item / Service Sales Report", buildItemSalesReportCsv(data, startDate, endDate))
            REPORT_CUSTOMER -> Triple("customer", "Customer Report", buildCustomerReportCsv(data, startDate, endDate))
            else -> {
                Toast.makeText(requireContext(), "Invalid report type", Toast.LENGTH_SHORT).show()
                return
            }
        }

        try {
            val fileName = "report_${reportNameSlug}_${startDate}_to_${endDate}.csv"
            val reportsDir = File(requireContext().cacheDir, "exported_reports").apply { mkdirs() }
            val csvFile = File(reportsDir, fileName)
            csvFile.writeText(csvContent, Charsets.UTF_8)

            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                csvFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, "$reportTitle ($startDate to $endDate)")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Export $reportTitle CSV")
            startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Error exporting CSV: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun escapeCsv(value: Any?): String {
        if (value == null) return ""
        val str = value.toString()
        return if (str.contains(",") || str.contains("\"") || str.contains("\n") || str.contains("\r")) {
            "\"${str.replace("\"", "\"\"")}\""
        } else {
            str
        }
    }

    private fun formatNumber(number: Any?): String {
        val doubleVal = (number as? Number)?.toDouble() ?: 0.0
        return String.format(Locale.US, "%.2f", doubleVal)
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildSalesReportCsv(data: Map<String, Any>, startDate: String, endDate: String): String {
        val sb = StringBuilder()
        sb.append("Report Name,").append(escapeCsv("Sales / Invoice Summary Report")).append("\n")
        sb.append("Period,").append(escapeCsv("$startDate to $endDate")).append("\n\n")

        val summary = data["summary"] as? Map<String, Any>
        summary?.let {
            sb.append("SUMMARY\n")
            sb.append("Total Revenue (ZAR),").append(formatNumber(it["total_revenue"])).append("\n")
            sb.append("Total Tax (ZAR),").append(formatNumber(it["total_tax"])).append("\n")
            sb.append("Total Invoices,").append(it["total_invoices"] ?: 0).append("\n\n")
        }

        val revenueWindows = data["revenue_windows"] as? Map<String, Any>
        revenueWindows?.let {
            sb.append("REVENUE WINDOWS\n")
            sb.append("Period,Amount (ZAR)\n")
            sb.append("Last 30 Days,").append(formatNumber(it["last_30_days"])).append("\n")
            sb.append("Last Quarter,").append(formatNumber(it["last_quarter"])).append("\n")
            sb.append("Last 6 Months,").append(formatNumber(it["last_six_months"])).append("\n")
            sb.append("Last Year,").append(formatNumber(it["last_year"])).append("\n\n")
        }

        val monthlyActivity = data["monthly_activity"] as? List<Map<String, Any>> ?: emptyList()
        if (monthlyActivity.isNotEmpty()) {
            sb.append("MONTHLY ACTIVITY\n")
            sb.append("Period,Invoices Created,Paid Invoices,Pending Invoices,Overdue Invoices,Revenue (ZAR),Tax Liability (ZAR)\n")
            for (row in monthlyActivity) {
                sb.append(escapeCsv(row["period"] ?: formatMonthLabel(row))).append(",")
                    .append(row["invoices_created"] ?: 0).append(",")
                    .append(row["paid"] ?: 0).append(",")
                    .append(row["pending"] ?: 0).append(",")
                    .append(row["overdue"] ?: 0).append(",")
                    .append(formatNumber(row["revenue"])).append(",")
                    .append(formatNumber(row["tax_liability"])).append("\n")
            }
            sb.append("\n")
        }

        val dataList = data["data"] as? List<Map<String, Any>> ?: emptyList()
        if (dataList.isNotEmpty()) {
            sb.append("PERIOD SALES BREAKDOWN\n")
            sb.append("Period,Invoice Count,Total Amount (ZAR),Total Tax (ZAR),Total Discount (ZAR),Total Shipping (ZAR)\n")
            for (row in dataList) {
                sb.append(escapeCsv(row["period"])).append(",")
                    .append(row["invoice_count"] ?: 0).append(",")
                    .append(formatNumber(row["total_amount"])).append(",")
                    .append(formatNumber(row["total_tax"])).append(",")
                    .append(formatNumber(row["total_discount"])).append(",")
                    .append(formatNumber(row["total_shipping"])).append("\n")
            }
        }

        return sb.toString()
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildExpenseReportCsv(data: Map<String, Any>, startDate: String, endDate: String): String {
        val sb = StringBuilder()
        sb.append("Report Name,").append(escapeCsv("Expense Report")).append("\n")
        sb.append("Period,").append(escapeCsv("$startDate to $endDate")).append("\n\n")

        val summary = data["summary"] as? Map<String, Any>
        summary?.let {
            sb.append("SUMMARY\n")
            sb.append("Total Expenses (ZAR),").append(formatNumber(it["total_expenses"])).append("\n")
            sb.append("Expense Count,").append(it["expense_count"] ?: 0).append("\n")
            sb.append("Average Expense (ZAR),").append(formatNumber(it["average_expense"])).append("\n\n")
        }

        val byCategory = data["expenses_by_category"] as? List<Map<String, Any>> ?: emptyList()
        if (byCategory.isNotEmpty()) {
            sb.append("EXPENSES BY CATEGORY\n")
            sb.append("Category,Amount (ZAR),Expense Count\n")
            for (row in byCategory) {
                sb.append(escapeCsv(row["category"] ?: "Uncategorized")).append(",")
                    .append(formatNumber(row["amount"])).append(",")
                    .append(row["count"] ?: 0).append("\n")
            }
            sb.append("\n")
        }

        val byVendor = data["expenses_by_vendor"] as? List<Map<String, Any>> ?: emptyList()
        if (byVendor.isNotEmpty()) {
            sb.append("EXPENSES BY VENDOR\n")
            sb.append("Vendor Name,Amount (ZAR),Expense Count\n")
            for (row in byVendor) {
                sb.append(escapeCsv(row["vendor_name"] ?: "No vendor")).append(",")
                    .append(formatNumber(row["amount"])).append(",")
                    .append(row["count"] ?: 0).append("\n")
            }
            sb.append("\n")
        }

        val byMonth = data["expenses_by_month"] as? List<Map<String, Any>> ?: emptyList()
        if (byMonth.isNotEmpty()) {
            sb.append("EXPENSES BY MONTH\n")
            sb.append("Year,Month,Amount (ZAR),Expense Count\n")
            for (row in byMonth) {
                sb.append(row["year"] ?: "").append(",")
                    .append(row["month"] ?: "").append(",")
                    .append(formatNumber(row["amount"])).append(",")
                    .append(row["count"] ?: 0).append("\n")
            }
        }

        return sb.toString()
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildProfitLossReportCsv(data: Map<String, Any>, startDate: String, endDate: String): String {
        val sb = StringBuilder()
        sb.append("Report Name,").append(escapeCsv("Profit & Loss Report")).append("\n")
        sb.append("Period,").append(escapeCsv("$startDate to $endDate")).append("\n\n")

        sb.append("SUMMARY\n")
        sb.append("Total Revenue (ZAR),").append(formatNumber(data["total_revenue"])).append("\n")
        sb.append("Total Expenses (ZAR),").append(formatNumber(data["total_expenses"])).append("\n")
        sb.append("Net Profit (ZAR),").append(formatNumber(data["net_profit"])).append("\n\n")

        val revenueByMonth = data["revenue_by_month"] as? List<Map<String, Any>> ?: emptyList()
        if (revenueByMonth.isNotEmpty()) {
            sb.append("REVENUE BY MONTH\n")
            sb.append("Year,Month,Revenue (ZAR)\n")
            for (row in revenueByMonth) {
                sb.append(row["year"] ?: "").append(",")
                    .append(row["month"] ?: "").append(",")
                    .append(formatNumber(row["revenue"])).append("\n")
            }
            sb.append("\n")
        }

        val expensesByCategory = data["expenses_by_category"] as? List<Map<String, Any>> ?: emptyList()
        if (expensesByCategory.isNotEmpty()) {
            sb.append("EXPENSES BY CATEGORY\n")
            sb.append("Category,Amount (ZAR)\n")
            for (row in expensesByCategory) {
                sb.append(escapeCsv(row["category"] ?: "Uncategorized")).append(",")
                    .append(formatNumber(row["amount"])).append("\n")
            }
            sb.append("\n")
        }

        val invoiceCountsByMonth = data["invoice_counts_by_month"] as? List<Map<String, Any>> ?: emptyList()
        if (invoiceCountsByMonth.isNotEmpty()) {
            sb.append("INVOICE COUNTS BY MONTH\n")
            sb.append("Year,Month,Paid,Pending,Quotation,Draft,Overdue,Total\n")
            for (row in invoiceCountsByMonth) {
                sb.append(row["year"] ?: "").append(",")
                    .append(row["month"] ?: "").append(",")
                    .append(row["paid"] ?: 0).append(",")
                    .append(row["pending"] ?: 0).append(",")
                    .append(row["quotation"] ?: 0).append(",")
                    .append(row["draft"] ?: 0).append(",")
                    .append(row["overdue"] ?: 0).append(",")
                    .append(row["total"] ?: 0).append("\n")
            }
        }

        return sb.toString()
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildTaxReportCsv(data: Map<String, Any>, startDate: String, endDate: String): String {
        val sb = StringBuilder()
        sb.append("Report Name,").append(escapeCsv("Tax Report")).append("\n")
        sb.append("Period,").append(escapeCsv("$startDate to $endDate")).append("\n\n")

        val summary = data["summary"] as? Map<String, Any>
        summary?.let {
            sb.append("SUMMARY\n")
            sb.append("Total Tax Collected (ZAR),").append(formatNumber(it["total_tax_collected"])).append("\n")
            sb.append("Total Tax Invoices,").append(it["total_tax_invoices"] ?: 0).append("\n\n")
        }

        val taxRates = data["tax_rates"] as? List<Map<String, Any>> ?: emptyList()
        if (taxRates.isNotEmpty()) {
            sb.append("TAX BREAKDOWN BY RATE\n")
            sb.append("Tax Rate (%),Invoice Count,Taxable Amount (ZAR),Tax Collected (ZAR)\n")
            for (row in taxRates) {
                sb.append(formatNumber(row["tax_rate"])).append(",")
                    .append(row["invoice_count"] ?: 0).append(",")
                    .append(formatNumber(row["taxable_amount"])).append(",")
                    .append(formatNumber(row["tax_collected"] ?: row["tax_liability"])).append("\n")
            }
            sb.append("\n")
        }

        val taxByMonth = data["tax_by_month"] as? List<Map<String, Any>> ?: emptyList()
        if (taxByMonth.isNotEmpty()) {
            sb.append("TAX BREAKDOWN BY MONTH\n")
            sb.append("Period,Invoice Count,Taxable Amount (ZAR),Tax Liability (ZAR)\n")
            for (row in taxByMonth) {
                sb.append(escapeCsv(row["period"] ?: "${row["year"]}-${row["month"]}")).append(",")
                    .append(row["invoice_count"] ?: 0).append(",")
                    .append(formatNumber(row["taxable_amount"])).append(",")
                    .append(formatNumber(row["tax_liability"] ?: row["tax_collected"])).append("\n")
            }
        }

        return sb.toString()
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildOutstandingReportCsv(data: Map<String, Any>, startDate: String, endDate: String): String {
        val sb = StringBuilder()
        sb.append("Report Name,").append(escapeCsv("Outstanding Receivables Report")).append("\n")
        sb.append("As of Date,").append(escapeCsv(endDate)).append("\n\n")

        sb.append("SUMMARY\n")
        sb.append("Total Outstanding (ZAR),").append(formatNumber(data["total_outstanding"])).append("\n\n")

        val outstandingInvoices = data["outstanding_invoices"] as? List<Map<String, Any>> ?: emptyList()
        sb.append("OUTSTANDING INVOICES\n")
        sb.append("Invoice Number,Customer Name,Customer Email,Customer Phone,Invoice Date,Due Date,Total Amount (ZAR),Amount Paid (ZAR),Balance Due (ZAR),Days Overdue\n")
        for (row in outstandingInvoices) {
            sb.append(escapeCsv(row["invoice_number"])).append(",")
                .append(escapeCsv(row["customer_name"])).append(",")
                .append(escapeCsv(row["customer_email"])).append(",")
                .append(escapeCsv(row["customer_phone"])).append(",")
                .append(escapeCsv(row["invoice_date"])).append(",")
                .append(escapeCsv(row["due_date"])).append(",")
                .append(formatNumber(row["total_amount"])).append(",")
                .append(formatNumber(row["amount_paid"])).append(",")
                .append(formatNumber(row["balance_due"])).append(",")
                .append(row["days_overdue"] ?: 0).append("\n")
        }

        return sb.toString()
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildInvoiceStatusReportCsv(data: Map<String, Any>, startDate: String, endDate: String): String {
        val sb = StringBuilder()
        sb.append("Report Name,").append(escapeCsv("Invoice Status Report")).append("\n")
        sb.append("Period,").append(escapeCsv("$startDate to $endDate")).append("\n\n")

        val totalInvoices = data["total_invoices"]
        if (totalInvoices != null) {
            sb.append("SUMMARY\n")
            sb.append("Total Invoices,").append(totalInvoices).append("\n\n")
        }

        val statusBreakdown = data["status_breakdown"] as? List<Map<String, Any>> ?: emptyList()
        if (statusBreakdown.isNotEmpty()) {
            sb.append("STATUS BREAKDOWN\n")
            sb.append("Status,Count,Total Amount (ZAR)\n")
            for (row in statusBreakdown) {
                sb.append(escapeCsv(row["status"])).append(",")
                    .append(row["count"] ?: 0).append(",")
                    .append(formatNumber(row["total_amount"])).append("\n")
            }
            sb.append("\n")
        }

        val monthlyStatus = data["monthly_status"] as? List<Map<String, Any>> ?: emptyList()
        if (monthlyStatus.isNotEmpty()) {
            sb.append("MONTHLY STATUS BREAKDOWN\n")
            sb.append("Period,Invoices Created,Paid,Pending,Overdue,Revenue (ZAR),Tax Liability (ZAR)\n")
            for (row in monthlyStatus) {
                sb.append(escapeCsv(row["period"] ?: "${row["year"]}-${row["month"]}")).append(",")
                    .append(row["invoices_created"] ?: 0).append(",")
                    .append(row["paid"] ?: 0).append(",")
                    .append(row["pending"] ?: 0).append(",")
                    .append(row["overdue"] ?: 0).append(",")
                    .append(formatNumber(row["revenue"])).append(",")
                    .append(formatNumber(row["tax_liability"])).append("\n")
            }
        }

        return sb.toString()
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildItemSalesReportCsv(data: Map<String, Any>, startDate: String, endDate: String): String {
        val sb = StringBuilder()
        sb.append("Report Name,").append(escapeCsv("Item / Service Sales Report")).append("\n")
        sb.append("Period,").append(escapeCsv("$startDate to $endDate")).append("\n\n")

        val totalItems = data["total_items_sold"]
        if (totalItems != null) {
            sb.append("SUMMARY\n")
            sb.append("Total Items Sold,").append(formatNumber(totalItems)).append("\n\n")
        }

        val items = data["items"] as? List<Map<String, Any>> ?: emptyList()
        sb.append("ITEM SALES DETAILS\n")
        sb.append("Item / Service Name,Quantity Sold,Invoice Count,Total Sales (ZAR)\n")
        for (row in items) {
            sb.append(escapeCsv(row["item_name"])).append(",")
                .append(formatNumber(row["quantity_sold"])).append(",")
                .append(row["invoice_count"] ?: 0).append(",")
                .append(formatNumber(row["total_sales"])).append("\n")
        }

        return sb.toString()
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildCustomerReportCsv(data: Map<String, Any>, startDate: String, endDate: String): String {
        val sb = StringBuilder()
        sb.append("Report Name,").append(escapeCsv("Customer Report")).append("\n")
        sb.append("Period,").append(escapeCsv("$startDate to $endDate")).append("\n\n")

        val customers = data["customers"] as? List<Map<String, Any>> ?: emptyList()
        sb.append("CUSTOMER SUMMARY\n")
        sb.append("Customer Name,Email,Phone,Invoice Count,Total Spent (ZAR),Outstanding Balance (ZAR)\n")
        for (row in customers) {
            sb.append(escapeCsv(row["customer_name"])).append(",")
                .append(escapeCsv(row["email"])).append(",")
                .append(escapeCsv(row["phone"])).append(",")
                .append(row["invoice_count"] ?: 0).append(",")
                .append(formatNumber(row["total_spent"])).append(",")
                .append(formatNumber(row["outstanding_balance"])).append("\n")
        }

        return sb.toString()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

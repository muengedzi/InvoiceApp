package com.invoiceapp.fragments

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
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
        Toast.makeText(requireContext(), "Export feature coming soon", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

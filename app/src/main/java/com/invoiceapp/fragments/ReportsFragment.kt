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
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.invoiceapp.R
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

        private const val PERIOD_DAILY = 0
        private const val PERIOD_WEEKLY = 1
        private const val PERIOD_MONTHLY = 2
        private const val PERIOD_QUARTERLY = 3
        private const val PERIOD_YEARLY = 4
        private const val PERIOD_CUSTOM = 5
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
        val periods = arrayOf("Daily", "Weekly", "Monthly", "Quarterly", "Yearly", "Custom Range")
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
            PERIOD_DAILY -> "Today"
            PERIOD_WEEKLY -> "This Week"
            PERIOD_MONTHLY -> "This Month"
            PERIOD_QUARTERLY -> "This Quarter"
            PERIOD_YEARLY -> "This Year"
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
            PERIOD_DAILY -> calendar.add(Calendar.DAY_OF_MONTH, -1)
            PERIOD_WEEKLY -> calendar.add(Calendar.WEEK_OF_YEAR, -1)
            PERIOD_MONTHLY -> calendar.add(Calendar.MONTH, -1)
            PERIOD_QUARTERLY -> calendar.add(Calendar.MONTH, -3)
            PERIOD_YEARLY -> calendar.add(Calendar.YEAR, -1)
        }
        return dateFormat.format(calendar.time)
    }

    private fun getPeriodGroupBy(period: Int): String {
        return when (period) {
            PERIOD_DAILY -> "day"
            PERIOD_WEEKLY -> "week"
            PERIOD_MONTHLY -> "month"
            PERIOD_QUARTERLY -> "quarter"
            PERIOD_YEARLY -> "year"
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

        // Update summary cards
        summary?.let {
            val totalRevenue = (it["total_revenue"] as? Number)?.toDouble() ?: 0.0
            binding.tvReportTotalRevenue.text = currencyFormat.format(totalRevenue)
        }

        // Prepare chart data
        val entries = mutableListOf<BarEntry>()
        val labels = mutableListOf<String>()

        dataList.forEachIndexed { index, item ->
            val amount = (item["total_amount"] as? Number)?.toFloat() ?: 0f
            entries.add(BarEntry(index.toFloat(), amount))
            val label = item["period"] as? String ?: ""
            labels.add(label.take(10))
        }

        if (entries.isNotEmpty()) {
            updateChart(entries, labels, "Sales Revenue")
        }

        // Update recycler view
        reportDetailAdapter.submitList(dataList)
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

        reportDetailAdapter.submitList(revenueByMonth)
    }

    private fun displayTaxReport(data: Map<String, Any>) {
        binding.tvReportTitle.text = "Tax Report"

        val summary = data["summary"] as? Map<String, Any>
        val taxRates = data["tax_rates"] as? List<Map<String, Any>> ?: emptyList()

        summary?.let {
            val totalTax = (it["total_tax_collected"] as? Number)?.toDouble() ?: 0.0
            binding.tvReportTotalRevenue.text = currencyFormat.format(totalTax)
            binding.tvReportTotalRevenue.setTextColor(android.graphics.Color.parseColor("#FF9800"))
        }

        val entries = mutableListOf<BarEntry>()
        val labels = mutableListOf<String>()

        taxRates.forEachIndexed { index, item ->
            val taxAmount = (item["tax_collected"] as? Number)?.toFloat() ?: 0f
            entries.add(BarEntry(index.toFloat(), taxAmount))
            labels.add("${item["tax_rate"]}%")
        }

        if (entries.isNotEmpty()) {
            updateChart(entries, labels, "Tax Collected by Rate")
        }

        reportDetailAdapter.submitList(taxRates)
    }

    private fun displayOutstandingReport(data: Map<String, Any>) {
        binding.tvReportTitle.text = "Outstanding Receivables Report"

        val totalOutstanding = (data["total_outstanding"] as? Number)?.toDouble() ?: 0.0
        val outstandingInvoices = data["outstanding_invoices"] as? List<Map<String, Any>> ?: emptyList()

        binding.tvReportTotalRevenue.text = currencyFormat.format(totalOutstanding)
        binding.tvReportTotalRevenue.setTextColor(android.graphics.Color.parseColor("#F44336"))
        binding.tvReportNetProfit.text = currencyFormat.format(totalOutstanding)
        binding.tvReportNetProfit.setTextColor(android.graphics.Color.parseColor("#F44336"))

        reportDetailAdapter.submitList(outstandingInvoices)
    }

    private fun displayInvoiceStatusReport(data: Map<String, Any>) {
        binding.tvReportTitle.text = "Invoice Status Report"

        val statusBreakdown = data["status_breakdown"] as? List<Map<String, Any>> ?: emptyList()

        val entries = mutableListOf<BarEntry>()
        val labels = mutableListOf<String>()

        statusBreakdown.forEachIndexed { index, item ->
            val count = (item["count"] as? Number)?.toFloat() ?: 0f
            entries.add(BarEntry(index.toFloat(), count))
            labels.add(item["status"] as? String ?: "")
        }

        if (entries.isNotEmpty()) {
            updateChart(entries, labels, "Invoices by Status")
        }

        reportDetailAdapter.submitList(statusBreakdown)
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

        reportDetailAdapter.submitList(customers)
    }

    private fun updateChart(entries: List<BarEntry>, labels: List<String>, title: String) {
        val dataSet = BarDataSet(entries, title).apply {
            color = requireContext().getColor(R.color.purple_500)
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

    private fun resetReportContent() {
        binding.tvReportTotalRevenue.text = currencyFormat.format(0)
        binding.tvReportTotalRevenue.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
        binding.tvReportTotalExpenses.text = currencyFormat.format(0)
        binding.tvReportTotalExpenses.setTextColor(android.graphics.Color.parseColor("#F44336"))
        binding.tvReportNetProfit.text = currencyFormat.format(0)
        binding.tvReportNetProfit.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
        reportDetailAdapter.submitList(emptyList())
        binding.barChart.clear()
        binding.barChart.invalidate()
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
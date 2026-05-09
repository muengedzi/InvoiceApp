package com.invoiceapp.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.app.DatePickerDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.invoiceapp.databinding.FragmentReportsBinding
import com.invoiceapp.network.ApiResponse
import com.invoiceapp.viewmodels.ReportViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class ReportsFragment : Fragment() {
    private var _binding: FragmentReportsBinding? = null
    private val binding get() = _binding!!
    private lateinit var reportViewModel: ReportViewModel
    private val apiDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayDateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
    private var selectedStartDate: Date = Date()
    private var selectedEndDate: Date = Date()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReportsBinding.inflate(inflater, container, false)
        reportViewModel = ViewModelProvider(this)[ReportViewModel::class.java]

        setupDateRange()
        setupClickListeners()
        observeViewModel()

        return binding.root
    }

    private fun setupDateRange() {
        val calendar = Calendar.getInstance()
        val endDate = calendar.time
        calendar.add(Calendar.DAY_OF_MONTH, -30)
        val startDate = calendar.time
        selectedStartDate = startDate
        selectedEndDate = endDate

        loadReportForSelectedRange()
    }

    private fun setupClickListeners() {
        binding.btnGenerateReport.setOnClickListener {
            showDateRangePicker()
        }
    }

    private fun showDateRangePicker() {
        val startCalendar = Calendar.getInstance().apply { time = selectedStartDate }

        DatePickerDialog(
            requireContext(),
            { _, startYear, startMonth, startDay ->
                val pickedStart = Calendar.getInstance().apply {
                    set(startYear, startMonth, startDay)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val endCalendar = Calendar.getInstance().apply { time = selectedEndDate }

                DatePickerDialog(
                    requireContext(),
                    { _, endYear, endMonth, endDay ->
                        val pickedEnd = Calendar.getInstance().apply {
                            set(endYear, endMonth, endDay)
                            set(Calendar.HOUR_OF_DAY, 23)
                            set(Calendar.MINUTE, 59)
                            set(Calendar.SECOND, 59)
                            set(Calendar.MILLISECOND, 999)
                        }

                        selectedStartDate = pickedStart.time
                        selectedEndDate = if (pickedEnd.before(pickedStart)) pickedStart.time else pickedEnd.time
                        loadReportForSelectedRange()
                    },
                    endCalendar.get(Calendar.YEAR),
                    endCalendar.get(Calendar.MONTH),
                    endCalendar.get(Calendar.DAY_OF_MONTH)
                ).apply {
                    setTitle("Select end date")
                }.show()
            },
            startCalendar.get(Calendar.YEAR),
            startCalendar.get(Calendar.MONTH),
            startCalendar.get(Calendar.DAY_OF_MONTH)
        ).apply {
            setTitle("Select start date")
        }.show()
    }

    private fun loadReportForSelectedRange() {
        binding.tvDateRange.text = "${formatDate(selectedStartDate)} - ${formatDate(selectedEndDate)}"
        reportViewModel.loadProfitLossReport(
            apiDateFormat.format(selectedStartDate),
            apiDateFormat.format(selectedEndDate)
        )
    }

    private fun observeViewModel() {
        reportViewModel.profitLossReport.observe(viewLifecycleOwner) { result ->
            when (result) {
                is ApiResponse.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.tvError.visibility = View.GONE
                }
                is ApiResponse.Success -> {
                    binding.progressBar.visibility = View.GONE
                    updateUI(result.data)
                }
                is ApiResponse.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.tvError.text = result.message
                    binding.tvError.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun updateUI(report: com.invoiceapp.models.ProfitLossReport) {
        binding.tvTotalRevenue.text = formatCurrency(report.total_revenue)
        binding.tvTotalExpenses.text = formatCurrency(report.total_expenses)
        binding.tvNetProfit.text = formatCurrency(report.net_profit)

        if (report.net_profit >= 0) {
            binding.tvNetProfit.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
        } else {
            binding.tvNetProfit.setTextColor(android.graphics.Color.parseColor("#F44336"))
        }

        setupBarChart(report.revenue_by_month)
        setupInvoiceStatusChart(report.invoice_counts_by_month)
    }

    private fun setupBarChart(monthlyRevenue: List<com.invoiceapp.models.MonthlyRevenue>) {
        val entries = mutableListOf<BarEntry>()
        val labels = mutableListOf<String>()

        monthlyRevenue.reversed().forEachIndexed { index, item ->
            entries.add(BarEntry(index.toFloat(), item.revenue.toFloat()))
            labels.add("${item.month}/${item.year}")
        }

        if (entries.isNotEmpty()) {
            val dataSet = BarDataSet(entries, "Monthly Revenue")
            dataSet.color = android.graphics.Color.parseColor("#2196F3")
            dataSet.valueTextSize = 10f
            dataSet.setDrawValues(true)

            val barData = BarData(dataSet)
            binding.barChart.data = barData
            binding.barChart.description.isEnabled = false
            binding.barChart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            binding.barChart.xAxis.granularity = 1f
            binding.barChart.xAxis.position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
            binding.barChart.axisLeft.setDrawGridLines(true)
            binding.barChart.axisRight.isEnabled = false
            binding.barChart.invalidate()
        } else {
            binding.barChart.clear()
            binding.barChart.setNoDataText("No data available")
        }
    }

    private fun formatDate(date: Date): String {
        return displayDateFormat.format(date)
    }

    private fun setupInvoiceStatusChart(monthlyCounts: List<com.invoiceapp.models.MonthlyInvoiceStatusCount>) {
        val entries = mutableListOf<BarEntry>()
        val labels = mutableListOf<String>()

        monthlyCounts.forEachIndexed { index, item ->
            entries.add(
                BarEntry(
                    index.toFloat(),
                    floatArrayOf(
                        item.paid.toFloat(),
                        item.pending.toFloat(),
                        item.quotation.toFloat(),
                        item.draft.toFloat()
                    )
                )
            )
            labels.add("${item.month}/${item.year}")
        }

        if (entries.isNotEmpty()) {
            val dataSet = BarDataSet(entries, "Invoices")
            dataSet.stackLabels = arrayOf("Paid", "Pending", "Quotation", "Draft")
            dataSet.colors = listOf(
                android.graphics.Color.parseColor("#4CAF50"),
                android.graphics.Color.parseColor("#FF9800"),
                android.graphics.Color.parseColor("#2196F3"),
                android.graphics.Color.parseColor("#9E9E9E")
            )
            dataSet.valueTextSize = 10f

            binding.invoiceStatusChart.data = BarData(dataSet)
            binding.invoiceStatusChart.description.isEnabled = false
            binding.invoiceStatusChart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            binding.invoiceStatusChart.xAxis.granularity = 1f
            binding.invoiceStatusChart.xAxis.position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
            binding.invoiceStatusChart.axisLeft.axisMinimum = 0f
            binding.invoiceStatusChart.axisRight.isEnabled = false
            binding.invoiceStatusChart.invalidate()
        } else {
            binding.invoiceStatusChart.clear()
            binding.invoiceStatusChart.setNoDataText("No invoice data available")
        }
    }

    private fun formatCurrency(amount: Double): String {
        val format = NumberFormat.getCurrencyInstance()
        return format.format(amount)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

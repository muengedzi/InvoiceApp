package com.invoiceapp.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        // Set default to last 30 days
        val endDate = calendar.time
        calendar.add(Calendar.DAY_OF_MONTH, -30)
        val startDate = calendar.time

        binding.tvDateRange.text = "${formatDate(startDate)} - ${formatDate(endDate)}"

        val startDateStr = dateFormat.format(startDate)
        val endDateStr = dateFormat.format(endDate)

        reportViewModel.loadProfitLossReport(startDateStr, endDateStr)
    }

    private fun setupClickListeners() {
        binding.btnGenerateReport.setOnClickListener {
            showDateRangePicker()
        }
    }

    private fun showDateRangePicker() {
        // TODO: Implement proper date range picker
        Toast.makeText(requireContext(), "Date range picker coming soon", Toast.LENGTH_SHORT).show()
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
        val format = SimpleDateFormat("MMM dd, yyyy", Locale.US)
        return format.format(date)
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
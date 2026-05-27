package com.invoiceapp.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import com.invoiceapp.R
import com.invoiceapp.activities.MainActivity
import com.invoiceapp.adapters.RecentInvoiceAdapter
import com.invoiceapp.adapters.RecentExpenseAdapter
import com.invoiceapp.databinding.FragmentDashboardBinding
import com.invoiceapp.network.ApiResponse
import com.invoiceapp.viewmodels.DashboardViewModel
import java.text.NumberFormat
import java.util.Locale

class DashboardFragment : Fragment() {
    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private lateinit var dashboardViewModel: DashboardViewModel
    private lateinit var invoiceAdapter: RecentInvoiceAdapter
    private lateinit var expenseAdapter: RecentExpenseAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        dashboardViewModel = ViewModelProvider(this)[DashboardViewModel::class.java]

        setupRecyclerViews()
        setupClickListeners()
        observeViewModel()
        dashboardViewModel.loadDashboardStats()

        return binding.root
    }

    private fun setupClickListeners() {
        binding.cardPaidInvoices.setOnClickListener {
            (requireActivity() as? MainActivity)?.navigateToInvoices("paid")
        }

        binding.cardPendingInvoices.setOnClickListener {
            (requireActivity() as? MainActivity)?.navigateToInvoices("pending")
        }

        binding.cardOverdueInvoices.setOnClickListener {
            (requireActivity() as? MainActivity)?.navigateToInvoices("overdue")
        }

        binding.cardExpenses.setOnClickListener {
            (requireActivity() as? MainActivity)?.navigateToExpenses()
        }
    }

    private fun setupRecyclerViews() {
        invoiceAdapter = RecentInvoiceAdapter { invoiceId ->
            val intent = android.content.Intent(requireContext(), com.invoiceapp.activities.InvoiceDetailActivity::class.java)
            intent.putExtra("invoice_id", invoiceId)
            startActivity(intent)
        }

        binding.rvRecentInvoices.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = invoiceAdapter
        }

        expenseAdapter = RecentExpenseAdapter { expenseId ->
            // Handle expense click - navigate to expense detail
        }

        binding.rvRecentExpenses.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = expenseAdapter
        }
    }

    private fun observeViewModel() {
        dashboardViewModel.dashboardStats.observe(viewLifecycleOwner) { result ->
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

    private fun updateUI(stats: com.invoiceapp.models.DashboardStats) {
        // Card 1: Paid Invoices - Main: Amount, Subtext: Count
        binding.tvStatValue.text = formatCurrency(stats.paid_amount)
        binding.tvStatSubtext.text = "${stats.paid_count} paid invoice(s)"
        binding.tvStatLabel.text = "Paid Invoices"

        // Card 2: Pending - Main: Amount, Subtext: Count
        binding.tvStatValuePending.text = formatCurrency(stats.pending_amount)
        binding.tvStatSubtextPending.text = "${stats.pending_count} pending invoice(s)"
        binding.tvStatLabelPending.text = "Pending"

        // Card 3: Overdue - Main: Amount, Subtext: Count
        binding.tvStatValueExpenses.text = formatCurrency(stats.overdue_amount)
        binding.tvStatSubtextExpenses.text = "${stats.overdue_count} overdue invoice(s)"
        binding.tvStatLabelExpenses.text = "Overdue"

        // Card 4: Expenses - Main: Amount (no count for expenses)
        binding.tvStatValueProfit.text = formatCurrency(stats.total_expenses)
        binding.tvStatLabelProfit.text = "Expenses"

        // Update recent invoices
        invoiceAdapter.submitList(stats.recent_invoices)

        // Update recent expenses
        expenseAdapter.submitList(stats.recent_expenses)

        // Setup pie chart
        setupPieChart(stats.invoice_counts)

        // Navigation for View All
        binding.tvViewAllInvoices.setOnClickListener {
            (requireActivity() as? MainActivity)?.navigateToInvoices()
        }

        binding.tvViewAllExpenses.setOnClickListener {
            (requireActivity() as? MainActivity)?.navigateToExpenses()
        }
    }

    private fun setupPieChart(invoiceCounts: Map<String, Int>) {
        val entries = mutableListOf<PieEntry>()
        val colors = mutableListOf<Int>()

        val colorMap = mapOf(
            "paid" to "#4CAF50",
            "pending" to "#FF9800",
            "overdue" to "#F44336",
            "draft" to "#2196F3",
            "quotation" to "#9C27B0",
            "quote" to "#9C27B0"
        )

        invoiceCounts.forEach { (status, count) ->
            if (count > 0) {
                val displayName = when (status.lowercase()) {
                    "quotation", "quote" -> "Quotations"
                    else -> status.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                }
                entries.add(PieEntry(count.toFloat(), displayName))
                val color = android.graphics.Color.parseColor(colorMap[status.lowercase()] ?: "#2196F3")
                colors.add(color)
            }
        }

        if (entries.isNotEmpty()) {
            val dataSet = PieDataSet(entries, "Invoice Status")
            dataSet.colors = colors
            dataSet.valueFormatter = PercentFormatter(binding.pieChart)
            dataSet.valueTextSize = 12f
            dataSet.setDrawValues(true)

            val pieData = PieData(dataSet)
            binding.pieChart.data = pieData
            binding.pieChart.description.isEnabled = false
            binding.pieChart.isDrawHoleEnabled = true
            binding.pieChart.setHoleRadius(40f)
            binding.pieChart.setTransparentCircleRadius(45f)
            binding.pieChart.animateY(1000)
            binding.pieChart.invalidate()
        } else {
            binding.pieChart.clear()
            binding.pieChart.setNoDataText("No invoice data available")
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

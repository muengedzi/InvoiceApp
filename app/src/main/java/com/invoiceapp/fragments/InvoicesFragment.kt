package com.invoiceapp.fragments

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.invoiceapp.R
import com.invoiceapp.activities.CreateInvoiceActivity
import com.invoiceapp.adapters.InvoiceAdapter
import com.invoiceapp.databinding.FragmentInvoicesBinding
import com.invoiceapp.models.Invoice
import com.invoiceapp.network.ApiResponse
import com.invoiceapp.viewmodels.InvoiceViewModel
import java.text.SimpleDateFormat
import java.util.*

class InvoicesFragment : Fragment() {
    private var _binding: FragmentInvoicesBinding? = null
    private val binding get() = _binding!!
    private lateinit var invoiceViewModel: InvoiceViewModel
    private lateinit var invoiceAdapter: InvoiceAdapter

    // Filter variables
    private var filterStatus: String? = null
    private var filterDateFrom: String? = null
    private var filterDateTo: String? = null
    private var isFilterApplied = false

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayDateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInvoicesBinding.inflate(inflater, container, false)
        invoiceViewModel = ViewModelProvider(this)[InvoiceViewModel::class.java]

        setupRecyclerView()
        setupFilterChips()
        setupFAB()
        observeViewModel()

        loadInvoices()

        return binding.root
    }

    private fun setupFilterChips() {
        binding.chipGroupFilters.setOnCheckedStateChangeListener { group, checkedIds ->
            val checkedId = checkedIds.firstOrNull()
            filterStatus = when (checkedId) {
                R.id.chipFilterPaid -> "paid"
                R.id.chipFilterPending -> "pending"
                R.id.chipFilterOverdue -> "overdue"
                R.id.chipFilterDraft -> "draft"
                else -> null
            }
            
            // Sync with isFilterApplied logic
            isFilterApplied = filterStatus != null || !filterDateFrom.isNullOrEmpty() || !filterDateTo.isNullOrEmpty()
            loadInvoices()
        }
    }

    override fun onResume() {
        super.onResume()
        loadInvoices()
    }

    private fun loadInvoices() {
        if (isFilterApplied) {
            invoiceViewModel.loadInvoicesWithFilters(
                page = 1,
                status = filterStatus,
                dateFrom = filterDateFrom,
                dateTo = filterDateTo
            )
        } else {
            invoiceViewModel.loadInvoices(page = 1, status = null)
        }
    }

    private fun setupRecyclerView() {
        invoiceAdapter = InvoiceAdapter { invoiceId ->
            val intent = Intent(requireContext(), com.invoiceapp.activities.InvoiceDetailActivity::class.java)
            intent.putExtra("invoice_id", invoiceId)
            startActivity(intent)
        }

        binding.rvInvoices.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = invoiceAdapter
        }
    }

    private fun setupFAB() {
        val fab = requireActivity().findViewById<FloatingActionButton>(com.invoiceapp.R.id.fab)
        fab?.setOnClickListener {
            startActivity(Intent(requireContext(), CreateInvoiceActivity::class.java))
        }
        fab?.show()
    }

    private fun observeViewModel() {
        invoiceViewModel.invoices.observe(viewLifecycleOwner) { result ->
            when (result) {
                is ApiResponse.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.tvError.visibility = View.GONE
                    binding.tvEmpty.visibility = View.GONE
                }
                is ApiResponse.Success -> {
                    binding.progressBar.visibility = View.GONE

                    val invoices = result.data
                    invoiceAdapter.submitList(invoices)

                    if (invoices.isEmpty()) {
                        binding.tvEmpty.visibility = View.VISIBLE
                        binding.rvInvoices.visibility = View.GONE

                        if (isFilterApplied) {
                            binding.tvEmpty.text = "No invoices match the selected filters.\nTap filter icon to change criteria."
                        } else {
                            binding.tvEmpty.text = "No invoices found.\nTap + to create an invoice."
                        }
                    } else {
                        binding.tvEmpty.visibility = View.GONE
                        binding.rvInvoices.visibility = View.VISIBLE
                    }
                }
                is ApiResponse.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.tvError.text = result.message
                    binding.tvError.visibility = View.VISIBLE
                    binding.tvEmpty.visibility = View.GONE
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.invoices_filter_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_filter -> {
                showFilterDialog()
                true
            }
            R.id.menu_clear_filter -> {
                clearFilters()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showFilterDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_invoice_filter, null)
        val radioGroupStatus = dialogView.findViewById<RadioGroup>(R.id.radioGroupStatus)
        val radioAll = dialogView.findViewById<RadioButton>(R.id.radioAll)
        val radioPaid = dialogView.findViewById<RadioButton>(R.id.radioPaid)
        val radioPending = dialogView.findViewById<RadioButton>(R.id.radioPending)
        val radioOverdue = dialogView.findViewById<RadioButton>(R.id.radioOverdue)
        val radioDraft = dialogView.findViewById<RadioButton>(R.id.radioDraft)
        val btnStartDate = dialogView.findViewById<Button>(R.id.btnStartDate)
        val btnEndDate = dialogView.findViewById<Button>(R.id.btnEndDate)
        val tvStartDate = dialogView.findViewById<TextView>(R.id.tvStartDate)
        val tvEndDate = dialogView.findViewById<TextView>(R.id.tvEndDate)

        // Set current filter values
        when (filterStatus) {
            "paid" -> radioPaid.isChecked = true
            "pending" -> radioPending.isChecked = true
            "overdue" -> radioOverdue.isChecked = true
            "draft" -> radioDraft.isChecked = true
            else -> radioAll.isChecked = true
        }

        if (!filterDateFrom.isNullOrEmpty()) {
            tvStartDate.text = formatDisplayDate(filterDateFrom!!)
            tvStartDate.visibility = View.VISIBLE
        }

        if (!filterDateTo.isNullOrEmpty()) {
            tvEndDate.text = formatDisplayDate(filterDateTo!!)
            tvEndDate.visibility = View.VISIBLE
        }

        // Start date picker
        btnStartDate.setOnClickListener {
            showDatePicker { selectedDate ->
                filterDateFrom = selectedDate
                tvStartDate.text = formatDisplayDate(selectedDate)
                tvStartDate.visibility = View.VISIBLE
            }
        }

        // End date picker
        btnEndDate.setOnClickListener {
            showDatePicker { selectedDate ->
                filterDateTo = selectedDate
                tvEndDate.text = formatDisplayDate(selectedDate)
                tvEndDate.visibility = View.VISIBLE
            }
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Filter Invoices")
            .setView(dialogView)
            .setPositiveButton("Apply") { _, _ ->
                filterStatus = when (radioGroupStatus.checkedRadioButtonId) {
                    R.id.radioPaid -> "paid"
                    R.id.radioPending -> "pending"
                    R.id.radioOverdue -> "overdue"
                    R.id.radioDraft -> "draft"
                    else -> null
                }

                isFilterApplied = filterStatus != null || !filterDateFrom.isNullOrEmpty() || !filterDateTo.isNullOrEmpty()

                loadInvoices()

                val message = buildFilterMessage()
                if (message.isNotEmpty()) {
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Clear All") { _, _ ->
                clearFilters()
            }
            .show()
    }

    private fun buildFilterMessage(): String {
        val parts = mutableListOf<String>()
        if (filterStatus != null) {
            parts.add("Status: ${filterStatus?.capitalize()}")
        }
        if (!filterDateFrom.isNullOrEmpty()) {
            parts.add("From: ${formatDisplayDate(filterDateFrom!!)}")
        }
        if (!filterDateTo.isNullOrEmpty()) {
            parts.add("To: ${formatDisplayDate(filterDateTo!!)}")
        }
        return if (parts.isNotEmpty()) "Filter applied: ${parts.joinToString(", ")}" else ""
    }

    private fun showDatePicker(onDateSelected: (String) -> Unit) {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(requireContext(), { _, selectedYear, selectedMonth, selectedDay ->
            val selectedDate = Calendar.getInstance()
            selectedDate.set(selectedYear, selectedMonth, selectedDay)
            val dateString = dateFormat.format(selectedDate.time)
            onDateSelected(dateString)
        }, year, month, day).show()
    }

    private fun formatDisplayDate(dateString: String): String {
        return try {
            val date = dateFormat.parse(dateString)
            displayDateFormat.format(date!!)
        } catch (e: Exception) {
            dateString
        }
    }

    private fun clearFilters() {
        filterStatus = null
        filterDateFrom = null
        filterDateTo = null
        isFilterApplied = false
        loadInvoices()
        Toast.makeText(requireContext(), "Filters cleared", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
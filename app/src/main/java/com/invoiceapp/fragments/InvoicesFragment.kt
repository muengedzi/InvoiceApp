package com.invoiceapp.fragments

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import com.invoiceapp.activities.MainActivity
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

    // State for local filtering
    private var allInvoices = listOf<Invoice>()
    private var filterStatus: String? = null
    private var filterDateFrom: String? = null
    private var filterDateTo: String? = null
    private var searchQuery: String = ""

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayDateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)

    companion object {
        private const val ARG_STATUS_FILTER = "status_filter"

        fun newInstance(statusFilter: String? = null): InvoicesFragment {
            return InvoicesFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_STATUS_FILTER, statusFilter)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        filterStatus = arguments?.getString(ARG_STATUS_FILTER)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInvoicesBinding.inflate(inflater, container, false)
        invoiceViewModel = ViewModelProvider(this)[InvoiceViewModel::class.java]

        setupRecyclerView()
        setupSearch()
        setupFilterChips()
        setupFAB()
        observeViewModel()

        loadInvoices()

        return binding.root
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString().orEmpty().trim()
                applyFiltersAndSorting()
            }
        })

        binding.etSearch.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(v.windowToken, 0)
                true
            } else {
                false
            }
        }
    }

    private fun setupFilterChips() {
        binding.chipGroupFilters.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull()
            filterStatus = when (checkedId) {
                R.id.chipFilterPaid -> "paid"
                R.id.chipFilterPending -> "pending"
                R.id.chipFilterOverdue -> "overdue"
                R.id.chipFilterDraft -> "draft"
                R.id.chipFilterQuotation -> "quotation"
                else -> null
            }
            applyFiltersAndSorting()
        }

        when (filterStatus) {
            "paid" -> binding.chipFilterPaid.isChecked = true
            "pending" -> binding.chipFilterPending.isChecked = true
            "overdue" -> binding.chipFilterOverdue.isChecked = true
        }
    }

    override fun onResume() {
        super.onResume()
        loadInvoices()
    }

    private fun loadInvoices() {
        // Load all for local processing as requested
        invoiceViewModel.loadInvoicesWithFilters(page = 1, perPage = 1000, resetList = true)
    }

    private fun applyFiltersAndSorting() {
        var filteredList = allInvoices

        // 1. Filter by Status
        if (filterStatus != null) {
            filteredList = filteredList.filter { 
                val s = it.status.lowercase()
                if (filterStatus == "quotation") {
                    s == "quotation" || s == "quote" || s == "quotations"
                } else {
                    s == filterStatus
                }
            }
        }

        // 2. Filter by Date Range
        if (!filterDateFrom.isNullOrEmpty()) {
            filteredList = filteredList.filter { it.invoice_date >= filterDateFrom!! }
        }
        if (!filterDateTo.isNullOrEmpty()) {
            filteredList = filteredList.filter { it.invoice_date <= filterDateTo!! }
        }

        // 3. Filter by Search Query
        if (searchQuery.isNotEmpty()) {
            val q = searchQuery.lowercase()
            filteredList = filteredList.filter {
                it.customer_name?.lowercase()?.contains(q) == true ||
                        it.invoice_number.lowercase().contains(q)
            }
        }

        // 4. Sort: Overdue > Pending > Draft > Quotation > Paid (Archived)
        // Within each group, sort by date. 
        // For Paid invoices, sort by updated_at to show "recently paid" at the top.
        val sortedList = filteredList.sortedWith(compareBy<Invoice> {
            when (it.status.lowercase()) {
                "overdue" -> 0
                "pending" -> 1
                "draft" -> 2
                "quotation", "quote", "quotations" -> 3
                "paid" -> 4
                else -> 5
            }
        }.thenByDescending { 
            // If status is paid, use updated_at for "recently paid" logic
            if (it.status.lowercase() == "paid") {
                it.updated_at ?: it.invoice_date
            } else {
                it.invoice_date 
            }
        })

        invoiceAdapter.submitList(sortedList)

        // UI Feedback
        if (sortedList.isEmpty()) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.rvInvoices.visibility = View.GONE
            binding.tvEmpty.text = if (allInvoices.isEmpty()) {
                "No invoices found.\nTap + to create an invoice."
            } else {
                "No matching invoices found."
            }
        } else {
            binding.tvEmpty.visibility = View.GONE
            binding.rvInvoices.visibility = View.VISIBLE
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
        val activity = activity ?: return
        val fab = activity.findViewById<FloatingActionButton>(com.invoiceapp.R.id.fab) ?: return
        fab.setOnClickListener {
            startActivity(Intent(requireContext(), CreateInvoiceActivity::class.java))
        }
        fab.show()
    }

    private fun observeViewModel() {
        invoiceViewModel.invoices.observe(viewLifecycleOwner) { result ->
            when (result) {
                is ApiResponse.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.tvError.visibility = View.GONE
                }
                is ApiResponse.Success -> {
                    binding.progressBar.visibility = View.GONE
                    allInvoices = result.data
                    applyFiltersAndSorting()
                }
                is ApiResponse.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.tvError.text = result.message
                    binding.tvError.visibility = View.VISIBLE
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
        val radioQuotation = dialogView.findViewById<RadioButton>(R.id.radioQuotation)
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
            "quotation" -> radioQuotation.isChecked = true
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

        btnStartDate.setOnClickListener {
            showDatePicker { selectedDate ->
                filterDateFrom = selectedDate
                tvStartDate.text = formatDisplayDate(selectedDate)
                tvStartDate.visibility = View.VISIBLE
            }
        }

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
                    R.id.radioQuotation -> "quotation"
                    else -> null
                }
                applyFiltersAndSorting()
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Clear All") { _, _ ->
                clearFilters()
            }
            .show()
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
        searchQuery = ""
        binding.etSearch.setText("")
        binding.chipGroupFilters.clearCheck()
        applyFiltersAndSorting()
        Toast.makeText(requireContext(), "Filters cleared", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

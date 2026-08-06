package com.invoiceapp.activities

import android.app.DatePickerDialog
import android.animation.ObjectAnimator
import android.content.Intent
import android.text.Editable
import android.text.TextWatcher
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.invoiceapp.R
import com.invoiceapp.adapters.InvoiceItemAdapter
import com.invoiceapp.databinding.ActivityCreateInvoiceBinding
import com.invoiceapp.models.*
import com.invoiceapp.network.RetrofitClient
import com.invoiceapp.utils.SessionManager
import androidx.lifecycle.ViewModelProvider
import com.invoiceapp.network.ApiResponse
import com.invoiceapp.viewmodels.InvoiceViewModel
import com.invoiceapp.viewmodels.SettingsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class CreateInvoiceActivity : BaseActivity() {

    private lateinit var binding: ActivityCreateInvoiceBinding
    private lateinit var itemAdapter: InvoiceItemAdapter
    private lateinit var invoiceViewModel: InvoiceViewModel
    private lateinit var settingsViewModel: SettingsViewModel
    private val invoiceItems = mutableListOf<InvoiceItemRequest>()
    private var selectedCustomer: Customer? = null
    private var editingInvoiceId: Int? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayDateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
    private lateinit var sessionManager: SessionManager
    private var customersList = listOf<Customer>()
    private var shouldRefreshCustomers = false
    private var loadingAnimator: ObjectAnimator? = null
    private var companySettings: CompanySettings? = null

    // Recurring frequency options
    private lateinit var frequencyAdapter: ArrayAdapter<String>
    private val frequencies = listOf("weekly", "biweekly", "monthly", "bimonthly", "quarterly", "yearly")
    private val frequencyDisplayLabels = mapOf(
        "weekly" to "Weekly (Every 7 days)",
        "biweekly" to "Bi-weekly (Every 14 days)",
        "monthly" to "Monthly (Every month)",
        "bimonthly" to "Bi-monthly (Every 2 months)",
        "quarterly" to "Quarterly (Every 3 months)",
        "yearly" to "Yearly (Every year)"
    )

    companion object {
        private const val TAG = "CreateInvoiceActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateInvoiceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)
        invoiceViewModel = ViewModelProvider(this)[InvoiceViewModel::class.java]
        settingsViewModel = ViewModelProvider(this)[SettingsViewModel::class.java]
        editingInvoiceId = intent.getIntExtra("invoice_id", -1).takeIf { it != -1 }

        setupToolbar()
        setupRecyclerView()
        setupRecurringFrequencySpinner()
        setupClickListeners()
        setupTextWatchers()
        observeViewModel()
        loadCustomers()

        if (editingInvoiceId != null) {
            invoiceViewModel.getInvoiceDetail(editingInvoiceId!!)
        } else {
            setDefaultDates()
            settingsViewModel.loadSettings()
        }
    }

    override fun onResume() {
        super.onResume()
        if (shouldRefreshCustomers) {
            shouldRefreshCustomers = false
            loadCustomers()
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (editingInvoiceId != null) {
            supportActionBar?.title = "Edit Invoice"
            binding.btnCreateInvoice.text = "Update Invoice"
        } else {
            supportActionBar?.title = "Create Invoice"
            binding.btnCreateInvoice.text = "Create Invoice"
        }
    }

    private fun setupRecyclerView() {
        itemAdapter = InvoiceItemAdapter(invoiceItems) { position ->
            invoiceItems.removeAt(position)
            itemAdapter.notifyItemRemoved(position)
            itemAdapter.notifyItemRangeChanged(position, invoiceItems.size - position)
            calculateTotals()
        }

        binding.recyclerInvoiceItems.apply {
            layoutManager = LinearLayoutManager(this@CreateInvoiceActivity)
            adapter = itemAdapter
            isNestedScrollingEnabled = false
            setHasFixedSize(false)
        }
    }

    private fun setupRecurringFrequencySpinner() {
        val displayLabels = frequencies.map { frequencyDisplayLabels[it] ?: it }
        frequencyAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, displayLabels)
        (binding.etRecurringFrequency as AutoCompleteTextView).setAdapter(frequencyAdapter)
        binding.etRecurringFrequency.setText(frequencyDisplayLabels["monthly"], false)
    }

    private fun setupClickListeners() {
        binding.btnSelectCustomer.setOnClickListener {
            showCustomerSelector()
        }

        binding.btnAddItem.setOnClickListener {
            showSelectItemDialog()
        }

        binding.btnSelectDate.setOnClickListener {
            showDatePicker()
        }

        binding.btnSelectDueDate.setOnClickListener {
            showDueDatePicker()
        }

        binding.btnCreateInvoice.setOnClickListener {
            if (editingInvoiceId != null) {
                updateInvoice()
            } else {
                createInvoice()
            }
        }

        binding.btnSendInvoice.setOnClickListener {
            if (editingInvoiceId != null) {
                convertDraftToPending()
            }
        }

        binding.btnSaveAsDraft.setOnClickListener {
            if (editingInvoiceId != null) {
                updateInvoiceAsDraft()
            } else {
                createInvoiceAsDraft()
            }
        }

        binding.btnCancel.setOnClickListener {
            finish()
        }

        binding.chkRecurring.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutRecurringOptions.visibility = if (isChecked) View.VISIBLE else View.GONE
            if (isChecked) {
                val currentDay = Calendar.getInstance().get(Calendar.DAY_OF_MONTH).coerceAtMost(28)
                binding.etRecurringDay.setText(currentDay.toString())
                updateNextSendDateInfo()
            }
        }

        binding.etRecurringFrequency.setOnItemClickListener { _, _, position, _ ->
            val selectedFrequency = frequencies[position]
            updateSendDayVisibility(selectedFrequency)
            updateNextSendDateInfo()
        }

        binding.etRecurringDay.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateNextSendDateInfo()
            }
        })
    }

    private fun updateSendDayVisibility(frequency: String) {
        val showSendDay = frequency == "monthly"
        binding.layoutSendDay.visibility = if (showSendDay) View.VISIBLE else View.GONE
    }

    private fun updateNextSendDateInfo() {
        if (!binding.chkRecurring.isChecked) return

        val selectedDisplay = binding.etRecurringFrequency.text.toString()
        val frequency = frequencies.find { frequencyDisplayLabels[it] == selectedDisplay } ?: "monthly"
        val dayOfMonth = binding.etRecurringDay.text.toString().toIntOrNull() ?: 1

        val nextDate = calculateNextSendDate(frequency, dayOfMonth)
        val formattedDate = SimpleDateFormat("MMM dd, yyyy", Locale.US).format(nextDate)

        val frequencyDisplay = frequencyDisplayLabels[frequency] ?: frequency
        binding.tvNextSendInfo.text = "Next invoice will be sent on: $formattedDate ($frequencyDisplay)"
    }

    private fun calculateNextSendDate(frequency: String, dayOfMonth: Int): Date {
        val calendar = Calendar.getInstance()
        val today = calendar.time

        return when (frequency) {
            "weekly" -> {
                calendar.add(Calendar.DAY_OF_MONTH, 7)
                calendar.time
            }
            "biweekly" -> {
                calendar.add(Calendar.DAY_OF_MONTH, 14)
                calendar.time
            }
            "monthly" -> {
                val targetDay = dayOfMonth.coerceAtMost(28)
                calendar.set(Calendar.DAY_OF_MONTH, targetDay)

                if (calendar.time <= today) {
                    calendar.add(Calendar.MONTH, 1)
                    val maxDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
                    if (targetDay > maxDay) {
                        calendar.set(Calendar.DAY_OF_MONTH, maxDay)
                    }
                }
                calendar.time
            }
            "bimonthly" -> {
                calendar.add(Calendar.MONTH, 2)
                calendar.time
            }
            "quarterly" -> {
                calendar.add(Calendar.MONTH, 3)
                calendar.time
            }
            "yearly" -> {
                calendar.add(Calendar.YEAR, 1)
                calendar.time
            }
            else -> {
                calendar.add(Calendar.MONTH, 1)
                calendar.time
            }
        }
    }

    private fun setupTextWatchers() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s === binding.etTaxRate.editableText) {
                    val newRate = s.toString().toDoubleOrNull() ?: 0.0
                    for (i in invoiceItems.indices) {
                        invoiceItems[i] = invoiceItems[i].copy(tax_rate = newRate)
                    }
                    itemAdapter.notifyDataSetChanged()
                }
                calculateTotals()
            }
        }

        binding.etTaxRate.addTextChangedListener(watcher)
        binding.etDiscount.addTextChangedListener(watcher)
        binding.etShipping.addTextChangedListener(watcher)

        binding.rgTaxType.setOnCheckedChangeListener { _, _ ->
            calculateTotals()
        }
    }

    private fun observeViewModel() {
        invoiceViewModel.createResult.observe(this) { result ->
            when (result) {
                is ApiResponse.Loading -> showLoading(true)
                is ApiResponse.Success -> {
                    showLoading(false)
                    // If recurring was checked, also create the template
                    if (binding.chkRecurring.isChecked) {
                        createRecurringTemplateSeparately()
                    } else {
                        showSuccessAndFinish("Invoice saved successfully!")
                    }
                }
                is ApiResponse.Error -> {
                    showLoading(false)
                    Toast.makeText(this, "Error: ${result.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        invoiceViewModel.sendResult.observe(this) { result ->
            when (result) {
                is ApiResponse.Loading -> showLoading(true)
                is ApiResponse.Success -> {
                    showLoading(false)
                    showSuccessAndFinish("Invoice sent successfully!")
                }
                is ApiResponse.Error -> {
                    showLoading(false)
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        invoiceViewModel.invoiceDetail.observe(this) { result ->
            when (result) {
                is ApiResponse.Loading -> showLoading(true)
                is ApiResponse.Success -> {
                    showLoading(false)
                    populateInvoiceForEditing(result.data)
                }
                is ApiResponse.Error -> {
                    showLoading(false)
                    Toast.makeText(this, "Failed to load invoice details", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }

        settingsViewModel.settings.observe(this) { result ->
            if (result is ApiResponse.Success && editingInvoiceId == null) {
                companySettings = result.data
                applyDefaultSettings(result.data)
            }
        }
    }

    private fun applyDefaultSettings(settings: CompanySettings) {
        binding.etTaxRate.setText(settings.tax_rate_default.toString())
        binding.etNotes.setText(settings.invoice_notes_default ?: "")
        binding.etTermsConditions.setText(settings.terms_conditions_default ?: "")
        binding.etFooterText.setText(settings.footer_text_default ?: "")
    }

    private fun loadCustomers() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getCustomers(page = 1, perPage = 100)
                if (response.isSuccessful) {
                    customersList = response.body()?.customers ?: emptyList()
                    if (editingInvoiceId != null && selectedCustomer == null) {
                        invoiceViewModel.invoiceDetail.value?.let { result ->
                            if (result is ApiResponse.Success) {
                                populateCustomerView(result.data.customer_id)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@CreateInvoiceActivity, "Error loading customers", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun populateCustomerView(customerId: Int?) {
        selectedCustomer = customersList.find { it.id == customerId }
        selectedCustomer?.let {
            binding.tvSelectedCustomer.text = it.customer_name
            binding.tvSelectedCustomerDetails.text = "${it.email ?: ""}\n${it.phone ?: ""}"
        }
    }

    private fun populateInvoiceForEditing(invoice: Invoice) {
        populateCustomerView(invoice.customer_id)

        binding.etPoNumber.setText(invoice.po_number ?: "")
        binding.etInvoiceDate.setText(invoice.invoice_date)
        binding.tvSelectedDate.text = formatDateForDisplay(invoice.invoice_date)

        if (!invoice.due_date.isNullOrEmpty() && invoice.due_date != "null") {
            binding.etDueDate.setText(invoice.due_date)
            binding.tvSelectedDueDate.text = formatDateForDisplay(invoice.due_date)
        }

        binding.etTaxRate.setText(invoice.taxRate.toString())
        if (invoice.tax_type == "inclusive") {
            binding.rbTaxInclusive.isChecked = true
        } else {
            binding.rbTaxExclusive.isChecked = true
        }
        binding.etDiscount.setText(invoice.discount_amount.toString())
        binding.etShipping.setText(invoice.shipping_fee.toString())
        binding.etNotes.setText(invoice.notes ?: "")
        binding.etTermsConditions.setText(invoice.terms_conditions ?: "")
        binding.etFooterText.setText(invoice.footer_text ?: "")

        if (invoice.status.lowercase() == "draft") {
            binding.btnSendInvoice.visibility = View.VISIBLE
            binding.btnCreateInvoice.visibility = View.GONE
            binding.btnSaveAsDraft.text = "Save Draft"
            supportActionBar?.title = "Edit Draft Invoice"
        } else {
            binding.btnSendInvoice.visibility = View.GONE
            binding.btnCreateInvoice.visibility = View.VISIBLE
            binding.btnSaveAsDraft.text = "Save as Draft"
            supportActionBar?.title = "Edit Invoice"
        }

        invoice.items?.let { items ->
            invoiceItems.clear()
            invoiceItems.addAll(items.map {
                InvoiceItemRequest(
                    item_name = it.itemName,
                    description = it.description,
                    quantity = it.quantity,
                    unit_price = it.unitPrice,
                    discount_amount = it.discountAmount,
                    tax_rate = it.taxRate
                )
            })
            itemAdapter.notifyDataSetChanged()
            calculateTotals()
        }
    }

    private fun showCustomerSelector() {
        if (customersList.isEmpty()) {
            showAddCustomerPrompt()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_select_customer, null)
        val searchInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etCustomerSearch)
        val customerListView = dialogView.findViewById<ListView>(R.id.listCustomers)
        val emptyView = dialogView.findViewById<TextView>(R.id.tvCustomerEmpty)
        var filteredCustomers = customersList
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, filteredCustomers.map(::formatCustomerChoice))
        customerListView.adapter = adapter

        fun updateFilteredCustomers(query: String) {
            val normalizedQuery = query.trim().lowercase()
            filteredCustomers = if (normalizedQuery.isBlank()) {
                customersList
            } else {
                customersList.filter { customer ->
                    listOfNotNull(
                        customer.customer_name,
                        customer.email,
                        customer.phone,
                        customer.address,
                        customer.city
                    ).any { it.lowercase().contains(normalizedQuery) }
                }
            }

            adapter.clear()
            adapter.addAll(filteredCustomers.map(::formatCustomerChoice))
            adapter.notifyDataSetChanged()
            emptyView.visibility = if (filteredCustomers.isEmpty()) View.VISIBLE else View.GONE
            customerListView.visibility = if (filteredCustomers.isEmpty()) View.GONE else View.VISIBLE
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Select Customer")
            .setView(dialogView)
            .setPositiveButton("Add Customer") { _, _ ->
                openAddCustomer()
            }
            .setNegativeButton("Cancel", null)
            .create()

        customerListView.setOnItemClickListener { _, _, position, _ ->
            selectCustomer(filteredCustomers[position])
            dialog.dismiss()
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                updateFilteredCustomers(s?.toString().orEmpty())
            }
        })

        dialog.setOnShowListener {
            searchInput.requestFocus()
        }
        dialog.show()
    }

    private fun formatCustomerChoice(customer: Customer): String {
        val contact = customer.email.ifBlank { customer.phone ?: "No contact" }
        return "${customer.customer_name}\n$contact"
    }

    private fun selectCustomer(customer: Customer) {
        selectedCustomer = customer
        binding.tvSelectedCustomer.text = customer.customer_name
        binding.tvSelectedCustomerDetails.text = "${customer.email}\n${customer.phone ?: ""}"
    }

    private fun showAddCustomerPrompt() {
        AlertDialog.Builder(this)
            .setTitle("No Customers Yet")
            .setMessage("Add a customer first, then return here to select them for this invoice.")
            .setPositiveButton("Add Customer") { _, _ ->
                openAddCustomer()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openAddCustomer() {
        shouldRefreshCustomers = true
        startActivity(Intent(this, AddEditCustomerActivity::class.java))
    }

    private fun showSelectItemDialog() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getInvoiceItems(page = 1, perPage = 100)
                if (response.isSuccessful && response.body() != null) {
                    val items = response.body()!!.items
                    if (items.isEmpty()) {
                        showAddItemDialog()
                        return@launch
                    }

                    val itemNames = items.map { "${it.itemName} - ${formatCurrency(it.unitPrice)}" }.toTypedArray()

                    AlertDialog.Builder(this@CreateInvoiceActivity)
                        .setTitle("Select Item")
                        .setItems(itemNames) { _, which ->
                            val selectedItem = items[which]
                            showAddItemDialogWithData(selectedItem)
                        }
                        .setPositiveButton("Create New") { _, _ ->
                            showAddItemDialog()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                } else {
                    showAddItemDialog()
                }
            } catch (e: Exception) {
                showAddItemDialog()
            }
        }
    }

    private fun showAddItemDialogWithData(item: InvoiceItemMaster) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_invoice_item, null)
        val etItemName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etItemName)
        val etDescription = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etDescription)
        val etQuantity = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etQuantity)
        val etUnitPrice = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etUnitPrice)
        val etDiscount = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etDiscount)
        val etTaxRate = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etItemTaxRate)
        val chkSaveForLater = dialogView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.chkSaveForLater)

        etItemName.setText(item.itemName)
        etDescription.setText(item.description ?: "")
        etUnitPrice.setText(item.unitPrice.toString())
        etQuantity.setText("1")

        val defaultTaxRate = if (item.taxRate > 0) item.taxRate else (binding.etTaxRate.text.toString().toDoubleOrNull() ?: 0.0)
        etTaxRate.setText(defaultTaxRate.toString())

        chkSaveForLater.visibility = View.GONE

        AlertDialog.Builder(this)
            .setTitle("Add Invoice Item")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val itemName = etItemName.text.toString().trim()
                if (itemName.isEmpty()) {
                    Toast.makeText(this, "Item name required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val quantity = etQuantity.text.toString().toDoubleOrNull() ?: 1.0
                val unitPrice = etUnitPrice.text.toString().toDoubleOrNull() ?: 0.0
                val discount = etDiscount.text.toString().toDoubleOrNull() ?: 0.0
                val taxRate = etTaxRate.text.toString().toDoubleOrNull() ?: 0.0

                val invoiceItem = InvoiceItemRequest(
                    item_name = itemName,
                    description = etDescription.text.toString().trim().ifEmpty { null },
                    quantity = quantity,
                    unit_price = unitPrice,
                    discount_amount = discount,
                    tax_rate = taxRate
                )

                invoiceItems.add(invoiceItem)
                itemAdapter.notifyItemInserted(invoiceItems.size - 1)
                calculateTotals()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddItemDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_invoice_item, null)
        val etItemName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etItemName)
        val etDescription = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etDescription)
        val etQuantity = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etQuantity)
        val etUnitPrice = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etUnitPrice)
        val etDiscount = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etDiscount)
        val etTaxRate = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etItemTaxRate)
        val chkSaveForLater = dialogView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.chkSaveForLater)

        etTaxRate.setText(binding.etTaxRate.text.toString())

        AlertDialog.Builder(this)
            .setTitle("Add Invoice Item")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val itemName = etItemName.text.toString().trim()
                if (itemName.isEmpty()) {
                    Toast.makeText(this, "Item name required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val quantity = etQuantity.text.toString().toDoubleOrNull() ?: 1.0
                val unitPrice = etUnitPrice.text.toString().toDoubleOrNull() ?: 0.0
                val discount = etDiscount.text.toString().toDoubleOrNull() ?: 0.0
                val taxRate = etTaxRate.text.toString().toDoubleOrNull() ?: 0.0
                val saveForLater = chkSaveForLater.isChecked

                if (saveForLater) {
                    saveItemToMasterList(itemName, etDescription.text.toString(), unitPrice, taxRate)
                }

                val item = InvoiceItemRequest(
                    item_name = itemName,
                    description = etDescription.text.toString().trim().ifEmpty { null },
                    quantity = quantity,
                    unit_price = unitPrice,
                    discount_amount = discount,
                    tax_rate = taxRate
                )

                invoiceItems.add(item)
                itemAdapter.notifyItemInserted(invoiceItems.size - 1)
                calculateTotals()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveItemToMasterList(name: String, description: String, price: Double, taxRate: Double) {
        lifecycleScope.launch {
            try {
                val request = InvoiceItemMasterRequest(
                    itemName = name,
                    description = description.ifBlank { null },
                    unitPrice = price,
                    taxRate = taxRate
                )
                RetrofitClient.apiService.createInvoiceItem(request)
            } catch (e: Exception) {}
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, day ->
                val date = Calendar.getInstance().apply { set(year, month, day) }.time
                binding.tvSelectedDate.text = displayDateFormat.format(date)
                binding.etInvoiceDate.setText(dateFormat.format(date))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showDueDatePicker() {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_MONTH, 30)
        DatePickerDialog(
            this,
            { _, year, month, day ->
                val date = Calendar.getInstance().apply { set(year, month, day) }.time
                binding.tvSelectedDueDate.text = displayDateFormat.format(date)
                binding.etDueDate.setText(dateFormat.format(date))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun setDefaultDates() {
        val today = Date()
        binding.tvSelectedDate.text = displayDateFormat.format(today)
        binding.etInvoiceDate.setText(dateFormat.format(today))

        val dueCalendar = Calendar.getInstance()
        dueCalendar.add(Calendar.DAY_OF_MONTH, 30)
        binding.tvSelectedDueDate.text = displayDateFormat.format(dueCalendar.time)
        binding.etDueDate.setText(dateFormat.format(dueCalendar.time))
    }

    private fun formatDateForDisplay(dateString: String): String {
        if (dateString.isEmpty() || dateString == "null") {
            return ""
        }
        return try {
            val date = dateFormat.parse(dateString)
            displayDateFormat.format(date)
        } catch (e: Exception) {
            dateString
        }
    }

    private fun calculateTotals() {
        var subtotal = 0.0
        var totalTaxAmount = 0.0
        val isTaxInclusive = binding.rbTaxInclusive.isChecked

        for (item in invoiceItems) {
            val itemAmount = item.quantity * item.unit_price - item.discount_amount
            subtotal += itemAmount

            val taxRate = item.tax_rate
            if (isTaxInclusive) {
                totalTaxAmount += itemAmount - (itemAmount / (1 + (taxRate / 100)))
            } else {
                totalTaxAmount += itemAmount * (taxRate / 100)
            }
        }

        val discount = binding.etDiscount.text.toString().toDoubleOrNull() ?: 0.0
        val shipping = binding.etShipping.text.toString().toDoubleOrNull() ?: 0.0

        val finalSubtotal = if (isTaxInclusive) subtotal - totalTaxAmount else subtotal
        val total = (if (isTaxInclusive) subtotal else subtotal + totalTaxAmount) - discount + shipping

        binding.tvSubtotal.text = formatCurrency(finalSubtotal)
        binding.tvTaxAmount.text = formatCurrency(totalTaxAmount)
        binding.tvTotal.text = formatCurrency(total)
    }

    private fun validateInvoice(): Boolean {
        if (selectedCustomer == null) {
            Toast.makeText(this, "Please select a customer", Toast.LENGTH_SHORT).show()
            return false
        }

        if (invoiceItems.isEmpty()) {
            Toast.makeText(this, "Please add at least one item", Toast.LENGTH_SHORT).show()
            return false
        }

        val invoiceDate = binding.etInvoiceDate.text.toString()
        if (invoiceDate.isEmpty()) {
            Toast.makeText(this, "Please select a date", Toast.LENGTH_SHORT).show()
            return false
        }

        val dueDate = binding.etDueDate.text.toString()
        if (dueDate.isEmpty()) {
            Toast.makeText(this, "Please select a due date", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    // ==================== RECURRING INVOICE METHODS ====================

    private fun createInvoice() {
        if (!validateInvoice()) return
        val request = getInvoiceRequest()
        invoiceViewModel.createInvoice(request)
    }

    private fun createInvoiceAsDraft() {
        if (!validateInvoice()) return
        val request = getInvoiceRequest()
        invoiceViewModel.createInvoiceAsDraft(request)
    }

    private fun updateInvoice() {
        if (!validateInvoice()) return
        editingInvoiceId?.let { id ->
            val request = getInvoiceRequest()
            invoiceViewModel.updateInvoice(id, request)
        }
    }

    private fun updateInvoiceAsDraft() {
        if (!validateInvoice()) return
        editingInvoiceId?.let { id ->
            val request = getInvoiceRequest()
            invoiceViewModel.updateInvoiceAsDraft(id, request)
        }
    }

    // This function creates the recurring template AFTER the invoice is successfully created
    private fun createRecurringTemplateSeparately() {
        lifecycleScope.launch {
            try {
                showLoading(true)

                val selectedDisplay = binding.etRecurringFrequency.text.toString()
                val frequency = frequencies.find { frequencyDisplayLabels[it] == selectedDisplay } ?: "monthly"
                val dayOfMonth = binding.etRecurringDay.text.toString().toIntOrNull()?.coerceIn(1, 28) ?: 1

                val dueDays = try {
                    val invDate = dateFormat.parse(binding.etInvoiceDate.text.toString())
                    val dueDate = dateFormat.parse(binding.etDueDate.text.toString())
                    if (invDate != null && dueDate != null) {
                        ((dueDate.time - invDate.time) / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(1)
                    } else 30
                } catch (e: Exception) { 30 }

                val templateName = "Recurring: ${selectedCustomer?.customer_name ?: "Invoice"}"

                val request = RecurringTemplateRequest(
                    customerId = selectedCustomer!!.id,
                    templateName = templateName,
                    invoiceDateDay = dayOfMonth,
                    dueDateDays = dueDays,
                    frequency = frequency,
                    taxType = if (binding.rbTaxInclusive.isChecked) "inclusive" else "exclusive",
                    items = invoiceItems.toList()
                )

                val response = RetrofitClient.apiService.createRecurringTemplate(request)
                showLoading(false)

                if (response.isSuccessful) {
                    Toast.makeText(this@CreateInvoiceActivity,
                        "Invoice and recurring template created successfully!",
                        Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    val err = response.errorBody()?.string() ?: "Failed to create recurring template"
                    Toast.makeText(this@CreateInvoiceActivity,
                        "Invoice created but recurring template failed: $err",
                        Toast.LENGTH_LONG).show()
                    finish()
                }
            } catch (e: Exception) {
                showLoading(false)
                Toast.makeText(this@CreateInvoiceActivity,
                    "Invoice created but template error: ${e.message}",
                    Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun convertDraftToPending() {
        if (!validateInvoice()) return
        editingInvoiceId?.let { id ->
            val request = getInvoiceRequest().copy(status = "pending")
            AlertDialog.Builder(this)
                .setTitle("Send Invoice")
                .setMessage("Are you sure you want to send this invoice? It will no longer be a draft.")
                .setPositiveButton("Send") { _, _ ->
                    invoiceViewModel.sendInvoice(id, request)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun getSelectedFrequency(): String? {
        if (!binding.chkRecurring.isChecked) return null
        val selectedDisplay = binding.etRecurringFrequency.text.toString()
        return frequencies.find { frequencyDisplayLabels[it] == selectedDisplay }
    }

    private fun getInvoiceRequest(): InvoiceRequest {
        return InvoiceRequest(
            customer_id = selectedCustomer!!.id,
            invoice_date = binding.etInvoiceDate.text.toString(),
            due_date = binding.etDueDate.text.toString(),
            po_number = binding.etPoNumber.text.toString().trim().ifEmpty { null },
            tax_rate = binding.etTaxRate.text.toString().toDoubleOrNull() ?: 0.0,
            discount_amount = binding.etDiscount.text.toString().toDoubleOrNull() ?: 0.0,
            discount_type = "fixed",
            shipping_fee = binding.etShipping.text.toString().toDoubleOrNull() ?: 0.0,
            notes = binding.etNotes.text.toString().trim().ifEmpty { null },
            terms_conditions = binding.etTermsConditions.text.toString().trim().ifEmpty { null },
            footer_text = binding.etFooterText.text.toString().trim().ifEmpty { null },
            items = invoiceItems.toList(),
            status = null,
            tax_type = if (binding.rbTaxInclusive.isChecked) "inclusive" else "exclusive"
        )
    }

    private fun formatCurrency(amount: Double): String {
        val format = NumberFormat.getCurrencyInstance()
        return format.format(amount)
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnCreateInvoice.isEnabled = !show
        binding.btnSendInvoice.isEnabled = !show
        binding.btnSaveAsDraft.isEnabled = !show
        binding.btnCancel.isEnabled = !show
    }

    private fun showSuccessAndFinish(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            delay(450)
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
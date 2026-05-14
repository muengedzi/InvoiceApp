package com.invoiceapp.activities

import android.app.DatePickerDialog
import android.content.Intent
import android.text.Editable
import android.text.TextWatcher
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class CreateInvoiceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateInvoiceBinding
    private lateinit var itemAdapter: InvoiceItemAdapter
    private lateinit var invoiceViewModel: InvoiceViewModel
    private val invoiceItems = mutableListOf<InvoiceItemRequest>()
    private var selectedCustomer: Customer? = null
    private var editingInvoiceId: Int? = null
    private var currentInvoiceStatus: String? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayDateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
    private lateinit var sessionManager: SessionManager
    private var customersList = listOf<Customer>()
    private var shouldRefreshCustomers = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateInvoiceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)
        invoiceViewModel = ViewModelProvider(this)[InvoiceViewModel::class.java]
        editingInvoiceId = intent.getIntExtra("invoice_id", -1).takeIf { it != -1 }

        setupToolbar()
        setupRecyclerView()
        setupClickListeners()
        setupTextWatchers()
        observeViewModel()
        loadCustomers()

        if (editingInvoiceId != null) {
            invoiceViewModel.getInvoiceDetail(editingInvoiceId!!)
        } else {
            setDefaultDates()
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
            binding.layoutRecurringDay.visibility = if (isChecked) View.VISIBLE else View.GONE
            if (isChecked && binding.etRecurringDay.text.isNullOrBlank()) {
                val currentDay = Calendar.getInstance().get(Calendar.DAY_OF_MONTH).coerceAtMost(28)
                binding.etRecurringDay.setText(currentDay.toString())
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
                    // When global tax rate changes, update all items (Bookipi behavior)
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
                    Toast.makeText(this, "Invoice saved successfully!", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                }
                is ApiResponse.Error -> {
                    showLoading(false)
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        invoiceViewModel.sendResult.observe(this) { result ->
            when (result) {
                is ApiResponse.Loading -> showLoading(true)
                is ApiResponse.Success -> {
                    showLoading(false)
                    Toast.makeText(this, "Invoice sent successfully!", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
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
                    currentInvoiceStatus = result.data.status.lowercase()
                    populateInvoiceForEditing(result.data)
                }
                is ApiResponse.Error -> {
                    showLoading(false)
                    Toast.makeText(this, "Failed to load invoice details", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
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
        // Populate customer
        populateCustomerView(invoice.customer_id)

        // Load invoice data
        binding.etPoNumber.setText(invoice.po_number ?: "")
        binding.etInvoiceDate.setText(invoice.invoice_date)
        binding.tvSelectedDate.text = formatDateForDisplay(invoice.invoice_date)
        binding.etDueDate.setText(invoice.due_date)
        binding.tvSelectedDueDate.text = formatDateForDisplay(invoice.due_date)
        binding.etTaxRate.setText(invoice.taxRate.toString())
        if (invoice.tax_type == "inclusive") {
            binding.rbTaxInclusive.isChecked = true
        } else {
            binding.rbTaxExclusive.isChecked = true
        }
        binding.etDiscount.setText(invoice.discount_amount.toString())
        binding.etShipping.setText(invoice.shipping_fee.toString())
        binding.etNotes.setText(invoice.notes ?: "")
        binding.chkRecurring.isChecked = invoice.is_recurring
        binding.layoutRecurringDay.visibility = if (invoice.is_recurring) View.VISIBLE else View.GONE
        binding.etRecurringDay.setText((invoice.recurring_day ?: 1).toString())

        // Show/hide Send Invoice button and draft warning based on status
        if (invoice.status.lowercase() == "draft") {
            // This is a draft invoice - show Send Invoice button
            binding.btnSendInvoice.visibility = View.VISIBLE
            binding.btnCreateInvoice.visibility = View.GONE
            binding.btnSaveAsDraft.text = "Save Draft"
            binding.cardDraftWarning.visibility = View.VISIBLE

            // Update toolbar title to indicate draft editing
            supportActionBar?.title = "Edit Draft Invoice"
        } else {
            // Regular invoice - hide Send Invoice button
            binding.btnSendInvoice.visibility = View.GONE
            binding.btnCreateInvoice.visibility = View.VISIBLE
            binding.btnSaveAsDraft.text = "Save as Draft"
            binding.cardDraftWarning.visibility = View.GONE

            supportActionBar?.title = "Edit Invoice"
        }

        // Load items
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

        val customerNames = customersList.map { "${it.customer_name} - ${it.email ?: it.phone ?: "No contact"}" }

        AlertDialog.Builder(this)
            .setTitle("Select Customer")
            .setItems(customerNames.toTypedArray()) { _, which ->
                selectedCustomer = customersList[which]
                binding.tvSelectedCustomer.text = selectedCustomer?.customer_name
                binding.tvSelectedCustomerDetails.text = "${selectedCustomer?.email ?: ""}\n${selectedCustomer?.phone ?: ""}"
            }
            .setPositiveButton("Add Customer") { _, _ ->
                openAddCustomer()
            }
            .setNegativeButton("Cancel", null)
            .show()
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
                            // Pre-fill the add item dialog with selected item data
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
        
        // Use item's tax rate or fall back to global rate
        val defaultTaxRate = if (item.taxRate > 0) item.taxRate else (binding.etTaxRate.text.toString().toDoubleOrNull() ?: 0.0)
        etTaxRate.setText(defaultTaxRate.toString())
        
        chkSaveForLater.visibility = View.GONE // Already a master item

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
                binding.recyclerInvoiceItems.post {
                    binding.recyclerInvoiceItems.smoothScrollToPosition(invoiceItems.lastIndex)
                }
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

        // Pre-fill with global tax rate
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
                binding.recyclerInvoiceItems.post {
                    binding.recyclerInvoiceItems.smoothScrollToPosition(invoiceItems.lastIndex)
                }
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
                val response = RetrofitClient.apiService.createInvoiceItem(request)
                if (!response.isSuccessful) {
                    Toast.makeText(this@CreateInvoiceActivity, "Failed to save item for later use", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                // Silently fail or log
            }
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
                // Formula: Tax = Total - (Total / (1 + Rate))
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
        val dueDate = binding.etDueDate.text.toString()

        if (invoiceDate.isEmpty() || dueDate.isEmpty()) {
            Toast.makeText(this, "Please select invoice and due dates", Toast.LENGTH_SHORT).show()
            return false
        }

        val user = sessionManager.getCurrentUser()
        if (user == null) {
            Toast.makeText(this, "Session expired. Please login again.", Toast.LENGTH_SHORT).show()
            finish()
            return false
        }

        if (binding.chkRecurring.isChecked) {
            val recurringDay = binding.etRecurringDay.text.toString().toIntOrNull()
            if (recurringDay == null || recurringDay !in 1..28) {
                Toast.makeText(this, "Recurring send day must be between 1 and 28", Toast.LENGTH_SHORT).show()
                return false
            }
        }

        return true
    }

    private fun createInvoice() {
        if (!validateInvoice()) return
        invoiceViewModel.createInvoice(getInvoiceRequest())
    }

    private fun createInvoiceAsDraft() {
        if (!validateInvoice()) return
        invoiceViewModel.createInvoiceAsDraft(getInvoiceRequest())
    }

    private fun updateInvoice() {
        if (!validateInvoice()) return
        editingInvoiceId?.let { id ->
            invoiceViewModel.updateInvoice(id, getInvoiceRequest())
        }
    }

    private fun updateInvoiceAsDraft() {
        if (!validateInvoice()) return
        editingInvoiceId?.let { id ->
            invoiceViewModel.updateInvoiceAsDraft(id, getInvoiceRequest())
        }
    }

    private fun convertDraftToPending() {
        if (!validateInvoice()) return

        editingInvoiceId?.let { id ->
            val request = getInvoiceRequest().copy(status = "pending")

            AlertDialog.Builder(this)
                .setTitle("Send Invoice")
                .setMessage("Are you sure you want to send this invoice? It will no longer be a draft and will be marked as Pending. The customer will be notified.")
                .setPositiveButton("Send Invoice") { _, _ ->
                    invoiceViewModel.sendInvoice(id, request)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
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
            terms_conditions = null,
            footer_text = null,
            is_recurring = binding.chkRecurring.isChecked,
            recurring_frequency = if (binding.chkRecurring.isChecked) "monthly" else null,
            recurring_day = if (binding.chkRecurring.isChecked) binding.etRecurringDay.text.toString().toIntOrNull() else null,
            recurring_active = binding.chkRecurring.isChecked,
            items = invoiceItems.toList(),
            status = null, // Status is set only when explicitly converting draft to pending
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

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
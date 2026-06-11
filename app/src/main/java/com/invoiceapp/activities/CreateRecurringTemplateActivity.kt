package com.invoiceapp.activities

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.invoiceapp.R
import com.invoiceapp.adapters.InvoiceItemAdapter
import com.invoiceapp.databinding.ActivityCreateRecurringTemplateBinding
import com.invoiceapp.models.*
import com.invoiceapp.network.RetrofitClient
import com.invoiceapp.utils.SessionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import android.content.Intent
import java.util.*

class CreateRecurringTemplateActivity : BaseActivity() {

    private lateinit var binding: ActivityCreateRecurringTemplateBinding
    private lateinit var itemAdapter: InvoiceItemAdapter
    private lateinit var sessionManager: SessionManager
    private val invoiceItems = mutableListOf<InvoiceItemRequest>()
    private var selectedCustomer: Customer? = null
    private var templateId: Int = -1
    private var isEditMode: Boolean = false
    private var customersList = listOf<Customer>()
    private var shouldRefreshCustomers = false

    private val frequencies = listOf("weekly", "biweekly", "monthly", "bimonthly", "quarterly", "yearly")
    private val frequencyDisplayLabels = mapOf(
        "weekly" to "Weekly (Every 7 days)",
        "biweekly" to "Bi-weekly (Every 14 days)",
        "monthly" to "Monthly (Every month)",
        "bimonthly" to "Bi-monthly (Every 2 months)",
        "quarterly" to "Quarterly (Every 3 months)",
        "yearly" to "Yearly (Every year)"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = ActivityCreateRecurringTemplateBinding.inflate(layoutInflater)
            setContentView(binding.root)
        } catch (e: Exception) {
            Toast.makeText(this, "Error inflating layout: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        sessionManager = SessionManager(this)
        templateId = intent.getIntExtra("template_id", -1)
        isEditMode = templateId != -1

        setupToolbar()
        setupRecyclerView()
        setupFrequencySpinner()
        setupClickListeners()
        setupTextWatchers()

        calculateTotals()
        updateNextSendDateInfo()

        // Load customers first, then template if in edit mode
        loadInitialData()
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
        supportActionBar?.title = if (isEditMode) "Edit Recurring Template" else "Create Recurring Template"
    }

    private fun setupRecyclerView() {
        itemAdapter = InvoiceItemAdapter(invoiceItems) { position ->
            if (position >= 0 && position < invoiceItems.size) {
                invoiceItems.removeAt(position)
                itemAdapter.notifyItemRemoved(position)
                calculateTotals()
            }
        }
        binding.recyclerItems.apply {
            layoutManager = LinearLayoutManager(this@CreateRecurringTemplateActivity)
            adapter = itemAdapter
        }
    }

    private fun setupFrequencySpinner() {
        val displayLabels = frequencies.map { frequencyDisplayLabels[it] ?: it }
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, displayLabels)
        (binding.etFrequency as? AutoCompleteTextView)?.let {
            it.setAdapter(adapter)
            it.setText(frequencyDisplayLabels["monthly"], false)
            
            it.setOnItemClickListener { _, _, position, _ ->
                updateNextSendDateInfo()
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnSelectCustomer.setOnClickListener { showCustomerSelector() }
        binding.btnAddItem.setOnClickListener { showSelectItemDialog() }
        binding.btnSave.setOnClickListener { saveTemplate() }
        binding.btnCancel.setOnClickListener { finish() }
    }

    private fun setupTextWatchers() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { calculateTotals() }
        }
        binding.etTaxRate.addTextChangedListener(watcher)
        binding.rgTaxType.setOnCheckedChangeListener { _, _ -> calculateTotals() }

        binding.etInvoiceDateDay.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateNextSendDateInfo()
            }
        })
    }

    private fun updateNextSendDateInfo() {
        val selectedDisplay = (binding.etFrequency as? AutoCompleteTextView)?.text?.toString() ?: ""
        val frequency = frequencies.find { frequencyDisplayLabels[it] == selectedDisplay } ?: "monthly"
        val dayOfMonth = binding.etInvoiceDateDay.text.toString().toIntOrNull() ?: 1

        val nextDate = calculateNextSendDate(frequency, dayOfMonth)
        val formattedDate = java.text.SimpleDateFormat("MMM dd, yyyy", Locale.US).format(nextDate)

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

    private fun loadInitialData() {
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            try {
                // Load customers
                val customersResponse = RetrofitClient.apiService.getCustomers(page = 1, perPage = 200)
                if (customersResponse.isSuccessful) {
                    customersList = customersResponse.body()?.customers ?: emptyList()
                }

                // If edit mode, load template
                if (isEditMode) {
                    val templateResponse = RetrofitClient.apiService.getRecurringTemplate(templateId)
                    if (templateResponse.isSuccessful && templateResponse.body() != null) {
                        populateUI(templateResponse.body()!!)
                    } else {
                        Toast.makeText(this@CreateRecurringTemplateActivity, "Failed to load template", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@CreateRecurringTemplateActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun loadCustomers() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getCustomers(page = 1, perPage = 200)
                if (response.isSuccessful) {
                    customersList = response.body()?.customers ?: emptyList()
                }
            } catch (e: Exception) {}
        }
    }

    private fun populateUI(template: RecurringTemplate) {
        binding.etTemplateName.setText(template.templateName)
        binding.etInvoiceDateDay.setText(template.invoiceDateDay.toString())
        
        val displayFreq = frequencyDisplayLabels[template.frequency] ?: template.frequency
        (binding.etFrequency as? AutoCompleteTextView)?.setText(displayFreq, false)
        
        binding.switchStatus.isChecked = template.status?.lowercase() == "active"

        if (template.taxType == "exclusive") {
            binding.rbTaxExclusive.isChecked = true
        } else {
            binding.rbTaxInclusive.isChecked = true
        }

        // Match customer
        selectedCustomer = customersList.find { it.id == template.customerId }
        selectedCustomer?.let {
            binding.tvSelectedCustomer.text = it.customer_name
            binding.tvSelectedCustomerDetails.text = "${it.email ?: ""}\n${it.phone ?: ""}"
        } ?: run {
            binding.tvSelectedCustomer.text = "Customer ID: ${template.customerId}"
            binding.tvSelectedCustomerDetails.text = "Customer details not found in current list"
        }

        // Load items
        invoiceItems.clear()
        template.items?.forEach { item ->
            invoiceItems.add(InvoiceItemRequest(
                item_name = item.itemName,
                description = item.description,
                quantity = item.quantity,
                unit_price = item.unitPrice,
                discount_amount = item.discountAmount,
                tax_rate = item.taxRate
            ))
        }
        itemAdapter.notifyDataSetChanged()

        if (invoiceItems.isNotEmpty()) {
            binding.etTaxRate.setText(invoiceItems[0].tax_rate.toString())
        }

        calculateTotals()
        updateNextSendDateInfo()
    }

    private fun calculateTotals() {
        var subtotal = 0.0
        var totalTax = 0.0
        val globalTaxRate = binding.etTaxRate.text.toString().toDoubleOrNull() ?: 0.0
        val isTaxInclusive = binding.rbTaxInclusive.isChecked

        for (item in invoiceItems) {
            val itemAmount = (item.quantity * item.unit_price) - item.discount_amount
            subtotal += itemAmount
            
            val rate = if (globalTaxRate > 0) globalTaxRate else item.tax_rate
            
            if (isTaxInclusive) {
                totalTax += itemAmount - (itemAmount / (1 + (rate / 100)))
            } else {
                totalTax += itemAmount * (rate / 100)
            }
        }

        val finalSubtotal = if (isTaxInclusive) subtotal - totalTax else subtotal
        val total = if (isTaxInclusive) subtotal else subtotal + totalTax

        binding.tvSubtotal.text = formatCurrency(finalSubtotal)
        binding.tvTax.text = formatCurrency(totalTax)
        binding.tvTotal.text = formatCurrency(total)
    }

    private fun formatCurrency(amount: Double): String {
        val format = NumberFormat.getCurrencyInstance(Locale("en", "ZA"))
        format.currency = Currency.getInstance("ZAR")
        return format.format(amount)
    }

    private fun formatCustomerChoice(customer: Customer): String {
        val contact = if (!customer.email.isNullOrBlank()) customer.email else (customer.phone ?: "No contact info")
        return "${customer.customer_name}\n$contact"
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

        val dialog = AlertDialog.Builder(this)
            .setTitle("Select Customer")
            .setView(dialogView)
            .setPositiveButton("Add Customer") { _, _ -> openAddCustomer() }
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
                val query = s?.toString()?.lowercase() ?: ""
                filteredCustomers = if (query.isEmpty()) {
                    customersList
                } else {
                    customersList.filter { 
                        it.customer_name.lowercase().contains(query) || 
                        (it.email?.lowercase()?.contains(query) == true) 
                    }
                }
                adapter.clear()
                adapter.addAll(filteredCustomers.map(::formatCustomerChoice))
                adapter.notifyDataSetChanged()
                
                emptyView?.visibility = if (filteredCustomers.isEmpty()) View.VISIBLE else View.GONE
                customerListView.visibility = if (filteredCustomers.isEmpty()) View.GONE else View.VISIBLE
            }
        })

        dialog.show()
    }

    private fun selectCustomer(customer: Customer) {
        selectedCustomer = customer
        binding.tvSelectedCustomer.text = customer.customer_name
        binding.tvSelectedCustomerDetails.text = "${customer.email ?: ""}\n${customer.phone ?: ""}"
    }

    private fun showAddCustomerPrompt() {
        AlertDialog.Builder(this)
            .setTitle("No Customers Yet")
            .setMessage("Add a customer first, then return here to select them.")
            .setPositiveButton("Add Customer") { _, _ -> openAddCustomer() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openAddCustomer() {
        shouldRefreshCustomers = true
        startActivity(Intent(this, AddEditCustomerActivity::class.java))
    }

    private fun showSelectItemDialog() {
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            try {
                val response = RetrofitClient.apiService.getInvoiceItems(page = 1, perPage = 200)
                if (response.isSuccessful && response.body() != null) {
                    val items = response.body()!!.items
                    if (items.isEmpty()) {
                        showAddItemDialog()
                        return@launch
                    }

                    val itemNames = items.map { "${it.itemName} - ${formatCurrency(it.unitPrice)}" }.toTypedArray()
                    AlertDialog.Builder(this@CreateRecurringTemplateActivity)
                        .setTitle("Select Item")
                        .setItems(itemNames) { _, which -> showAddItemDialog(items[which]) }
                        .setPositiveButton("Create New") { _, _ -> showAddItemDialog() }
                        .setNegativeButton("Cancel", null)
                        .show()
                } else {
                    showAddItemDialog()
                }
            } catch (e: Exception) {
                showAddItemDialog()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun showAddItemDialog(masterItem: InvoiceItemMaster? = null) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_invoice_item, null)
        val etItemName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etItemName)
        val etDescription = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etDescription)
        val etQuantity = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etQuantity)
        val etUnitPrice = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etUnitPrice)
        val etTaxRate = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etItemTaxRate)
        val chkSaveForLater = dialogView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.chkSaveForLater)

        if (masterItem != null) {
            etItemName.setText(masterItem.itemName)
            etDescription.setText(masterItem.description ?: "")
            etUnitPrice.setText(masterItem.unitPrice.toString())
            etTaxRate.setText(masterItem.taxRate.toString())
            chkSaveForLater.visibility = View.GONE
        } else {
            etTaxRate.setText(binding.etTaxRate.text.toString())
            chkSaveForLater.visibility = View.VISIBLE
        }

        AlertDialog.Builder(this)
            .setTitle(if (masterItem == null) "Add New Item" else "Add Selected Item")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val itemName = etItemName.text.toString().trim()
                if (itemName.isEmpty()) {
                    Toast.makeText(this, "Item name is required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val item = InvoiceItemRequest(
                    item_name = itemName,
                    description = etDescription.text.toString().trim().ifEmpty { null },
                    quantity = etQuantity.text.toString().toDoubleOrNull() ?: 1.0,
                    unit_price = etUnitPrice.text.toString().toDoubleOrNull() ?: 0.0,
                    tax_rate = etTaxRate.text.toString().toDoubleOrNull() ?: 0.0
                )
                
                if (masterItem == null && chkSaveForLater.isChecked) {
                    saveItemToMasterList(itemName, item.description ?: "", item.unit_price, item.tax_rate)
                }
                
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

    private fun saveTemplate() {
        if (selectedCustomer == null) {
            Toast.makeText(this, "Please select a customer", Toast.LENGTH_SHORT).show()
            return
        }
        val name = binding.etTemplateName.text.toString().trim()
        if (name.isEmpty()) {
            binding.etTemplateName.error = "Name required"
            return
        }

        val invoiceDateDay = binding.etInvoiceDateDay.text.toString().toIntOrNull()?.coerceIn(1, 28) ?: 1
        val dueDateDays = 30 // Default as it's no longer in UI
        
        val selectedDisplay = (binding.etFrequency as? AutoCompleteTextView)?.text?.toString() ?: "monthly"
        val frequency = frequencies.find { frequencyDisplayLabels[it] == selectedDisplay } ?: "monthly"
        val status = if (binding.switchStatus.isChecked) "active" else "inactive"
        val taxType = if (binding.rbTaxInclusive.isChecked) "inclusive" else "exclusive"

        if (invoiceItems.isEmpty()) {
            Toast.makeText(this, "Please add at least one item", Toast.LENGTH_SHORT).show()
            return
        }

        val itemsData = invoiceItems.map {
            mapOf(
                "item_name" to it.item_name,
                "description" to (it.description ?: ""),
                "quantity" to it.quantity,
                "unit_price" to it.unit_price,
                "discount_amount" to it.discount_amount,
                "tax_rate" to it.tax_rate
            )
        }

        val requestData = mapOf(
            "customer_id" to selectedCustomer!!.id,
            "template_name" to name,
            "invoice_date_day" to invoiceDateDay,
            "due_date_days" to dueDateDays,
            "frequency" to frequency,
            "status" to status,
            "tax_type" to taxType,
            "items" to itemsData
        )

        binding.progressBar.visibility = View.VISIBLE
        binding.btnSave.isEnabled = false

        lifecycleScope.launch {
            try {
                val response = if (isEditMode) {
                    RetrofitClient.apiService.updateRecurringTemplate(templateId, requestData)
                } else {
                    val createRequest = RecurringTemplateRequest(
                        customerId = selectedCustomer!!.id,
                        templateName = name,
                        invoiceDateDay = invoiceDateDay,
                        dueDateDays = dueDateDays,
                        frequency = frequency,
                        taxType = taxType,
                        items = invoiceItems
                    )
                    RetrofitClient.apiService.createRecurringTemplate(createRequest)
                }

                if (response.isSuccessful) {
                    Toast.makeText(this@CreateRecurringTemplateActivity, "Template saved successfully!", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    val err = response.errorBody()?.string() ?: "Unknown error"
                    Toast.makeText(this@CreateRecurringTemplateActivity, "Failed: $err", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@CreateRecurringTemplateActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.btnSave.isEnabled = true
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
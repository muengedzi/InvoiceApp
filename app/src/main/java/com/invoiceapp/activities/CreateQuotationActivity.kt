package com.invoiceapp.activities

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.invoiceapp.R
import com.invoiceapp.adapters.InvoiceItemAdapter
import com.invoiceapp.databinding.ActivityCreateQuotationBinding
import com.invoiceapp.models.*
import com.invoiceapp.network.ApiResponse
import com.invoiceapp.network.RetrofitClient
import com.invoiceapp.utils.SessionManager
import com.invoiceapp.viewmodels.SettingsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class CreateQuotationActivity : BaseActivity() {

    private lateinit var binding: ActivityCreateQuotationBinding
    private lateinit var itemAdapter: InvoiceItemAdapter
    private lateinit var settingsViewModel: SettingsViewModel
    private val quotationItems = mutableListOf<InvoiceItemRequest>()
    private var selectedCustomer: Customer? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayDateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
    private lateinit var sessionManager: SessionManager
    private var customersList = listOf<Customer>()
    private var shouldRefreshCustomers = false
    private var companySettings: CompanySettings? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateQuotationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)
        settingsViewModel = ViewModelProvider(this)[SettingsViewModel::class.java]

        setupToolbar()
        setupRecyclerView()
        setupClickListeners()
        setupTextWatchers()
        observeViewModel()
        loadCustomers()
        setDefaultDate()
        
        settingsViewModel.loadSettings()
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
        supportActionBar?.title = "Create Quotation"
    }

    private fun setupRecyclerView() {
        itemAdapter = InvoiceItemAdapter(quotationItems) { position ->
            quotationItems.removeAt(position)
            itemAdapter.notifyItemRemoved(position)
            itemAdapter.notifyItemRangeChanged(position, quotationItems.size - position)
            calculateTotals()
        }

        binding.recyclerQuotationItems.apply {
            layoutManager = LinearLayoutManager(this@CreateQuotationActivity)
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

        binding.btnCreateQuotation.setOnClickListener {
            createQuotation()
        }

        binding.btnCancel.setOnClickListener {
            finish()
        }
    }

    private fun setupTextWatchers() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s === binding.etTaxRate.editableText) {
                    val newRate = s.toString().toDoubleOrNull() ?: 0.0
                    for (i in quotationItems.indices) {
                        quotationItems[i] = quotationItems[i].copy(tax_rate = newRate)
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
        settingsViewModel.settings.observe(this) { result ->
            if (result is ApiResponse.Success) {
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
                }
            } catch (e: Exception) {
                Toast.makeText(this@CreateQuotationActivity, "Error loading customers", Toast.LENGTH_SHORT).show()
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
            .setMessage("Add a customer first, then return here to select them for this quotation.")
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

                    AlertDialog.Builder(this@CreateQuotationActivity)
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
            .setTitle("Add Quotation Item")
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

                quotationItems.add(invoiceItem)
                itemAdapter.notifyItemInserted(quotationItems.size - 1)
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
            .setTitle("Add Quotation Item")
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

                quotationItems.add(item)
                itemAdapter.notifyItemInserted(quotationItems.size - 1)
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
                binding.etQuotationDate.setText(dateFormat.format(date))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun setDefaultDate() {
        val today = Date()
        binding.tvSelectedDate.text = displayDateFormat.format(today)
        binding.etQuotationDate.setText(dateFormat.format(today))
    }

    private fun calculateTotals() {
        var subtotal = 0.0
        var totalTaxAmount = 0.0
        val isTaxInclusive = binding.rbTaxInclusive.isChecked

        for (item in quotationItems) {
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

    private fun validateQuotation(): Boolean {
        if (selectedCustomer == null) {
            Toast.makeText(this, "Please select a customer", Toast.LENGTH_SHORT).show()
            return false
        }

        if (quotationItems.isEmpty()) {
            Toast.makeText(this, "Please add at least one item", Toast.LENGTH_SHORT).show()
            return false
        }

        val quotationDate = binding.etQuotationDate.text.toString()
        if (quotationDate.isEmpty()) {
            Toast.makeText(this, "Please select a date", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun createQuotation() {
        if (!validateQuotation()) return

        lifecycleScope.launch {
            try {
                showLoading(true)
                val request = getQuotationRequest()
                val quotationRequest = request.copy(status = "quotation")

                val response = RetrofitClient.apiService.createQuotation(quotationRequest)
                if (response.isSuccessful) {
                    showLoading(false)
                    showSuccessAndFinish("Quotation created successfully!")
                } else {
                    showLoading(false)
                    val errorMsg = response.errorBody()?.string() ?: "Failed to create quotation"
                    Toast.makeText(this@CreateQuotationActivity, errorMsg, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                showLoading(false)
                Toast.makeText(this@CreateQuotationActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun getQuotationRequest(): InvoiceRequest {
        return InvoiceRequest(
            customer_id = selectedCustomer!!.id,
            invoice_date = binding.etQuotationDate.text.toString(),
            due_date = null,
            po_number = binding.etPoNumber.text.toString().trim().ifEmpty { null },
            tax_rate = binding.etTaxRate.text.toString().toDoubleOrNull() ?: 0.0,
            discount_amount = binding.etDiscount.text.toString().toDoubleOrNull() ?: 0.0,
            discount_type = "fixed",
            shipping_fee = binding.etShipping.text.toString().toDoubleOrNull() ?: 0.0,
            notes = binding.etNotes.text.toString().trim().ifEmpty { null },
            terms_conditions = binding.etTermsConditions.text.toString().trim().ifEmpty { null },
            footer_text = binding.etFooterText.text.toString().trim().ifEmpty { null },
            items = quotationItems.toList(),
            status = "quotation",
            tax_type = if (binding.rbTaxInclusive.isChecked) "inclusive" else "exclusive"
        )
    }

    private fun formatCurrency(amount: Double): String {
        val format = NumberFormat.getCurrencyInstance()
        return format.format(amount)
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnCreateQuotation.isEnabled = !show
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

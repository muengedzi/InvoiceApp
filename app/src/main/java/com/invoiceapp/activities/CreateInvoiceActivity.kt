package com.invoiceapp.activities

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.ArrayAdapter
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
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class CreateInvoiceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateInvoiceBinding
    private lateinit var itemAdapter: InvoiceItemAdapter
    private val invoiceItems = mutableListOf<InvoiceItemRequest>()
    private var selectedCustomer: Customer? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayDateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateInvoiceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        setupToolbar()
        setupRecyclerView()
        setupClickListeners()
        loadCustomers()
        setDefaultDates()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Create Invoice"
    }

    private fun setupRecyclerView() {
        itemAdapter = InvoiceItemAdapter(invoiceItems) { position ->
            invoiceItems.removeAt(position)
            itemAdapter = InvoiceItemAdapter(invoiceItems) { pos ->
                invoiceItems.removeAt(pos)
                setupRecyclerView()
                calculateTotals()
            }
            setupRecyclerView()
            calculateTotals()
        }

        binding.recyclerInvoiceItems.apply {
            layoutManager = LinearLayoutManager(this@CreateInvoiceActivity)
            adapter = itemAdapter
        }
    }

    private fun setupClickListeners() {
        binding.btnSelectCustomer.setOnClickListener {
            showCustomerSelector()
        }

        binding.btnAddItem.setOnClickListener {
            showAddItemDialog()
        }

        binding.btnSelectDate.setOnClickListener {
            showDatePicker()
        }

        binding.btnSelectDueDate.setOnClickListener {
            showDueDatePicker()
        }

        binding.btnCreateInvoice.setOnClickListener {
            createInvoice()
        }

        binding.btnCancel.setOnClickListener {
            finish()
        }
    }

    private fun loadCustomers() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getCustomers(page = 1, perPage = 100)
                if (response.isSuccessful) {
                    val customers = response.body()?.customers ?: emptyList()
                    // Store customers for later use
                }
            } catch (e: Exception) {
                Toast.makeText(this@CreateInvoiceActivity, "Error loading customers", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showCustomerSelector() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getCustomers(page = 1, perPage = 100)
                if (response.isSuccessful) {
                    val customers = response.body()?.customers ?: emptyList()

                    val customerNames = customers.map { "${it.customer_name} - ${it.email ?: it.phone ?: "No contact"}" }

                    AlertDialog.Builder(this@CreateInvoiceActivity)
                        .setTitle("Select Customer")
                        .setItems(customerNames.toTypedArray()) { _, which ->
                            selectedCustomer = customers[which]
                            binding.tvSelectedCustomer.text = selectedCustomer?.customer_name
                            binding.tvSelectedCustomerDetails.text = "${selectedCustomer?.email ?: ""}\n${selectedCustomer?.phone ?: ""}"
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@CreateInvoiceActivity, "Error loading customers", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showAddItemDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_invoice_item, null)
        val etItemName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etItemName)
        val etDescription = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etDescription)
        val etQuantity = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etQuantity)
        val etUnitPrice = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etUnitPrice)
        val etDiscount = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etDiscount)

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

                val item = InvoiceItemRequest(
                    item_name = itemName,
                    description = etDescription.text.toString().trim().ifEmpty { null },
                    quantity = quantity,
                    unit_price = unitPrice,
                    discount_amount = discount,
                    tax_rate = 0.0
                )

                invoiceItems.add(item)
                setupRecyclerView()
                calculateTotals()
            }
            .setNegativeButton("Cancel", null)
            .show()
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

    private fun calculateTotals() {
        var subtotal = 0.0
        for (item in invoiceItems) {
            subtotal += item.quantity * item.unit_price - item.discount_amount
        }

        val taxRate = binding.etTaxRate.text.toString().toDoubleOrNull() ?: 0.0
        val taxAmount = subtotal * (taxRate / 100)
        val discount = binding.etDiscount.text.toString().toDoubleOrNull() ?: 0.0
        val shipping = binding.etShipping.text.toString().toDoubleOrNull() ?: 0.0
        val total = subtotal + taxAmount - discount + shipping

        binding.tvSubtotal.text = formatCurrency(subtotal)
        binding.tvTaxAmount.text = formatCurrency(taxAmount)
        binding.tvTotal.text = formatCurrency(total)
    }

    private fun createInvoice() {
        if (selectedCustomer == null) {
            Toast.makeText(this, "Please select a customer", Toast.LENGTH_SHORT).show()
            return
        }

        if (invoiceItems.isEmpty()) {
            Toast.makeText(this, "Please add at least one item", Toast.LENGTH_SHORT).show()
            return
        }

        val invoiceDate = binding.etInvoiceDate.text.toString()
        val dueDate = binding.etDueDate.text.toString()

        if (invoiceDate.isEmpty() || dueDate.isEmpty()) {
            Toast.makeText(this, "Please select invoice and due dates", Toast.LENGTH_SHORT).show()
            return
        }

        val user = sessionManager.getCurrentUser()
        if (user == null) {
            Toast.makeText(this, "Session expired. Please login again.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val invoiceRequest = InvoiceRequest(
            customer_id = selectedCustomer!!.id,
            invoice_date = invoiceDate,
            due_date = dueDate,
            po_number = binding.etPoNumber.text.toString().trim().ifEmpty { null },
            tax_rate = binding.etTaxRate.text.toString().toDoubleOrNull() ?: 0.0,
            discount_amount = binding.etDiscount.text.toString().toDoubleOrNull() ?: 0.0,
            shipping_fee = binding.etShipping.text.toString().toDoubleOrNull() ?: 0.0,
            notes = binding.etNotes.text.toString().trim().ifEmpty { null },
            terms_conditions = null,
            footer_text = null,
            items = invoiceItems
        )

        showLoading(true)

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.createInvoice(invoiceRequest)

                if (response.isSuccessful && response.body() != null) {
                    val result = response.body()
                    if (result?.get("success") == true) {
                        Toast.makeText(this@CreateInvoiceActivity, "Invoice created successfully!", Toast.LENGTH_SHORT).show()
                        setResult(RESULT_OK)
                        finish()
                    } else {
                        val errorMsg = result?.get("message") as? String ?: "Failed to create invoice"
                        Toast.makeText(this@CreateInvoiceActivity, errorMsg, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@CreateInvoiceActivity, "Failed to create invoice", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@CreateInvoiceActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                showLoading(false)
            }
        }
    }

    private fun formatCurrency(amount: Double): String {
        val format = NumberFormat.getCurrencyInstance()
        return format.format(amount)
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
        binding.btnCreateInvoice.isEnabled = !show
        binding.btnCancel.isEnabled = !show
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
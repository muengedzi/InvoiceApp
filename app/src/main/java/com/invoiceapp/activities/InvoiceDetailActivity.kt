package com.invoiceapp.activities

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.invoiceapp.R
import com.invoiceapp.adapters.InvoiceItemAdapter
import com.invoiceapp.databinding.ActivityInvoiceDetailBinding
import com.invoiceapp.models.Invoice
import com.invoiceapp.models.PaymentRequest
import com.invoiceapp.network.ApiResponse
import com.invoiceapp.viewmodels.InvoiceViewModel
import com.invoiceapp.utils.SessionManager
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class InvoiceDetailActivity : BaseActivity() {
    private lateinit var binding: ActivityInvoiceDetailBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var invoiceViewModel: InvoiceViewModel
    private lateinit var invoiceItemsAdapter: InvoiceItemAdapter
    private var invoiceId: Int = -1
    private var currentInvoice: Invoice? = null
    private var progressDialog: android.app.ProgressDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInvoiceDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)
        invoiceViewModel = ViewModelProvider(this)[InvoiceViewModel::class.java]
        invoiceId = intent.getIntExtra("invoice_id", -1)

        if (invoiceId == -1) {
            Toast.makeText(this, "Invalid invoice", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupToolbar()
        setupClickListeners()
        setupItemsRecyclerView()
        observeViewModel()
        loadInvoiceDetails()
    }

    override fun onResume() {
        super.onResume()
        if (::invoiceViewModel.isInitialized && invoiceId != -1) {
            loadInvoiceDetails()
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Invoice Details"
    }

    private fun setupClickListeners() {
        binding.btnRecordPayment.setOnClickListener {
            showPaymentDialog()
        }

        binding.btnViewPayments.setOnClickListener {
            showPaymentHistory()
        }

        binding.btnCommunication.setOnClickListener {
            val intent = Intent(this, CommunicationActivity::class.java)
            intent.putExtra("invoice_id", invoiceId)
            intent.putExtra("invoice_number", currentInvoice?.invoice_number)
            intent.putExtra("customer_name", currentInvoice?.customer_name)
            intent.putExtra("customer_email", currentInvoice?.customer_email)
            intent.putExtra("customer_phone", currentInvoice?.customer_phone)
            intent.putExtra("balance_due", currentInvoice?.balance_due ?: 0.0)
            startActivity(intent)
        }
    }

    private fun showPaymentHistory() {
        val dialog = PaymentHistoryDialog.newInstance(invoiceId)
        dialog.setOnPaymentChanged {
            loadInvoiceDetails()
        }
        dialog.show(supportFragmentManager, "payment_history")
    }

    private fun setupItemsRecyclerView() {
        invoiceItemsAdapter = InvoiceItemAdapter(emptyList(), null)
        binding.rvInvoiceItems.apply {
            layoutManager = LinearLayoutManager(this@InvoiceDetailActivity)
            adapter = invoiceItemsAdapter
        }
    }

    private fun observeViewModel() {
        invoiceViewModel.invoiceDetail.observe(this) { result ->
            when (result) {
                is ApiResponse.Loading -> {
                    // Show loading if needed
                }
                is ApiResponse.Success -> {
                    currentInvoice = result.data
                    displayInvoice(currentInvoice!!)
                }
                is ApiResponse.Error -> {
                    Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                }
            }
        }

        invoiceViewModel.paymentResult.observe(this) { result ->
            when (result) {
                is ApiResponse.Loading -> {
                    binding.btnRecordPayment.isEnabled = false
                }
                is ApiResponse.Success -> {
                    binding.btnRecordPayment.isEnabled = true
                    Toast.makeText(this, "Payment recorded successfully!", Toast.LENGTH_SHORT).show()
                    loadInvoiceDetails()
                }
                is ApiResponse.Error -> {
                    binding.btnRecordPayment.isEnabled = true
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        invoiceViewModel.createResult.observe(this) { result ->
            when (result) {
                is ApiResponse.Loading -> {
                    // Delete uses this result stream; no full-screen loading needed.
                }
                is ApiResponse.Success -> {
                    if (result.data is Map<*, *> && result.data["success"] == true && result.data["deleted"] == true) {
                        val message = if (currentInvoice?.status?.lowercase() == "quotation") {
                            "Quotation deleted successfully"
                        } else {
                            "Invoice deleted successfully"
                        }
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
                is ApiResponse.Error -> {
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        invoiceViewModel.sendResult.observe(this) { result ->
            when (result) {
                is ApiResponse.Loading -> showProgressDialog("Sending invoice...")
                is ApiResponse.Success -> {
                    dismissProgressDialog()
                    Toast.makeText(this, "Invoice sent successfully", Toast.LENGTH_SHORT).show()
                    loadInvoiceDetails()
                }
                is ApiResponse.Error -> {
                    dismissProgressDialog()
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        invoiceViewModel.pdfResult.observe(this) { result ->
            when (result) {
                is ApiResponse.Loading -> {
                    showProgressDialog("Downloading PDF...")
                }
                is ApiResponse.Success -> {
                    dismissProgressDialog()
                    saveAndOpenPdf(result.data)
                }
                is ApiResponse.Error -> {
                    dismissProgressDialog()
                    Toast.makeText(this, "Failed to download PDF: ${result.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun loadInvoiceDetails() {
        invoiceViewModel.getInvoiceDetail(invoiceId)
    }

    private fun displayInvoice(invoice: Invoice) {
        binding.tvInvoiceNumber.text = invoice.invoice_number
        binding.tvCustomerName.text = invoice.customer_name ?: "N/A"
        binding.tvInvoiceDate.text = formatDate(invoice.invoice_date)

        // Handle due date - quotations may not have a due date
        val dueDate = invoice.due_date
        if (dueDate.isNullOrEmpty() || dueDate == "null") {
            binding.tvDueDate.text = "N/A"
        } else {
            binding.tvDueDate.text = formatDate(dueDate)
        }

        val status = invoice.status.lowercase()
        if (status == "paid") {
            binding.tvStatus.text = "ARCHIVED"
        } else if (status == "quotation") {
            binding.tvStatus.text = "QUOTATION"
        } else {
            binding.tvStatus.text = invoice.status.uppercase()
        }

        binding.ivOverdueWarning.visibility = if (status == "overdue") View.VISIBLE else View.GONE

        invalidateOptionsMenu()

        binding.tvSubtotal.text = formatCurrency(invoice.subtotal)
        binding.tvTax.text = formatCurrency(invoice.tax_amount)

        if (invoice.taxRate > 0) {
            binding.tvTaxType.visibility = View.VISIBLE
            binding.tvTaxType.text = "(${invoice.taxRate}% ${invoice.tax_type.capitalize()})"
        } else {
            binding.tvTaxType.visibility = View.GONE
        }

        binding.tvDiscount.text = formatCurrency(invoice.discount_amount)
        binding.tvShipping.text = formatCurrency(invoice.shipping_fee)
        binding.tvTotal.text = formatCurrency(invoice.total_amount)
        binding.tvAmountPaid.text = formatCurrency(invoice.amount_paid)
        binding.tvBalanceDue.text = formatCurrency(invoice.balance_due)

        if (invoice.balance_due > 0) {
            binding.tvBalanceDue.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.error))
        } else {
            binding.tvBalanceDue.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.success))
        }

        if (!invoice.items.isNullOrEmpty()) {
            val itemRequests = invoice.items.map {
                com.invoiceapp.models.InvoiceItemRequest(
                    item_name = it.itemName,
                    description = it.description,
                    quantity = it.quantity,
                    unit_price = it.unitPrice,
                    discount_amount = it.discountAmount,
                    tax_rate = it.taxRate
                )
            }
            invoiceItemsAdapter = InvoiceItemAdapter(itemRequests, null)
            binding.rvInvoiceItems.adapter = invoiceItemsAdapter
            binding.rvInvoiceItems.visibility = View.VISIBLE
        } else {
            binding.rvInvoiceItems.visibility = View.GONE
        }

        // Configure buttons based on status
        when (status) {
            "paid" -> {
                binding.btnRecordPayment.isEnabled = false
                binding.btnRecordPayment.text = "Fully Paid"
            }
            "draft" -> {
                binding.btnRecordPayment.isEnabled = false
                binding.btnRecordPayment.text = "Draft - Send to Enable Payment"
            }
            "quotation" -> {
                binding.btnRecordPayment.isEnabled = false
                binding.btnRecordPayment.text = "Quotation"
                binding.btnRecordPayment.visibility = View.GONE
                binding.btnViewPayments.visibility = View.GONE
            }
            "pending", "overdue" -> {
                binding.btnRecordPayment.isEnabled = true
                binding.btnRecordPayment.text = "Record Payment"
                binding.btnRecordPayment.visibility = View.VISIBLE
                binding.btnViewPayments.visibility = View.VISIBLE
            }
            else -> {
                binding.btnRecordPayment.isEnabled = false
                binding.btnRecordPayment.text = "Cannot Pay"
            }
        }

        when (status) {
            "paid" -> binding.tvStatus.setBackgroundResource(R.drawable.bg_status_paid)
            "pending" -> binding.tvStatus.setBackgroundResource(R.drawable.bg_status_pending)
            "overdue" -> binding.tvStatus.setBackgroundResource(R.drawable.bg_status_overdue)
            "draft" -> binding.tvStatus.setBackgroundResource(R.drawable.bg_status_draft)
            "quotation" -> binding.tvStatus.setBackgroundResource(R.drawable.bg_status_quotation)
            else -> binding.tvStatus.setBackgroundResource(R.drawable.bg_status_draft)
        }
        binding.tvStatus.setTextColor(android.graphics.Color.WHITE)
    }

    private fun showPaymentDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_record_payment, null)
        val tilAmount = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilAmount)
        val etAmount = dialogView.findViewById<EditText>(R.id.etAmount)
        val etPaymentType = dialogView.findViewById<android.widget.AutoCompleteTextView>(R.id.etPaymentType)
        val etPaymentMethod = dialogView.findViewById<android.widget.AutoCompleteTextView>(R.id.etPaymentMethod)
        val tvBalanceHint = dialogView.findViewById<TextView>(R.id.tvBalanceHint)
        
        val paymentTypes = listOf(getString(R.string.full_payment), getString(R.string.partial_payment))
        val typeAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, paymentTypes)
        etPaymentType.setAdapter(typeAdapter)
        etPaymentType.setText(paymentTypes.first(), false)

        val paymentMethods = listOf("Cash", "Card Payment", "Bank Transfer")
        etPaymentMethod.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, paymentMethods)
        )
        etPaymentMethod.setText(paymentMethods.first(), false)

        currentInvoice?.let {
            val balance = it.balance_due
            tvBalanceHint.text = getString(R.string.balance_due_format, formatCurrency(balance))
            tilAmount.hint = getString(R.string.amount_to_pay)
            
            // Set initial amount for Full Payment
            etAmount.setText("%.2f".format(Locale.US, balance))
            etAmount.isEnabled = false 
            
            etPaymentType.setOnItemClickListener { _, _, position, _ ->
                if (position == 0) { // Full Payment
                    etAmount.setText("%.2f".format(Locale.US, balance))
                    etAmount.isEnabled = false
                } else { // Partial Payment
                    etAmount.setText("")
                    etAmount.isEnabled = true
                    etAmount.requestFocus()
                }
            }
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.record_payment_title))
            .setView(dialogView)
            .setPositiveButton("Record") { _, _ ->
                val amountStr = etAmount.text.toString()
                val paymentMethod = etPaymentMethod.text.toString().trim()

                if (amountStr.isEmpty()) {
                    Toast.makeText(this, getString(R.string.please_enter_amount), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val amount = amountStr.toDoubleOrNull()
                if (amount == null || amount <= 0) {
                    Toast.makeText(this, getString(R.string.please_enter_valid_amount), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                currentInvoice?.let {
                    if (amount > it.balance_due) {
                        Toast.makeText(this, getString(R.string.amount_exceeds_balance, formatCurrency(it.balance_due)), Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                }

                recordPayment(amount, paymentMethod.ifEmpty { "cash" })
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun recordPayment(amount: Double, paymentMethod: String) {
        val paymentDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val paymentRequest = PaymentRequest(
            amount = amount,
            payment_method = paymentMethod,
            payment_date = paymentDate,
            reference_number = null,
            notes = null
        )
        invoiceViewModel.recordPayment(invoiceId, paymentRequest)
    }

    private fun saveAndOpenPdf(pdfFile: File) {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val invoiceDir = File(downloadsDir, "InvoiceApp")
            if (!invoiceDir.exists()) {
                invoiceDir.mkdirs()
            }

            val fileName = "invoice_${currentInvoice?.invoice_number ?: invoiceId}.pdf"
            val destFile = File(invoiceDir, fileName)

            pdfFile.copyTo(destFile, overwrite = true)

            Toast.makeText(this, "PDF saved to: ${destFile.absolutePath}", Toast.LENGTH_LONG).show()
            openPdfFile(destFile)
        } catch (e: Exception) {
            Toast.makeText(this, "Error saving PDF: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun openPdfFile(pdfFile: File) {
        try {
            val fileToOpen = if (pdfFile.absolutePath.contains("/Download/") ||
                pdfFile.absolutePath.contains("Download")) {
                val cachedFile = File(cacheDir, pdfFile.name)
                pdfFile.inputStream().use { input ->
                    cachedFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                cachedFile
            } else {
                pdfFile
            }

            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                fileToOpen
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                clipData = ClipData.newUri(contentResolver, pdfFile.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            }

            startActivity(Intent.createChooser(intent, "Open invoice PDF"))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                this,
                "No PDF viewer app found. File saved at: ${pdfFile.absolutePath}",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error opening PDF: ${e.message}", Toast.LENGTH_LONG).show()
            Toast.makeText(
                this,
                "PDF saved to: ${pdfFile.absolutePath}\nOpen manually from file manager",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showProgressDialog(message: String) {
        progressDialog = android.app.ProgressDialog(this).apply {
            setMessage(message)
            setCancelable(false)
            show()
        }
    }

    private fun dismissProgressDialog() {
        progressDialog?.dismiss()
        progressDialog = null
    }

    private fun formatDate(dateString: String): String {
        if (dateString.isNullOrEmpty() || dateString == "null") {
            return "N/A"
        }
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val outputFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
            val date = inputFormat.parse(dateString)
            outputFormat.format(date)
        } catch (e: Exception) {
            dateString
        }
    }

    private fun formatCurrency(amount: Double): String {
        val format = NumberFormat.getCurrencyInstance(Locale("en", "ZA"))
        format.currency = Currency.getInstance("ZAR")
        return format.format(amount)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.invoice_detail_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        val status = currentInvoice?.status?.lowercase()

        // Send button: visible for draft, pending, overdue (not for paid or quotation)
        menu?.findItem(R.id.menu_send_invoice)?.isVisible = status == "draft" || status == "pending" || status == "overdue"

        // Edit button: visible for all except paid (quotations can be edited)
        menu?.findItem(R.id.menu_edit)?.isVisible = status != "paid"

        // Delete button: visible for draft, pending, and quotation
        menu?.findItem(R.id.menu_delete)?.isVisible = status == "draft" || status == "pending" || status == "quotation"

        // Download PDF: visible for all
        menu?.findItem(R.id.menu_download_pdf)?.isVisible = true

        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_edit -> {
                val intent = Intent(this, CreateInvoiceActivity::class.java)
                intent.putExtra("invoice_id", invoiceId)
                startActivity(intent)
                true
            }
            R.id.menu_delete -> {
                showDeleteConfirmation()
                true
            }
            R.id.menu_download_pdf -> {
                downloadPdf()
                true
            }
            R.id.menu_send_invoice -> {
                showSendConfirmation()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun downloadPdf() {
        currentInvoice?.let { invoice ->
            invoiceViewModel.downloadInvoicePdf(this, invoiceId)
        } ?: Toast.makeText(this, "Invoice data not loaded", Toast.LENGTH_SHORT).show()
    }

    private fun showSendConfirmation() {
        val status = currentInvoice?.status?.lowercase()
        val message = if (status == "draft") {
            "This will mark the invoice as Pending and email it to ${currentInvoice?.customer_email ?: "the customer"}."
        } else {
            "This will email the invoice to ${currentInvoice?.customer_email ?: "the customer"}."
        }

        AlertDialog.Builder(this)
            .setTitle("Send Invoice")
            .setMessage(message)
            .setPositiveButton("Send") { _, _ ->
                invoiceViewModel.sendInvoice(invoiceId)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteConfirmation() {
        val status = currentInvoice?.status?.lowercase()

        // Allow deletion for draft, pending, and quotation
        when (status) {
            "draft", "pending" -> {
                AlertDialog.Builder(this)
                    .setTitle("Delete Invoice")
                    .setMessage("Are you sure you want to delete this invoice? This action cannot be undone.")
                    .setPositiveButton("Delete") { _, _ ->
                        invoiceViewModel.deleteInvoice(invoiceId)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            "quotation" -> {
                AlertDialog.Builder(this)
                    .setTitle("Delete Quotation")
                    .setMessage("Are you sure you want to delete this quotation? This action cannot be undone.")
                    .setPositiveButton("Delete") { _, _ ->
                        invoiceViewModel.deleteInvoice(invoiceId)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            else -> {
                Toast.makeText(this, "Only draft, pending invoices, and quotations can be deleted", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}

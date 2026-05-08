package com.invoiceapp.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.invoiceapp.R
import com.invoiceapp.adapters.InvoiceItemAdapter
import com.invoiceapp.databinding.ActivityInvoiceDetailBinding
import com.invoiceapp.models.Invoice
import com.invoiceapp.models.PaymentRequest
import com.invoiceapp.network.ApiResponse
import com.invoiceapp.viewmodels.InvoiceViewModel
import com.invoiceapp.utils.SessionManager
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class InvoiceDetailActivity : AppCompatActivity() {
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
        // Reload invoice details when returning from edit to show updated status
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
            if (result is ApiResponse.Success && result.data is Map<*, *> && result.data["success"] == true) {
                // This is for deletion result
                Toast.makeText(this, "Invoice deleted successfully", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        // Observe PDF download result
        invoiceViewModel.pdfResult.observe(this) { result ->
            when (result) {
                is ApiResponse.Loading -> {
                    showProgressDialog("Downloading PDF...")
                }
                is ApiResponse.Success -> {
                    dismissProgressDialog()
                    savePdfToDevice(result.data)
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
        binding.tvDueDate.text = formatDate(invoice.due_date)
        binding.tvStatus.text = invoice.status.uppercase()

        // Financial details
        binding.tvSubtotal.text = formatCurrency(invoice.subtotal)
        binding.tvTax.text = formatCurrency(invoice.tax_amount)
        binding.tvDiscount.text = formatCurrency(invoice.discount_amount)
        binding.tvShipping.text = formatCurrency(invoice.shipping_fee)
        binding.tvTotal.text = formatCurrency(invoice.total_amount)
        binding.tvAmountPaid.text = formatCurrency(invoice.amount_paid)
        binding.tvBalanceDue.text = formatCurrency(invoice.balance_due)

        // Display invoice items
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

        // Show/hide payment button based on status
        when (invoice.status.lowercase()) {
            "paid" -> {
                binding.btnRecordPayment.isEnabled = false
                binding.btnRecordPayment.text = "Fully Paid"
            }
            "draft" -> {
                binding.btnRecordPayment.isEnabled = false
                binding.btnRecordPayment.text = "Draft - Send to Enable Payment"
            }
            "pending", "overdue" -> {
                binding.btnRecordPayment.isEnabled = true
                binding.btnRecordPayment.text = "Record Payment"
            }
            else -> {
                binding.btnRecordPayment.isEnabled = false
                binding.btnRecordPayment.text = "Cannot Pay"
            }
        }

        // Set status color
        when (invoice.status.lowercase()) {
            "paid" -> binding.tvStatus.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
            "pending" -> binding.tvStatus.setTextColor(android.graphics.Color.parseColor("#FF9800"))
            "overdue" -> binding.tvStatus.setTextColor(android.graphics.Color.parseColor("#F44336"))
            "draft" -> binding.tvStatus.setTextColor(android.graphics.Color.parseColor("#2196F3"))
            else -> binding.tvStatus.setTextColor(android.graphics.Color.parseColor("#9E9E9E"))
        }
    }

    private fun showPaymentDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_record_payment, null)
        val etAmount = dialogView.findViewById<EditText>(R.id.etAmount)
        val etPaymentMethod = dialogView.findViewById<EditText>(R.id.etPaymentMethod)
        val tvBalanceHint = dialogView.findViewById<TextView>(R.id.tvBalanceHint)

        currentInvoice?.let {
            tvBalanceHint.text = "Balance Due: ${formatCurrency(it.balance_due)}"
            etAmount.hint = "Enter amount (max ${formatCurrency(it.balance_due)})"
        }

        AlertDialog.Builder(this)
            .setTitle("Record Payment")
            .setView(dialogView)
            .setPositiveButton("Record") { _, _ ->
                val amountStr = etAmount.text.toString()
                val paymentMethod = etPaymentMethod.text.toString().trim()

                if (amountStr.isEmpty()) {
                    Toast.makeText(this, "Please enter an amount", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val amount = amountStr.toDoubleOrNull()
                if (amount == null || amount <= 0) {
                    Toast.makeText(this, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                currentInvoice?.let {
                    if (amount > it.balance_due) {
                        Toast.makeText(this, "Amount exceeds balance due", Toast.LENGTH_SHORT).show()
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

    private fun savePdfToDevice(pdfBytes: ByteArray) {
        try {
            // Create directory for invoices
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val invoiceDir = File(downloadsDir, "InvoiceApp")
            if (!invoiceDir.exists()) {
                invoiceDir.mkdirs()
            }

            // Create file name
            val fileName = "invoice_${currentInvoice?.invoice_number ?: invoiceId}_${System.currentTimeMillis()}.pdf"
            val pdfFile = File(invoiceDir, fileName)

            // Write PDF bytes to file
            FileOutputStream(pdfFile).use { outputStream ->
                outputStream.write(pdfBytes)
            }

            Toast.makeText(this, "PDF saved to: ${pdfFile.absolutePath}", Toast.LENGTH_LONG).show()

            // Open the PDF
            openPdfFile(pdfFile)

        } catch (e: Exception) {
            Toast.makeText(this, "Error saving PDF: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun openPdfFile(pdfFile: File) {
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                pdfFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            // Check if there's an app to handle PDF
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                Toast.makeText(this, "No PDF viewer app found. File saved at: ${pdfFile.absolutePath}", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error opening PDF: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
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
        val format = NumberFormat.getCurrencyInstance()
        return format.format(amount)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.invoice_detail_menu, menu)
        return true
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
                Toast.makeText(this, "Downloading PDF...", Toast.LENGTH_SHORT).show()
                invoiceViewModel.downloadInvoicePdf(invoiceId)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showDeleteConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Delete Invoice")
            .setMessage("Are you sure you want to delete this invoice? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                invoiceViewModel.deleteInvoice(invoiceId)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
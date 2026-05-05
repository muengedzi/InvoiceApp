package com.invoiceapp.activities

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.invoiceapp.databinding.ActivityInvoiceDetailBinding
import com.invoiceapp.models.Invoice
import com.invoiceapp.network.RetrofitClient
import com.invoiceapp.utils.SessionManager
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class InvoiceDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityInvoiceDetailBinding
    private lateinit var sessionManager: SessionManager
    private var invoiceId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInvoiceDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)
        invoiceId = intent.getIntExtra("invoice_id", -1)

        if (invoiceId == -1) {
            Toast.makeText(this, "Invalid invoice", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupToolbar()
        loadInvoiceDetails()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Invoice Details"
    }

    private fun loadInvoiceDetails() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getInvoice(invoiceId)
                if (response.isSuccessful && response.body() != null) {
                    displayInvoice(response.body()!!)
                } else {
                    Toast.makeText(this@InvoiceDetailActivity, "Failed to load invoice", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@InvoiceDetailActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun displayInvoice(invoice: Invoice) {
        binding.tvInvoiceNumber.text = invoice.invoice_number
        binding.tvCustomerName.text = invoice.customer_name ?: "N/A"
        binding.tvInvoiceDate.text = formatDate(invoice.invoice_date)
        binding.tvDueDate.text = formatDate(invoice.due_date)
        binding.tvStatus.text = invoice.status.toUpperCase()
        binding.tvSubtotal.text = formatCurrency(invoice.subtotal)
        binding.tvTax.text = formatCurrency(invoice.tax_amount)
        binding.tvDiscount.text = formatCurrency(invoice.discount_amount)
        binding.tvShipping.text = formatCurrency(invoice.shipping_fee)
        binding.tvTotal.text = formatCurrency(invoice.total_amount)
        binding.tvAmountPaid.text = formatCurrency(invoice.amount_paid)
        binding.tvBalanceDue.text = formatCurrency(invoice.balance_due)

        // Set status color
        when (invoice.status) {
            "paid" -> binding.tvStatus.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
            "pending" -> binding.tvStatus.setTextColor(android.graphics.Color.parseColor("#FF9800"))
            "overdue" -> binding.tvStatus.setTextColor(android.graphics.Color.parseColor("#F44336"))
        }
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

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
// adapters/InvoiceAdapter.kt
package com.invoiceapp.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.invoiceapp.databinding.ItemInvoiceBinding
import com.invoiceapp.models.Invoice
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class InvoiceAdapter(
    private val onItemClick: (Int) -> Unit
) : RecyclerView.Adapter<InvoiceAdapter.ViewHolder>() {

    private var invoices = listOf<Invoice>()

    fun submitList(newList: List<Invoice>) {
        invoices = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemInvoiceBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(invoices[position])
    }

    override fun getItemCount(): Int = invoices.size

    inner class ViewHolder(private val binding: ItemInvoiceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(invoice: Invoice) {
            binding.tvInvoiceNumber.text = invoice.invoice_number
            binding.tvCustomerName.text = invoice.customer_name ?: "Unknown Customer"
            binding.tvInvoiceDate.text = formatDate(invoice.invoice_date)
            binding.tvAmount.text = formatCurrency(invoice.total_amount)

            // Set status color
            when (invoice.status.lowercase()) {
                "paid" -> binding.tvStatus.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                "pending" -> binding.tvStatus.setTextColor(android.graphics.Color.parseColor("#FF9800"))
                "overdue" -> binding.tvStatus.setTextColor(android.graphics.Color.parseColor("#F44336"))
                else -> binding.tvStatus.setTextColor(android.graphics.Color.parseColor("#9E9E9E"))
            }
            binding.tvStatus.text = invoice.status.uppercase()

            binding.root.setOnClickListener {
                onItemClick(invoice.id)
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
    }
}
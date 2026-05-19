package com.invoiceapp.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.invoiceapp.R
import com.invoiceapp.databinding.ItemInvoiceBinding
import com.invoiceapp.models.Invoice
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class InvoiceAdapter(
    private val onItemClick: (Int) -> Unit
) : RecyclerView.Adapter<InvoiceAdapter.InvoiceViewHolder>() {

    private var invoices: List<Invoice> = emptyList()
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "ZA")).apply {
        currency = java.util.Currency.getInstance("ZAR")
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InvoiceViewHolder {
        val binding = ItemInvoiceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return InvoiceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: InvoiceViewHolder, position: Int) {
        holder.bind(invoices[position])
    }

    override fun getItemCount(): Int = invoices.size

    fun submitList(newList: List<Invoice>) {
        invoices = newList
        notifyDataSetChanged()
    }

    inner class InvoiceViewHolder(
        private val binding: ItemInvoiceBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(invoice: Invoice) {
            binding.tvInvoiceNumber.text = invoice.invoice_number
            binding.tvCustomerName.text = invoice.customer_name ?: "Unknown Customer"

            // Format dates
            val dueDateStr = try {
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(invoice.due_date)
                dateFormat.format(date!!)
            } catch (e: Exception) {
                invoice.due_date
            }
            binding.tvDueDate.text = dueDateStr

            binding.tvTotalAmount.text = currencyFormat.format(invoice.total_amount)
            binding.tvBalanceDue.text = currencyFormat.format(invoice.balance_due)

            // UI Polishing: Update status pill
            val status = invoice.status.lowercase()
            binding.tvStatus.text = status.uppercase()
            
            // Show warning icon for overdue invoices
            binding.ivOverdueWarning.visibility = if (status == "overdue") android.view.View.VISIBLE else android.view.View.GONE
            
            when (status) {
                "paid" -> {
                    binding.tvStatus.setBackgroundResource(R.drawable.bg_status_paid)
                    binding.tvBalanceDue.setTextColor(ContextCompat.getColor(itemView.context, R.color.status_paid))
                }
                "pending" -> {
                    binding.tvStatus.setBackgroundResource(R.drawable.bg_status_pending)
                    binding.tvBalanceDue.setTextColor(ContextCompat.getColor(itemView.context, R.color.status_pending))
                }
                "overdue" -> {
                    binding.tvStatus.setBackgroundResource(R.drawable.bg_status_overdue)
                    binding.tvBalanceDue.setTextColor(ContextCompat.getColor(itemView.context, R.color.status_overdue))
                }
                "draft" -> {
                    binding.tvStatus.setBackgroundResource(R.drawable.bg_status_draft)
                    binding.tvBalanceDue.setTextColor(ContextCompat.getColor(itemView.context, R.color.status_draft))
                }
                else -> {
                    binding.tvStatus.setBackgroundResource(R.drawable.bg_status_draft)
                    binding.tvBalanceDue.setTextColor(ContextCompat.getColor(itemView.context, R.color.gray_600))
                }
            }

            binding.root.setOnClickListener {
                onItemClick(invoice.id)
            }
        }
    }
}

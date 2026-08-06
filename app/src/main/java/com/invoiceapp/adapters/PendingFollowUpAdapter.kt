package com.invoiceapp.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.invoiceapp.R
import com.invoiceapp.databinding.ItemPendingFollowUpBinding
import com.invoiceapp.models.InvoiceFollowUp
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class PendingFollowUpAdapter(
    private var items: List<InvoiceFollowUp>,
    private val onItemClick: (InvoiceFollowUp) -> Unit,
    private val onSendFollowUpClick: (InvoiceFollowUp) -> Unit
) : RecyclerView.Adapter<PendingFollowUpAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "ZA")).apply {
        currency = java.util.Currency.getInstance("ZAR")
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPendingFollowUpBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun updateList(newList: List<InvoiceFollowUp>) {
        items = newList
        notifyDataSetChanged()
    }

    inner class ViewHolder(private val binding: ItemPendingFollowUpBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: InvoiceFollowUp) {
            binding.tvInvoiceNumber.text = item.invoiceNumber ?: "N/A"
            binding.tvCustomerName.text = item.customerName ?: "N/A"

            // Format and display balance due amount
            val balance = item.balanceDue ?: 0.0
            binding.tvBalanceDue.text = currencyFormat.format(balance)

            // Format and display due date
            val dueDate = try {
                dateFormat.format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(item.followUpDate))
            } catch (e: Exception) {
                item.followUpDate
            }
            binding.tvDueDate.text = dueDate

            // Get days overdue
            val daysOverdue = item.daysOverdue ?: 0

            // Set amount color based on days overdue (red for any overdue, orange for pending)
            if (daysOverdue > 0) {
                // Overdue - Red color for amount
                binding.tvBalanceDue.setTextColor(
                    ContextCompat.getColor(binding.root.context, R.color.error)
                )
                // Set status text based on severity
                when {
                    daysOverdue > 30 -> binding.tvStatus.text = "Critical"
                    daysOverdue > 14 -> binding.tvStatus.text = "Urgent"
                    daysOverdue > 7 -> binding.tvStatus.text = "Overdue"
                    else -> binding.tvStatus.text = "Overdue"
                }
                binding.tvStatus.setTextColor(
                    ContextCompat.getColor(binding.root.context, R.color.error)
                )
            } else {
                // Pending (not overdue yet) - Orange color for amount (matching dashboard)
                binding.tvBalanceDue.setTextColor(
                    ContextCompat.getColor(binding.root.context, R.color.warning)
                )
                binding.tvStatus.text = "Pending"
                binding.tvStatus.setTextColor(
                    ContextCompat.getColor(binding.root.context, R.color.warning)
                )
            }

            // Set click listeners
            binding.root.setOnClickListener {
                onItemClick(item)
            }

            binding.btnSendFollowUp.setOnClickListener {
                onSendFollowUpClick(item)
            }
        }
    }
}
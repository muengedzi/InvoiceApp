package com.invoiceapp.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
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

            // Now balanceDue is available in the model
            val balance = item.balanceDue ?: 0.0
            binding.tvBalanceDue.text = currencyFormat.format(balance)

            val dueDate = try {
                dateFormat.format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(item.followUpDate))
            } catch (e: Exception) {
                item.followUpDate
            }
            binding.tvDueDate.text = dueDate

            // Now daysOverdue is available in the model
            val daysOverdue = item.daysOverdue ?: 0

            when {
                daysOverdue > 30 -> binding.tvStatus.text = "Critical"
                daysOverdue > 14 -> binding.tvStatus.text = "Urgent"
                daysOverdue > 7 -> binding.tvStatus.text = "Overdue"
                else -> binding.tvStatus.text = "Pending"
            }

            binding.root.setOnClickListener {
                onItemClick(item)
            }

            binding.btnSendFollowUp.setOnClickListener {
                onSendFollowUpClick(item)
            }
        }
    }
}
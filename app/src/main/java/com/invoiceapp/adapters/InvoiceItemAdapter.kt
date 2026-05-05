package com.invoiceapp.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.invoiceapp.databinding.ItemInvoiceItemBinding
import com.invoiceapp.models.InvoiceItemRequest
import java.text.NumberFormat

class InvoiceItemAdapter(
    private val items: List<InvoiceItemRequest>,
    private val onDeleteClick: (Int) -> Unit
) : RecyclerView.Adapter<InvoiceItemAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemInvoiceItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(private val binding: ItemInvoiceItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: InvoiceItemRequest, position: Int) {
            binding.tvItemName.text = item.item_name
            binding.tvDescription.text = item.description ?: ""
            binding.tvQuantity.text = "Qty: ${item.quantity}"
            binding.tvUnitPrice.text = formatCurrency(item.unit_price)
            binding.tvTotal.text = formatCurrency(item.quantity * item.unit_price - item.discount_amount)

            binding.btnDelete.setOnClickListener {
                onDeleteClick(position)
            }
        }

        private fun formatCurrency(amount: Double): String {
            val format = NumberFormat.getCurrencyInstance()
            return format.format(amount)
        }
    }
}
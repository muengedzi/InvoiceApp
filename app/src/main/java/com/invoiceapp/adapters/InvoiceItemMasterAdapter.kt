package com.invoiceapp.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.invoiceapp.databinding.ItemInvoiceItemMasterBinding
import com.invoiceapp.models.InvoiceItemMaster
import java.text.NumberFormat

class InvoiceItemMasterAdapter(
    private var items: List<InvoiceItemMaster>,
    private val onItemClick: (InvoiceItemMaster) -> Unit
) : RecyclerView.Adapter<InvoiceItemMasterAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemInvoiceItemMasterBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun updateList(newList: List<InvoiceItemMaster>) {
        items = newList
        notifyDataSetChanged()
    }

    inner class ViewHolder(private val binding: ItemInvoiceItemMasterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: InvoiceItemMaster) {
            val currencyFormat = NumberFormat.getCurrencyInstance()

            binding.tvItemName.text = item.itemName
            binding.tvDescription.text = item.description ?: "No description"
            binding.tvUnitPrice.text = currencyFormat.format(item.unitPrice)
            binding.tvTaxRate.text = "Tax: ${item.taxRate}%"

            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }
    }
}
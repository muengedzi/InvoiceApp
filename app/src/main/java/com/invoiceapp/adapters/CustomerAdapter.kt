package com.invoiceapp.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.invoiceapp.databinding.ItemCustomerBinding
import com.invoiceapp.models.Customer

class CustomerAdapter(
    private val onEditClick: (Int) -> Unit,
    private val onCommunicationClick: (Int) -> Unit
) : RecyclerView.Adapter<CustomerAdapter.ViewHolder>() {

    private var customers = listOf<Customer>()

    fun submitList(newList: List<Customer>) {
        customers = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCustomerBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(customers[position])
    }

    override fun getItemCount(): Int = customers.size

    inner class ViewHolder(private val binding: ItemCustomerBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(customer: Customer) {
            binding.tvCustomerName.text = if (customer.is_company) {
                "${customer.customer_name} - Company"
            } else {
                customer.customer_name
            }
            binding.tvCustomerEmail.text = customer.email
            binding.tvCustomerPhone.text = when {
                customer.vat_registered && !customer.vat_number.isNullOrBlank() -> "VAT: ${customer.vat_number}"
                !customer.phone.isNullOrBlank() -> customer.phone
                else -> "No phone"
            }

            // Edit button click
            binding.btnEdit.setOnClickListener {
                onEditClick(customer.id)
            }

            // Communication button click
            binding.btnCommunication.setOnClickListener {
                onCommunicationClick(customer.id)
            }

            // Item click (optional - you can remove if not needed)
            binding.root.setOnClickListener {
                onEditClick(customer.id)
            }
        }
    }
}

package com.invoiceapp.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.invoiceapp.databinding.ItemCustomerBinding
import com.invoiceapp.models.Customer

class CustomerAdapter(
    private val onItemClick: (Int) -> Unit
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
            binding.tvCustomerName.text = customer.customer_name
            binding.tvCustomerEmail.text = customer.email
            binding.tvCustomerPhone.text = customer.phone ?: "No phone"

            binding.root.setOnClickListener {
                onItemClick(customer.id)
            }
        }
    }
}
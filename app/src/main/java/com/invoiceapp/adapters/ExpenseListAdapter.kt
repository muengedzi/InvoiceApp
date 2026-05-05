// adapters/ExpenseListAdapter.kt
package com.invoiceapp.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.invoiceapp.databinding.ItemExpenseBinding
import com.invoiceapp.models.Expense
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class ExpenseListAdapter(
    private val onItemClick: (Int) -> Unit
) : RecyclerView.Adapter<ExpenseListAdapter.ViewHolder>() {

    private var expenses = listOf<Expense>()

    fun submitList(newList: List<Expense>) {
        expenses = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemExpenseBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(expenses[position])
    }

    override fun getItemCount(): Int = expenses.size

    inner class ViewHolder(private val binding: ItemExpenseBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(expense: Expense) {
            binding.tvCategory.text = expense.category
            binding.tvDescription.text = expense.description ?: "No description"
            binding.tvAmount.text = formatCurrency(expense.amount)
            binding.tvDate.text = formatDate(expense.expense_date)

            binding.root.setOnClickListener {
                onItemClick(expense.id)
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
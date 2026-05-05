// adapters/ExpenseAdapter.kt
package com.invoiceapp.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.invoiceapp.databinding.ItemExpenseBinding
import com.invoiceapp.models.RecentExpense
import java.text.NumberFormat

class ExpenseAdapter(
    private val onItemClick: (Int) -> Unit
) : RecyclerView.Adapter<ExpenseAdapter.ViewHolder>() {

    private var expenses = listOf<RecentExpense>()

    fun submitList(newList: List<RecentExpense>) {
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

        fun bind(expense: RecentExpense) {
            binding.tvCategory.text = expense.category
            binding.tvDescription.text = expense.description
            binding.tvDate.text = expense.expense_date
            binding.tvAmount.text = formatCurrency(expense.amount)

            binding.root.setOnClickListener {
                onItemClick(expense.id)
            }
        }

        private fun formatCurrency(amount: Double): String {
            val format = NumberFormat.getCurrencyInstance()
            return format.format(amount)
        }
    }
}
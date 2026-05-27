package com.invoiceapp.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.invoiceapp.databinding.ItemReportDetailBinding
import java.text.NumberFormat
import java.util.*

class ReportDetailAdapter : RecyclerView.Adapter<ReportDetailAdapter.ViewHolder>() {

    private var items: List<Map<String, Any>> = emptyList()
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "ZA")).apply {
        currency = java.util.Currency.getInstance("ZAR")
    }

    fun submitList(newList: List<Map<String, Any>>) {
        items = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemReportDetailBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(private val binding: ItemReportDetailBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Map<String, Any>) {
            val period = firstString(
                item,
                "period",
                "category",
                "status",
                "item_name",
                "customer_name",
                "invoice_number"
            ).ifEmpty {
                val year = (item["year"] as? Number)?.toInt()
                val month = (item["month"] as? Number)?.toInt()
                if (year != null && month != null) "$year-${month.toString().padStart(2, '0')}" else "Detail"
            }
            val amount = firstNumber(
                item,
                "amount",
                "total_amount",
                "total_sales",
                "total_spent",
                "outstanding_balance",
                "balance_due",
                "revenue",
                "tax_collected",
                "tax_liability",
                "taxable_amount",
                "total_expenses",
                "net_profit"
            )
            val count = firstNumber(
                item,
                "count",
                "invoice_count",
                "total",
                "quantity_sold",
                "days_overdue"
            )

            binding.tvPeriod.text = period
            binding.tvAmount.text = currencyFormat.format(amount)
            binding.tvCount.text = if (count > 0) formatCount(item, count) else ""
        }

        private fun firstString(item: Map<String, Any>, vararg keys: String): String {
            return keys.firstNotNullOfOrNull { key -> item[key] as? String } ?: ""
        }

        private fun firstNumber(item: Map<String, Any>, vararg keys: String): Double {
            return keys.firstNotNullOfOrNull { key -> (item[key] as? Number)?.toDouble() } ?: 0.0
        }

        private fun formatCount(item: Map<String, Any>, count: Double): String {
            val label = when {
                item.containsKey("days_overdue") -> "days overdue"
                item.containsKey("quantity_sold") -> "qty"
                item.containsKey("invoice_count") -> "invoices"
                item.containsKey("invoices_created") -> {
                    val paid = number(item, "paid")
                    val pending = number(item, "pending")
                    val overdue = number(item, "overdue")
                    return "created ${displayCount(count)}, paid ${displayCount(paid)}, pending ${displayCount(pending)}, overdue ${displayCount(overdue)}"
                }
                else -> "count"
            }
            return "${displayCount(count)} $label"
        }

        private fun number(item: Map<String, Any>, key: String): Double {
            return (item[key] as? Number)?.toDouble() ?: 0.0
        }

        private fun displayCount(count: Double): String {
            return if (count % 1.0 == 0.0) count.toInt().toString() else count.toString()
        }
    }
}

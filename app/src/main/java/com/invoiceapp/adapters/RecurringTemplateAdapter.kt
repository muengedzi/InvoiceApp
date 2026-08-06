package com.invoiceapp.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.invoiceapp.databinding.ItemRecurringTemplateBinding
import com.invoiceapp.models.RecurringTemplate
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class RecurringTemplateAdapter(
    private var templates: List<RecurringTemplate>,
    private val onEditClick: (RecurringTemplate) -> Unit,
    private val onGenerateClick: (RecurringTemplate) -> Unit,
    private val onDeleteClick: (RecurringTemplate) -> Unit,
    private val onToggleStatusClick: (RecurringTemplate) -> Unit
) : RecyclerView.Adapter<RecurringTemplateAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecurringTemplateBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(templates[position])
    }

    override fun getItemCount(): Int = templates.size

    fun updateList(newList: List<RecurringTemplate>) {
        templates = newList
        notifyDataSetChanged()
    }

    /**
     * Calculate the total amount for a recurring template
     * Takes into account quantity, unit price, discounts, and tax
     */
    private fun calculateTemplateTotal(template: RecurringTemplate): Double {
        var subtotal = 0.0
        var totalTaxAmount = 0.0
        val isTaxInclusive = template.taxType.equals("inclusive", ignoreCase = true)

        template.items?.forEach { item ->
            // Calculate item amount after discount
            val itemAmount = (item.quantity * item.unitPrice) - item.discountAmount
            subtotal += itemAmount

            // Calculate tax
            if (isTaxInclusive) {
                // Tax is included in the price, so we need to extract it
                totalTaxAmount += itemAmount - (itemAmount / (1 + (item.taxRate / 100)))
            } else {
                // Tax is added on top
                totalTaxAmount += itemAmount * (item.taxRate / 100)
            }
        }

        // Return total (subtotal + tax if exclusive, or subtotal if inclusive)
        return if (isTaxInclusive) subtotal else subtotal + totalTaxAmount
    }

    /**
     * Calculate the next date when this template will generate an invoice
     */
    private fun calculateNextRecurringDate(frequency: String, dayOfMonth: Int): String {
        val calendar = Calendar.getInstance()
        val today = calendar.time

        return when (frequency.lowercase()) {
            "weekly" -> {
                calendar.add(Calendar.DAY_OF_MONTH, 7)
                dateFormat.format(calendar.time)
            }
            "biweekly" -> {
                calendar.add(Calendar.DAY_OF_MONTH, 14)
                dateFormat.format(calendar.time)
            }
            "monthly" -> {
                val targetDay = dayOfMonth.coerceAtMost(28)
                calendar.set(Calendar.DAY_OF_MONTH, targetDay)

                if (calendar.time <= today) {
                    calendar.add(Calendar.MONTH, 1)
                    val maxDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
                    if (targetDay > maxDay) {
                        calendar.set(Calendar.DAY_OF_MONTH, maxDay)
                    }
                }
                dateFormat.format(calendar.time)
            }
            "bimonthly" -> {
                calendar.add(Calendar.MONTH, 2)
                dateFormat.format(calendar.time)
            }
            "quarterly" -> {
                calendar.add(Calendar.MONTH, 3)
                dateFormat.format(calendar.time)
            }
            "yearly" -> {
                calendar.add(Calendar.YEAR, 1)
                dateFormat.format(calendar.time)
            }
            else -> {
                // Default to monthly
                calendar.add(Calendar.MONTH, 1)
                dateFormat.format(calendar.time)
            }
        }
    }

    /**
     * Format currency in South African Rand (ZAR)
     */
    private fun formatCurrency(amount: Double): String {
        return try {
            val format = NumberFormat.getCurrencyInstance(Locale("en", "ZA"))
            format.currency = Currency.getInstance("ZAR")
            format.format(amount)
        } catch (e: Exception) {
            // Fallback formatting
            String.format("R %.2f", amount)
        }
    }

    inner class ViewHolder(private val binding: ItemRecurringTemplateBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(template: RecurringTemplate) {
            // Template name
            binding.tvTemplateName.text = template.templateName

            // Customer name
            binding.tvCustomerName.text = template.customerName ?: "No Customer"

            // Frequency display
            val frequencyText = when (template.frequency.lowercase()) {
                "weekly" -> "Weekly"
                "biweekly" -> "Bi-weekly"
                "monthly" -> "Monthly"
                "bimonthly" -> "Bi-monthly"
                "quarterly" -> "Quarterly"
                "yearly" -> "Yearly"
                else -> template.frequency
            }
            binding.tvFrequency.text = frequencyText

            // Calculate and display total amount
            val totalAmount = calculateTemplateTotal(template)
            binding.tvAmount.text = formatCurrency(totalAmount)

            // Calculate and display next recurring date
            val nextDate = calculateNextRecurringDate(template.frequency, template.invoiceDateDay)
            binding.tvNextRecurringDate.text = nextDate

            // Status chip
            val isActive = template.status?.lowercase() == "active"
            binding.chipStatus.text = if (isActive) "Active" else "Inactive"

            val chipColor = if (isActive) {
                android.graphics.Color.parseColor("#4CAF50") // Green
            } else {
                android.graphics.Color.parseColor("#FF9800") // Orange
            }
            binding.chipStatus.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(chipColor))
            binding.chipStatus.setTextColor(android.graphics.Color.WHITE)

            binding.btnToggleStatus.text = if (isActive) "Deactivate" else "Activate"

            // Button click listeners
            binding.btnGenerate.setOnClickListener { onGenerateClick(template) }
            binding.btnEdit.setOnClickListener { onEditClick(template) }
            binding.btnDelete.setOnClickListener { onDeleteClick(template) }
            binding.btnToggleStatus.setOnClickListener { onToggleStatusClick(template) }
        }
    }
}
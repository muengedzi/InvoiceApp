package com.invoiceapp.utils

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

object DateUtils {
    private val apiDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayDateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)

    fun formatDateForApi(date: Date): String {
        return apiDateFormat.format(date)
    }

    fun formatDateForDisplay(dateString: String): String {
        return try {
            val date = apiDateFormat.parse(dateString)
            displayDateFormat.format(date)
        } catch (e: Exception) {
            dateString
        }
    }

    fun formatCurrency(amount: Double, currencySymbol: String = "$"): String {
        val formatter = NumberFormat.getCurrencyInstance(Locale.US)
        return formatter.format(amount)
    }

    fun getCurrentDate(): String {
        return apiDateFormat.format(Date())
    }

    fun getDueDate(daysFromNow: Int = 30): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, daysFromNow)
        return apiDateFormat.format(calendar.time)
    }

    fun parseDate(dateString: String): Date? {
        return try {
            apiDateFormat.parse(dateString)
        } catch (e: Exception) {
            null
        }
    }
}
package com.invoiceapp.activities

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.invoiceapp.databinding.ActivityAddEditExpenseBinding
import com.invoiceapp.models.ExpenseRequest
import com.invoiceapp.network.RetrofitClient
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class AddEditExpenseActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAddEditExpenseBinding
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayDateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
    private var expenseId: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddEditExpenseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        expenseId = intent.getIntExtra("expense_id", -1).takeIf { it != -1 }
        setupToolbar()
        setupClickListeners()
        if (expenseId == null) {
            binding.btnDelete.visibility = View.GONE
            setDefaultDate()
        } else {
            binding.btnDelete.visibility = View.VISIBLE
            loadExpense()
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = if (expenseId == null) "Add Expense" else "Edit Expense"
    }

    private fun setupClickListeners() {
        binding.btnSelectDate.setOnClickListener {
            showDatePicker()
        }

        binding.btnSave.setOnClickListener {
            saveExpense()
        }

        binding.btnCancel.setOnClickListener {
            finish()
        }

        binding.btnDelete.setOnClickListener {
            confirmDeleteExpense()
        }

        binding.chkIsRecurring.setOnCheckedChangeListener { _, isChecked ->
            binding.etRecurringPeriod.isEnabled = isChecked
        }
    }

    private fun confirmDeleteExpense() {
        val id = expenseId ?: return
        AlertDialog.Builder(this)
            .setTitle("Delete Expense")
            .setMessage("Delete this expense? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ -> deleteExpense(id) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteExpense(id: Int) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.deleteExpense(id)
                if (response.isSuccessful) {
                    Toast.makeText(this@AddEditExpenseActivity, "Expense deleted", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this@AddEditExpenseActivity, "Failed to delete expense", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@AddEditExpenseActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setDefaultDate() {
        val today = Date()
        binding.tvSelectedDate.text = displayDateFormat.format(today)
        binding.etExpenseDate.setText(dateFormat.format(today))
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        binding.etExpenseDate.text.toString().takeIf { it.isNotBlank() }?.let {
            runCatching { dateFormat.parse(it) }.getOrNull()?.let { selectedDate ->
                calendar.time = selectedDate
            }
        }
        DatePickerDialog(
            this,
            { _, year, month, day ->
                val date = Calendar.getInstance().apply { set(year, month, day) }.time
                binding.tvSelectedDate.text = displayDateFormat.format(date)
                binding.etExpenseDate.setText(dateFormat.format(date))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun loadExpense() {
        val id = expenseId ?: return
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getExpense(id)
                val expense = response.body()
                if (response.isSuccessful && expense != null) {
                    binding.etExpenseDate.setText(expense.expense_date.take(10))
                    runCatching { dateFormat.parse(expense.expense_date.take(10)) }.getOrNull()?.let {
                        binding.tvSelectedDate.text = displayDateFormat.format(it)
                    }
                    binding.etAmount.setText(expense.amount.toString())
                    binding.etCategory.setText(expense.category)
                    binding.etDescription.setText(expense.description ?: "")
                    binding.etVendor.setText(expense.vendor_name ?: "")
                    binding.etPaymentMethod.setText(expense.payment_method ?: "")
                    binding.chkIsRecurring.isChecked = expense.is_recurring
                    binding.etRecurringPeriod.isEnabled = expense.is_recurring
                    binding.etRecurringPeriod.setText(expense.recurring_period ?: "")
                } else {
                    Toast.makeText(this@AddEditExpenseActivity, "Expense not found", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                Toast.makeText(this@AddEditExpenseActivity, "Error loading expense: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveExpense() {
        val amount = binding.etAmount.text.toString().toDoubleOrNull()
        val category = binding.etCategory.text.toString().trim()
        val expenseDate = binding.etExpenseDate.text.toString()

        if (amount == null || amount <= 0) {
            Toast.makeText(this, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
            return
        }

        if (category.isEmpty()) {
            Toast.makeText(this, "Please enter a category", Toast.LENGTH_SHORT).show()
            return
        }

        if (expenseDate.isEmpty()) {
            Toast.makeText(this, "Please select a date", Toast.LENGTH_SHORT).show()
            return
        }

        val request = ExpenseRequest(
            expense_date = expenseDate,
            category = category,
            amount = amount,
            description = binding.etDescription.text.toString().trim().ifEmpty { null },
            vendor_name = binding.etVendor.text.toString().trim().ifEmpty { null },
            payment_method = binding.etPaymentMethod.text.toString().trim().ifEmpty { null },
            is_recurring = binding.chkIsRecurring.isChecked,
            recurring_period = if (binding.chkIsRecurring.isChecked) {
                binding.etRecurringPeriod.text.toString().trim().ifEmpty { null }
            } else null
        )

        lifecycleScope.launch {
            try {
                val response = if (expenseId == null) {
                    RetrofitClient.apiService.createExpense(request)
                } else {
                    RetrofitClient.apiService.updateExpense(expenseId!!, request)
                }

                if (response.isSuccessful && response.body() != null) {
                    Toast.makeText(this@AddEditExpenseActivity, "Expense saved successfully!", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this@AddEditExpenseActivity, "Failed to save expense", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@AddEditExpenseActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}

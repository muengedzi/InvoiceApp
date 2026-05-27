package com.invoiceapp.activities

import android.os.Bundle
import android.content.Intent
import com.invoiceapp.databinding.ActivityExpensesBinding

class ExpensesActivity : BaseActivity() {
    private lateinit var binding: ActivityExpensesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExpensesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val intent = Intent(this, AddEditExpenseActivity::class.java)
        if (getIntent().hasExtra("expense_id")) {
            intent.putExtra("expense_id", getIntent().getIntExtra("expense_id", -1))
        }
        startActivity(intent)
        finish()
    }
}

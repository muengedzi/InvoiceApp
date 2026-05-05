package com.invoiceapp.activities

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.invoiceapp.databinding.ActivityExpensesBinding

class ExpensesActivity : AppCompatActivity() {
    private lateinit var binding: ActivityExpensesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExpensesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Toast.makeText(this, "Expenses Management - Coming Soon", Toast.LENGTH_SHORT).show()
        finish()
    }
}
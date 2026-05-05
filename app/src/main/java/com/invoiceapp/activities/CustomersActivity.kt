package com.invoiceapp.activities

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.invoiceapp.databinding.ActivityCustomersBinding

class CustomersActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCustomersBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Toast.makeText(this, "Customers Management - Coming Soon", Toast.LENGTH_SHORT).show()
        finish()
    }
}
package com.invoiceapp.activities

import android.os.Bundle
import android.content.Intent
import com.invoiceapp.databinding.ActivityCustomersBinding

class CustomersActivity : BaseActivity() {
    private lateinit var binding: ActivityCustomersBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val intent = Intent(this, AddEditCustomerActivity::class.java)
        if (getIntent().hasExtra("customer_id")) {
            intent.putExtra("customer_id", getIntent().getIntExtra("customer_id", -1))
        }
        startActivity(intent)
        finish()
    }
}

package com.invoiceapp.activities

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.invoiceapp.databinding.ActivityAddEditCustomerBinding
import com.invoiceapp.models.CustomerRequest
import com.invoiceapp.network.RetrofitClient
import kotlinx.coroutines.launch

class AddEditCustomerActivity : BaseActivity() {
    private lateinit var binding: ActivityAddEditCustomerBinding
    private var customerId: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddEditCustomerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        customerId = intent.getIntExtra("customer_id", -1).takeIf { it != -1 }

        if (customerId != null) {
            supportActionBar?.title = "Edit Customer"
            binding.btnDelete.visibility = View.VISIBLE
            loadCustomerDetails()
        } else {
            supportActionBar?.title = "Add Customer"
            binding.btnDelete.visibility = View.GONE
        }

        setupClickListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun loadCustomerDetails() {
        lifecycleScope.launch {
            try {
                val id = customerId ?: return@launch
                val response = RetrofitClient.apiService.getCustomer(id)
                if (response.isSuccessful && response.body() != null) {
                    val customer = response.body()
                    customer?.let {
                        binding.etCustomerName.setText(it.customer_name)
                        binding.etEmail.setText(it.email)
                        binding.etPhone.setText(it.phone ?: "")
                        binding.etAddress.setText(it.address ?: "")
                        binding.etCity.setText(it.city ?: "")
                        binding.etState.setText(it.state ?: "")
                        binding.etPostalCode.setText(it.postal_code ?: "")
                        binding.etCountry.setText(it.country)
                        binding.chkTaxExempt.isChecked = it.tax_exempt
                        binding.chkIsCompany.isChecked = it.is_company
                        binding.etCompanyRegistrationNumber.setText(it.company_registration_number ?: "")
                        binding.chkVatRegistered.isChecked = it.vat_registered
                        binding.etVatNumber.setText(it.vat_number ?: "")
                        binding.etCompanyAddress.setText(it.company_address ?: "")
                        binding.etNotes.setText(it.notes ?: "")
                        updateCompanyFieldVisibility()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@AddEditCustomerActivity, "Error loading customer: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnSave.setOnClickListener {
            saveCustomer()
        }
        binding.btnCancel.setOnClickListener {
            finish()
        }
        binding.btnDelete.setOnClickListener {
            confirmDeleteCustomer()
        }
        binding.chkIsCompany.setOnCheckedChangeListener { _, _ -> updateCompanyFieldVisibility() }
        binding.chkVatRegistered.setOnCheckedChangeListener { _, _ -> updateCompanyFieldVisibility() }
        updateCompanyFieldVisibility()
    }

    private fun updateCompanyFieldVisibility() {
        val isCompany = binding.chkIsCompany.isChecked
        val vatRegistered = binding.chkVatRegistered.isChecked
        binding.layoutCompanyRegistrationNumber.visibility = if (isCompany) View.VISIBLE else View.GONE
        binding.chkVatRegistered.visibility = if (isCompany) View.VISIBLE else View.GONE
        binding.layoutCompanyAddress.visibility = if (isCompany) View.VISIBLE else View.GONE
        binding.layoutVatNumber.visibility = if (isCompany && vatRegistered) View.VISIBLE else View.GONE
        if (!isCompany) {
            binding.chkVatRegistered.isChecked = false
        }
    }

    private fun confirmDeleteCustomer() {
        val id = customerId ?: return
        AlertDialog.Builder(this)
            .setTitle("Delete Customer")
            .setMessage("Delete this customer? Customers with invoices cannot be deleted.")
            .setPositiveButton("Delete") { _, _ -> deleteCustomer(id) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteCustomer(id: Int) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.deleteCustomer(id)
                if (response.isSuccessful) {
                    Toast.makeText(this@AddEditCustomerActivity, "Customer deleted", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this@AddEditCustomerActivity, "Failed to delete customer", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@AddEditCustomerActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveCustomer() {
        val name = binding.etCustomerName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()

        if (name.isEmpty() || email.isEmpty()) {
            Toast.makeText(this, "Name and email are required", Toast.LENGTH_SHORT).show()
            return
        }

        val request = CustomerRequest(
            customer_name = name,
            email = email,
            phone = binding.etPhone.text.toString().trim().ifEmpty { null },
            address = binding.etAddress.text.toString().trim().ifEmpty { null },
            city = binding.etCity.text.toString().trim().ifEmpty { null },
            state = binding.etState.text.toString().trim().ifEmpty { null },
            postal_code = binding.etPostalCode.text.toString().trim().ifEmpty { null },
            country = binding.etCountry.text.toString().trim().ifEmpty { "USA" },
            tax_exempt = binding.chkTaxExempt.isChecked,
            is_company = binding.chkIsCompany.isChecked,
            company_registration_number = binding.etCompanyRegistrationNumber.text.toString().trim().ifEmpty { null },
            vat_registered = binding.chkIsCompany.isChecked && binding.chkVatRegistered.isChecked,
            vat_number = binding.etVatNumber.text.toString().trim().ifEmpty { null },
            company_address = binding.etCompanyAddress.text.toString().trim().ifEmpty { null },
            notes = binding.etNotes.text.toString().trim().ifEmpty { null }
        )

        lifecycleScope.launch {
            try {
                val response = if (customerId != null) {
                    RetrofitClient.apiService.updateCustomer(customerId!!, request)
                } else {
                    RetrofitClient.apiService.createCustomer(request)
                }

                if (response.isSuccessful && response.body() != null) {
                    Toast.makeText(this@AddEditCustomerActivity, "Customer saved successfully!", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this@AddEditCustomerActivity, "Failed to save customer", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@AddEditCustomerActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}

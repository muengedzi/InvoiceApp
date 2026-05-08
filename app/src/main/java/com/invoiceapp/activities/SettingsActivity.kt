// activities/SettingsActivity.kt
package com.invoiceapp.activities

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.invoiceapp.databinding.ActivitySettingsBinding
import com.invoiceapp.models.CompanySettings
import com.invoiceapp.network.RetrofitClient
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        loadSettings()
        setupClickListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Company Settings"
    }

    private fun loadSettings() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getCompanySettings()
                if (response.isSuccessful && response.body() != null) {
                    val settings = response.body()!!
                    populateFields(settings)
                } else {
                    Toast.makeText(this@SettingsActivity, "Failed to load settings", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, "Error loading settings: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun populateFields(settings: CompanySettings) {
        binding.etCompanyName.setText(settings.company_name ?: "")
        binding.etCompanyEmail.setText(settings.company_email ?: "")
        binding.etCompanyPhone.setText(settings.company_phone ?: "")

        // Parse the combined address into separate fields
        settings.company_address?.let { fullAddress ->
            parseAddress(fullAddress)
        }

        binding.etTaxRate.setText(settings.tax_rate_default.toString())
    }

    private fun parseAddress(fullAddress: String) {
        // Try to parse address components
        // Format: "Street Address|Apartment|City|State|Postal Code|Country"
        // Or use delimiters like commas

        val parts = fullAddress.split(",").map { it.trim() }

        when (parts.size) {
            1 -> {
                binding.etStreetAddress.setText(parts[0])
            }
            2 -> {
                binding.etStreetAddress.setText(parts[0])
                binding.etCity.setText(parts[1])
            }
            3 -> {
                binding.etStreetAddress.setText(parts[0])
                binding.etCity.setText(parts[1])
                binding.etState.setText(parts[2])
            }
            4 -> {
                binding.etStreetAddress.setText(parts[0])
                binding.etCity.setText(parts[1])
                binding.etState.setText(parts[2])
                binding.etPostalCode.setText(parts[3])
            }
            5 -> {
                binding.etStreetAddress.setText(parts[0])
                binding.etCity.setText(parts[1])
                binding.etState.setText(parts[2])
                binding.etPostalCode.setText(parts[3])
                binding.etCountry.setText(parts[4])
            }
            else -> {
                // If format is different, just put everything in street address
                binding.etStreetAddress.setText(fullAddress)
            }
        }
    }

    private fun combineAddress(): String {
        val street = binding.etStreetAddress.text.toString().trim()
        val apartment = binding.etApartment.text.toString().trim()
        val city = binding.etCity.text.toString().trim()
        val state = binding.etState.text.toString().trim()
        val postalCode = binding.etPostalCode.text.toString().trim()
        val country = binding.etCountry.text.toString().trim()

        val parts = mutableListOf<String>()

        // Build the address string
        val streetWithApartment = if (apartment.isNotEmpty()) {
            "$street, $apartment"
        } else {
            street
        }

        if (streetWithApartment.isNotEmpty()) parts.add(streetWithApartment)
        if (city.isNotEmpty()) parts.add(city)
        if (state.isNotEmpty()) parts.add(state)
        if (postalCode.isNotEmpty()) parts.add(postalCode)
        if (country.isNotEmpty() && country != "USA") parts.add(country)

        return parts.joinToString(", ")
    }

    private fun setupClickListeners() {
        binding.btnSave.setOnClickListener {
            saveSettings()
        }
    }

    private fun saveSettings() {
        val combinedAddress = combineAddress()

        val settings = CompanySettings(
            company_name = binding.etCompanyName.text.toString().trim().ifEmpty { null },
            company_email = binding.etCompanyEmail.text.toString().trim().ifEmpty { null },
            company_phone = binding.etCompanyPhone.text.toString().trim().ifEmpty { null },
            company_address = combinedAddress.ifEmpty { null },  // Save combined address
            tax_rate_default = binding.etTaxRate.text.toString().toDoubleOrNull() ?: 0.0
        )

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.updateCompanySettings(settings)
                if (response.isSuccessful && response.body() != null) {
                    Toast.makeText(this@SettingsActivity, "Settings saved successfully!", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this@SettingsActivity, "Failed to save settings: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
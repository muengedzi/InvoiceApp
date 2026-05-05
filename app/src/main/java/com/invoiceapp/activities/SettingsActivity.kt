// activities/SettingsActivity.kt
package com.invoiceapp.activities

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.invoiceapp.databinding.ActivitySettingsBinding
import com.invoiceapp.utils.SessionManager

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

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
        // Load existing settings from SharedPreferences or API
        // For now, just set some defaults
        binding.etCompanyName.setText(sessionManager.getUserName())
        binding.etCompanyEmail.setText(sessionManager.getUserEmail())
    }

    private fun setupClickListeners() {
        binding.btnSave.setOnClickListener {
            saveSettings()
        }
    }

    private fun saveSettings() {
        val companyName = binding.etCompanyName.text.toString().trim()
        val companyEmail = binding.etCompanyEmail.text.toString().trim()
        val companyPhone = binding.etCompanyPhone.text.toString().trim()
        val companyAddress = binding.etCompanyAddress.text.toString().trim()
        val taxRate = binding.etTaxRate.text.toString().toDoubleOrNull() ?: 0.0

        // TODO: Save to API
        // For now, just show success message

        Toast.makeText(this, "Settings saved successfully!", Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
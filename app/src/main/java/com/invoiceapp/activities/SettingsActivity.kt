package com.invoiceapp.activities

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.invoiceapp.R
import com.invoiceapp.databinding.ActivitySettingsBinding
import com.invoiceapp.models.CompanySettings
import com.invoiceapp.network.ApiResponse
import com.invoiceapp.network.RetrofitClient
import com.invoiceapp.utils.SessionManager
import com.invoiceapp.viewmodels.SettingsViewModel
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream

class SettingsActivity : BaseActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var viewModel: SettingsViewModel
    private var selectedLogoUri: Uri? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedLogoUri = uri
                binding.ivCompanyLogo.setImageURI(uri)
                uploadLogoToServer(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)
        viewModel = ViewModelProvider(this)[SettingsViewModel::class.java]

        setupToolbar()
        setupClickListeners()
        observeViewModel()
        loadSettings()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Company Settings"
    }

    private fun setupClickListeners() {
        binding.btnSelectLogo.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            pickImageLauncher.launch(intent)
        }

        binding.btnSave.setOnClickListener {
            saveSettings()
        }
    }

    private fun observeViewModel() {
        viewModel.settings.observe(this) { result ->
            when (result) {
                is ApiResponse.Loading -> {
                    binding.progressBar.visibility = android.view.View.VISIBLE
                }
                is ApiResponse.Success -> {
                    binding.progressBar.visibility = android.view.View.GONE
                    populateSettings(result.data)
                }
                is ApiResponse.Error -> {
                    binding.progressBar.visibility = android.view.View.GONE
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        viewModel.updateResult.observe(this) { result ->
            when (result) {
                is ApiResponse.Loading -> {
                    binding.btnSave.isEnabled = false
                    binding.progressBar.visibility = android.view.View.VISIBLE
                }
                is ApiResponse.Success -> {
                    binding.btnSave.isEnabled = true
                    binding.progressBar.visibility = android.view.View.GONE
                    Toast.makeText(this, "Settings saved successfully!", Toast.LENGTH_SHORT).show()
                }
                is ApiResponse.Error -> {
                    binding.btnSave.isEnabled = true
                    binding.progressBar.visibility = android.view.View.GONE
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun loadSettings() {
        viewModel.loadSettings()
    }

    private fun parseAddress(address: String): AddressComponents {
        // Expected format: "69 Riverbend Road, Umzimkhulu, KwaZulu-Natal, 3297, South Africa"
        val components = address.split(",").map { it.trim() }

        return AddressComponents(
            street = if (components.isNotEmpty()) components[0] else "",
            city = if (components.size > 1) components[1] else "",
            state = if (components.size > 2) components[2] else "",
            postalCode = if (components.size > 3) components[3] else "",
            country = if (components.size > 4) components[4] else "South Africa"
        )
    }

    private fun formatAddress(street: String, city: String, state: String, postalCode: String, country: String): String {
        val parts = mutableListOf<String>()
        if (street.isNotEmpty()) parts.add(street)
        if (city.isNotEmpty()) parts.add(city)
        if (state.isNotEmpty()) parts.add(state)
        if (postalCode.isNotEmpty()) parts.add(postalCode)
        if (country.isNotEmpty()) parts.add(country)
        return parts.joinToString(", ")
    }

    private fun populateSettings(settings: CompanySettings) {
        binding.etCompanyName.setText(settings.company_name ?: "")
        binding.etCompanyEmail.setText(settings.company_email ?: "")
        binding.etCompanyPhone.setText(settings.company_phone ?: "")
        binding.etCompanyWebsite.setText(settings.company_website ?: "")
        binding.etCompanyVatNumber.setText(settings.company_vat_number ?: "")
        binding.etTaxRate.setText(settings.tax_rate_default?.toString() ?: "0")

        // Parse the address from the database
        val address = settings.company_address ?: ""
        val parsedAddress = parseAddress(address)

        binding.etStreetAddress.setText(parsedAddress.street)
        binding.etCity.setText(parsedAddress.city)
        binding.etState.setText(parsedAddress.state)
        binding.etPostalCode.setText(parsedAddress.postalCode)
        binding.etCountry.setText(parsedAddress.country)

        // Load logo
        if (!settings.company_logo_url.isNullOrEmpty()) {
            Glide.with(this)
                .load(settings.company_logo_url)
                .placeholder(R.drawable.ic_company_placeholder)
                .into(binding.ivCompanyLogo)
        }
    }

    private fun uploadLogoToServer(uri: Uri) {
        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = android.view.View.VISIBLE

                val inputStream = contentResolver.openInputStream(uri)
                val file = File(cacheDir, "company_logo_temp.png")
                FileOutputStream(file).use { output ->
                    inputStream?.copyTo(output)
                }

                val requestBody = file.asRequestBody("image/png".toMediaTypeOrNull())
                val body = MultipartBody.Part.createFormData("logo", file.name, requestBody)

                val response = RetrofitClient.apiService.uploadCompanyLogo(body)
                if (response.isSuccessful && response.body() != null) {
                    val result = response.body()
                    val logoUrl = result?.get("logo_url") as? String ?: ""
                    binding.tvLogoStatus.text = "Logo uploaded successfully"
                    Toast.makeText(this@SettingsActivity, "Logo uploaded", Toast.LENGTH_SHORT).show()
                } else {
                    binding.tvLogoStatus.text = "Upload failed"
                    Toast.makeText(this@SettingsActivity, "Upload failed", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                binding.tvLogoStatus.text = "Error: ${e.message}"
                Toast.makeText(this@SettingsActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = android.view.View.GONE
            }
        }
    }

    private fun saveSettings() {
        // Get individual address components from the form
        val street = binding.etStreetAddress.text.toString().trim()
        val city = binding.etCity.text.toString().trim()
        val state = binding.etState.text.toString().trim()
        val postalCode = binding.etPostalCode.text.toString().trim()
        val country = binding.etCountry.text.toString().trim()

        // Format the address as a single string
        val fullAddress = formatAddress(street, city, state, postalCode, country)

        val settings = CompanySettings(
            id = null,
            company_name = binding.etCompanyName.text.toString().trim(),
            company_address = fullAddress,
            company_phone = binding.etCompanyPhone.text.toString().trim(),
            company_email = binding.etCompanyEmail.text.toString().trim(),
            company_website = binding.etCompanyWebsite.text.toString().trim(),
            company_vat_number = binding.etCompanyVatNumber.text.toString().trim(),
            tax_rate_default = binding.etTaxRate.text.toString().toDoubleOrNull() ?: 0.0,
            currency_symbol = "R",
            currency_code = "ZAR",
            invoice_prefix = "INV-",
            estimate_prefix = "EST-",
            company_logo_url = null,
            invoice_notes_default = null,
            terms_conditions_default = null,
            footer_text_default = null,
            payment_terms_default = null
        )

        viewModel.updateSettings(settings)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    // Data class for address components
    data class AddressComponents(
        val street: String,
        val city: String,
        val state: String,
        val postalCode: String,
        val country: String
    )
}
package com.invoiceapp.activities

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
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
import java.util.Locale

class SettingsActivity : BaseActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var viewModel: SettingsViewModel
    private var selectedLogoUri: Uri? = null
    private var currentSettings: CompanySettings? = null

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

        binding.btnRemoveLogo.setOnClickListener {
            removeLogo()
        }

        binding.btnSave.setOnClickListener {
            saveSettings()
        }
    }

    private fun observeViewModel() {
        viewModel.settings.observe(this) { result ->
            when (result) {
                is ApiResponse.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                }
                is ApiResponse.Success -> {
                    binding.progressBar.visibility = View.GONE
                    currentSettings = result.data
                    populateSettings(result.data)
                }
                is ApiResponse.Error -> {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        viewModel.updateResult.observe(this) { result ->
            when (result) {
                is ApiResponse.Loading -> {
                    binding.btnSave.isEnabled = false
                    binding.progressBar.visibility = View.VISIBLE
                }
                is ApiResponse.Success -> {
                    binding.btnSave.isEnabled = true
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, "Settings saved successfully!", Toast.LENGTH_SHORT).show()
                    loadSettings() // Refresh
                }
                is ApiResponse.Error -> {
                    binding.btnSave.isEnabled = true
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun loadSettings() {
        viewModel.loadSettings()
    }

    private fun parseAddress(address: String): AddressComponents {
        // Use a safe delimiter for structured data
        if (address.contains("|")) {
            val components = address.split("|")
            return AddressComponents(
                street = components.getOrElse(0) { "" },
                city = components.getOrElse(1) { "" },
                state = components.getOrElse(2) { "" },
                postalCode = components.getOrElse(3) { "" },
                country = components.getOrElse(4) { "South Africa" }
            )
        } else {
            // Fallback for legacy comma-separated address
            val components = address.split(",").map { it.trim() }
            return AddressComponents(
                street = components.getOrElse(0) { "" },
                city = components.getOrElse(1) { "" },
                state = components.getOrElse(2) { "" },
                postalCode = components.getOrElse(3) { "" },
                country = components.getOrElse(4) { "South Africa" }
            )
        }
    }

    private fun formatAddress(street: String, city: String, state: String, postalCode: String, country: String): String {
        return "$street|$city|$state|$postalCode|$country"
    }

    private fun populateSettings(settings: CompanySettings) {
        binding.etCompanyName.setText(settings.company_name ?: "")
        binding.etCompanyEmail.setText(settings.company_email ?: "")
        binding.etCompanyPhone.setText(settings.company_phone ?: "")
        binding.etCompanyWebsite.setText(settings.company_website ?: "")
        binding.etCompanyVatNumber.setText(settings.company_vat_number ?: "")
        binding.etBankAccountDetails.setText(settings.bank_account_details ?: "")
        binding.etTaxRate.setText(settings.tax_rate_default.toString())
        
        binding.etInvoiceNotes.setText(settings.invoice_notes_default ?: "")
        binding.etTermsConditions.setText(settings.terms_conditions_default ?: "")
        binding.etFooterText.setText(settings.footer_text_default ?: "")

        val address = settings.company_address ?: ""
        val parsedAddress = parseAddress(address)

        binding.etStreetAddress.setText(parsedAddress.street)
        binding.etCity.setText(parsedAddress.city)
        binding.etState.setText(parsedAddress.state)
        binding.etPostalCode.setText(parsedAddress.postalCode)
        binding.etCountry.setText(parsedAddress.country)

        if (!settings.company_logo_url.isNullOrEmpty()) {
            val logoUrl = settings.company_logo_url!!
            val fullUrl = if (logoUrl.startsWith("http")) {
                logoUrl
            } else {
                val baseUrl = com.invoiceapp.network.Constants.BASE_URL.trimEnd('/')
                val formattedLogoUrl = if (logoUrl.startsWith("/")) logoUrl else "/$logoUrl"
                "$baseUrl$formattedLogoUrl"
            }

            Glide.with(this)
                .load(fullUrl)
                .placeholder(R.drawable.ic_company_placeholder)
                .error(R.drawable.ic_company_placeholder)
                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                .into(binding.ivCompanyLogo)
        } else {
            binding.ivCompanyLogo.setImageResource(R.drawable.ic_company_placeholder)
        }
    }

    private fun uploadLogoToServer(uri: Uri) {
        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE

                val inputStream = contentResolver.openInputStream(uri)
                val originalBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                val orientation = getOrientation(uri)
                val correctedBitmap = rotateBitmapIfNeeded(originalBitmap, orientation)

                val file = File(cacheDir, "company_logo_temp.png")
                FileOutputStream(file).use { output ->
                    correctedBitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                }
                correctedBitmap.recycle()
                originalBitmap.recycle()

                val requestBody = file.asRequestBody("image/png".toMediaTypeOrNull())
                val body = MultipartBody.Part.createFormData("logo", file.name, requestBody)

                val response = RetrofitClient.apiService.uploadCompanyLogo(body)
                if (response.isSuccessful && response.body() != null) {
                    val result = response.body()
                    val logoUrl = result?.get("logo_url") as? String ?: ""
                    binding.tvLogoStatus.text = "Logo uploaded successfully"
                    Toast.makeText(this@SettingsActivity, "Logo uploaded", Toast.LENGTH_SHORT).show()
                    loadSettings()
                } else {
                    binding.tvLogoStatus.text = "Upload failed"
                    Toast.makeText(this@SettingsActivity, "Upload failed", Toast.LENGTH_SHORT).show()
                }
                file.delete()
            } catch (e: Exception) {
                binding.tvLogoStatus.text = "Error: ${e.message}"
                Toast.makeText(this@SettingsActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun getOrientation(uri: Uri): Int {
        var orientation = 0
        try {
            val cursor = contentResolver.query(uri, arrayOf(MediaStore.Images.ImageColumns.ORIENTATION), null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                orientation = cursor.getInt(0)
                cursor.close()
            }
        } catch (e: Exception) {}
        return orientation
    }

    private fun rotateBitmapIfNeeded(bitmap: Bitmap, orientation: Int): Bitmap {
        return when (orientation) {
            90 -> rotateBitmap(bitmap, 90f)
            180 -> rotateBitmap(bitmap, 180f)
            270 -> rotateBitmap(bitmap, 270f)
            else -> bitmap
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun removeLogo() {
        AlertDialog.Builder(this)
            .setTitle("Remove Logo")
            .setMessage("Are you sure you want to remove your company logo?")
            .setPositiveButton("Remove") { _, _ ->
                lifecycleScope.launch {
                    try {
                        binding.progressBar.visibility = View.VISIBLE
                        val response = RetrofitClient.apiService.removeCompanyLogo()
                        if (response.isSuccessful) {
                            binding.ivCompanyLogo.setImageResource(R.drawable.ic_company_placeholder)
                            binding.tvLogoStatus.text = "Logo removed"
                            Toast.makeText(this@SettingsActivity, "Logo removed successfully", Toast.LENGTH_SHORT).show()
                            loadSettings()
                        } else {
                            binding.tvLogoStatus.text = "Failed to remove logo"
                            Toast.makeText(this@SettingsActivity, "Failed to remove logo", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        binding.tvLogoStatus.text = "Error: ${e.message}"
                        Toast.makeText(this@SettingsActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    } finally {
                        binding.progressBar.visibility = View.GONE
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveSettings() {
        val street = binding.etStreetAddress.text.toString().trim()
        val city = binding.etCity.text.toString().trim()
        val state = binding.etState.text.toString().trim()
        val postalCode = binding.etPostalCode.text.toString().trim()
        val country = binding.etCountry.text.toString().trim()

        val fullAddress = formatAddress(street, city, state, postalCode, country)

        val settings = CompanySettings(
            id = currentSettings?.id,
            company_name = binding.etCompanyName.text.toString().trim(),
            company_address = fullAddress,
            company_phone = binding.etCompanyPhone.text.toString().trim(),
            company_email = binding.etCompanyEmail.text.toString().trim(),
            company_website = binding.etCompanyWebsite.text.toString().trim(),
            company_vat_number = binding.etCompanyVatNumber.text.toString().trim(),
            bank_account_details = binding.etBankAccountDetails.text.toString().trim(),
            tax_rate_default = binding.etTaxRate.text.toString().toDoubleOrNull() ?: 0.0,
            currency_symbol = currentSettings?.currency_symbol ?: "R",
            currency_code = currentSettings?.currency_code ?: "ZAR",
            invoice_prefix = currentSettings?.invoice_prefix ?: "INV-",
            estimate_prefix = currentSettings?.estimate_prefix ?: "EST-",
            company_logo_url = currentSettings?.company_logo_url,
            invoice_notes_default = binding.etInvoiceNotes.text.toString().trim(),
            terms_conditions_default = binding.etTermsConditions.text.toString().trim(),
            footer_text_default = binding.etFooterText.text.toString().trim(),
            payment_terms_default = currentSettings?.payment_terms_default
        )

        viewModel.updateSettings(settings)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    data class AddressComponents(
        val street: String,
        val city: String,
        val state: String,
        val postalCode: String,
        val country: String
    )
}

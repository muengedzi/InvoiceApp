package com.invoiceapp.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.invoiceapp.databinding.ActivityVerifyEmailBinding
import com.invoiceapp.network.RetrofitClient
import kotlinx.coroutines.launch

class VerifyEmailActivity : BaseActivity() {

    private lateinit var binding: ActivityVerifyEmailBinding
    private var token: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVerifyEmailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get token from intent or deep link - handle all possible sources
        token = getTokenFromIntent()

        if (token.isNullOrEmpty()) {
            Toast.makeText(this, "Invalid verification link", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupClickListeners()
        verifyEmail()
    }

    private fun getTokenFromIntent(): String? {
        // Check if token was passed as an extra
        intent.getStringExtra("token")?.let { return it }

        // Check if token is in the deep link data
        intent.data?.let { uri ->
            // Handle invoiceapp://verify?token=xxx
            uri.getQueryParameter("token")?.let { return it }

            // Handle http://host/verify-email-page?token=xxx
            val path = uri.path
            if (path != null && path.contains("verify-email-page")) {
                uri.getQueryParameter("token")?.let { return it }
            }
        }

        return null
    }

    private fun setupClickListeners() {
        binding.btnVerify.setOnClickListener {
            verifyEmail()
        }

        binding.btnLogin.setOnClickListener {
            finish()
            startActivity(Intent(this, LoginActivity::class.java))
        }
    }

    private fun verifyEmail() {
        binding.progressBar.visibility = android.view.View.VISIBLE
        binding.btnVerify.isEnabled = false

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.verifyEmail(mapOf("token" to token!!))
                if (response.isSuccessful && response.body()?.get("success") == true) {
                    binding.progressBar.visibility = android.view.View.GONE
                    binding.tvStatus.text = "✅ Email verified successfully!"
                    binding.tvStatus.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                    binding.btnVerify.visibility = android.view.View.GONE
                    binding.btnLogin.visibility = android.view.View.VISIBLE
                    Toast.makeText(this@VerifyEmailActivity, "Email verified! You can now login.", Toast.LENGTH_LONG).show()
                } else {
                    binding.progressBar.visibility = android.view.View.GONE
                    binding.btnVerify.isEnabled = true
                    val errorMsg = response.body()?.get("error") as? String ?: "Verification failed"
                    binding.tvStatus.text = "❌ $errorMsg"
                    binding.tvStatus.setTextColor(android.graphics.Color.parseColor("#F44336"))
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = android.view.View.GONE
                binding.btnVerify.isEnabled = true
                binding.tvStatus.text = "❌ Error: ${e.message}"
                binding.tvStatus.setTextColor(android.graphics.Color.parseColor("#F44336"))
            }
        }
    }
}
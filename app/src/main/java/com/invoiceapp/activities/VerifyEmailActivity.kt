package com.invoiceapp.activities

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.invoiceapp.databinding.ActivityVerifyEmailBinding
import com.invoiceapp.network.ApiClient
import com.invoiceapp.network.ApiResponse
import com.invoiceapp.viewmodels.AuthViewModel
import kotlinx.coroutines.launch

class VerifyEmailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVerifyEmailBinding
    private var token: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVerifyEmailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get token from intent or deep link
        token = intent.getStringExtra("token") ?: intent.data?.getQueryParameter("token")

        if (token == null) {
            Toast.makeText(this, "Invalid verification link", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupClickListeners()
        verifyEmail()
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
                val response = ApiClient.apiService.verifyEmail(mapOf("token" to token!!))
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
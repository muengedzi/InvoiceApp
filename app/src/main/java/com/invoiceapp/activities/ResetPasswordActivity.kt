package com.invoiceapp.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.invoiceapp.databinding.ActivityResetPasswordBinding
import com.invoiceapp.models.ResetPasswordRequest
import com.invoiceapp.network.RetrofitClient
import kotlinx.coroutines.launch

class ResetPasswordActivity : BaseActivity() {
    private lateinit var binding: ActivityResetPasswordBinding
    private var resetToken: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResetPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        resetToken = intent?.data?.getQueryParameter("token")
            ?: intent?.getStringExtra("token")

        if (resetToken.isNullOrBlank()) {
            binding.tvResetInstructions.text = "This reset link is invalid or incomplete."
            binding.btnResetPassword.isEnabled = false
        }

        binding.btnResetPassword.setOnClickListener {
            resetPassword()
        }

        binding.tvBackToLogin.setOnClickListener {
            navigateToLogin()
        }
    }

    private fun resetPassword() {
        val token = resetToken
        val password = binding.etNewPassword.text?.toString().orEmpty()
        val confirmPassword = binding.etConfirmPassword.text?.toString().orEmpty()

        if (token.isNullOrBlank()) {
            Toast.makeText(this, "Invalid reset link", Toast.LENGTH_LONG).show()
            return
        }

        if (password.length < 8) {
            binding.etNewPassword.error = "Password must be at least 8 characters"
            return
        }

        if (password != confirmPassword) {
            binding.etConfirmPassword.error = "Passwords do not match"
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.btnResetPassword.isEnabled = false

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.resetPassword(ResetPasswordRequest(token, password))
                val body = response.body()
                if (response.isSuccessful && body?.success == true) {
                    Toast.makeText(
                        this@ResetPasswordActivity,
                        body.message ?: "Password reset successfully. Please log in.",
                        Toast.LENGTH_LONG
                    ).show()
                    navigateToLogin()
                } else {
                    Toast.makeText(
                        this@ResetPasswordActivity,
                        body?.error ?: body?.message ?: "Unable to reset password",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ResetPasswordActivity, e.message ?: "Network error", Toast.LENGTH_LONG).show()
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.btnResetPassword.isEnabled = true
            }
        }
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}

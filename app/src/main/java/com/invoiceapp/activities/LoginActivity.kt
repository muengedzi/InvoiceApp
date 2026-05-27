package com.invoiceapp.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.invoiceapp.models.ForgotPasswordRequest
import com.invoiceapp.databinding.ActivityLoginBinding
import com.invoiceapp.network.ApiResponse
import com.invoiceapp.network.RetrofitClient
import com.invoiceapp.utils.SessionManager
import com.invoiceapp.viewmodels.AuthViewModel
import kotlinx.coroutines.launch

class LoginActivity : BaseActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var authViewModel: AuthViewModel
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        authViewModel = ViewModelProvider(this)[AuthViewModel::class.java]
        sessionManager = SessionManager(this)

        // Check if already logged in
        if (sessionManager.isLoggedIn()) {
            navigateToMain()
            return
        }

        setupClickListeners()
        observeViewModel()
    }

    private fun setupClickListeners() {
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()

            if (validateInputs(email, password)) {
                authViewModel.login(email, password)
            }
        }

        binding.tvRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        binding.tvForgotPassword.setOnClickListener {
            showForgotPasswordDialog()
        }
    }

    private fun validateInputs(email: String, password: String): Boolean {
        if (email.isEmpty()) {
            binding.etEmail.error = "Email required"
            return false
        }
        if (password.isEmpty()) {
            binding.etPassword.error = "Password required"
            return false
        }
        return true
    }

    private fun observeViewModel() {
        authViewModel.loginResult.observe(this) { result ->
            when (result) {
                is ApiResponse.Loading -> {
                    binding.btnLogin.isEnabled = false
                    binding.progressBar.visibility = android.view.View.VISIBLE
                }
                is ApiResponse.Success -> {
                    binding.btnLogin.isEnabled = true
                    binding.progressBar.visibility = android.view.View.GONE

                    val loginResponse = result.data as com.invoiceapp.models.LoginResponse
                    sessionManager.saveUser(
                        loginResponse.user.id,
                        loginResponse.user.email,
                        loginResponse.user.full_name,
                        loginResponse.user.role
                    )
                    Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show()
                    navigateToMain()
                }
                is ApiResponse.Error -> {
                    binding.btnLogin.isEnabled = true
                    binding.progressBar.visibility = android.view.View.GONE
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showForgotPasswordDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 8, 48, 0)
        }

        val inputLayout = TextInputLayout(this).apply {
            hint = "Email Address"
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        }
        val emailInput = TextInputEditText(inputLayout.context).apply {
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setText(binding.etEmail.text?.toString()?.trim().orEmpty())
        }
        inputLayout.addView(emailInput)
        container.addView(inputLayout)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Reset password")
            .setMessage("Enter your account email and we'll send you a reset link.")
            .setView(container)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Send Link", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val email = emailInput.text?.toString()?.trim().orEmpty()
                if (email.isBlank()) {
                    inputLayout.error = "Email required"
                } else {
                    inputLayout.error = null
                    requestPasswordReset(email, dialog)
                }
            }
        }

        dialog.show()
    }

    private fun requestPasswordReset(email: String, dialog: AlertDialog) {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.forgotPassword(ForgotPasswordRequest(email))
                val body = response.body()
                if (response.isSuccessful && body?.success == true) {
                    Toast.makeText(
                        this@LoginActivity,
                        body.message ?: "If that email exists, a reset link has been sent.",
                        Toast.LENGTH_LONG
                    ).show()
                    dialog.dismiss()
                } else {
                    Toast.makeText(
                        this@LoginActivity,
                        body?.error ?: body?.message ?: "Unable to send reset link",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@LoginActivity, e.message ?: "Network error", Toast.LENGTH_LONG).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}

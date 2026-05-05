package com.invoiceapp.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.invoiceapp.databinding.ActivityRegisterBinding
import com.invoiceapp.network.ApiResponse
import com.invoiceapp.viewmodels.AuthViewModel

class RegisterActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegisterBinding
    private lateinit var authViewModel: AuthViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        authViewModel = ViewModelProvider(this)[AuthViewModel::class.java]

        setupClickListeners()
        observeViewModel()
    }

    private fun setupClickListeners() {
        binding.btnRegister.setOnClickListener {
            val fullName = binding.etFullName.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()
            val confirmPassword = binding.etConfirmPassword.text.toString()
            val businessName = binding.etBusinessName.text.toString().trim()

            if (validateInputs(fullName, email, password, confirmPassword)) {
                authViewModel.register(fullName, email, password, businessName.ifEmpty { null })
            }
        }

        binding.tvLogin.setOnClickListener {
            finish()
        }
    }

    private fun validateInputs(fullName: String, email: String, password: String, confirmPassword: String): Boolean {
        if (fullName.isEmpty()) {
            binding.etFullName.error = "Full name required"
            return false
        }
        if (email.isEmpty()) {
            binding.etEmail.error = "Email required"
            return false
        }
        if (password.isEmpty()) {
            binding.etPassword.error = "Password required"
            return false
        }
        if (password.length < 8) {
            binding.etPassword.error = "Password must be at least 8 characters"
            return false
        }
        if (password != confirmPassword) {
            binding.etConfirmPassword.error = "Passwords do not match"
            return false
        }
        return true
    }

    private fun observeViewModel() {
        authViewModel.registerResult.observe(this) { result ->
            when (result) {
                is ApiResponse.Loading -> {
                    binding.btnRegister.isEnabled = false
                    binding.progressBar.visibility = android.view.View.VISIBLE
                }
                is ApiResponse.Success -> {
                    binding.btnRegister.isEnabled = true
                    binding.progressBar.visibility = android.view.View.GONE

                    Toast.makeText(this, "Registration successful! Please check your email for verification.", Toast.LENGTH_LONG).show()
                    finish()
                }
                is ApiResponse.Error -> {
                    binding.btnRegister.isEnabled = true
                    binding.progressBar.visibility = android.view.View.GONE
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
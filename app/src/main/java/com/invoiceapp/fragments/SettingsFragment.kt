// fragments/SettingsFragment.kt
package com.invoiceapp.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.invoiceapp.activities.LoginActivity
import com.invoiceapp.activities.SettingsActivity
import com.invoiceapp.databinding.FragmentSettingsBinding
import com.invoiceapp.utils.SessionManager

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var sessionManager: SessionManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        sessionManager = SessionManager(requireContext())

        setupClickListeners()
        loadUserInfo()

        return binding.root
    }

    private fun setupClickListeners() {
        binding.btnCompanySettings.setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }

        binding.btnLogout.setOnClickListener {
            sessionManager.clearSession()
            val intent = Intent(requireContext(), LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            requireActivity().finish()
        }

        binding.btnAbout.setOnClickListener {
            showAboutDialog()
        }
    }

    private fun loadUserInfo() {
        val userName = sessionManager.getUserName()
        val userEmail = sessionManager.getUserEmail()

        binding.tvUserName.text = userName
        binding.tvUserEmail.text = userEmail
    }

    private fun showAboutDialog() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("About InvoiceApp")
            .setMessage("Version 1.0\n\nInvoice management app for businesses.\n\nCreated with ❤️")
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
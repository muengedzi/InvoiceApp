package com.invoiceapp.fragments

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.invoiceapp.activities.CreateRecurringTemplateActivity
import com.invoiceapp.adapters.RecurringTemplateAdapter
import com.invoiceapp.databinding.FragmentRecurringInvoicesBinding
import com.invoiceapp.models.RecurringTemplate
import com.invoiceapp.network.RetrofitClient
import com.invoiceapp.utils.SessionManager
import kotlinx.coroutines.launch

class RecurringInvoicesFragment : Fragment() {

    private var _binding: FragmentRecurringInvoicesBinding? = null
    private val binding get() = _binding!!
    private lateinit var sessionManager: SessionManager
    private lateinit var adapter: RecurringTemplateAdapter
    private var templatesList = mutableListOf<RecurringTemplate>()
    private var filteredList = mutableListOf<RecurringTemplate>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecurringInvoicesBinding.inflate(inflater, container, false)
        sessionManager = SessionManager(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupSearch()
        setupClickListeners()
        loadTemplates()
    }

    private fun setupRecyclerView() {
        adapter = RecurringTemplateAdapter(
            filteredList,
            onEditClick = { template -> editTemplate(template) },
            onGenerateClick = { template -> generateInvoiceNow(template) },
            onDeleteClick = { template -> deleteTemplate(template) },
            onToggleStatusClick = { template -> toggleTemplateStatus(template) }
        )

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@RecurringInvoicesFragment.adapter
        }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterTemplates(s?.toString() ?: "")
            }
        })
    }

    private fun filterTemplates(query: String) {
        val currentBinding = _binding ?: return
        val lowerQuery = query.lowercase().trim()
        filteredList.clear()
        if (lowerQuery.isEmpty()) {
            filteredList.addAll(templatesList)
        } else {
            for (template in templatesList) {
                if (template.templateName.lowercase().contains(lowerQuery) ||
                    (template.customerName?.lowercase()?.contains(lowerQuery) == true)
                ) {
                    filteredList.add(template)
                }
            }
        }
        adapter.notifyDataSetChanged()

        if (filteredList.isEmpty()) {
            currentBinding.tvEmpty.visibility = View.VISIBLE
            currentBinding.tvEmpty.text = if (templatesList.isEmpty()) "No templates found" else "No matches found"
            currentBinding.recyclerView.visibility = View.GONE
        } else {
            currentBinding.tvEmpty.visibility = View.GONE
            currentBinding.recyclerView.visibility = View.VISIBLE
        }
    }

    private fun setupClickListeners() {
        binding.fabAddRecurring.setOnClickListener {
            startActivity(Intent(requireContext(), CreateRecurringTemplateActivity::class.java))
        }

        binding.swipeRefresh.setOnRefreshListener {
            loadTemplates()
        }
    }

    private fun loadTemplates() {
        val currentBinding = _binding ?: return
        currentBinding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getRecurringTemplates()
                if (_binding == null) return@launch
                
                if (response.isSuccessful && response.body() != null) {
                    templatesList.clear()
                    templatesList.addAll(response.body()!!)
                    filterTemplates(binding.etSearch.text?.toString() ?: "")
                } else {
                    Toast.makeText(requireContext(), "Failed to load templates", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (_binding != null) {
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                _binding?.let {
                    it.progressBar.visibility = View.GONE
                    it.swipeRefresh.isRefreshing = false
                }
            }
        }
    }

    private fun editTemplate(template: RecurringTemplate) {
        val intent = Intent(requireContext(), CreateRecurringTemplateActivity::class.java)
        intent.putExtra("template_id", template.id)
        intent.putExtra("is_edit", true)
        startActivity(intent)
    }

    private fun generateInvoiceNow(template: RecurringTemplate) {
        AlertDialog.Builder(requireContext())
            .setTitle("Generate Invoice")
            .setMessage("Generate an invoice from '${template.templateName}' now?")
            .setPositiveButton("Generate") { _, _ ->
                lifecycleScope.launch {
                    try {
                        val response = RetrofitClient.apiService.generateInvoiceFromTemplate(template.id)
                        if (response.isSuccessful) {
                            Toast.makeText(requireContext(), "Invoice generated successfully!", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(requireContext(), "Failed to generate invoice", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteTemplate(template: RecurringTemplate) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Template")
            .setMessage("Delete '${template.templateName}'? This will not affect already generated invoices.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    try {
                        val response = RetrofitClient.apiService.deleteRecurringTemplate(template.id)
                        if (response.isSuccessful) {
                            Toast.makeText(requireContext(), "Template deleted", Toast.LENGTH_SHORT).show()
                            loadTemplates()
                        } else {
                            Toast.makeText(requireContext(), "Failed to delete template", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun toggleTemplateStatus(template: RecurringTemplate) {
        lifecycleScope.launch {
            try {
                val newStatus = if (template.status == "active") "inactive" else "active"
                
                val updateRequest = mapOf(
                    "customer_id" to template.customerId,
                    "template_name" to template.templateName,
                    "invoice_date_day" to template.invoiceDateDay,
                    "due_date_days" to template.dueDateDays,
                    "frequency" to template.frequency,
                    "status" to newStatus,
                    "items" to (template.items?.map {
                        mapOf(
                            "item_name" to it.itemName,
                            "description" to (it.description ?: ""),
                            "quantity" to it.quantity,
                            "unit_price" to it.unitPrice,
                            "discount_amount" to it.discountAmount,
                            "tax_rate" to it.taxRate
                        )
                    } ?: emptyList<Any>())
                )

                val response = RetrofitClient.apiService.updateRecurringTemplate(
                    template.id,
                    updateRequest
                )
                
                if (response.isSuccessful) {
                    loadTemplates()
                    Toast.makeText(requireContext(), "Template ${if (newStatus == "active") "activated" else "deactivated"}", Toast.LENGTH_SHORT).show()
                } else {
                    val errorBody = response.errorBody()?.string()
                    Toast.makeText(requireContext(), "Failed to update status: $errorBody", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadTemplates()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
package com.invoiceapp.activities

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.invoiceapp.R
import com.invoiceapp.databinding.ActivityTemplateSettingsBinding
import com.invoiceapp.models.TemplateInfo
import com.invoiceapp.network.ApiResponse
import com.invoiceapp.network.RetrofitClient
import com.invoiceapp.utils.InvoiceTemplateManager
import com.invoiceapp.utils.SessionManager
import kotlinx.coroutines.launch

class TemplateSettingsActivity : BaseActivity() {

    private lateinit var binding: ActivityTemplateSettingsBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var adapter: TemplateAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTemplateSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        setupToolbar()
        setupRecyclerView()
        loadCurrentTemplate()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Invoice Templates"
    }

    private fun setupRecyclerView() {
        adapter = TemplateAdapter(InvoiceTemplateManager.templates) { template ->
            showTemplatePreview(template)
        }

        binding.templatesRecyclerView.apply {
            layoutManager = GridLayoutManager(this@TemplateSettingsActivity, 2)
            adapter = this@TemplateSettingsActivity.adapter
        }
    }

    private fun loadCurrentTemplate() {
        // Load from backend to ensure it's in sync
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getUserTemplate()
                if (response.isSuccessful && response.body() != null) {
                    val templateId = response.body()!!.template
                    sessionManager.saveInvoiceTemplate(templateId)
                    val currentTemplate = InvoiceTemplateManager.getTemplateInfo(templateId)
                    binding.currentTemplateText.text = "Current: ${currentTemplate.name}"
                } else {
                    // Fallback to local
                    val currentTemplateId = sessionManager.getInvoiceTemplate()
                    val currentTemplate = InvoiceTemplateManager.getTemplateInfo(currentTemplateId)
                    binding.currentTemplateText.text = "Current: ${currentTemplate.name}"
                }
            } catch (e: Exception) {
                // Fallback to local
                val currentTemplateId = sessionManager.getInvoiceTemplate()
                val currentTemplate = InvoiceTemplateManager.getTemplateInfo(currentTemplateId)
                binding.currentTemplateText.text = "Current: ${currentTemplate.name}"
            }
        }
    }

    private fun showTemplatePreview(template: TemplateInfo) {
        AlertDialog.Builder(this)
            .setTitle(template.name)
            .setMessage("${template.description}\n\nApply this template to your invoices?")
            .setPositiveButton("Apply") { _, _ ->
                applyTemplate(template)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun applyTemplate(template: TemplateInfo) {
        // Save to backend first
        lifecycleScope.launch {
            try {
                // Show loading
                Toast.makeText(this@TemplateSettingsActivity, "Applying template...", Toast.LENGTH_SHORT).show()

                val response = RetrofitClient.apiService.updateUserTemplate(mapOf("template" to template.id))
                if (response.isSuccessful) {
                    // Save locally after successful backend update
                    sessionManager.saveInvoiceTemplate(template.id)
                    Toast.makeText(this@TemplateSettingsActivity, "Template '${template.name}' applied successfully!", Toast.LENGTH_LONG).show()
                    loadCurrentTemplate()
                    adapter.notifyDataSetChanged()
                } else {
                    val errorMsg = response.errorBody()?.string() ?: "Failed to apply template"
                    Toast.makeText(this@TemplateSettingsActivity, errorMsg, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@TemplateSettingsActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    // Adapter inner class
    inner class TemplateAdapter(
        private val templates: List<TemplateInfo>,
        private val onItemClick: (TemplateInfo) -> Unit
    ) : RecyclerView.Adapter<TemplateAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_template, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(templates[position])
        }

        override fun getItemCount(): Int = templates.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val templateName: TextView = itemView.findViewById(R.id.templateName)
            private val templateDesc: TextView = itemView.findViewById(R.id.templateDesc)
            private val templateColor: View = itemView.findViewById(R.id.templateColor)
            private val checkIcon: ImageView = itemView.findViewById(R.id.checkIcon)

            fun bind(template: TemplateInfo) {
                templateName.text = template.name
                templateDesc.text = template.description
                templateColor.setBackgroundColor(android.graphics.Color.parseColor(template.primaryColor))

                val currentTemplate = sessionManager.getInvoiceTemplate()
                checkIcon.visibility = if (currentTemplate == template.id) View.VISIBLE else View.GONE

                itemView.setOnClickListener { onItemClick(template) }
            }
        }
    }
}
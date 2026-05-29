package com.invoiceapp.activities

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.invoiceapp.R
import com.invoiceapp.databinding.ActivityTemplateSettingsBinding
import com.invoiceapp.models.InvoiceTemplate
import com.invoiceapp.models.InvoiceTemplates
import com.invoiceapp.network.RetrofitClient
import com.invoiceapp.utils.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        adapter = TemplateAdapter(InvoiceTemplates.templates) { template ->
            showTemplatePreview(template)
        }

        binding.templatesRecyclerView.apply {
            layoutManager = GridLayoutManager(this@TemplateSettingsActivity, 2)
            adapter = this@TemplateSettingsActivity.adapter
        }
    }

    private fun loadCurrentTemplate() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getUserTemplate()
                if (response.isSuccessful && response.body() != null) {
                    val templateId = response.body()!!.template
                    sessionManager.saveInvoiceTemplate(templateId)
                    val currentTemplate = InvoiceTemplates.getTemplate(templateId)
                    binding.currentTemplateText.text = "Current: ${currentTemplate.name}"
                } else {
                    val currentTemplateId = sessionManager.getInvoiceTemplate()
                    val currentTemplate = InvoiceTemplates.getTemplate(currentTemplateId)
                    binding.currentTemplateText.text = "Current: ${currentTemplate.name}"
                }
            } catch (e: Exception) {
                val currentTemplateId = sessionManager.getInvoiceTemplate()
                val currentTemplate = InvoiceTemplates.getTemplate(currentTemplateId)
                binding.currentTemplateText.text = "Current: ${currentTemplate.name}"
            }
        }
    }

    private fun showTemplatePreview(template: InvoiceTemplate) {
        // Show a larger preview dialog
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_template_preview, null)
        val previewImage = dialogView.findViewById<ImageView>(R.id.previewImage)
        val templateName = dialogView.findViewById<TextView>(R.id.templateName)
        val templateDesc = dialogView.findViewById<TextView>(R.id.templateDesc)
        val primaryColorText = dialogView.findViewById<TextView>(R.id.primaryColorText)
        val secondaryColorText = dialogView.findViewById<TextView>(R.id.secondaryColorText)

        templateName.text = template.name
        templateDesc.text = template.description
        primaryColorText.text = "Primary: ${template.primaryColor}"
        secondaryColorText.text = "Secondary: ${template.secondaryColor}"

        // Generate larger preview (400x560)
        lifecycleScope.launch(Dispatchers.IO) {
            val bitmap = template.getPreviewBitmap(this@TemplateSettingsActivity, 400, 560)
            withContext(Dispatchers.Main) {
                previewImage.setImageBitmap(bitmap)
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Template Preview")
            .setView(dialogView)
            .setPositiveButton("Apply Template") { _, _ ->
                applyTemplate(template)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun applyTemplate(template: InvoiceTemplate) {
        lifecycleScope.launch {
            try {
                Toast.makeText(this@TemplateSettingsActivity, "Applying template...", Toast.LENGTH_SHORT).show()

                val response = RetrofitClient.apiService.updateUserTemplate(mapOf("template" to template.id))
                if (response.isSuccessful) {
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

    inner class TemplateAdapter(
        private val templates: List<InvoiceTemplate>,
        private val onItemClick: (InvoiceTemplate) -> Unit
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
            private val templatePreviewImage: ImageView = itemView.findViewById(R.id.templatePreviewImage)
            private val templateName: TextView = itemView.findViewById(R.id.templateName)
            private val templateDesc: TextView = itemView.findViewById(R.id.templateDesc)
            private val templateColor: View = itemView.findViewById(R.id.templateColor)
            private val checkIcon: ImageView = itemView.findViewById(R.id.checkIcon)

            fun bind(template: InvoiceTemplate) {
                templateName.text = template.name
                templateDesc.text = template.description
                templateColor.setBackgroundColor(android.graphics.Color.parseColor(template.primaryColor))

                // Load preview image asynchronously
                itemView.post {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val bitmap = template.getPreviewBitmap(itemView.context, 200, 280)
                        withContext(Dispatchers.Main) {
                            templatePreviewImage.setImageBitmap(bitmap)
                        }
                    }
                }

                val currentTemplate = sessionManager.getInvoiceTemplate()
                checkIcon.visibility = if (currentTemplate == template.id) View.VISIBLE else View.GONE

                itemView.setOnClickListener { onItemClick(template) }
            }
        }
    }
}
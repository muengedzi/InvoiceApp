package com.invoiceapp.utils

import android.content.Context
import android.content.SharedPreferences
import com.invoiceapp.models.TemplateInfo

object InvoiceTemplateManager {
    private const val PREF_TEMPLATE = "invoice_template"
    private const val DEFAULT_TEMPLATE = "modern"

    val templates = listOf(
        TemplateInfo("modern", "Modern", "Clean and minimal design with bold typography", "#2196F3", "#FF9800"),
        TemplateInfo("classic", "Classic", "Traditional invoice layout with elegant styling", "#2C3E50", "#34495E"),
        TemplateInfo("corporate", "Corporate", "Professional design for businesses", "#1A237E", "#0D47A1"),
        TemplateInfo("creative", "Creative", "Bold and artistic design for creative businesses", "#9C27B0", "#E91E63"),
        TemplateInfo("minimal", "Minimal", "Simple and distraction-free layout", "#607D8B", "#78909C"),
        TemplateInfo("elegant", "Elegant", "Sophisticated design with refined details", "#800000", "#A52A2A")
    )

    fun saveTemplate(context: Context, templateId: String) {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_TEMPLATE, templateId).apply()
    }

    fun getSavedTemplate(context: Context): String {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        return prefs.getString(PREF_TEMPLATE, DEFAULT_TEMPLATE) ?: DEFAULT_TEMPLATE
    }

    fun getTemplateInfo(templateId: String): TemplateInfo {
        return templates.find { it.id == templateId } ?: templates[0]
    }
}
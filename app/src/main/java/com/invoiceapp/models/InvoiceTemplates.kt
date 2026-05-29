package com.invoiceapp.models

import android.content.Context
import android.graphics.Bitmap
import com.invoiceapp.utils.TemplatePreviewGenerator

data class InvoiceTemplate(
    val id: String,
    val name: String,
    val description: String,
    val layoutFile: String,
    val primaryColor: String,
    val secondaryColor: String
) {
    /**
     * Get preview bitmap for this template (from cache or generate new)
     */
    fun getPreviewBitmap(context: Context, width: Int = 200, height: Int = 280): Bitmap {
        // Try to get from cache first
        TemplatePreviewGenerator.getCachedPreview(context, id)?.let { return it }

        // Generate new preview
        val bitmap = TemplatePreviewGenerator.generatePreview(
            context, id, name, primaryColor, secondaryColor, width, height
        )

        // Save to cache
        TemplatePreviewGenerator.savePreviewToCache(context, id, bitmap)

        return bitmap
    }
}

object InvoiceTemplates {
    val templates = listOf(
        InvoiceTemplate(
            id = "modern",
            name = "Modern",
            description = "Clean and minimal design",
            layoutFile = "pdf_invoice.html",
            primaryColor = "#2196F3",
            secondaryColor = "#FF9800"
        ),
        InvoiceTemplate(
            id = "classic",
            name = "Classic",
            description = "Traditional elegant styling",
            layoutFile = "template_classic.html",
            primaryColor = "#2C3E50",
            secondaryColor = "#34495E"
        ),
        InvoiceTemplate(
            id = "corporate",
            name = "Corporate",
            description = "Professional design for businesses",
            layoutFile = "template_corporate.html",
            primaryColor = "#1A237E",
            secondaryColor = "#0D47A1"
        ),
        InvoiceTemplate(
            id = "creative",
            name = "Creative",
            description = "Bold and artistic design",
            layoutFile = "template_creative.html",
            primaryColor = "#9C27B0",
            secondaryColor = "#E91E63"
        ),
        InvoiceTemplate(
            id = "minimal",
            name = "Minimal",
            description = "Simple layout",
            layoutFile = "template_minimal.html",
            primaryColor = "#607D8B",
            secondaryColor = "#78909C"
        ),
        InvoiceTemplate(
            id = "elegant",
            name = "Elegant",
            description = "Sophisticated with refined details",
            layoutFile = "template_elegant.html",
            primaryColor = "#800000",
            secondaryColor = "#A52A2A"
        )
    )

    fun getTemplate(id: String): InvoiceTemplate {
        return templates.find { it.id == id } ?: templates[0]
    }
}
package com.invoiceapp.models

import com.invoiceapp.R
data class InvoiceTemplate(
    val id: String,
    val name: String,
    val description: String,
    val previewImage: Int,
    val layoutFile: String,
    val primaryColor: String,
    val secondaryColor: String
)

object InvoiceTemplates {
    val templates = listOf(
        InvoiceTemplate(
            id = "modern",
            name = "Modern",
            description = "Clean and minimal design with bold typography",
            previewImage = R.drawable.template_modern,
            layoutFile = "template_modern.html",
            primaryColor = "#2196F3",
            secondaryColor = "#FF9800"
        ),
        InvoiceTemplate(
            id = "classic",
            name = "Classic",
            description = "Traditional invoice layout with elegant styling",
            previewImage = R.drawable.template_classic,
            layoutFile = "template_classic.html",
            primaryColor = "#2C3E50",
            secondaryColor = "#34495E"
        ),
        InvoiceTemplate(
            id = "corporate",
            name = "Corporate",
            description = "Professional design for businesses",
            previewImage = R.drawable.template_corporate,
            layoutFile = "template_corporate.html",
            primaryColor = "#1A237E",
            secondaryColor = "#0D47A1"
        ),
        InvoiceTemplate(
            id = "creative",
            name = "Creative",
            description = "Bold and artistic design for creative businesses",
            previewImage = R.drawable.template_creative,
            layoutFile = "template_creative.html",
            primaryColor = "#9C27B0",
            secondaryColor = "#E91E63"
        ),
        InvoiceTemplate(
            id = "minimal",
            name = "Minimal",
            description = "Simple and distraction-free layout",
            previewImage = R.drawable.template_minimal,
            layoutFile = "template_minimal.html",
            primaryColor = "#607D8B",
            secondaryColor = "#78909C"
        ),
        InvoiceTemplate(
            id = "elegant",
            name = "Elegant",
            description = "Sophisticated design with refined details",
            previewImage = R.drawable.template_elegant,
            layoutFile = "template_elegant.html",
            primaryColor = "#800000",
            secondaryColor = "#A52A2A"
        )
    )

    fun getTemplate(id: String): InvoiceTemplate {
        return templates.find { it.id == id } ?: templates[0]
    }
}
package com.invoiceapp.models

data class CompanySettings(
    val id: Int? = null,
    val company_name: String? = null,
    val company_logo_url: String? = null,
    val company_address: String? = null,
    val company_phone: String? = null,
    val company_email: String? = null,
    val company_website: String? = null,
    val tax_rate_default: Double = 0.0,
    val currency_symbol: String = "$",
    val currency_code: String = "USD",
    val invoice_prefix: String = "INV-",
    val estimate_prefix: String = "EST-",
    val invoice_notes_default: String? = null,
    val terms_conditions_default: String? = null,
    val footer_text_default: String? = null,
    val payment_terms_default: String? = null
)
// models/CompanySettings.kt
package com.invoiceapp.models

import com.google.gson.annotations.SerializedName

data class CompanySettings(
    @SerializedName("id")
    val id: Int? = null,

    @SerializedName("company_name")
    val company_name: String? = null,

    @SerializedName("company_logo_url")
    val company_logo_url: String? = null,

    @SerializedName("company_address")  // Single field in database
    val company_address: String? = null,

    @SerializedName("company_phone")
    val company_phone: String? = null,

    @SerializedName("company_email")
    val company_email: String? = null,

    @SerializedName("company_website")
    val company_website: String? = null,

    @SerializedName("tax_rate_default")
    val tax_rate_default: Double = 0.0,

    @SerializedName("currency_symbol")
    val currency_symbol: String = "$",

    @SerializedName("currency_code")
    val currency_code: String = "USD",

    @SerializedName("invoice_prefix")
    val invoice_prefix: String = "INV-",

    @SerializedName("estimate_prefix")
    val estimate_prefix: String = "EST-",

    @SerializedName("invoice_notes_default")
    val invoice_notes_default: String? = null,

    @SerializedName("terms_conditions_default")
    val terms_conditions_default: String? = null,

    @SerializedName("footer_text_default")
    val footer_text_default: String? = null,

    @SerializedName("payment_terms_default")
    val payment_terms_default: String? = null
)
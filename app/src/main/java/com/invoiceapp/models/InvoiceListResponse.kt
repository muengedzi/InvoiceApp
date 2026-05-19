package com.invoiceapp.models

import com.google.gson.annotations.SerializedName

data class InvoiceListResponse(
    @SerializedName("invoices")
    val invoices: List<Invoice>? = emptyList(),

    @SerializedName("total")
    val total: Int = 0,

    @SerializedName("page")
    val page: Int = 1,

    @SerializedName("per_page")
    val perPage: Int = 20,

    @SerializedName("total_pages")
    val totalPages: Int = 0
)
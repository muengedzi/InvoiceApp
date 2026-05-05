package com.invoiceapp.models

import com.google.gson.annotations.SerializedName

data class PaginatedResponse<T>(
    @SerializedName("customers")
    val customers: List<T>? = null,

    @SerializedName("invoices")
    val invoices: List<T>? = null,

    @SerializedName("expenses")
    val expenses: List<T>? = null,

    @SerializedName("total")
    val total: Int,

    @SerializedName("page")
    val page: Int,

    @SerializedName("per_page")
    val perPage: Int,

    @SerializedName("total_pages")
    val totalPages: Int
)
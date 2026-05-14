package com.invoiceapp.models

import com.google.gson.annotations.SerializedName

data class InvoiceItemMaster(
    @SerializedName("id")
    val id: Int,

    @SerializedName("item_name")
    val itemName: String,

    @SerializedName("description")
    val description: String? = null,

    @SerializedName("unit_price")
    val unitPrice: Double,

    @SerializedName("tax_rate")
    val taxRate: Double = 0.0,

    @SerializedName("is_active")
    val isActive: Boolean = true,

    @SerializedName("category_id")
    val categoryId: Int? = null,

    @SerializedName("category_name")
    val categoryName: String? = null,

    @SerializedName("created_at")
    val createdAt: String? = null
)

data class InvoiceItemMasterListResponse(
    @SerializedName("items")
    val items: List<InvoiceItemMaster>,

    @SerializedName("total")
    val total: Int,

    @SerializedName("page")
    val page: Int,

    @SerializedName("per_page")
    val perPage: Int,

    @SerializedName("total_pages")
    val totalPages: Int
)

data class InvoiceItemMasterRequest(
    @SerializedName("item_name")
    val itemName: String,

    @SerializedName("description")
    val description: String? = null,

    @SerializedName("unit_price")
    val unitPrice: Double,

    @SerializedName("tax_rate")
    val taxRate: Double = 0.0,

    @SerializedName("category_id")
    val categoryId: Int? = null
)

data class InvoiceItemCategory(
    @SerializedName("id")
    val id: Int,

    @SerializedName("category_name")
    val categoryName: String,

    @SerializedName("is_default")
    val isDefault: Boolean = false,

    @SerializedName("sort_order")
    val sortOrder: Int = 0
)

data class InvoiceItemCategoryRequest(
    @SerializedName("category_name")
    val categoryName: String
)
package com.invoiceapp.models

import com.google.gson.annotations.SerializedName
data class RecurringTemplate(
    @SerializedName("id") val id: Int,
    @SerializedName("user_id") val userId: Int,
    @SerializedName("customer_id") val customerId: Int,
    @SerializedName("template_name") val templateName: String,
    @SerializedName("invoice_date_day") val invoiceDateDay: Int,
    @SerializedName("due_date_days") val dueDateDays: Int,
    @SerializedName("frequency") val frequency: String,
    @SerializedName("status") val status: String,
    @SerializedName("tax_type") val taxType: String = "inclusive",
    @SerializedName("customer_name") val customerName: String? = null,
    @SerializedName("customer_email") val customerEmail: String? = null,
    @SerializedName("customer_phone") val customerPhone: String? = null,
    @SerializedName("items") val items: List<RecurringTemplateItem>? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    val totalAmount: Double = 0.0
)
data class RecurringTemplateItem(
    @SerializedName("id") val id: Int,
    @SerializedName("item_name") val itemName: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("quantity") val quantity: Double,
    @SerializedName("unit_price") val unitPrice: Double,
    @SerializedName("discount_amount") val discountAmount: Double = 0.0,
    @SerializedName("tax_rate") val taxRate: Double = 0.0,
    @SerializedName("sort_order") val sortOrder: Int = 0
)
data class RecurringTemplateRequest(
    @SerializedName("customer_id") val customerId: Int,
    @SerializedName("template_name") val templateName: String,
    @SerializedName("invoice_date_day") val invoiceDateDay: Int,
    @SerializedName("due_date_days") val dueDateDays: Int,
    @SerializedName("frequency") val frequency: String,
    @SerializedName("tax_type") val taxType: String = "inclusive",
    @SerializedName("items") val items: List<InvoiceItemRequest>
)
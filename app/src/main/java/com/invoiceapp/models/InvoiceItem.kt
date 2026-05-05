package com.invoiceapp.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import com.google.gson.annotations.SerializedName

@Parcelize
data class InvoiceItem(
    @SerializedName("id")
    val id: Int? = null,

    @SerializedName("item_name")
    val itemName: String,

    @SerializedName("description")
    val description: String? = null,

    @SerializedName("quantity")
    val quantity: Double,

    @SerializedName("unit_price")
    val unitPrice: Double,

    @SerializedName("discount_amount")
    val discountAmount: Double = 0.0,

    @SerializedName("tax_rate")
    val taxRate: Double = 0.0,

    @SerializedName("tax_amount")
    val taxAmount: Double = 0.0,

    @SerializedName("total_amount")
    val totalAmount: Double,

    @SerializedName("sort_order")
    val sortOrder: Int = 0
) : Parcelable
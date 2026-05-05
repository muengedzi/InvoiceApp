package com.invoiceapp.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import com.google.gson.annotations.SerializedName

@Parcelize
data class Payment(
    @SerializedName("id")
    val id: Int,

    @SerializedName("payment_number")
    val paymentNumber: String,

    @SerializedName("payment_date")
    val paymentDate: String,

    @SerializedName("amount")
    val amount: Double,

    @SerializedName("payment_method")
    val paymentMethod: String,

    @SerializedName("reference_number")
    val referenceNumber: String? = null,

    @SerializedName("notes")
    val notes: String? = null,

    @SerializedName("status")
    val status: String
) : Parcelable
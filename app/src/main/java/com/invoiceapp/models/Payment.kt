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
    val status: String,  // 'completed' or 'reversed'

    @SerializedName("created_at")
    val createdAt: String? = null,

    @SerializedName("reversed_at")
    val reversedAt: String? = null,

    @SerializedName("reversal_reason")
    val reversalReason: String? = null
) : Parcelable

data class PaymentListResponse(
    @SerializedName("payments")
    val payments: List<Payment>,

    @SerializedName("total_payments")
    val totalPayments: Int,

    @SerializedName("total_amount")
    val totalAmount: Double
)

// Helper extension function
fun Payment.isReversed(): Boolean = status == "reversed"
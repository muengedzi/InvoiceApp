package com.invoiceapp.models

import com.google.gson.annotations.SerializedName

data class Invoice(
    val id: Int,
    val invoice_number: String,
    val invoice_date: String,
    val due_date: String,
    val status: String,
    val subtotal: Double,
    val tax_amount: Double,
    val discount_amount: Double,
    val shipping_fee: Double,
    val total_amount: Double,
    val amount_paid: Double,
    val balance_due: Double,
    val customer_name: String? = null,
    val customer_email: String? = null,
    val customer_phone: String? = null,
    val customer_id: Int? = null,
    val po_number: String? = null,
    val notes: String? = null,
    val terms_conditions: String? = null,
    val footer_text: String? = null,
    val is_recurring: Boolean = false,
    val recurring_frequency: String? = null,
    val recurring_day: Int? = null,
    val recurring_next_send_date: String? = null,
    val recurring_active: Boolean = true,
    val items: List<InvoiceItem>? = null,
    val payments: List<Payment>? = null,
    @SerializedName("tax_rate")
    val taxRate: Double = 0.0,
    val tax_type: String = "exclusive"
)

data class InvoiceItemRequest(
    val item_name: String,
    val description: String? = null,
    val quantity: Double,
    val unit_price: Double,
    val discount_amount: Double = 0.0,
    val tax_rate: Double = 0.0
)

data class InvoiceRequest(
    val customer_id: Int,
    val invoice_date: String,
    val due_date: String? = null,
    val po_number: String? = null,
    val tax_rate: Double = 0.0,
    val discount_amount: Double = 0.0,
    val discount_type: String = "fixed",
    val shipping_fee: Double = 0.0,
    val notes: String? = null,
    val terms_conditions: String? = null,
    val footer_text: String? = null,
    val is_recurring: Boolean = false,
    val recurring_frequency: String? = null,
    val recurring_day: Int? = null,
    val recurring_active: Boolean = true,
    val items: List<InvoiceItemRequest>,
    val status: String? = null,
    val tax_type: String = "exclusive"
)

data class PaymentRequest(
    val amount: Double,
    val payment_method: String = "cash",
    val payment_date: String,
    val reference_number: String? = null,
    val notes: String? = null
)

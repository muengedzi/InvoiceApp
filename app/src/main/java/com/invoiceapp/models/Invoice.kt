package com.invoiceapp.models

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
    val customer_id: Int? = null,
    val po_number: String? = null,
    val notes: String? = null,
    val terms_conditions: String? = null,
    val footer_text: String? = null,
    val items: List<InvoiceItem>? = null,
    val payments: List<Payment>? = null
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
    val due_date: String,
    val po_number: String? = null,
    val tax_rate: Double = 0.0,
    val discount_amount: Double = 0.0,
    val discount_type: String = "fixed",
    val shipping_fee: Double = 0.0,
    val notes: String? = null,
    val terms_conditions: String? = null,
    val footer_text: String? = null,
    val items: List<InvoiceItemRequest>
)

data class PaymentRequest(
    val amount: Double,
    val payment_method: String = "cash",
    val payment_date: String,
    val reference_number: String? = null,
    val notes: String? = null
)

data class InvoiceListResponse(
    val invoices: List<Invoice>,
    val total: Int,
    val page: Int,
    val per_page: Int,
    val total_pages: Int
)
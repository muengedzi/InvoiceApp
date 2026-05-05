package com.invoiceapp.models

data class Customer(
    val id: Int,
    val customer_name: String,
    val email: String,
    val phone: String? = null,
    val address: String? = null,
    val city: String? = null,
    val state: String? = null,
    val postal_code: String? = null,
    val country: String = "USA",
    val tax_exempt: Boolean = false,
    val notes: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null
)

data class CustomerRequest(
    val customer_name: String,
    val email: String,
    val phone: String? = null,
    val address: String? = null,
    val city: String? = null,
    val state: String? = null,
    val postal_code: String? = null,
    val country: String = "USA",
    val tax_exempt: Boolean = false,
    val notes: String? = null
)

data class CustomerListResponse(
    val customers: List<Customer>,
    val total: Int,
    val page: Int,
    val per_page: Int,
    val total_pages: Int
)
package com.invoiceapp.models

import com.google.gson.annotations.SerializedName

data class InvoiceCommunication(
    @SerializedName("id")
    val id: Int,

    @SerializedName("invoice_id")
    val invoiceId: Int,

    @SerializedName("invoice_number")
    val invoiceNumber: String? = null,

    @SerializedName("customer_id")
    val customerId: Int,

    @SerializedName("customer_name")
    val customerName: String? = null,

    @SerializedName("customer_email")
    val customerEmail: String? = null,

    @SerializedName("communication_type")
    val communicationType: String, // 'sent', 'follow_up', 'reply'

    @SerializedName("subject")
    val subject: String,

    @SerializedName("message_body")
    val messageBody: String,

    @SerializedName("sent_at")
    val sentAt: String,

    @SerializedName("is_read_by_customer")
    val isReadByCustomer: Boolean = false,

    @SerializedName("read_at")
    val readAt: String? = null,

    @SerializedName("customer_replied")
    val customerReplied: Boolean = false,

    @SerializedName("reply_at")
    val replyAt: String? = null,

    @SerializedName("reply_body")
    val replyBody: String? = null
)

data class SendInvoiceEmailRequest(
    @SerializedName("invoice_id")
    val invoiceId: Int,

    @SerializedName("subject")
    val subject: String,

    @SerializedName("message")
    val message: String,

    @SerializedName("is_follow_up")
    val isFollowUp: Boolean = false
)

data class SendReplyRequest(
    @SerializedName("communication_id")
    val communicationId: Int,

    @SerializedName("subject")
    val subject: String,

    @SerializedName("message")
    val message: String
)

data class InvoiceFollowUp(
    @SerializedName("id")
    val id: Int,

    @SerializedName("invoice_id")
    val invoiceId: Int,

    @SerializedName("invoice_number")
    val invoiceNumber: String? = null,

    @SerializedName("customer_name")
    val customerName: String? = null,

    @SerializedName("follow_up_date")
    val followUpDate: String,

    @SerializedName("follow_up_type")
    val followUpType: String,

    @SerializedName("message_sent")
    val messageSent: Boolean = false,

    @SerializedName("message_sent_at")
    val messageSentAt: String? = null,

    @SerializedName("customer_responded")
    val customerResponded: Boolean = false,

    @SerializedName("responded_at")
    val respondedAt: String? = null,

    @SerializedName("notes")
    val notes: String? = null,

    @SerializedName("completed")
    val completed: Boolean = false
)

data class InvoiceCommunicationSummary(
    @SerializedName("total_sent")
    val totalSent: Int,

    @SerializedName("opened")
    val opened: Int,

    @SerializedName("replied")
    val replied: Int,

    @SerializedName("pending_follow_ups")
    val pendingFollowUps: Int
)

data class CustomerCommunicationThread(
    @SerializedName("customer_id")
    val customerId: Int,

    @SerializedName("customer_name")
    val customerName: String,

    @SerializedName("customer_email")
    val customerEmail: String,

    @SerializedName("communications")
    val communications: List<InvoiceCommunication>
)
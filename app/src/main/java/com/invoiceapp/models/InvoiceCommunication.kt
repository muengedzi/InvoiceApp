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

    @SerializedName("customer_id")
    val customerId: Int? = null,

    @SerializedName("customer_name")
    val customerName: String? = null,

    @SerializedName("follow_up_date")
    val followUpDate: String,

    @SerializedName("follow_up_type")
    val followUpType: String,

    @SerializedName("balance_due")
    val balanceDue: Double? = null,

    @SerializedName("days_overdue")
    val daysOverdue: Int? = null,

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
    val pendingFollowUps: Int,

    @SerializedName("recent_replies")
    val recentReplies: List<RecentCustomerReply> = emptyList()
)

data class RecentCustomerReply(
    @SerializedName("invoice_id")
    val invoiceId: Int,

    @SerializedName("invoice_number")
    val invoiceNumber: String? = null,

    @SerializedName("customer_name")
    val customerName: String? = null,

    @SerializedName("reply_at")
    val replyAt: String? = null,

    @SerializedName("reply_body")
    val replyBody: String? = null
)

data class CustomerCommunicationThread(
    @SerializedName("customer_id")
    val customerId: Int,

    @SerializedName("customer_name")
    val customerName: String,

    @SerializedName("customer_email")
    val customerEmail: String? = null,

    @SerializedName("customer_phone")
    val customerPhone: String? = null,

    @SerializedName("communications")
    val communications: List<InvoiceCommunication>,

    @SerializedName("summary")
    val summary: CommunicationSummary? = null
)

data class CommunicationSummary(
    @SerializedName("total_communications")
    val totalCommunications: Int,

    @SerializedName("emails_sent")
    val emailsSent: Int,

    @SerializedName("customer_replies")
    val customerReplies: Int,

    @SerializedName("response_rate")
    val responseRate: Double
)

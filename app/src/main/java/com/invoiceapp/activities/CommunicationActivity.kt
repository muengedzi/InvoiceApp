package com.invoiceapp.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.invoiceapp.R
import com.invoiceapp.adapters.CommunicationAdapter
import com.invoiceapp.databinding.ActivityCommunicationBinding
import com.invoiceapp.models.InvoiceCommunication
import com.invoiceapp.models.SendInvoiceEmailRequest
import com.invoiceapp.models.SendReplyRequest
import com.invoiceapp.network.RetrofitClient
import com.invoiceapp.utils.SessionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.text.NumberFormat
import java.util.Locale

class CommunicationActivity : BaseActivity() {

    private lateinit var binding: ActivityCommunicationBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var adapter: CommunicationAdapter
    private var communications = mutableListOf<InvoiceCommunication>()
    private var invoiceId: Int = -1
    private var invoiceNumber: String = ""
    private var customerName: String = ""
    private var customerEmail: String = ""
    private var customerPhone: String = ""
    private var balanceDue: Double = 0.0
    private var communicationFilter: String? = null
    private var communicationTitle: String = "Communications"
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "ZA")).apply {
        currency = java.util.Currency.getInstance("ZAR")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCommunicationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)
        invoiceId = intent.getIntExtra("invoice_id", -1)
        invoiceNumber = intent.getStringExtra("invoice_number") ?: ""
        customerName = intent.getStringExtra("customer_name") ?: ""
        customerEmail = intent.getStringExtra("customer_email") ?: ""
        customerPhone = intent.getStringExtra("customer_phone") ?: ""
        balanceDue = intent.getDoubleExtra("balance_due", 0.0)
        communicationFilter = intent.getStringExtra("communication_filter")
        communicationTitle = intent.getStringExtra("communication_title") ?: "Communications"

        setupToolbar()
        setupRecyclerView()
        setupClickListeners()
        loadCommunications()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = if (communicationFilter != null) {
            communicationTitle
        } else {
            "Communication - $invoiceNumber"
        }
    }

    private fun setupRecyclerView() {
        adapter = CommunicationAdapter(communications) { communication ->
            if (communication.customerReplied && communication.replyBody != null) {
                showReplyDialog(communication)
            }
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupClickListeners() {
        if (communicationFilter != null) {
            binding.communicationActions.visibility = View.GONE
            return
        }

        binding.btnSendMessage.setOnClickListener {
            showSendMessageDialog()
        }
        binding.btnSendFollowUp.setOnClickListener {
            showFollowUpDialog()
        }
    }

    private fun loadCommunications() {
        lifecycleScope.launch {
            try {
                val response = if (communicationFilter != null) {
                    RetrofitClient.apiService.getAllInvoiceCommunications(communicationFilter)
                } else {
                    RetrofitClient.apiService.getInvoiceCommunications(invoiceId)
                }
                if (response.isSuccessful && response.body() != null) {
                    communications.clear()
                    communications.addAll(response.body()!!)
                    adapter.updateList(communications)

                    if (communications.isEmpty()) {
                        binding.tvEmptyView.visibility = View.VISIBLE
                        binding.tvEmptyView.text = if (communicationFilter != null) {
                            "No ${communicationTitle.lowercase()} found."
                        } else {
                            "No communications yet.\nSend a message to get started."
                        }
                        binding.recyclerView.visibility = View.GONE
                    } else {
                        binding.tvEmptyView.visibility = View.GONE
                        binding.recyclerView.visibility = View.VISIBLE
                        binding.recyclerView.startAnimation(AnimationUtils.loadAnimation(this@CommunicationActivity, R.anim.fade_in_up))
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@CommunicationActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showSendMessageDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_send_email, null)
        val etSubject = dialogView.findViewById<EditText>(R.id.etSubject)
        val etMessage = dialogView.findViewById<EditText>(R.id.etMessage)

        etSubject.setText("Invoice #$invoiceNumber - Payment Reminder")

        AlertDialog.Builder(this)
            .setTitle("Send Email to $customerName")
            .setView(dialogView)
            .setPositiveButton("Send") { _, _ ->
                val subject = etSubject.text.toString().trim()
                val message = etMessage.text.toString().trim()

                if (subject.isEmpty() || message.isEmpty()) {
                    Toast.makeText(this, "Please fill in subject and message", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                sendEmail(subject, message, false)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showFollowUpDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_send_email, null)
        val etSubject = dialogView.findViewById<EditText>(R.id.etSubject)
        val etMessage = dialogView.findViewById<EditText>(R.id.etMessage)

        etSubject.setText("Follow-up: Invoice #$invoiceNumber payment reminder")
        etMessage.setText(buildFollowUpMessage())

        AlertDialog.Builder(this)
            .setTitle("Send Follow-up Email & WhatsApp")
            .setMessage("This will email the customer and then open WhatsApp with the same message ready to send.")
            .setView(dialogView)
            .setPositiveButton("Send") { _, _ ->
                val subject = etSubject.text.toString().trim()
                val message = etMessage.text.toString().trim()

                if (subject.isEmpty() || message.isEmpty()) {
                    Toast.makeText(this, "Please fill in subject and message", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                sendEmail(subject, message, true, openWhatsAppAfterSend = true)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showReplyDialog(communication: InvoiceCommunication) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_send_email, null)
        val etSubject = dialogView.findViewById<EditText>(R.id.etSubject)
        val etMessage = dialogView.findViewById<EditText>(R.id.etMessage)

        etSubject.setText("Re: ${communication.subject}")

        AlertDialog.Builder(this)
            .setTitle("Reply to ${communication.customerName ?: customerName}")
            .setView(dialogView)
            .setPositiveButton("Send") { _, _ ->
                val subject = etSubject.text.toString().trim()
                val message = etMessage.text.toString().trim()

                if (subject.isEmpty() || message.isEmpty()) {
                    Toast.makeText(this, "Please fill in subject and message", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                sendReply(communication.id, subject, message)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendEmail(subject: String, message: String, isFollowUp: Boolean, openWhatsAppAfterSend: Boolean = false) {
        lifecycleScope.launch {
            try {
                setProcessing(true, if (isFollowUp) "Sending follow-up..." else "Sending message...")
                val request = SendInvoiceEmailRequest(
                    invoiceId = invoiceId,
                    subject = subject,
                    message = message,
                    isFollowUp = isFollowUp
                )
                val response = RetrofitClient.apiService.sendInvoiceEmail(request)
                if (response.isSuccessful && response.body() != null) {
                    Toast.makeText(this@CommunicationActivity, "Email sent successfully", Toast.LENGTH_SHORT).show()
                    showProcessingSuccess(if (isFollowUp) "Follow-up sent" else "Message sent")
                    if (openWhatsAppAfterSend) {
                        openWhatsAppMessage(message)
                    }
                    delay(450)
                    loadCommunications()
                } else {
                    Toast.makeText(this@CommunicationActivity, "Failed to send email", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@CommunicationActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                setProcessing(false)
            }
        }
    }

    private fun buildFollowUpMessage(): String {
        val balance = currencyFormat.format(balanceDue)
        return "Dear ${customerName.ifBlank { "Customer" }},\n\n" +
            "This is a friendly reminder that invoice #$invoiceNumber has an outstanding balance of $balance. " +
            "Please arrange payment at your earliest convenience.\n\n" +
            "Please contact us if payment has already been made or if you need any assistance.\n\nThank you."
    }

    private fun openWhatsAppMessage(message: String) {
        val phone = normalizeWhatsAppPhone(customerPhone)
        if (phone.isBlank()) {
            Toast.makeText(this, "No customer phone number found for WhatsApp", Toast.LENGTH_LONG).show()
            return
        }

        val encodedMessage = URLEncoder.encode(message, "UTF-8")
        val whatsappUri = Uri.parse("https://wa.me/$phone?text=$encodedMessage")
        val intent = Intent(Intent.ACTION_VIEW, whatsappUri)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to open WhatsApp: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun normalizeWhatsAppPhone(rawPhone: String?): String {
        var digits = rawPhone.orEmpty().filter { it.isDigit() }
        if (digits.startsWith("00")) {
            digits = digits.drop(2)
        }
        if (digits.startsWith("0") && digits.length == 10) {
            digits = "27${digits.drop(1)}"
        }
        return digits
    }

    private fun sendReply(communicationId: Int, subject: String, message: String) {
        lifecycleScope.launch {
            try {
                setProcessing(true, "Sending reply...")
                val request = SendReplyRequest(
                    communicationId = communicationId,
                    subject = subject,
                    message = message
                )
                val response = RetrofitClient.apiService.sendReply(request)
                if (response.isSuccessful && response.body() != null) {
                    Toast.makeText(this@CommunicationActivity, "Reply sent successfully", Toast.LENGTH_SHORT).show()
                    showProcessingSuccess("Reply sent")
                    delay(450)
                    loadCommunications()
                } else {
                    Toast.makeText(this@CommunicationActivity, "Failed to send reply", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@CommunicationActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                setProcessing(false)
            }
        }
    }

    private fun setProcessing(show: Boolean, message: String = "") {
        binding.cardProcessing.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            binding.tvProcessingMessage.text = message
            binding.cardProcessing.alpha = 0f
            binding.cardProcessing.translationY = -12f
            binding.cardProcessing.animate().alpha(1f).translationY(0f).setDuration(180).start()
        }
        binding.btnSendMessage.isEnabled = !show
        binding.btnSendFollowUp.isEnabled = !show
    }

    private fun showProcessingSuccess(message: String) {
        binding.cardProcessing.visibility = View.VISIBLE
        binding.tvProcessingMessage.text = message
        binding.cardProcessing.animate()
            .scaleX(1.02f)
            .scaleY(1.02f)
            .setDuration(120)
            .withEndAction {
                binding.cardProcessing.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
            }
            .start()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

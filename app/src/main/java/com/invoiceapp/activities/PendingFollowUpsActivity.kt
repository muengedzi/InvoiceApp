package com.invoiceapp.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.invoiceapp.R
import com.invoiceapp.adapters.PendingFollowUpAdapter
import com.invoiceapp.databinding.ActivityPendingFollowUpsBinding
import com.invoiceapp.models.InvoiceFollowUp
import com.invoiceapp.models.SendInvoiceEmailRequest
import com.invoiceapp.network.RetrofitClient
import com.invoiceapp.utils.SessionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.text.NumberFormat
import java.util.Locale

class PendingFollowUpsActivity : BaseActivity() {

    private lateinit var binding: ActivityPendingFollowUpsBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var adapter: PendingFollowUpAdapter
    private var followUps = mutableListOf<InvoiceFollowUp>()
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "ZA")).apply {
        currency = java.util.Currency.getInstance("ZAR")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPendingFollowUpsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        setupToolbar()
        setupRecyclerView()
        loadPendingFollowUps()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Pending Follow-ups"
    }

    private fun setupRecyclerView() {
        adapter = PendingFollowUpAdapter(
            followUps,
            onItemClick = { followUp ->
                showFollowUpActionsDialog(followUp)
            },
            onSendFollowUpClick = { followUp ->
                sendFollowUpEmail(followUp)
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun loadPendingFollowUps() {
        binding.progressBar.visibility = View.VISIBLE
        binding.recyclerView.visibility = View.GONE
        binding.tvEmptyView.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getPendingFollowUps()
                if (response.isSuccessful && response.body() != null) {
                    followUps.clear()
                    followUps.addAll(response.body()!!)
                    adapter.updateList(followUps)

                    if (followUps.isEmpty()) {
                        binding.tvEmptyView.visibility = View.VISIBLE
                        binding.recyclerView.visibility = View.GONE
                    } else {
                        binding.tvEmptyView.visibility = View.GONE
                        binding.recyclerView.visibility = View.VISIBLE
                        binding.recyclerView.startAnimation(AnimationUtils.loadAnimation(this@PendingFollowUpsActivity, R.anim.fade_in_up))
                    }
                } else {
                    Toast.makeText(this@PendingFollowUpsActivity, "Failed to load follow-ups", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@PendingFollowUpsActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun showFollowUpActionsDialog(followUp: InvoiceFollowUp) {
        val options = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        // View Invoice
        options.add("View Invoice")
        actions.add { viewInvoice(followUp.invoiceId) }

        // Send Follow-up. Keep this available even after a previous reminder was sent.
        options.add("Send Follow-up Email & WhatsApp")
        actions.add { sendFollowUpEmail(followUp) }

        // Mark as Sent (if not sent)
        if (!followUp.messageSent) {
            options.add("Mark as Sent")
            actions.add { markAsSent(followUp) }
        }

        // Mark as Completed
        if (!followUp.completed) {
            options.add("Mark as Completed")
            actions.add { markAsCompleted(followUp) }
        }

        options.add("Cancel")
        actions.add({})

        AlertDialog.Builder(this)
            .setTitle("Follow-up Options")
            .setMessage("Invoice #${followUp.invoiceNumber}\nCustomer: ${followUp.customerName}\nOverdue: ${followUp.daysOverdue ?: 0} days")
            .setItems(options.toTypedArray()) { _, which ->
                actions[which]()
            }
            .show()
    }

    private fun viewInvoice(invoiceId: Int) {
        val intent = Intent(this, InvoiceDetailActivity::class.java)
        intent.putExtra("invoice_id", invoiceId)
        startActivity(intent)
    }

    private fun sendFollowUpEmail(followUp: InvoiceFollowUp) {
        val subject = "Follow-up: Invoice #${followUp.invoiceNumber ?: followUp.invoiceId} payment reminder"
        val defaultMessage = buildFollowUpMessage(followUp)

        val dialogLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
        }
        val subjectInput = EditText(this).apply {
            hint = "Subject"
            setText(subject)
            setSingleLine(true)
        }
        val messageInput = EditText(this).apply {
            hint = "Message"
            setText(defaultMessage)
            minLines = 5
            gravity = android.view.Gravity.TOP
        }
        dialogLayout.addView(subjectInput)
        dialogLayout.addView(messageInput)

        AlertDialog.Builder(this)
            .setTitle("Send Follow-up")
            .setMessage("This will email the customer and then open WhatsApp with the same message ready to send.")
            .setView(dialogLayout)
            .setPositiveButton("Send") { _, _ ->
                val finalSubject = subjectInput.text.toString().trim()
                val finalMessage = messageInput.text.toString().trim()
                if (finalSubject.isEmpty() || finalMessage.isEmpty()) {
                    Toast.makeText(this, "Please enter a subject and message", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                sendFollowUpMessage(followUp, finalSubject, finalMessage)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendFollowUpMessage(followUp: InvoiceFollowUp, subject: String, message: String) {
        lifecycleScope.launch {
            try {
                setProcessing(true, "Sending follow-up email...")
                val request = SendInvoiceEmailRequest(
                    invoiceId = followUp.invoiceId,
                    subject = subject,
                    message = message,
                    isFollowUp = true
                )
                val response = RetrofitClient.apiService.sendInvoiceEmail(request)
                if (response.isSuccessful && response.body() != null) {
                    showProcessingSuccess("Follow-up email sent")
                    Toast.makeText(this@PendingFollowUpsActivity, "Follow-up email sent", Toast.LENGTH_SHORT).show()
                    openWhatsAppFollowUp(followUp, message)
                    delay(450)
                    loadPendingFollowUps()
                } else {
                    Toast.makeText(this@PendingFollowUpsActivity, "Failed to send follow-up email", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@PendingFollowUpsActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                setProcessing(false)
            }
        }
    }

    private fun buildFollowUpMessage(followUp: InvoiceFollowUp): String {
        val invoiceNumber = followUp.invoiceNumber ?: followUp.invoiceId.toString()
        val balance = currencyFormat.format(followUp.balanceDue ?: 0.0)
        val daysOverdue = followUp.daysOverdue ?: 0
        val overdueSentence = if (daysOverdue > 0) {
            "The invoice is currently $daysOverdue day(s) overdue.\n\n"
        } else {
            "Please arrange payment at your earliest convenience.\n\n"
        }
        return "Dear ${followUp.customerName ?: "Customer"},\n\n" +
            "This is a friendly reminder that invoice #$invoiceNumber has an outstanding balance of $balance. " +
            overdueSentence +
            "Please contact us if payment has already been made or if you need any assistance.\n\nThank you."
    }

    private fun openWhatsAppFollowUp(followUp: InvoiceFollowUp, message: String) {
        val phone = normalizeWhatsAppPhone(followUp.customerPhone)
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

    private fun markAsSent(followUp: InvoiceFollowUp) {
        lifecycleScope.launch {
            try {
                setProcessing(true, "Marking follow-up as sent...")
                val response = RetrofitClient.apiService.markFollowUpSent(followUp.id)
                if (response.isSuccessful && response.body() != null) {
                    Toast.makeText(this@PendingFollowUpsActivity, "Follow-up marked as sent", Toast.LENGTH_SHORT).show()
                    showProcessingSuccess("Follow-up updated")
                    delay(450)
                    loadPendingFollowUps()
                } else {
                    Toast.makeText(this@PendingFollowUpsActivity, "Failed to mark as sent", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@PendingFollowUpsActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                setProcessing(false)
            }
        }
    }

    private fun markAsCompleted(followUp: InvoiceFollowUp) {
        AlertDialog.Builder(this)
            .setTitle("Complete Follow-up")
            .setMessage("Mark this follow-up as completed? This will remove it from the pending list.")
            .setPositiveButton("Yes") { _, _ ->
                lifecycleScope.launch {
                    try {
                        setProcessing(true, "Completing follow-up...")
                        val response = RetrofitClient.apiService.completeFollowUp(followUp.id)
                        if (response.isSuccessful && response.body() != null) {
                            Toast.makeText(this@PendingFollowUpsActivity, "Follow-up completed", Toast.LENGTH_SHORT).show()
                            showProcessingSuccess("Follow-up completed")
                            delay(450)
                            loadPendingFollowUps()
                        } else {
                            Toast.makeText(this@PendingFollowUpsActivity, "Failed to complete", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@PendingFollowUpsActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    } finally {
                        setProcessing(false)
                    }
                }
            }
            .setNegativeButton("No", null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setProcessing(show: Boolean, message: String = "") {
        binding.cardProcessing.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            binding.tvProcessingMessage.text = message
            binding.cardProcessing.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_in_up))
        }
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
}

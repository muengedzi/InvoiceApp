package com.invoiceapp.activities

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

class CommunicationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCommunicationBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var adapter: CommunicationAdapter
    private var communications = mutableListOf<InvoiceCommunication>()
    private var invoiceId: Int = -1
    private var invoiceNumber: String = ""
    private var customerName: String = ""
    private var customerEmail: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCommunicationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)
        invoiceId = intent.getIntExtra("invoice_id", -1)
        invoiceNumber = intent.getStringExtra("invoice_number") ?: ""
        customerName = intent.getStringExtra("customer_name") ?: ""
        customerEmail = intent.getStringExtra("customer_email") ?: ""

        setupToolbar()
        setupRecyclerView()
        setupClickListeners()
        loadCommunications()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Communication - $invoiceNumber"
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
                val response = RetrofitClient.apiService.getInvoiceCommunications(invoiceId)
                if (response.isSuccessful && response.body() != null) {
                    communications.clear()
                    communications.addAll(response.body()!!)
                    adapter.updateList(communications)

                    if (communications.isEmpty()) {
                        binding.tvEmptyView.visibility = View.VISIBLE
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

        etSubject.setText("URGENT: Invoice #$invoiceNumber - Payment Overdue")

        AlertDialog.Builder(this)
            .setTitle("Send Follow-up to $customerName")
            .setView(dialogView)
            .setPositiveButton("Send") { _, _ ->
                val subject = etSubject.text.toString().trim()
                val message = etMessage.text.toString().trim()

                if (subject.isEmpty() || message.isEmpty()) {
                    Toast.makeText(this, "Please fill in subject and message", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                sendEmail(subject, message, true)
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
            .setTitle("Reply to ${customerName}")
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

    private fun sendEmail(subject: String, message: String, isFollowUp: Boolean) {
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

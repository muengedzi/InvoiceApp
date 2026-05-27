package com.invoiceapp.activities

import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.invoiceapp.R
import com.invoiceapp.adapters.CommunicationAdapter
import com.invoiceapp.databinding.ActivityCommunicationThreadBinding
import com.invoiceapp.models.InvoiceCommunication
import com.invoiceapp.models.SendReplyRequest
import com.invoiceapp.network.RetrofitClient
import com.invoiceapp.utils.SessionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CustomerCommunicationThreadActivity : BaseActivity() {

    private lateinit var binding: ActivityCommunicationThreadBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var adapter: CommunicationAdapter
    private var customerId: Int = -1
    private var communications = mutableListOf<InvoiceCommunication>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCommunicationThreadBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)
        customerId = intent.getIntExtra("customer_id", -1)

        if (customerId == -1) {
            Toast.makeText(this, "Invalid customer", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupToolbar()
        setupRecyclerView()
        loadCommunicationThread()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Communication History"
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

    private fun loadCommunicationThread() {
        binding.progressBar.visibility = View.VISIBLE
        binding.recyclerView.visibility = View.GONE
        binding.tvEmptyView.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getCustomerCommunicationThread(customerId)
                if (response.isSuccessful && response.body() != null) {
                    val thread = response.body()!!
                    displayCustomerInfo(thread)

                    communications.clear()
                    communications.addAll(thread.communications)
                    adapter.updateList(communications)

                    if (communications.isEmpty()) {
                        binding.tvEmptyView.visibility = View.VISIBLE
                        binding.recyclerView.visibility = View.GONE
                    } else {
                        binding.tvEmptyView.visibility = View.GONE
                        binding.recyclerView.visibility = View.VISIBLE
                    }
                } else {
                    Toast.makeText(this@CustomerCommunicationThreadActivity, "Failed to load communication history", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@CustomerCommunicationThreadActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun displayCustomerInfo(thread: com.invoiceapp.models.CustomerCommunicationThread) {
        binding.tvCustomerName.text = thread.customerName
        binding.tvCustomerEmail.text = thread.customerEmail ?: "No email address"

        val summary = thread.summary
        binding.tvSummary.text = """
            Total Communications: ${summary?.totalCommunications ?: 0}
            Emails Sent: ${summary?.emailsSent ?: 0}
            Customer Replies: ${summary?.customerReplies ?: 0}
            Response Rate: ${summary?.responseRate ?: 0}%
        """.trimIndent()
    }

    private fun showReplyDialog(communication: InvoiceCommunication) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_send_email, null)
        val etSubject = dialogView.findViewById<TextInputEditText>(R.id.etSubject)
        val etMessage = dialogView.findViewById<TextInputEditText>(R.id.etMessage)

        etSubject.setText("Re: ${communication.subject}")

        AlertDialog.Builder(this)
            .setTitle("Reply to Customer")
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
                    Toast.makeText(this@CustomerCommunicationThreadActivity, "Reply sent successfully", Toast.LENGTH_SHORT).show()
                    showProcessingSuccess("Reply sent")
                    delay(450)
                    loadCommunicationThread()
                } else {
                    Toast.makeText(this@CustomerCommunicationThreadActivity, "Failed to send reply", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@CustomerCommunicationThreadActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                setProcessing(false)
            }
        }
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

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

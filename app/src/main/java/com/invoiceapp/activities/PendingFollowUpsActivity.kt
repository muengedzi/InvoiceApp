package com.invoiceapp.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.invoiceapp.R
import com.invoiceapp.adapters.PendingFollowUpAdapter
import com.invoiceapp.databinding.ActivityPendingFollowUpsBinding
import com.invoiceapp.models.InvoiceFollowUp
import com.invoiceapp.network.RetrofitClient
import com.invoiceapp.utils.SessionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PendingFollowUpsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPendingFollowUpsBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var adapter: PendingFollowUpAdapter
    private var followUps = mutableListOf<InvoiceFollowUp>()

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
        adapter = PendingFollowUpAdapter(followUps) { followUp ->
            showFollowUpActionsDialog(followUp)
        }
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

        // Send Follow-up (if not sent)
        if (!followUp.messageSent) {
            options.add("Send Follow-up Email")
            actions.add { sendFollowUpEmail(followUp) }
        }

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
        val intent = Intent(this, CommunicationActivity::class.java)
        intent.putExtra("invoice_id", followUp.invoiceId)
        intent.putExtra("invoice_number", followUp.invoiceNumber)
        intent.putExtra("customer_name", followUp.customerName)
        startActivity(intent)
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

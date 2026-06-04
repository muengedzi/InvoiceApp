package com.invoiceapp.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.invoiceapp.R
import com.invoiceapp.activities.CommunicationActivity
import com.invoiceapp.activities.PendingFollowUpsActivity
import com.invoiceapp.models.RecentCustomerReply
import com.invoiceapp.network.RetrofitClient
import com.invoiceapp.utils.SessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class CommunicationSummaryFragment : Fragment() {

    private lateinit var sessionManager: SessionManager
    private lateinit var cardTotalSent: CardView
    private lateinit var cardOpened: CardView
    private lateinit var cardReplied: CardView
    private lateinit var cardPendingFollowUps: CardView
    private lateinit var tvTotalSent: TextView
    private lateinit var tvOpened: TextView
    private lateinit var tvReplied: TextView
    private lateinit var tvPendingFollowUps: TextView
    private lateinit var recentRepliesContainer: LinearLayout
    private lateinit var tvNoRecentReplies: TextView
    private lateinit var btnSyncInbox: View
    private lateinit var cardSyncProcessing: View
    private lateinit var tvSyncProcessing: TextView
    private lateinit var progressBar: View
    private var refreshJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_communication_summary, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sessionManager = SessionManager(requireContext())

        cardTotalSent = view.findViewById(R.id.cardTotalSent)
        cardOpened = view.findViewById(R.id.cardOpened)
        cardReplied = view.findViewById(R.id.cardReplied)
        cardPendingFollowUps = view.findViewById(R.id.cardPendingFollowUps)
        tvTotalSent = view.findViewById(R.id.tvTotalSent)
        tvOpened = view.findViewById(R.id.tvOpened)
        tvReplied = view.findViewById(R.id.tvReplied)
        tvPendingFollowUps = view.findViewById(R.id.tvPendingFollowUps)
        recentRepliesContainer = view.findViewById(R.id.recentRepliesContainer)
        tvNoRecentReplies = view.findViewById(R.id.tvNoRecentReplies)
        btnSyncInbox = view.findViewById(R.id.btnSyncInbox)
        cardSyncProcessing = view.findViewById(R.id.cardSyncProcessing)
        tvSyncProcessing = view.findViewById(R.id.tvSyncProcessing)
        progressBar = view.findViewById(R.id.progressBar)

        btnSyncInbox.setOnClickListener {
            syncInboxReplies()
        }
        loadCommunicationSummary()
    }

    override fun onResume() {
        super.onResume()
        refreshJob?.cancel()
        refreshJob = lifecycleScope.launch {
            while (isActive) {
                loadCommunicationSummary(showProgress = false)
                delay(30000)
            }
        }
    }

    override fun onPause() {
        refreshJob?.cancel()
        refreshJob = null
        super.onPause()
    }

    private fun loadCommunicationSummary(showProgress: Boolean = true) {
        if (showProgress) {
            progressBar.visibility = View.VISIBLE
        }

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getCommunicationSummary()
                if (response.isSuccessful && response.body() != null) {
                    val summary = response.body()!!
                    tvTotalSent.text = summary.totalSent.toString()
                    tvOpened.text = summary.opened.toString()
                    tvReplied.text = summary.replied.toString()
                    tvPendingFollowUps.text = summary.pendingFollowUps.toString()
                    renderRecentReplies(summary.recentReplies)
                    view?.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.fade_in_up))

                    // Set click listeners to navigate to appropriate screens
                    cardTotalSent.setOnClickListener {
                        navigateToCommunications("sent", "Sent Communications")
                    }

                    cardOpened.setOnClickListener {
                        navigateToCommunications("opened", "Opened Communications")
                    }

                    cardReplied.setOnClickListener {
                        navigateToCommunications("replied", "Replied Communications")
                    }

                    cardPendingFollowUps.setOnClickListener {
                        navigateToPendingFollowUps()
                    }
                } else {
                    Toast.makeText(requireContext(), "Failed to load summary", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error loading summary: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                if (showProgress) {
                    progressBar.visibility = View.GONE
                }
            }
        }
    }

    private fun navigateToPendingFollowUps() {
        // Launch PendingFollowUpsActivity directly
        val intent = Intent(requireContext(), PendingFollowUpsActivity::class.java)
        startActivity(intent)
    }

    private fun renderRecentReplies(replies: List<RecentCustomerReply>) {
        recentRepliesContainer.removeAllViews()
        tvNoRecentReplies.visibility = if (replies.isEmpty()) View.VISIBLE else View.GONE

        replies.forEach { reply ->
            val card = layoutInflater.inflate(
                R.layout.item_recent_communication_reply,
                recentRepliesContainer,
                false
            )
            card.findViewById<TextView>(R.id.tvReplyCustomer).text = reply.customerName ?: "Customer"
            card.findViewById<TextView>(R.id.tvReplyInvoice).text = reply.invoiceNumber ?: "Invoice"
            card.findViewById<TextView>(R.id.tvReplyBody).text = reply.replyBody ?: ""
            card.setOnClickListener {
                val intent = Intent(requireContext(), com.invoiceapp.activities.InvoiceDetailActivity::class.java)
                intent.putExtra("invoice_id", reply.invoiceId)
                startActivity(intent)
            }
            recentRepliesContainer.addView(card)
        }
    }

    private fun navigateToCommunications(filter: String, title: String) {
        val intent = Intent(requireContext(), CommunicationActivity::class.java)
        intent.putExtra("communication_filter", filter)
        intent.putExtra("communication_title", title)
        startActivity(intent)
    }

    private fun syncInboxReplies() {
        setSyncProcessing(true, "Checking inbox for replies...")
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.syncCommunicationInbox(
                    mapOf("days_back" to 14, "limit" to 50)
                )
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    val matched = (body["matched"] as? Double)?.toInt()
                        ?: (body["matched"] as? Int)
                        ?: 0
                    setSyncProcessing(true, "Synced $matched customer replies")
                    loadCommunicationSummary()
                } else {
                    Toast.makeText(requireContext(), "Failed to sync inbox", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Inbox sync error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                cardSyncProcessing.postDelayed({
                    setSyncProcessing(false)
                }, 650)
            }
        }
    }

    private fun setSyncProcessing(show: Boolean, message: String = "") {
        cardSyncProcessing.visibility = if (show) View.VISIBLE else View.GONE
        btnSyncInbox.isEnabled = !show
        if (show) {
            tvSyncProcessing.text = message
            cardSyncProcessing.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.fade_in_up))
        }
    }
}

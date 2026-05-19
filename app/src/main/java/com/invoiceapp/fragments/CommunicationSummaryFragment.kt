package com.invoiceapp.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.invoiceapp.R
import com.invoiceapp.activities.MainActivity
import com.invoiceapp.activities.PendingFollowUpsActivity
import com.invoiceapp.network.RetrofitClient
import com.invoiceapp.utils.SessionManager
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
    private lateinit var progressBar: View

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
        progressBar = view.findViewById(R.id.progressBar)

        loadCommunicationSummary()
    }

    private fun loadCommunicationSummary() {
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getCommunicationSummary()
                if (response.isSuccessful && response.body() != null) {
                    val summary = response.body()!!
                    tvTotalSent.text = summary.totalSent.toString()
                    tvOpened.text = summary.opened.toString()
                    tvReplied.text = summary.replied.toString()
                    tvPendingFollowUps.text = summary.pendingFollowUps.toString()

                    // Set click listeners to navigate to appropriate screens
                    cardTotalSent.setOnClickListener {
                        navigateToInvoices()
                    }

                    cardOpened.setOnClickListener {
                        navigateToInvoices()
                    }

                    cardReplied.setOnClickListener {
                        navigateToInvoices()
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
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun navigateToInvoices() {
        // Check if activity is MainActivity
        if (activity is MainActivity) {
            (activity as MainActivity).navigateToInvoices()
        } else {
            // If not in MainActivity, navigate using intent
            Toast.makeText(requireContext(), "Navigate to Invoices", Toast.LENGTH_SHORT).show()
            // You can add alternative navigation here if needed
        }
    }

    private fun navigateToPendingFollowUps() {
        // Launch PendingFollowUpsActivity directly
        val intent = Intent(requireContext(), PendingFollowUpsActivity::class.java)
        startActivity(intent)
    }
}
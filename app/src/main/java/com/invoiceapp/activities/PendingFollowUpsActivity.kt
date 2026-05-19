package com.invoiceapp.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.invoiceapp.R
import com.invoiceapp.adapters.PendingFollowUpAdapter
import com.invoiceapp.databinding.ActivityPendingFollowUpsBinding
import com.invoiceapp.models.InvoiceFollowUp
import com.invoiceapp.network.RetrofitClient
import com.invoiceapp.utils.SessionManager
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
            // Navigate to invoice detail
            val intent = Intent(this, InvoiceDetailActivity::class.java)
            intent.putExtra("invoice_id", followUp.invoiceId)
            startActivity(intent)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun loadPendingFollowUps() {
        binding.progressBar.visibility = View.VISIBLE

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
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@PendingFollowUpsActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
package com.invoiceapp.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.invoiceapp.activities.CreateInvoiceActivity
import com.invoiceapp.adapters.InvoiceAdapter
import com.invoiceapp.databinding.FragmentInvoicesBinding
import com.invoiceapp.network.ApiResponse
import com.invoiceapp.viewmodels.InvoiceViewModel

class InvoicesFragment : Fragment() {
    private var _binding: FragmentInvoicesBinding? = null
    private val binding get() = _binding!!
    private lateinit var invoiceViewModel: InvoiceViewModel
    private lateinit var invoiceAdapter: InvoiceAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInvoicesBinding.inflate(inflater, container, false)
        invoiceViewModel = ViewModelProvider(this)[InvoiceViewModel::class.java]

        setupRecyclerView()
        setupFAB()
        observeViewModel()

        invoiceViewModel.loadInvoices()

        return binding.root
    }

    private fun setupRecyclerView() {
        invoiceAdapter = InvoiceAdapter { invoiceId ->
            // Navigate to invoice detail
            val intent = Intent(requireContext(), com.invoiceapp.activities.InvoiceDetailActivity::class.java)
            intent.putExtra("invoice_id", invoiceId)
            startActivity(intent)
        }

        binding.rvInvoices.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = invoiceAdapter
        }
    }

    private fun setupFAB() {
        val fab = requireActivity().findViewById<FloatingActionButton>(com.invoiceapp.R.id.fab)
        fab?.setOnClickListener {
            startActivity(Intent(requireContext(), CreateInvoiceActivity::class.java))
        }
        fab?.show()
    }

    private fun observeViewModel() {
        invoiceViewModel.invoices.observe(viewLifecycleOwner) { result ->
            when (result) {
                is ApiResponse.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                }
                is ApiResponse.Success -> {
                    binding.progressBar.visibility = View.GONE
                    invoiceAdapter.submitList(result.data)

                    if (result.data.isEmpty()) {
                        binding.tvEmpty.visibility = View.VISIBLE
                    } else {
                        binding.tvEmpty.visibility = View.GONE
                    }
                }
                is ApiResponse.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.tvError.text = result.message
                    binding.tvError.visibility = View.VISIBLE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
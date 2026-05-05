package com.invoiceapp.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.invoiceapp.activities.CustomersActivity
import com.invoiceapp.adapters.CustomerAdapter
import com.invoiceapp.databinding.FragmentCustomersBinding
import com.invoiceapp.network.ApiResponse
import com.invoiceapp.viewmodels.CustomerViewModel
import android.content.Intent

class CustomersFragment : Fragment() {
    private var _binding: FragmentCustomersBinding? = null
    private val binding get() = _binding!!
    private lateinit var customerViewModel: CustomerViewModel
    private lateinit var customerAdapter: CustomerAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCustomersBinding.inflate(inflater, container, false)
        customerViewModel = ViewModelProvider(this)[CustomerViewModel::class.java]

        setupRecyclerView()
        setupFAB()
        observeViewModel()

        customerViewModel.loadCustomers()

        return binding.root
    }

    private fun setupRecyclerView() {
        customerAdapter = CustomerAdapter { customerId ->
            // Navigate to edit customer
            val intent = Intent(requireContext(), CustomersActivity::class.java)
            intent.putExtra("customer_id", customerId)
            startActivity(intent)
        }

        binding.rvCustomers.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = customerAdapter
        }
    }

    private fun setupFAB() {
        val fab = requireActivity().findViewById<FloatingActionButton>(com.invoiceapp.R.id.fab)
        fab?.setOnClickListener {
            startActivity(Intent(requireContext(), CustomersActivity::class.java))
        }
        fab?.show()
    }

    private fun observeViewModel() {
        customerViewModel.customers.observe(viewLifecycleOwner) { result ->
            when (result) {
                is ApiResponse.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                }
                is ApiResponse.Success -> {
                    binding.progressBar.visibility = View.GONE
                    customerAdapter.submitList(result.data)
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
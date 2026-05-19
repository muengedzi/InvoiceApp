package com.invoiceapp.fragments

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.invoiceapp.R
import com.invoiceapp.activities.AddEditCustomerActivity
import com.invoiceapp.activities.CustomerCommunicationThreadActivity
import com.invoiceapp.adapters.CustomerAdapter
import com.invoiceapp.databinding.FragmentCustomersBinding
import com.invoiceapp.models.Customer
import com.invoiceapp.network.ApiResponse
import com.invoiceapp.viewmodels.CustomerViewModel

class CustomersFragment : Fragment() {
    private var _binding: FragmentCustomersBinding? = null
    private val binding get() = _binding!!
    private lateinit var customerViewModel: CustomerViewModel
    private lateinit var customerAdapter: CustomerAdapter
    private var allCustomers = listOf<Customer>()
    private var currentQuery = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCustomersBinding.inflate(inflater, container, false)
        customerViewModel = ViewModelProvider(this)[CustomerViewModel::class.java]

        setupRecyclerView()
        setupSearch()
        setupFAB()
        observeViewModel()

        customerViewModel.loadCustomers()

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        if (::customerViewModel.isInitialized) {
            customerViewModel.loadCustomers()
        }
    }

    private fun setupRecyclerView() {
        customerAdapter = CustomerAdapter(
            onEditClick = { customerId ->
                val intent = Intent(requireContext(), AddEditCustomerActivity::class.java)
                intent.putExtra("customer_id", customerId)
                startActivity(intent)
            },
            onCommunicationClick = { customerId ->
                val intent = Intent(requireContext(), CustomerCommunicationThreadActivity::class.java)
                intent.putExtra("customer_id", customerId)
                startActivity(intent)
            }
        )

        binding.rvCustomers.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = customerAdapter
        }
    }

    private fun setupFAB() {
        val fab = requireActivity().findViewById<FloatingActionButton>(com.invoiceapp.R.id.fab)
        fab?.setOnClickListener {
            startActivity(Intent(requireContext(), AddEditCustomerActivity::class.java))
        }
        fab?.show()
    }

    private fun setupSearch() {
        binding.etSearchCustomers.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                currentQuery = s?.toString().orEmpty()
                applyCustomerFilter()
            }
        })
    }

    private fun observeViewModel() {
        customerViewModel.customers.observe(viewLifecycleOwner) { result ->
            when (result) {
                is ApiResponse.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.tvError.visibility = View.GONE
                }
                is ApiResponse.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.tvError.visibility = View.GONE
                    allCustomers = result.data
                    applyCustomerFilter()
                }
                is ApiResponse.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.tvError.text = result.message
                    binding.tvError.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun applyCustomerFilter() {
        val query = currentQuery.trim().lowercase()
        val filteredCustomers = if (query.isBlank()) {
            allCustomers
        } else {
            allCustomers.filter { customer ->
                listOfNotNull(
                    customer.customer_name,
                    customer.email,
                    customer.phone,
                    customer.address,
                    customer.city
                ).any { it.lowercase().contains(query) }
            }
        }

        customerAdapter.submitList(filteredCustomers)
        binding.tvEmpty.text = if (allCustomers.isEmpty()) {
            "No customers found.\nTap + to add a customer."
        } else {
            "No customers match \"$currentQuery\"."
        }
        binding.tvEmpty.visibility = if (filteredCustomers.isEmpty()) View.VISIBLE else View.GONE
        if (filteredCustomers.isNotEmpty()) {
            binding.rvCustomers.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.fade_in_up))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

package com.invoiceapp.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.invoiceapp.activities.ExpensesActivity
import com.invoiceapp.adapters.ExpenseListAdapter
import com.invoiceapp.databinding.FragmentExpensesBinding
import com.invoiceapp.network.ApiResponse
import com.invoiceapp.viewmodels.ExpenseViewModel
import android.content.Intent

class ExpensesFragment : Fragment() {
    private var _binding: FragmentExpensesBinding? = null
    private val binding get() = _binding!!
    private lateinit var expenseViewModel: ExpenseViewModel
    private lateinit var expenseAdapter: ExpenseListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExpensesBinding.inflate(inflater, container, false)
        expenseViewModel = ViewModelProvider(this)[ExpenseViewModel::class.java]

        setupRecyclerView()
        setupFAB()
        observeViewModel()

        expenseViewModel.loadExpenses()

        return binding.root
    }

    private fun setupRecyclerView() {
        expenseAdapter = ExpenseListAdapter { expenseId ->
            // Handle expense click - navigate to expense detail
            // You can create an ExpenseDetailActivity later
        }

        binding.rvExpenses.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = expenseAdapter
        }
    }

    private fun setupFAB() {
        val fab = requireActivity().findViewById<FloatingActionButton>(com.invoiceapp.R.id.fab)
        fab?.setOnClickListener {
            startActivity(Intent(requireContext(), ExpensesActivity::class.java))
        }
        fab?.show()
    }

    private fun observeViewModel() {
        expenseViewModel.expenses.observe(viewLifecycleOwner) { result ->
            when (result) {
                is ApiResponse.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.tvError.visibility = View.GONE
                    binding.tvEmpty.visibility = View.GONE
                }
                is ApiResponse.Success -> {
                    binding.progressBar.visibility = View.GONE
                    expenseAdapter.submitList(result.data)
                    if (result.data.isEmpty()) {
                        binding.tvEmpty.visibility = View.VISIBLE
                        binding.rvExpenses.visibility = View.GONE
                    } else {
                        binding.tvEmpty.visibility = View.GONE
                        binding.rvExpenses.visibility = View.VISIBLE
                    }
                }
                is ApiResponse.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.tvError.text = result.message
                    binding.tvError.visibility = View.VISIBLE
                    binding.rvExpenses.visibility = View.GONE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
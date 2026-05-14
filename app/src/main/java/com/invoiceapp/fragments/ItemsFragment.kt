package com.invoiceapp.fragments

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.invoiceapp.R
import com.invoiceapp.adapters.InvoiceItemMasterAdapter
import com.invoiceapp.databinding.FragmentItemsBinding
import com.invoiceapp.models.InvoiceItemCategory
import com.invoiceapp.models.InvoiceItemMaster
import com.invoiceapp.models.InvoiceItemMasterRequest
import com.invoiceapp.network.RetrofitClient
import kotlinx.coroutines.launch

class ItemsFragment : Fragment() {

    private var _binding: FragmentItemsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: InvoiceItemMasterAdapter
    private var itemsList = mutableListOf<InvoiceItemMaster>()
    private var categoriesList = mutableListOf<InvoiceItemCategory>()
    private var currentPage = 1
    private var isLoading = false
    private var hasMorePages = true
    private var selectedCategoryId: Int? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentItemsBinding.inflate(inflater, container, false)
        setHasOptionsMenu(true)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupClickListeners()
        setupFAB()
        loadCategories()
        loadItems()
    }

    private fun setupFAB() {
        val fab = requireActivity().findViewById<FloatingActionButton>(R.id.fab)
        fab?.setOnClickListener {
            showAddItemDialog()
        }
        fab?.show()
    }

    private fun setupRecyclerView() {
        adapter = InvoiceItemMasterAdapter(itemsList) { item ->
            showEditItemDialog(item)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                if (!isLoading && hasMorePages) {
                    if (visibleItemCount + firstVisibleItemPosition >= totalItemCount - 5) {
                        loadMoreItems()
                    }
                }
            }
        })
    }

    private fun setupClickListeners() {
        binding.btnSearch.setOnClickListener {
            currentPage = 1
            itemsList.clear()
            hasMorePages = true
            loadItems()
        }
        binding.etSearch.setOnEditorActionListener { _, _, _ ->
            currentPage = 1
            itemsList.clear()
            hasMorePages = true
            loadItems()
            true
        }
        binding.btnFilterCategory.setOnClickListener {
            showCategoryFilterDialog()
        }
        binding.btnClearFilter.setOnClickListener {
            clearCategoryFilter()
        }
    }

    private fun loadCategories() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getInvoiceItemCategories()
                if (response.isSuccessful && response.body() != null) {
                    categoriesList.clear()
                    categoriesList.addAll(response.body()!!)
                    updateCategoryChips()
                }
            } catch (e: Exception) {
                // Silently fail
            }
        }
    }

    private fun updateCategoryChips() {
        binding.chipGroupCategories.removeAllViews()

        val allChip = Chip(requireContext()).apply {
            text = "All"
            isCheckable = true
            isChecked = selectedCategoryId == null
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedCategoryId = null
                    refreshItems()
                }
            }
        }
        binding.chipGroupCategories.addView(allChip)

        for (category in categoriesList) {
            val chip = Chip(requireContext()).apply {
                text = category.categoryName
                isCheckable = true
                tag = category.id
                isChecked = selectedCategoryId == category.id
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedCategoryId = category.id
                        refreshItems()
                    }
                }
            }
            binding.chipGroupCategories.addView(chip)
        }
    }

    private fun refreshItems() {
        currentPage = 1
        itemsList.clear()
        hasMorePages = true
        loadItems()
    }

    private fun loadItems() {
        if (isLoading) return
        isLoading = true
        binding.progressBar.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val searchQuery = binding.etSearch.text.toString()
                val response = RetrofitClient.apiService.getInvoiceItems(
                    page = currentPage,
                    perPage = 20,
                    search = searchQuery.ifEmpty { null },
                    categoryId = selectedCategoryId
                )

                if (response.isSuccessful && response.body() != null) {
                    val result = response.body()!!
                    if (currentPage == 1) {
                        itemsList.clear()
                    }
                    itemsList.addAll(result.items)
                    adapter.updateList(itemsList)
                    hasMorePages = currentPage < result.totalPages
                    currentPage++

                    if (itemsList.isEmpty()) {
                        binding.tvEmptyView.visibility = View.VISIBLE
                        binding.recyclerView.visibility = View.GONE
                    } else {
                        binding.tvEmptyView.visibility = View.GONE
                        binding.recyclerView.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun loadMoreItems() {
        if (hasMorePages && !isLoading) {
            loadItems()
        }
    }

    private fun showAddItemDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_manage_item, null)
        val etItemName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etItemName)
        val etDescription = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etDescription)
        val etUnitPrice = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etUnitPrice)
        val etTaxRate = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etTaxRate)
        val spinnerCategory = dialogView.findViewById<Spinner>(R.id.spinnerCategory)

        val categoryNames = listOf("None") + categoriesList.map { it.categoryName }
        val categoryAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, categoryNames)
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCategory.adapter = categoryAdapter

        AlertDialog.Builder(requireContext())
            .setTitle("Add New Item")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val itemName = etItemName.text.toString().trim()
                val unitPrice = etUnitPrice.text.toString().toDoubleOrNull()

                if (itemName.isEmpty() || unitPrice == null) {
                    Toast.makeText(requireContext(), "Name and price required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val taxRate = etTaxRate.text.toString().toDoubleOrNull() ?: 0.0
                val description = etDescription.text.toString().trim().ifEmpty { null }
                val categoryId = if (spinnerCategory.selectedItemPosition == 0) null else categoriesList[spinnerCategory.selectedItemPosition - 1].id

                createItem(InvoiceItemMasterRequest(itemName, description, unitPrice, taxRate, categoryId))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditItemDialog(item: InvoiceItemMaster) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_manage_item, null)
        val etItemName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etItemName)
        val etDescription = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etDescription)
        val etUnitPrice = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etUnitPrice)
        val etTaxRate = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etTaxRate)
        val spinnerCategory = dialogView.findViewById<Spinner>(R.id.spinnerCategory)

        val categoryNames = listOf("None") + categoriesList.map { it.categoryName }
        val categoryAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, categoryNames)
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCategory.adapter = categoryAdapter

        val selectedPosition = if (item.categoryId == null) 0 else categoriesList.indexOfFirst { it.id == item.categoryId } + 1
        spinnerCategory.setSelection(selectedPosition)

        etItemName.setText(item.itemName)
        etDescription.setText(item.description ?: "")
        etUnitPrice.setText(item.unitPrice.toString())
        etTaxRate.setText(item.taxRate.toString())

        AlertDialog.Builder(requireContext())
            .setTitle("Edit Item")
            .setView(dialogView)
            .setPositiveButton("Update") { _, _ ->
                val itemName = etItemName.text.toString().trim()
                val unitPrice = etUnitPrice.text.toString().toDoubleOrNull()

                if (itemName.isEmpty() || unitPrice == null) return@setPositiveButton

                val taxRate = etTaxRate.text.toString().toDoubleOrNull() ?: 0.0
                val description = etDescription.text.toString().trim().ifEmpty { null }
                val categoryId = if (spinnerCategory.selectedItemPosition == 0) null else categoriesList[spinnerCategory.selectedItemPosition - 1].id

                updateItem(item.id, InvoiceItemMasterRequest(itemName, description, unitPrice, taxRate, categoryId))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createItem(request: InvoiceItemMasterRequest) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.createInvoiceItem(request)
                if (response.isSuccessful) {
                    refreshItems()
                    loadCategories()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateItem(id: Int, request: InvoiceItemMasterRequest) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.updateInvoiceItem(id, request)
                if (response.isSuccessful) {
                    refreshItems()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showCategoryFilterDialog() {
        val categoryNames = listOf("All Categories") + categoriesList.map { it.categoryName }
        val checkedItem = if (selectedCategoryId == null) 0 else categoriesList.indexOfFirst { it.id == selectedCategoryId } + 1

        AlertDialog.Builder(requireContext())
            .setTitle("Filter by Category")
            .setSingleChoiceItems(categoryNames.toTypedArray(), checkedItem) { dialog, which ->
                selectedCategoryId = if (which == 0) null else categoriesList[which - 1].id
                refreshItems()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun clearCategoryFilter() {
        selectedCategoryId = null
        updateCategoryChips()
        refreshItems()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.manage_items_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_manage_categories -> {
                showCategoryManagementDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showCategoryManagementDialog() {
        val categoryNames = categoriesList.map { it.categoryName }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle("Manage Categories")
            .setItems(categoryNames) { _, which ->
                showEditCategoryDialog(categoriesList[which])
            }
            .setPositiveButton("Add New") { _, _ -> showAddCategoryDialog() }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showAddCategoryDialog() {
        val input = EditText(requireContext())
        input.hint = "Category Name"
        AlertDialog.Builder(requireContext())
            .setTitle("Add Category")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) createCategory(name)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditCategoryDialog(category: InvoiceItemCategory) {
        val input = EditText(requireContext())
        input.setText(category.categoryName)
        AlertDialog.Builder(requireContext())
            .setTitle("Edit Category")
            .setView(input)
            .setPositiveButton("Update") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) updateCategory(category.id, name)
            }
            .setNeutralButton("Delete") { _, _ -> deleteCategory(category) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createCategory(name: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                RetrofitClient.apiService.createInvoiceItemCategory(mapOf("category_name" to name))
                loadCategories()
            } catch (e: Exception) {}
        }
    }

    private fun updateCategory(id: Int, name: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                RetrofitClient.apiService.updateInvoiceItemCategory(id, mapOf("category_name" to name))
                loadCategories()
                refreshItems()
            } catch (e: Exception) {}
        }
    }

    private fun deleteCategory(category: InvoiceItemCategory) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Category")
            .setMessage("Delete '${category.categoryName}'?")
            .setPositiveButton("Delete") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        RetrofitClient.apiService.deleteInvoiceItemCategory(category.id)
                        if (selectedCategoryId == category.id) selectedCategoryId = null
                        loadCategories()
                        refreshItems()
                    } catch (e: Exception) {}
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
package com.invoiceapp.fragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
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
    private var allItems = mutableListOf<InvoiceItemMaster>()
    private var filteredItems = mutableListOf<InvoiceItemMaster>()
    private var categoriesList = mutableListOf<InvoiceItemCategory>()
    private var selectedCategoryId: Int? = null
    private var currentSearchQuery = ""

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
        setupSearch()
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
        adapter = InvoiceItemMasterAdapter(filteredItems) { item ->
            showEditItemDialog(item)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                currentSearchQuery = s?.toString().orEmpty()
                applyFilters()
            }
        })
    }

    private fun setupClickListeners() {
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
                    applyFilters()
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
                        applyFilters()
                    }
                }
            }
            binding.chipGroupCategories.addView(chip)
        }
    }

    private fun loadItems() {
        binding.progressBar.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getInvoiceItems(page = 1, perPage = 1000)
                if (response.isSuccessful && response.body() != null) {
                    allItems.clear()
                    allItems.addAll(response.body()!!.items)
                    applyFilters()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun applyFilters() {
        // Filter by category
        var filtered = if (selectedCategoryId == null) {
            allItems.toList()
        } else {
            allItems.filter { it.categoryId == selectedCategoryId }
        }

        // Filter by search query
        if (currentSearchQuery.isNotBlank()) {
            val query = currentSearchQuery.lowercase()
            filtered = filtered.filter { item ->
                item.itemName.lowercase().contains(query) ||
                        (item.description?.lowercase()?.contains(query) == true)
            }
        }

        filteredItems.clear()
        filteredItems.addAll(filtered)
        adapter.updateList(filteredItems)

        if (filteredItems.isEmpty()) {
            binding.tvEmptyView.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
        } else {
            binding.tvEmptyView.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
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
            .setNeutralButton("Delete") { _, _ ->
                deleteItem(item)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createItem(request: InvoiceItemMasterRequest) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.createInvoiceItem(request)
                if (response.isSuccessful) {
                    loadItems()
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
                    loadItems()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteItem(item: InvoiceItemMaster) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Item")
            .setMessage("Delete '${item.itemName}'? This removes it from future item selection.")
            .setPositiveButton("Delete") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val response = RetrofitClient.apiService.deleteInvoiceItem(item.id)
                        if (response.isSuccessful) {
                            Toast.makeText(requireContext(), "Item deleted", Toast.LENGTH_SHORT).show()
                            loadItems()
                        } else {
                            Toast.makeText(requireContext(), "Failed to delete item", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCategoryFilterDialog() {
        val categoryNames = listOf("All Categories") + categoriesList.map { it.categoryName }
        val checkedItem = if (selectedCategoryId == null) 0 else categoriesList.indexOfFirst { it.id == selectedCategoryId } + 1

        AlertDialog.Builder(requireContext())
            .setTitle("Filter by Category")
            .setSingleChoiceItems(categoryNames.toTypedArray(), checkedItem) { dialog, which ->
                selectedCategoryId = if (which == 0) null else categoriesList[which - 1].id
                applyFilters()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun clearCategoryFilter() {
        selectedCategoryId = null
        updateCategoryChips()
        applyFilters()
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
                loadItems()
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
                        loadItems()
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
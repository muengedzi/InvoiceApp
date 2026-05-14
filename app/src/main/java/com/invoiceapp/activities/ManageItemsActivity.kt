package com.invoiceapp.activities

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.invoiceapp.R
import com.invoiceapp.adapters.InvoiceItemMasterAdapter
import com.invoiceapp.databinding.ActivityManageItemsBinding
import com.invoiceapp.models.InvoiceItemCategory
import com.invoiceapp.models.InvoiceItemMaster
import com.invoiceapp.models.InvoiceItemMasterRequest
import com.invoiceapp.network.RetrofitClient
import com.invoiceapp.utils.SessionManager
import kotlinx.coroutines.launch
import java.text.NumberFormat

class ManageItemsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManageItemsBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var adapter: InvoiceItemMasterAdapter
    private var itemsList = mutableListOf<InvoiceItemMaster>()
    private var categoriesList = mutableListOf<InvoiceItemCategory>()
    private var currentPage = 1
    private var isLoading = false
    private var hasMorePages = true
    private var selectedCategoryId: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManageItemsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        setupToolbar()
        setupRecyclerView()
        setupClickListeners()
        loadCategories()
        loadItems()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Manage Items"
    }

    private fun setupRecyclerView() {
        adapter = InvoiceItemMasterAdapter(itemsList) { item ->
            showEditItemDialog(item)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        // Pagination
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
        binding.fabAdd.setOnClickListener {
            showAddItemDialog()
        }
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
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getInvoiceItemCategories()
                if (response.isSuccessful && response.body() != null) {
                    categoriesList.clear()
                    categoriesList.addAll(response.body()!!)
                    updateCategoryChips()
                }
            } catch (e: Exception) {
                // Silently fail - categories are optional
            }
        }
    }

    private fun updateCategoryChips() {
        binding.chipGroupCategories.removeAllViews()

        // Add "All" chip
        val allChip = Chip(this).apply {
            text = "All"
            isCheckable = true
            isChecked = selectedCategoryId == null
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedCategoryId = null
                    currentPage = 1
                    itemsList.clear()
                    hasMorePages = true
                    loadItems()
                }
            }
        }
        binding.chipGroupCategories.addView(allChip)

        // Add category chips
        for (category in categoriesList) {
            val chip = Chip(this).apply {
                text = category.categoryName
                isCheckable = true
                tag = category.id
                isChecked = selectedCategoryId == category.id
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedCategoryId = category.id
                        currentPage = 1
                        itemsList.clear()
                        hasMorePages = true
                        loadItems()
                    }
                }
            }
            binding.chipGroupCategories.addView(chip)
        }
    }

    private fun showCategoryFilterDialog() {
        val categoryNames = listOf("All Categories") + categoriesList.map { it.categoryName }
        val checkedItem = when (selectedCategoryId) {
            null -> 0
            else -> categoriesList.indexOfFirst { it.id == selectedCategoryId } + 1
        }

        AlertDialog.Builder(this)
            .setTitle("Filter by Category")
            .setSingleChoiceItems(categoryNames.toTypedArray(), checkedItem) { dialog, which ->
                if (which == 0) {
                    selectedCategoryId = null
                } else {
                    selectedCategoryId = categoriesList[which - 1].id
                }
                currentPage = 1
                itemsList.clear()
                hasMorePages = true
                loadItems()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun clearCategoryFilter() {
        selectedCategoryId = null
        updateCategoryChips()
        currentPage = 1
        itemsList.clear()
        hasMorePages = true
        loadItems()
        Toast.makeText(this, "Filter cleared", Toast.LENGTH_SHORT).show()
    }

    private fun loadItems() {
        if (isLoading) return
        isLoading = true
        showLoading(true)

        lifecycleScope.launch {
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

                    // Show empty view if no items
                    if (itemsList.isEmpty()) {
                        binding.tvEmptyView.visibility = View.VISIBLE
                        binding.recyclerView.visibility = View.GONE
                    } else {
                        binding.tvEmptyView.visibility = View.GONE
                        binding.recyclerView.visibility = View.VISIBLE
                    }
                } else {
                    Toast.makeText(this@ManageItemsActivity, "Failed to load items", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ManageItemsActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
                showLoading(false)
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

        // Setup category spinner
        val categoryNames = listOf("None") + categoriesList.map { it.categoryName }
        val categoryAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categoryNames)
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCategory.adapter = categoryAdapter

        AlertDialog.Builder(this)
            .setTitle("Add New Item")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val itemName = etItemName.text.toString().trim()
                val unitPrice = etUnitPrice.text.toString().toDoubleOrNull()

                if (itemName.isEmpty()) {
                    Toast.makeText(this, "Item name required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (unitPrice == null || unitPrice <= 0) {
                    Toast.makeText(this, "Valid unit price required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val taxRate = etTaxRate.text.toString().toDoubleOrNull() ?: 0.0
                val description = etDescription.text.toString().trim().ifEmpty { null }
                val categoryId = when (val selectedPosition = spinnerCategory.selectedItemPosition) {
                    0 -> null
                    else -> categoriesList[selectedPosition - 1].id
                }

                createItem(InvoiceItemMasterRequest(
                    itemName = itemName,
                    description = description,
                    unitPrice = unitPrice,
                    taxRate = taxRate,
                    categoryId = categoryId
                ))
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

        // Setup category spinner
        val categoryNames = listOf("None") + categoriesList.map { it.categoryName }
        val categoryAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categoryNames)
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCategory.adapter = categoryAdapter

        // Set selected category
        val selectedPosition = when (item.categoryId) {
            null -> 0
            else -> categoriesList.indexOfFirst { it.id == item.categoryId } + 1
        }
        spinnerCategory.setSelection(selectedPosition)

        etItemName.setText(item.itemName)
        etDescription.setText(item.description ?: "")
        etUnitPrice.setText(item.unitPrice.toString())
        etTaxRate.setText(item.taxRate.toString())

        AlertDialog.Builder(this)
            .setTitle("Edit Item")
            .setView(dialogView)
            .setPositiveButton("Update") { _, _ ->
                val itemName = etItemName.text.toString().trim()
                val unitPrice = etUnitPrice.text.toString().toDoubleOrNull()

                if (itemName.isEmpty()) {
                    Toast.makeText(this, "Item name required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (unitPrice == null || unitPrice <= 0) {
                    Toast.makeText(this, "Valid unit price required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val taxRate = etTaxRate.text.toString().toDoubleOrNull() ?: 0.0
                val description = etDescription.text.toString().trim().ifEmpty { null }
                val categoryId = when (val selectedPos = spinnerCategory.selectedItemPosition) {
                    0 -> null
                    else -> categoriesList[selectedPos - 1].id
                }

                updateItem(item.id, InvoiceItemMasterRequest(
                    itemName = itemName,
                    description = description,
                    unitPrice = unitPrice,
                    taxRate = taxRate,
                    categoryId = categoryId
                ))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createItem(request: InvoiceItemMasterRequest) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.createInvoiceItem(request)
                if (response.isSuccessful && response.body() != null) {
                    Toast.makeText(this@ManageItemsActivity, "Item created successfully", Toast.LENGTH_SHORT).show()
                    // Refresh list
                    currentPage = 1
                    itemsList.clear()
                    hasMorePages = true
                    loadItems()
                    loadCategories() // Reload categories in case new ones were added
                } else {
                    Toast.makeText(this@ManageItemsActivity, "Failed to create item", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ManageItemsActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateItem(id: Int, request: InvoiceItemMasterRequest) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.updateInvoiceItem(id, request)
                if (response.isSuccessful && response.body() != null) {
                    Toast.makeText(this@ManageItemsActivity, "Item updated successfully", Toast.LENGTH_SHORT).show()
                    // Refresh list
                    currentPage = 1
                    itemsList.clear()
                    hasMorePages = true
                    loadItems()
                } else {
                    Toast.makeText(this@ManageItemsActivity, "Failed to update item", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ManageItemsActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.manage_items_menu, menu)
        return true
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

        AlertDialog.Builder(this)
            .setTitle("Manage Categories")
            .setItems(categoryNames) { _, which ->
                val category = categoriesList[which]
                showEditCategoryDialog(category)
            }
            .setPositiveButton("Add New") { _, _ ->
                showAddCategoryDialog()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showAddCategoryDialog() {
        val input = EditText(this)
        input.hint = "Category Name"

        AlertDialog.Builder(this)
            .setTitle("Add Category")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val categoryName = input.text.toString().trim()
                if (categoryName.isNotEmpty()) {
                    createCategory(categoryName)
                } else {
                    Toast.makeText(this, "Category name required", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditCategoryDialog(category: InvoiceItemCategory) {
        val input = EditText(this)
        input.setText(category.categoryName)
        input.hint = "Category Name"

        AlertDialog.Builder(this)
            .setTitle("Edit Category")
            .setView(input)
            .setPositiveButton("Update") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    updateCategory(category.id, newName)
                } else {
                    Toast.makeText(this, "Category name required", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("Delete") { _, _ ->
                deleteCategory(category.id, category.categoryName)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createCategory(categoryName: String) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.createInvoiceItemCategory(
                    mapOf("category_name" to categoryName)
                )
                if (response.isSuccessful && response.body() != null) {
                    Toast.makeText(this@ManageItemsActivity, "Category created", Toast.LENGTH_SHORT).show()
                    loadCategories()
                } else {
                    Toast.makeText(this@ManageItemsActivity, "Failed to create category", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ManageItemsActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateCategory(categoryId: Int, newName: String) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.updateInvoiceItemCategory(
                    categoryId,
                    mapOf("category_name" to newName)
                )
                if (response.isSuccessful && response.body() != null) {
                    Toast.makeText(this@ManageItemsActivity, "Category updated", Toast.LENGTH_SHORT).show()
                    loadCategories()
                    // Refresh items to show updated category names
                    currentPage = 1
                    itemsList.clear()
                    hasMorePages = true
                    loadItems()
                } else {
                    Toast.makeText(this@ManageItemsActivity, "Failed to update category", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ManageItemsActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteCategory(categoryId: Int, categoryName: String) {
        AlertDialog.Builder(this)
            .setTitle("Delete Category")
            .setMessage("Delete '$categoryName'? Items in this category will become uncategorized.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    try {
                        val response = RetrofitClient.apiService.deleteInvoiceItemCategory(categoryId)
                        if (response.isSuccessful && response.body() != null) {
                            Toast.makeText(this@ManageItemsActivity, "Category deleted", Toast.LENGTH_SHORT).show()
                            // Clear filter if it was on this category
                            if (selectedCategoryId == categoryId) {
                                clearCategoryFilter()
                            }
                            loadCategories()
                            // Refresh items
                            currentPage = 1
                            itemsList.clear()
                            hasMorePages = true
                            loadItems()
                        } else {
                            Toast.makeText(this@ManageItemsActivity, "Failed to delete category", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@ManageItemsActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
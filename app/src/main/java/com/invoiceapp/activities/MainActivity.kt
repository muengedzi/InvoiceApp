package com.invoiceapp.activities

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.invoiceapp.R
import com.invoiceapp.databinding.ActivityMainBinding
import com.invoiceapp.fragments.*
import com.invoiceapp.utils.SessionManager

class MainActivity : BaseActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var sessionManager: SessionManager
    private var currentBottomItemId = R.id.navigation_dashboard
    private var showingMenuPage = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        setupToolbar()
        setupBottomNavigation()
        setupBackHandling()
        setupFAB()
        loadFragment(DashboardFragment())
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "Dashboard"
    }

    private fun setupBottomNavigation() {
        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNavigation.setOnItemSelectedListener { menuItem ->
            currentBottomItemId = menuItem.itemId
            showingMenuPage = false
            supportActionBar?.setDisplayHomeAsUpEnabled(false)
            when (menuItem.itemId) {
                R.id.navigation_dashboard -> {
                    loadFragment(DashboardFragment())
                    supportActionBar?.title = "Dashboard"
                    hideFAB()
                    true
                }
                R.id.navigation_invoices -> {
                    loadFragment(InvoicesFragment())
                    supportActionBar?.title = "Invoices"
                    showFAB()
                    true
                }
                R.id.navigation_customers -> {
                    loadFragment(CustomersFragment())
                    supportActionBar?.title = "Customers"
                    showFAB()
                    true
                }
                R.id.navigation_expenses -> {
                    loadFragment(ExpensesFragment())
                    supportActionBar?.title = "Expenses"
                    showFAB()
                    true
                }
                R.id.navigation_items -> {
                    loadFragment(ItemsFragment())
                    supportActionBar?.title = "Items"
                    showFAB()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupFAB() {
        binding.fab.setOnClickListener {
            val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
            when (currentFragment) {
                is InvoicesFragment -> {
                    startActivity(Intent(this, CreateInvoiceActivity::class.java))
                }
                is CustomersFragment -> {
                    startActivity(Intent(this, AddEditCustomerActivity::class.java))
                }
                is ExpensesFragment -> {
                    startActivity(Intent(this, AddEditExpenseActivity::class.java))
                }
                else -> {
                    // FAB hidden for other fragments
                }
            }
        }
    }

    private fun showFAB() {
        binding.fab.show()
    }

    private fun hideFAB() {
        binding.fab.hide()
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun setupBackHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    showingMenuPage -> {
                        returnToBottomPage()
                    }
                    currentBottomItemId != R.id.navigation_dashboard -> {
                        binding.bottomNavigation.selectedItemId = R.id.navigation_dashboard
                    }
                    else -> moveTaskToBack(true)
                }
            }
        })
    }

    private fun returnToBottomPage() {
        showingMenuPage = false
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        binding.bottomNavigation.selectedItemId = currentBottomItemId
        when (currentBottomItemId) {
            R.id.navigation_dashboard -> {
                loadFragment(DashboardFragment())
                supportActionBar?.title = "Dashboard"
                hideFAB()
            }
            R.id.navigation_invoices -> {
                loadFragment(InvoicesFragment())
                supportActionBar?.title = "Invoices"
                showFAB()
            }
            R.id.navigation_customers -> {
                loadFragment(CustomersFragment())
                supportActionBar?.title = "Customers"
                showFAB()
            }
            R.id.navigation_expenses -> {
                loadFragment(ExpensesFragment())
                supportActionBar?.title = "Expenses"
                showFAB()
            }
            R.id.navigation_items -> {
                loadFragment(ItemsFragment())
                supportActionBar?.title = "Items"
                showFAB()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_reports -> {
                loadFragment(ReportsFragment())
                supportActionBar?.title = "Reports"
                supportActionBar?.setDisplayHomeAsUpEnabled(true)
                showingMenuPage = true
                hideFAB()
                true
            }
            R.id.menu_create_quotation -> {
                val intent = Intent(this, CreateQuotationActivity::class.java)
                startActivity(intent)
                true
            }
            R.id.menu_recurring_invoices -> {
                loadFragment(RecurringInvoicesFragment())
                supportActionBar?.title = "Recurring Invoices"
                supportActionBar?.setDisplayHomeAsUpEnabled(true)
                showingMenuPage = true
                hideFAB()
                true
            }
            R.id.menu_communications -> {
                loadFragment(CommunicationSummaryFragment())
                supportActionBar?.title = "Communications"
                supportActionBar?.setDisplayHomeAsUpEnabled(true)
                showingMenuPage = true
                hideFAB()
                true
            }
            R.id.menu_theme_settings -> {
                startActivity(Intent(this, ThemeSettingsActivity::class.java))
                true
            }
            R.id.menu_template_settings -> {
                startActivity(Intent(this, TemplateSettingsActivity::class.java))
                true
            }
            R.id.menu_company_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.menu_logout -> {
                showLogoutConfirmation()
                true
            }
            android.R.id.home -> {
                returnToBottomPage()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    }

    private fun showLogoutConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { _, _ -> logout() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun logout() {
        sessionManager.clearSession()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    fun navigateToInvoices() {
        navigateToInvoices(null)
    }

    fun navigateToInvoices(statusFilter: String?) {
        currentBottomItemId = R.id.navigation_invoices
        showingMenuPage = false
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        findViewById<BottomNavigationView>(R.id.bottomNavigation).selectedItemId = R.id.navigation_invoices
        loadFragment(InvoicesFragment.newInstance(statusFilter))
        supportActionBar?.title = "Invoices"
        showFAB()
    }

    fun navigateToExpenses() {
        findViewById<BottomNavigationView>(R.id.bottomNavigation).selectedItemId = R.id.navigation_expenses
    }

    fun navigateToRecurringInvoices() {
        loadFragment(RecurringInvoicesFragment())
        supportActionBar?.title = "Recurring Invoices"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        showingMenuPage = true
        hideFAB()
    }

    fun navigateToPendingFollowUps() {
        val intent = Intent(this, PendingFollowUpsActivity::class.java)
        startActivity(intent)
    }
}
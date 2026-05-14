package com.invoiceapp.activities

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.invoiceapp.R
import com.invoiceapp.databinding.ActivityMainBinding
import com.invoiceapp.fragments.*
import com.invoiceapp.utils.SessionManager

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        setupToolbar()
        setupBottomNavigation()
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

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.menu_logout -> {
                sessionManager.clearSession()
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
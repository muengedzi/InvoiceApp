package com.invoiceapp.activities

import android.os.Bundle
import android.widget.Toast
import com.invoiceapp.R
import com.invoiceapp.databinding.ActivityThemeSettingsBinding
import com.invoiceapp.utils.ThemeManager

class ThemeSettingsActivity : BaseActivity() {

    private lateinit var binding: ActivityThemeSettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityThemeSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        loadCurrentTheme()
        setupThemeSelection()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Theme Settings"
    }

    private fun loadCurrentTheme() {
        val currentTheme = ThemeManager.getSavedTheme(this)
        when (currentTheme) {
            ThemeManager.THEME_LIGHT -> binding.radioLight.isChecked = true
            ThemeManager.THEME_DARK -> binding.radioDark.isChecked = true
            ThemeManager.THEME_BLUE -> binding.radioBlue.isChecked = true
            ThemeManager.THEME_GREEN -> binding.radioGreen.isChecked = true
            ThemeManager.THEME_PURPLE -> binding.radioPurple.isChecked = true
        }
    }

    private fun setupThemeSelection() {
        binding.themeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val theme = when (checkedId) {
                R.id.radioLight -> ThemeManager.THEME_LIGHT
                R.id.radioDark -> ThemeManager.THEME_DARK
                R.id.radioBlue -> ThemeManager.THEME_BLUE
                R.id.radioGreen -> ThemeManager.THEME_GREEN
                R.id.radioPurple -> ThemeManager.THEME_PURPLE
                else -> ThemeManager.THEME_LIGHT
            }

            ThemeManager.saveTheme(this, theme)
            Toast.makeText(this, "Theme will apply on next restart", Toast.LENGTH_SHORT).show()
            recreate()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
package com.invoiceapp.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.invoiceapp.utils.ThemeManager

abstract class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply saved theme before super.onCreate
        val savedTheme = ThemeManager.getSavedTheme(this)
        ThemeManager.applyTheme(savedTheme)
        setTheme(ThemeManager.getThemeResId(savedTheme))
        super.onCreate(savedInstanceState)
    }
}

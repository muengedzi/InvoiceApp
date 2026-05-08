// activities/SplashActivity.kt
package com.invoiceapp.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.invoiceapp.utils.SessionManager

class SplashActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private val TAG = "SplashActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            // Try to set content view first
            setContentView(com.invoiceapp.R.layout.activity_splash)

            sessionManager = SessionManager(this)

            // Handle deep link
            intent?.data?.let { uri ->
                handleDeepLink(uri)
                return
            }

            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    if (sessionManager.isLoggedIn()) {
                        startActivity(Intent(this, MainActivity::class.java))
                    } else {
                        startActivity(Intent(this, LoginActivity::class.java))
                    }
                    finish()
                } catch (e: Exception) {
                    Log.e(TAG, "Navigation error: ${e.message}", e)
                }
            }, 2000)
        } catch (e: Exception) {
            Log.e(TAG, "SplashActivity onCreate error: ${e.message}", e)
            // Fallback - go directly to login
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.data?.let { uri ->
            handleDeepLink(uri)
        }
    }

    private fun handleDeepLink(uri: Uri) {
        try {
            when {
                uri.scheme == "invoiceapp" && uri.host == "verify" -> {
                    val token = uri.getQueryParameter("token")
                    if (token != null) {
                        val intent = Intent(this, VerifyEmailActivity::class.java)
                        intent.putExtra("token", token)
                        startActivity(intent)
                        finish()
                    } else {
                        startActivity(Intent(this, LoginActivity::class.java))
                        finish()
                    }
                }
                else -> {
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Deep link error: ${e.message}", e)
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}
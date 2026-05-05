package com.invoiceapp.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.invoiceapp.utils.SessionManager

class SplashActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sessionManager = SessionManager(this)

        // Handle deep link
        intent?.data?.let { uri ->
            handleDeepLink(uri)
            return
        }

        Handler(Looper.getMainLooper()).postDelayed({
            if (sessionManager.isLoggedIn()) {
                startActivity(Intent(this, MainActivity::class.java))
            } else {
                startActivity(Intent(this, LoginActivity::class.java))
            }
            finish()
        }, 2000)
    }

    private fun handleDeepLink(uri: Uri) {
        when {
            uri.host == "10.0.0.150" || uri.host == "verify" -> {
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
    }
}
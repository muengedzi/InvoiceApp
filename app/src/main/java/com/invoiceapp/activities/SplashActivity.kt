// activities/SplashActivity.kt
package com.invoiceapp.activities

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import com.invoiceapp.R
import com.invoiceapp.utils.SessionManager

class SplashActivity : BaseActivity() {

    private lateinit var sessionManager: SessionManager
    private val TAG = "SplashActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            // Try to set content view first
            setContentView(R.layout.activity_splash)
            startSplashAnimations()

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

    private fun startSplashAnimations() {
        findViewById<View>(R.id.imgSplashLogo)?.apply {
            alpha = 0f
            scaleX = 0.9f
            scaleY = 0.9f
            animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(520)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        findViewById<View>(R.id.tvSplashTitle)?.apply {
            alpha = 0f
            translationY = 16f
            animate().alpha(1f).translationY(0f).setStartDelay(120).setDuration(420).start()
        }

        findViewById<View>(R.id.tvSplashSubtitle)?.apply {
            alpha = 0f
            translationY = 16f
            animate().alpha(1f).translationY(0f).setStartDelay(220).setDuration(420).start()
        }

        findViewById<View>(R.id.progressSplash)?.let { progress ->
            ObjectAnimator.ofFloat(progress, View.ROTATION, 0f, 360f).apply {
                duration = 900
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                start()
            }
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

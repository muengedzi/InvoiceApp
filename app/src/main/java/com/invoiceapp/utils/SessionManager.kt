package com.invoiceapp.utils

import android.content.Context
import android.content.SharedPreferences
import com.invoiceapp.models.User

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE)

    fun saveUser(userId: Int, email: String, fullName: String, role: String) {
        prefs.edit().apply {
            putInt(Constants.KEY_USER_ID, userId)
            putString(Constants.KEY_USER_EMAIL, email)
            putString(Constants.KEY_USER_NAME, fullName)
            putString("user_role", role)
            putBoolean(Constants.KEY_IS_LOGGED_IN, true)
            apply()
        }
    }

    fun getUserId(): Int = prefs.getInt(Constants.KEY_USER_ID, -1)
    fun getUserEmail(): String = prefs.getString(Constants.KEY_USER_EMAIL, "") ?: ""
    fun getUserName(): String = prefs.getString(Constants.KEY_USER_NAME, "") ?: ""
    fun getUserRole(): String = prefs.getString("user_role", "user") ?: "user"
    fun isLoggedIn(): Boolean = prefs.getBoolean(Constants.KEY_IS_LOGGED_IN, false)

    fun getCurrentUser(): User? {
        if (!isLoggedIn()) return null
        return User(
            id = getUserId(),
            email = getUserEmail(),
            full_name = getUserName(),
            role = getUserRole(),
            business_name = null
        )
    }

    // Add these methods for session cookie management
    fun saveSessionCookie(cookie: String) {
        prefs.edit().putString("session_cookie", cookie).apply()
    }

    fun getSessionCookie(): String? {
        return prefs.getString("session_cookie", null)
    }

    fun clearSession() {
        prefs.edit().clear().apply()
    }
}
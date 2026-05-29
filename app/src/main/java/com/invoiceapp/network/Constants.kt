package com.invoiceapp.network

object Constants {
    // ONLY CHANGE THIS ONE LINE when your server IP changes
    const val BASE_URL = "http://192.168.8.150:5000/"

    // These are derived automatically from BASE_URL
    val BASE_HOST: String
        get() = BASE_URL.replace("http://", "").replace("https://", "").split(":")[0]

    val BASE_PORT: Int
        get() = BASE_URL.replace("http://", "").replace("https://", "").split(":")[1].split("/")[0].toIntOrNull() ?: 5000

    const val PREF_NAME = "InvoiceAppPref"
    const val KEY_USER_ID = "user_id"
    const val KEY_USER_EMAIL = "user_email"
    const val KEY_USER_NAME = "user_name"
    const val KEY_IS_LOGGED_IN = "is_logged_in"
}
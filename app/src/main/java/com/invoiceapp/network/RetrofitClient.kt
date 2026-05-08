package com.invoiceapp.network

import com.google.gson.GsonBuilder
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val BASE_URL = "http://10.0.0.150:5000/"

    // Manual cookie storage
    private var sessionCookie: String? = null

    private val client: OkHttpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(CookieInterceptor())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(false)
            .build()
    }

    private val gson = GsonBuilder()
        .setLenient()
        .create()

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(ApiService::class.java)
    }

    // Custom interceptor to handle cookies manually
    class CookieInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val builder = chain.request().newBuilder()

            // Add stored cookie to request
            sessionCookie?.let { cookie ->
                builder.addHeader("Cookie", cookie)
                println("📤 Sending cookie: $cookie")
            }

            val response = chain.proceed(builder.build())

            // Save cookie from response
            val setCookie = response.headers("Set-Cookie")
            if (setCookie.isNotEmpty()) {
                val cookieValue = setCookie[0].split(";")[0]
                sessionCookie = cookieValue
                println("📥 Cookie saved: $sessionCookie")
            }

            return response
        }
    }
}
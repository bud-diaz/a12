package com.paperweight.os.network

import android.content.Context
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory

// Retrofit requires an absolute base URL at construction time even though
// the real one is only known after pairing — DynamicBaseUrlInterceptor
// rewrites every request to the paired station, so this value is never
// actually dialed.
private const val PLACEHOLDER_BASE_URL = "https://paperweight.invalid/"

class ApiClient(context: Context) {

    val sessionStore = SessionStore(context.applicationContext)

    private val json = Json { ignoreUnknownKeys = true }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(SessionCookieJar(sessionStore))
        .addInterceptor(DynamicBaseUrlInterceptor(sessionStore))
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(PLACEHOLDER_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    fun <T> create(service: Class<T>): T = retrofit.create(service)

    val auth: AuthApi by lazy { create(AuthApi::class.java) }
    val stream: StreamApi by lazy { create(StreamApi::class.java) }
    val library: LibraryApi by lazy { create(LibraryApi::class.java) }
    val analytics: DashboardAnalyticsApi by lazy { create(DashboardAnalyticsApi::class.java) }
    val earnings: DashboardEarningsApi by lazy { create(DashboardEarningsApi::class.java) }
}

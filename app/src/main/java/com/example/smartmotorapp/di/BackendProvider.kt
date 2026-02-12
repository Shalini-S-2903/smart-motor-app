package com.example.smartmotorapp.di

import com.example.smartmotorapp.data.BackendConfig
import com.example.smartmotorapp.data.remote.BackendApi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Provides BackendApi. Base URL from BuildConfig (configurable per build).
 * No secrets in app — backend holds Tuya credentials.
 */
object BackendProvider {

    private val baseUrl: String = BackendConfig.BACKEND_BASE_URL
        .trimEnd('/')
        .ifEmpty { "http://10.0.2.2:8000" }

    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .apply {
            val log = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            addInterceptor(log)
        }
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("$baseUrl/")
        .client(okHttp)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: BackendApi = retrofit.create(BackendApi::class.java)
}

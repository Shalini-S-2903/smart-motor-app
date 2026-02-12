package com.example.smartmotorapp.di

import android.content.Context
import com.example.smartmotorapp.data.BackendConfig
import com.example.smartmotorapp.data.TokenStore
import com.example.smartmotorapp.data.remote.AuthApi
import com.example.smartmotorapp.data.remote.BackendApi
import com.example.smartmotorapp.data.remote.DeviceEventDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Provides BackendApi (authenticated) and AuthApi (no token).
 * Base URL from BuildConfig via BackendConfig.
 * Must call init(context) before using (e.g. in MainActivity.onCreate).
 */
object BackendProvider {

    private var _tokenStore: TokenStore? = null
    val tokenStore: TokenStore
        get() = _tokenStore ?: error("BackendProvider not initialized. Call init(context) first.")

    private val baseUrl: String
        get() = BackendConfig.baseUrl.ifEmpty { "http://10.0.2.2:8000" }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val timeouts = 15L to TimeUnit.SECONDS

    private var _authApi: AuthApi? = null
    val authApi: AuthApi
        get() = _authApi ?: error("BackendProvider not initialized. Call init(context) first.")

    private var _api: BackendApi? = null
    val api: BackendApi
        get() = _api ?: error("BackendProvider not initialized. Call init(context) first.")

    private var _deviceEventScope: CoroutineScope? = null
    private var _deviceEventFlow: MutableSharedFlow<DeviceEventDto>? = null
    /** Realtime device events from SSE (event: device_event). Collect in DeviceDetailViewModel. */
    val deviceEventFlow: SharedFlow<DeviceEventDto>
        get() = _deviceEventFlow?.asSharedFlow() ?: error("BackendProvider not initialized. Call init(context) first.")

    /** Call from SSE client when event: device_event is received. Thread-safe. */
    fun offerDeviceEvent(event: DeviceEventDto) {
        _deviceEventScope?.launch {
            _deviceEventFlow?.emit(event)
        }
    }

    fun init(context: Context) {
        if (_tokenStore != null) return
        _tokenStore = TokenStore(context.applicationContext)
        val store = _tokenStore!!

        val noAuthClient = OkHttpClient.Builder()
            .connectTimeout(timeouts.first, timeouts.second)
            .readTimeout(timeouts.first, timeouts.second)
            .writeTimeout(timeouts.first, timeouts.second)
            .addInterceptor(loggingInterceptor)
            .build()

        val authRetrofit = Retrofit.Builder()
            .baseUrl(baseUrl.ensureEndSlash())
            .client(noAuthClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        _authApi = authRetrofit.create(AuthApi::class.java)
        val authApi = _authApi!!

        val authClient = OkHttpClient.Builder()
            .connectTimeout(timeouts.first, timeouts.second)
            .readTimeout(timeouts.first, timeouts.second)
            .writeTimeout(timeouts.first, timeouts.second)
            .addInterceptor(AuthInterceptor(store, authApi))
            .addInterceptor(loggingInterceptor)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl.ensureEndSlash())
            .client(authClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        _api = retrofit.create(BackendApi::class.java)
        _deviceEventScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        _deviceEventFlow = MutableSharedFlow(extraBufferCapacity = 64)
    }

    private fun String.ensureEndSlash(): String = if (this.endsWith("/")) this else "$this/"
}

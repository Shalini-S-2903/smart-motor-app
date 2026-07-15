package com.example.smartmotorapp.data.sse

import com.example.smartmotorapp.data.BackendConfig
import com.example.smartmotorapp.data.remote.DeviceDto
import com.example.smartmotorapp.data.remote.DeviceEventDto
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * SSE client for GET /events. Parses device_update and device_event;
 * notifies listeners. Auto-reconnects after 3s on failure.
 * If tokenProvider is set, adds Authorization: Bearer for authenticated /events.
 */
class SseClient(
    private val listener: (DeviceDto) -> Unit,
    private val tokenProvider: (() -> String?)? = null,
    private val deviceEventListener: ((DeviceEventDto) -> Unit)? = null,
) {
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // no timeout for long-lived stream
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private var job: Job? = null

    fun start() {
        job?.cancel()
        job = scope.launch {
            while (true) {
                runCatching { connectAndRead() }
                delay(RECONNECT_DELAY_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun connectAndRead() {
        val url = "${BackendConfig.baseUrl}/events"
        val builder = Request.Builder().url(url).get()
        builder.addHeader("bypass-tunnel-reminder", "true")
        tokenProvider?.invoke()?.let { token ->
            if (token.isNotBlank()) builder.addHeader("Authorization", "Bearer $token")
        }
        val request = builder.build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return
            val body = response.body ?: return
            val reader = BufferedReader(InputStreamReader(body.byteStream(), Charsets.UTF_8))
            var eventType: String? = null
            var dataBuffer = StringBuilder()
            reader.lineSequence().forEach { line ->
                when {
                    line.startsWith("event:") -> eventType = line.removePrefix("event:").trim()
                    line.startsWith("data:") -> dataBuffer.append(line.removePrefix("data:").trim())
                    line.isEmpty() -> {
                        if (dataBuffer.isNotEmpty()) {
                            when (eventType) {
                                "device_update" -> runCatching {
                                    val root = gson.fromJson(dataBuffer.toString(), JsonObject::class.java)
                                    val device = root.getAsJsonObject("device") ?: return@runCatching
                                    fun JsonObject.str(key: String): String? =
                                        get(key)?.takeIf { !it.isJsonNull }?.asJsonPrimitive?.asString
                                    fun JsonObject.bool(key: String): Boolean =
                                        get(key)?.takeIf { !it.isJsonNull }?.asJsonPrimitive?.asBoolean ?: false
                                    fun JsonObject.dbl(key: String): Double? =
                                        get(key)?.takeIf { !it.isJsonNull }?.asJsonPrimitive?.asDouble
                                    val id = device.str("id") ?: return@runCatching
                                    val dto = DeviceDto(
                                        id = id,
                                        online = device.bool("online"),
                                        lastSeen = device.str("last_seen"),
                                        lastState = device.str("last_state"),
                                        lastPower = device.dbl("last_power"),
                                        description = device.str("description") ?: return@runCatching
                                    )
                                    listener(dto)
                                }
                                "device_event" -> runCatching {
                                    val dto = gson.fromJson(dataBuffer.toString(), DeviceEventDto::class.java)
                                    deviceEventListener?.invoke(dto)
                                }
                            }
                        }
                        eventType = null
                        dataBuffer = StringBuilder()
                    }
                }
            }
        }
    }

    companion object {
        private const val RECONNECT_DELAY_MS = 3000L
    }
}

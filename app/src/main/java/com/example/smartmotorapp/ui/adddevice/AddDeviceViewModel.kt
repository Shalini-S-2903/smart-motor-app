package com.example.smartmotorapp.ui.adddevice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartmotorapp.data.remote.ClaimRequestDto
import com.example.smartmotorapp.data.remote.ClaimResponseDto
import com.example.smartmotorapp.di.BackendProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class AddDeviceUiState(
    val isLoading: Boolean = false,
    val isClaimed: Boolean = false,
    val errorMessage: String? = null,
    val isProvisioning: Boolean = false,
    val provisionSuccess: Boolean = false,
    val wifiSsid: String = "",
    val wifiPass: String = "",
    val deviceId: String? = null,
)

class AddDeviceViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AddDeviceUiState())
    val uiState: StateFlow<AddDeviceUiState> = _uiState.asStateFlow()

    private val api get() = BackendProvider.api
    private val localClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    fun updateWifiSsid(value: String) {
        _uiState.update { it.copy(wifiSsid = value, errorMessage = null) }
    }

    fun updateWifiPass(value: String) {
        _uiState.update { it.copy(wifiPass = value, errorMessage = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun claimDevice() {
        if (_uiState.value.isLoading) return
        val deviceId = _uiState.value.deviceId ?: return

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            runCatching {
                val res = api.claimDevice(ClaimRequestDto(deviceId = deviceId))
                if (!res.isSuccessful) {
                    val msg = res.errorBody()?.string() ?: "Failed to claim device"
                    throw Exception(msg)
                }
                res.body()
            }.fold(
                onSuccess = { _: ClaimResponseDto? ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isClaimed = true,
                            errorMessage = null
                        )
                    }
                },
                onFailure = { e: Throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isClaimed = false,
                            errorMessage = e.message ?: "Failed to claim device"
                        )
                    }
                },
            )
        }
    }

    fun provisionDevice() {
        val ssid = _uiState.value.wifiSsid.trim()
        val pass = _uiState.value.wifiPass.trim()

        if (ssid.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "SSID is required") }
            return
        }

        _uiState.update { it.copy(isProvisioning = true, errorMessage = null) }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val json = JSONObject().apply {
                    put("ssid", ssid)
                    put("password", pass)
                }
                val body = json.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("http://192.168.4.1/provision")
                    .post(body)
                    .build()

                localClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw Exception("Failed to send credentials to device")
                    val body = response.body?.string() ?: "{}"
                    val respObj = JSONObject(body)
                    respObj.optString("device_id").takeIf { it.isNotBlank() }
                }
            }.fold(
                onSuccess = { devId ->
                    _uiState.update { 
                        it.copy(
                            isProvisioning = false, 
                            provisionSuccess = true,
                            deviceId = devId
                        ) 
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isProvisioning = false,
                            errorMessage = "Make sure you are connected to SmartMotor-Setup WiFi"
                        )
                    }
                }
            )
        }
    }
}
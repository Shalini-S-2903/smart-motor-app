package com.example.smartmotorapp.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartmotorapp.data.remote.CommandRequestDto
import com.example.smartmotorapp.data.remote.DeviceDto
import com.example.smartmotorapp.di.BackendProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = false,
    val devices: List<DeviceDto> = emptyList(),
    val errorMessage: String? = null,
    val turningId: String? = null, // device id currently sending a command
    val snackbarMessage: String? = null,
)

class HomeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val api by lazy { BackendProvider.api }

    private val backendOfflineMessage = "Backend offline"

    fun loadDevices() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            runCatching {
                val healthRes = api.health()
                if (!healthRes.isSuccessful || healthRes.body()?.status != "ok") {
                    throw Exception(backendOfflineMessage)
                }
            }.fold(
                onSuccess = { fetchDevices() },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = if (e.message == backendOfflineMessage) backendOfflineMessage else (e.message ?: backendOfflineMessage),
                    )
                }
            )
        }
    }

    private suspend fun fetchDevices() {
        runCatching {
            val res = api.getDevices()
            if (res.isSuccessful) {
                res.body()?.devices ?: emptyList()
            } else {
                throw Exception(res.message() ?: "Failed to load devices")
            }
        }.fold(
            onSuccess = { list ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    devices = list,
                    errorMessage = null,
                )
            },
            onFailure = { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Unknown error",
                )
            }
        )
    }

    fun setDeviceOn(deviceId: String) {
        sendCommand(deviceId, "ON")
    }

    fun setDeviceOff(deviceId: String) {
        sendCommand(deviceId, "OFF")
    }

    private fun sendCommand(deviceId: String, action: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(turningId = deviceId)
            runCatching {
                // 1) send command, get command_id
                val sendRes = api.sendCommand(deviceId, CommandRequestDto(action))
                if (!sendRes.isSuccessful) {
                    throw Exception(sendRes.message() ?: "Failed to send command")
                }
                val commandId = sendRes.body()?.commandId
                    ?: throw Exception("Missing command_id")

                // 2) poll status every 1s up to 15s
                val start = System.currentTimeMillis()
                var status = "PENDING"
                while (status == "PENDING" && System.currentTimeMillis() - start < 15_000) {
                    delay(1_000)
                    val statusRes = api.getCommandStatus(commandId)
                    if (!statusRes.isSuccessful) {
                        throw Exception(statusRes.message() ?: "Failed to read command status")
                    }
                    status = statusRes.body()?.status ?: "FAILED"
                }
                status
            }.fold(
                onSuccess = { status ->
                    if (status == "SUCCESS") {
                        loadDevices()
                    } else {
                        _uiState.value = _uiState.value.copy(
                            snackbarMessage = "Command failed for $deviceId",
                        )
                    }
                    _uiState.value = _uiState.value.copy(turningId = null)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        turningId = null,
                        snackbarMessage = e.message ?: "Command failed",
                    )
                }
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun clearSnackbar() {
        _uiState.value = _uiState.value.copy(snackbarMessage = null)
    }
}

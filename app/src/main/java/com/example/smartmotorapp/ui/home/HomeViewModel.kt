package com.example.smartmotorapp.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartmotorapp.data.remote.CommandRequestDto
import com.example.smartmotorapp.data.remote.DeviceDto
import com.example.smartmotorapp.data.sse.SseClient
import com.example.smartmotorapp.di.BackendProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeUiState(
    val isInitialLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val devices: Map<String, DeviceDto> = emptyMap(),
    val backendOffline: Boolean = false,
    val errorMessage: String? = null,
    val turningId: String? = null,
    val snackbarMessage: String? = null,
)

class HomeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(isInitialLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val api by lazy { BackendProvider.api }
    private val backendOfflineMessage = "Server unreachable"

    private var sseClient: SseClient? = null
    private var commandTimeoutJob: Job? = null

    init {
        startSse()
    }

    private fun startSse() {
        sseClient?.stop()
        sseClient = SseClient(
            listener = { dto ->
                viewModelScope.launch {
                    withContext(Dispatchers.Main) {
                        val current = _uiState.value
                        val newMap = current.devices.toMutableMap().apply { put(dto.id, dto) }
                        val wasTurning = current.turningId == dto.id
                        val newTurningId = if (wasTurning) null else current.turningId
                        if (wasTurning) commandTimeoutJob?.cancel()
                        _uiState.value = current.copy(
                            devices = newMap,
                            turningId = newTurningId,
                        )
                    }
                }
            },
            tokenProvider = { BackendProvider.tokenStore.getAccessToken() },
            deviceEventListener = { BackendProvider.offerDeviceEvent(it) },
        )
        sseClient?.start()
    }

    fun loadDevices(initial: Boolean = false) {
        viewModelScope.launch {
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    isInitialLoading = initial,
                    isRefreshing = !initial,
                    backendOffline = false,
                    errorMessage = null,
                )
            }
            runCatching {
                val healthRes = api.health()
                if (!healthRes.isSuccessful) throw Exception(backendOfflineMessage)
                val health = healthRes.body() ?: throw Exception(backendOfflineMessage)
                if (health.status != "ok" || !health.mqttConnected) throw Exception(backendOfflineMessage)
            }.fold(
                onSuccess = { fetchDevices() },
                onFailure = {
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            isInitialLoading = false,
                            isRefreshing = false,
                            backendOffline = true,
                            errorMessage = backendOfflineMessage,
                        )
                    }
                }
            )
        }
    }

    private suspend fun fetchDevices() {
        runCatching {
            val res = api.getDevices()
            if (res.isSuccessful) res.body()?.devices ?: emptyList()
            else throw Exception(res.message() ?: "Failed to load devices")
        }.fold(
            onSuccess = { list ->
                val map = list.associateBy { it.id }
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isInitialLoading = false,
                        isRefreshing = false,
                        devices = map,
                        backendOffline = false,
                        errorMessage = null,
                    )
                }
            },
            onFailure = { e ->
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isInitialLoading = false,
                        isRefreshing = false,
                        errorMessage = e.message ?: "Unknown error",
                    )
                }
            }
        )
    }

    fun setDeviceOn(deviceId: String) = sendCommand(deviceId, "ON")
    fun setDeviceOff(deviceId: String) = sendCommand(deviceId, "OFF")

    private fun sendCommand(deviceId: String, action: String) {
        viewModelScope.launch {
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(turningId = deviceId)
            }

            commandTimeoutJob?.cancel()
            commandTimeoutJob = viewModelScope.launch {
                delay(15_000)
                withContext(Dispatchers.Main) {
                    if (_uiState.value.turningId == deviceId) {
                        _uiState.value = _uiState.value.copy(
                            turningId = null,
                            snackbarMessage = "Device did not respond",
                        )
                    }
                }
            }

            runCatching {
                val sendRes = api.sendCommand(deviceId, CommandRequestDto(action))
                if (!sendRes.isSuccessful) throw Exception("network")
            }.onFailure {
                commandTimeoutJob?.cancel()
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        turningId = null,
                        snackbarMessage = "Network error while sending command",
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun clearSnackbar() {
        _uiState.value = _uiState.value.copy(snackbarMessage = null)
    }

    override fun onCleared() {
        sseClient?.stop()
        commandTimeoutJob?.cancel()
        super.onCleared()
    }
}

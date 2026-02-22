package com.example.smartmotorapp.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartmotorapp.data.remote.CommandRequestDto
import com.example.smartmotorapp.data.remote.DeviceDto
import com.example.smartmotorapp.data.remote.RoomDto
import com.example.smartmotorapp.data.sse.SseClient
import com.example.smartmotorapp.di.BackendProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

    private val _rooms = MutableStateFlow<List<RoomDto>>(emptyList())
    val rooms: StateFlow<List<RoomDto>> = _rooms.asStateFlow()

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

    fun loadRooms() {
        viewModelScope.launch {
            try {
                _rooms.value = api.getRooms().body() ?: emptyList()
            } catch (_: Exception) { }
        }
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
                if (health.status != "ok") throw Exception(backendOfflineMessage)
            }.fold(
                onSuccess = {
                    loadRooms()
                    fetchDevices()
                },
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

    fun setRoomOn(roomId: Int) = sendRoomCommand(roomId, "ON")
    fun setRoomOff(roomId: Int) = sendRoomCommand(roomId, "OFF")

    private fun sendRoomCommand(roomId: Int, action: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            runCatching {
                api.controlRoom(roomId, mapOf("action" to action))
            }.onSuccess {
                // Refresh to show new states
                delay(2000) // Give it a moment to process
                loadDevices(initial = false)
            }.onFailure {
                _uiState.update { it.copy(isRefreshing = false, snackbarMessage = "Failed to send room command") }
            }
        }
    }

    private fun sendCommand(deviceId: String, action: String) {
        viewModelScope.launch {
            val oldState = _uiState.value.devices[deviceId]
            
            withContext(Dispatchers.Main) {
                // Optimistic update: Toggle state locally immediately
                _uiState.update { current ->
                    val updatedDevices = current.devices.toMutableMap()
                    val device = updatedDevices[deviceId]
                    if (device != null) {
                        updatedDevices[deviceId] = device.copy(lastState = action)
                    }
                    current.copy(
                        devices = updatedDevices,
                        turningId = deviceId,
                        errorMessage = null
                    )
                }
            }

            commandTimeoutJob?.cancel()
            commandTimeoutJob = viewModelScope.launch {
                delay(15_000) // Reduced timeout for better feel
                withContext(Dispatchers.Main) {
                    if (_uiState.value.turningId == deviceId) {
                        // Revert on timeout
                        _uiState.update { current ->
                            val revertedDevices = current.devices.toMutableMap()
                            if (oldState != null) {
                                revertedDevices[deviceId] = oldState
                            }
                            current.copy(
                                devices = revertedDevices,
                                turningId = null,
                                snackbarMessage = "Device did not respond",
                            )
                        }
                    }
                }
            }

            runCatching {
                val sendRes = api.sendCommand(deviceId, CommandRequestDto(action))
                if (!sendRes.isSuccessful) throw Exception("network")
            }.onFailure {
                commandTimeoutJob?.cancel()
                withContext(Dispatchers.Main) {
                    // Revert on failure
                    _uiState.update { current ->
                        val revertedDevices = current.devices.toMutableMap()
                        if (oldState != null) {
                            revertedDevices[deviceId] = oldState
                        }
                        current.copy(
                            devices = revertedDevices,
                            turningId = null,
                            snackbarMessage = "Network error while sending command",
                        )
                    }
                }
            }
        }
    }

    fun removeDevice(deviceId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            runCatching {
                api.removeDevice(deviceId)
            }.onSuccess {
                _uiState.update { it.copy(snackbarMessage = "Device removed") }
                loadDevices(initial = false)
            }.onFailure {
                _uiState.update { it.copy(isRefreshing = false, snackbarMessage = "Failed to remove device") }
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

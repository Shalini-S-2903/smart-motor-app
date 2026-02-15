package com.example.smartmotorapp.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.smartmotorapp.data.remote.CommandRequestDto
import com.example.smartmotorapp.data.remote.DeviceDto
import com.example.smartmotorapp.data.remote.EventDto
import com.example.smartmotorapp.di.BackendProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DeviceDetailUiState(
    val deviceId: String,
    val device: DeviceDto? = null,
    val events: List<EventDto> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val turning: Boolean = false,
)

class DeviceDetailViewModel(
    private val deviceId: String,
) : ViewModel() {

    private val api by lazy { BackendProvider.api }

    private val _uiState = MutableStateFlow(DeviceDetailUiState(deviceId))
    val uiState: StateFlow<DeviceDetailUiState> = _uiState.asStateFlow()

    init {
        refresh()
        observeRealtimeEvents()
    }

    // ---------------------------
    // PUBLIC ACTIONS
    // ---------------------------

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val deviceRes = api.getDevice(deviceId)
                    val eventsRes = api.getDeviceEvents(deviceId)

                    if (!deviceRes.isSuccessful) error("Failed to load device")
                    if (!eventsRes.isSuccessful) error("Failed to load events")

                    Pair(deviceRes.body(), eventsRes.body() ?: emptyList())
                }
            }

            result.onSuccess { (device, events) ->
                _uiState.update {
                    it.copy(
                        device = device,
                        events = events,
                        isLoading = false,
                        error = null
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Unknown error"
                    )
                }
            }
        }
    }

    fun setDeviceOn() = sendCommand("ON")
    fun setDeviceOff() = sendCommand("OFF")

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // ---------------------------
    // PRIVATE
    // ---------------------------

    private fun sendCommand(action: String) {
        viewModelScope.launch {

            _uiState.update { it.copy(turning = true) }

            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val res = api.sendCommand(deviceId, CommandRequestDto(action))
                    if (!res.isSuccessful) error("Network error")
                }
            }

            result.onSuccess {
                refresh() // reload device state after command
            }.onFailure { e ->
                _uiState.update {
                    it.copy(error = e.message ?: "Command failed")
                }
            }

            _uiState.update { it.copy(turning = false) }
        }
    }

    private fun observeRealtimeEvents() {
        viewModelScope.launch {
            BackendProvider.deviceEventFlow
                .filter { it.deviceId == deviceId }
                .collect { dto ->
                    val newEvent = EventDto(
                        timestamp = dto.timestamp,
                        type = dto.type,
                        message = dto.message
                    )

                    _uiState.update {
                        it.copy(events = listOf(newEvent) + it.events.take(49))
                    }
                }
        }
    }
}

class DeviceDetailViewModelFactory(
    private val deviceId: String
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DeviceDetailViewModel::class.java)) {
            return DeviceDetailViewModel(deviceId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

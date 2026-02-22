package com.example.smartmotorapp.ui.rooms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartmotorapp.data.remote.DeviceDto
import com.example.smartmotorapp.data.remote.RoomCreateDto
import com.example.smartmotorapp.data.remote.RoomDeviceAddDto
import com.example.smartmotorapp.di.BackendProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RoomCreateUiState(
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val errorMessage: String? = null,
    val availableDevices: List<DeviceDto> = emptyList(),
    val selectedDeviceIds: Set<String> = emptySet()
)

class RoomCreateViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(RoomCreateUiState())
    val uiState: StateFlow<RoomCreateUiState> = _uiState.asStateFlow()

    private val roomApi get() = BackendProvider.roomApi
    private val api get() = BackendProvider.api

    init {
        loadAvailableDevices()
    }

    private fun loadAvailableDevices() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            runCatching {
                api.getDevices().body()?.devices ?: emptyList()
            }.fold(
                onSuccess = { devices ->
                    _uiState.update { it.copy(isLoading = false, availableDevices = devices) }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
                }
            )
        }
    }

    fun toggleDeviceSelection(deviceId: String) {
        _uiState.update { current ->
            val newSelection = if (current.selectedDeviceIds.contains(deviceId)) {
                current.selectedDeviceIds - deviceId
            } else {
                current.selectedDeviceIds + deviceId
            }
            current.copy(selectedDeviceIds = newSelection)
        }
    }

    fun createRoom(name: String, description: String?) {
        if (name.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Room name cannot be empty") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching {
                val roomRes = roomApi.createRoom(RoomCreateDto(name, description))
                if (!roomRes.isSuccessful) throw Exception("Failed to create room")
                val room = roomRes.body()!!

                // Add selected devices
                _uiState.value.selectedDeviceIds.forEach { deviceId ->
                    roomApi.addDeviceToRoom(room.id, RoomDeviceAddDto(deviceId))
                }
            }.fold(
                onSuccess = {
                    _uiState.update { it.copy(isLoading = false, isSuccess = true) }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
                }
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

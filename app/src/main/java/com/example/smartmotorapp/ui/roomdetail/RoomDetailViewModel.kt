package com.example.smartmotorapp.ui.roomdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartmotorapp.data.remote.DeviceDto
import com.example.smartmotorapp.di.BackendProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RoomDetailViewModel : ViewModel() {

    private val api by lazy { BackendProvider.api }

    private val _devices = MutableStateFlow<List<DeviceDto>>(emptyList())
    val devices: StateFlow<List<DeviceDto>> = _devices.asStateFlow()

    private val _allDevices = MutableStateFlow<List<DeviceDto>>(emptyList())
    val allDevices: StateFlow<List<DeviceDto>> = _allDevices.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    fun load(roomId: Int) {
        viewModelScope.launch {
            _loading.value = true
            try {
                _devices.value = api.getRoomDevices(roomId).body() ?: emptyList()
            } catch (_: Exception) {
                _devices.value = emptyList()
            } finally {
                _loading.value = false
            }
        }
    }

    fun turnOn(roomId: Int) {
        viewModelScope.launch {
            try {
                api.controlRoom(roomId, mapOf("action" to "ON"))
                load(roomId)
            } catch (_: Exception) { }
        }
    }

    fun turnOff(roomId: Int) {
        viewModelScope.launch {
            try {
                api.controlRoom(roomId, mapOf("action" to "OFF"))
                load(roomId)
            } catch (_: Exception) { }
        }
    }

    fun loadAvailableDevices() {
        viewModelScope.launch {
            try {
                _allDevices.value = api.getDevices().body()?.devices ?: emptyList()
            } catch (_: Exception) {
                _allDevices.value = emptyList()
            }
        }
    }

    fun addDevice(roomId: Int, deviceId: String) {
        viewModelScope.launch {
            try {
                api.addDeviceToRoom(roomId, mapOf("device_id" to deviceId))
                load(roomId)
            } catch (_: Exception) { }
        }
    }

    fun removeDevice(roomId: Int, deviceId: String) {
        viewModelScope.launch {
            try {
                api.removeDeviceFromRoom(roomId, deviceId)
                load(roomId)
            } catch (_: Exception) { }
        }
    }
}

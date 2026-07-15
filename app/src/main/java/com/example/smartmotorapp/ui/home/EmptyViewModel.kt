package com.example.smartmotorapp.ui.home

import androidx.lifecycle.ViewModel
import com.example.smartmotorapp.data.remote.RoomDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class EmptyViewModel : ViewModel() {
    // We create a "Dummy" state so your UI doesn't crash
    // when it tries to read uiState
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _rooms = MutableStateFlow<List<RoomDto>>(emptyList())
    val rooms: StateFlow<List<RoomDto>> = _rooms.asStateFlow()

    // Empty functions so the UI calls don't break
    fun loadDevices(initial: Boolean = false) {}
    fun clearSnackbar() {}
    fun setRoomOn(id: Int) {}
    fun setRoomOff(id: Int) {}
    fun setDeviceOn(id: String) {}
    fun setDeviceOff(id: String) {}
}

// Ensure this matches your actual State class name

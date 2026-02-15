package com.example.smartmotorapp.ui.rooms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartmotorapp.data.remote.RoomApi
import com.example.smartmotorapp.data.remote.RoomDto
import com.example.smartmotorapp.di.BackendProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RoomUiState(
    val isLoading: Boolean = false,
    val rooms: List<RoomDto> = emptyList(),
    val errorMessage: String? = null,
)

class RoomViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(RoomUiState())
    val uiState: StateFlow<RoomUiState> = _uiState.asStateFlow()

    private val roomApi get() = BackendProvider.roomApi

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun loadRooms() {
        if (_uiState.value.isLoading) return

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            runCatching {
                val res = roomApi.getRooms()
                if (!res.isSuccessful) {
                    val msg = res.errorBody()?.string() ?: "Failed to load rooms"
                    throw Exception(msg)
                }
                res.body() ?: emptyList()
            }.fold(
                onSuccess = { rooms ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            rooms = rooms,
                            errorMessage = null
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            rooms = emptyList(),
                            errorMessage = e.message ?: "Failed to load rooms"
                        )
                    }
                },
            )
        }
    }
}
package com.example.smartmotorapp.ui.adddevice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartmotorapp.data.remote.ClaimRequestDto
import com.example.smartmotorapp.data.remote.ClaimResponseDto
import com.example.smartmotorapp.di.BackendProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddDeviceUiState(
    val isLoading: Boolean = false,
    val isClaimed: Boolean = false,
    val errorMessage: String? = null,
)

class AddDeviceViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AddDeviceUiState())
    val uiState: StateFlow<AddDeviceUiState> = _uiState.asStateFlow()

    private val api get() = BackendProvider.api

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun claimDevice(pairCode: String) {
        if (_uiState.value.isLoading) return

        if (pairCode.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Pair code is required") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            runCatching {
                val res = api.claimDevice(ClaimRequestDto(pairCode = pairCode.trim()))
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
}
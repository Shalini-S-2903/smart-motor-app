package com.example.smartmotorapp.ui.adddevice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartmotorapp.data.remote.ClaimRequestDto
import com.example.smartmotorapp.di.BackendProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddDeviceUiState(
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val errorMessage: String? = null,
)

class AddDeviceViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AddDeviceUiState())
    val uiState: StateFlow<AddDeviceUiState> = _uiState.asStateFlow()

    private val api get() = BackendProvider.api

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun claim(pairCode: String) {
        if (_uiState.value.isLoading) return

        val code = pairCode.trim()
        if (code.length != 6) {
            _uiState.update { it.copy(errorMessage = "Enter the 6-digit code") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            runCatching {
                val res = api.claimDevice(ClaimRequestDto(pairCode = code))
                if (!res.isSuccessful) {
                    throw Exception("Invalid or expired code")
                }
            }.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(isLoading = false, isSuccess = true, errorMessage = null)
                    }
                },
                onFailure = {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isSuccess = false,
                            errorMessage = "Invalid or expired code",
                        )
                    }
                },
            )
        }
    }
}

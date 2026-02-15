package com.example.smartmotorapp.ui.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartmotorapp.data.remote.RegisterRequestDto
import com.example.smartmotorapp.di.BackendProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RegisterUiState(
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val errorMessage: String? = null,
)

class RegisterViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    private val authApi get() = BackendProvider.authApi

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun register(email: String, password: String) {
        if (_uiState.value.isLoading) return

        if (email.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Email and password are required") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            runCatching {
                val res = authApi.register(RegisterRequestDto(email = email.trim(), password = password))
                if (!res.isSuccessful) {
                    val msg = res.errorBody()?.string() ?: "Registration failed"
                    throw Exception(msg)
                }
            }.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(isLoading = false, isSuccess = true, errorMessage = null)
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isSuccess = false,
                            errorMessage = e.message ?: "Registration failed",
                        )
                    }
                },
            )
        }
    }
}

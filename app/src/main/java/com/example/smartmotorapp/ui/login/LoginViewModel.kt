package com.example.smartmotorapp.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val errorMessage: String? = null,
)

class LoginViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun login(
        email: String,
        password: String,
        countryCode: String = DEFAULT_COUNTRY_CODE,
    ) {
        if (_uiState.value.isLoading) return  // prevent double tap

        if (email.isBlank() || password.isBlank()) {
            _uiState.update {
                it.copy(errorMessage = "Email and password are required")
            }
            return
        }

        _uiState.update {
            it.copy(isLoading = true, errorMessage = null)
        }

        // Stub login: pretend it succeeded (since real Tuya SDK is removed).
        viewModelScope.launch {
            delay(500)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isLoggedIn = true,
                    errorMessage = null,
                )
            }
        }
    }

    /** Dev-only: skip real login and mark as logged in (for testing without Tuya SDK). */
    fun devLoginBypass() {
        _uiState.update {
            it.copy(
                isLoggedIn = true,
                errorMessage = null,
                isLoading = false,
            )
        }
    }

    companion object {
        private const val DEFAULT_COUNTRY_CODE = "91"
    }
}


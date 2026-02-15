package com.example.smartmotorapp.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartmotorapp.data.remote.LoginRequestDto
import com.example.smartmotorapp.di.BackendProvider
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

    private val authApi get() = BackendProvider.authApi
    private val tokenStore get() = BackendProvider.tokenStore

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun login(email: String, password: String) {
        if (_uiState.value.isLoading) return

        if (email.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Email and password are required") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            runCatching {
                val res = authApi.login(LoginRequestDto(email = email.trim(), password = password))
                if (!res.isSuccessful) {
                    val msg = res.errorBody()?.string() ?: "Login failed"
                    throw Exception(msg)
                }
                val body = res.body() ?: throw Exception("No token in response")
                tokenStore.saveTokens(body.accessToken, body.refreshToken)
            }.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(isLoading = false, isLoggedIn = true, errorMessage = null)
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLoggedIn = false,
                            errorMessage = e.message ?: "Login failed",
                        )
                    }
                },
            )
        }
    }
}

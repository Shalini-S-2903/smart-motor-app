package com.example.smartmotorapp.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartmotorapp.data.remote.ActivityDto
import com.example.smartmotorapp.di.BackendProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ActivityUiState(
    val isLoading: Boolean = false,
    val activities: List<ActivityDisplayItem> = emptyList(),
    val errorMessage: String? = null
)

data class ActivityDisplayItem(
    val id: Int,
    val title: String,
    val details: String?,
    val timestamp: String,
    val date: String
)

class ActivityViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ActivityUiState())
    val uiState: StateFlow<ActivityUiState> = _uiState.asStateFlow()

    private val api by lazy { BackendProvider.api }
    private val istZone = ZoneId.of("Asia/Kolkata")
    private val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault())
    private val dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault())

    init {
        loadActivities()
    }

    fun loadActivities() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            runCatching {
                val res = api.getActivities()
                val dtos = if (res.isSuccessful) {
                    res.body() ?: emptyList()
                } else {
                    throw Exception("Failed to load activities")
                }
                dtos.map { it.toDisplayItem() }
            }.fold(
                onSuccess = { displayItems ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        activities = displayItems
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = e.message
                    )
                }
            )
        }
    }

    private fun ActivityDto.toDisplayItem(): ActivityDisplayItem {
        val instant = try {
            Instant.parse(timestamp)
        } catch (e: Exception) {
            Instant.now()
        }
        val zonedDateTime = instant.atZone(istZone)
        
        val title = when (activityType) {
            "DEVICE_ON" -> "Motor Turned ON"
            "DEVICE_OFF" -> "Motor Turned OFF"
            "LOGIN" -> "User Login"
            else -> activityType.replace("_", " ")
        }

        return ActivityDisplayItem(
            id = id,
            title = title,
            details = details,
            timestamp = zonedDateTime.format(timeFormatter),
            date = zonedDateTime.format(dateFormatter)
        )
    }
}

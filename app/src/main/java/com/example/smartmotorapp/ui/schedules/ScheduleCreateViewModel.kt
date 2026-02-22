package com.example.smartmotorapp.ui.schedules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartmotorapp.data.remote.ScheduleCreateDto
import com.example.smartmotorapp.di.BackendProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class ScheduleCreateUiState(
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val errorMessage: String? = null,
    val name: String = "",
    val type: String = "ONEOFF", // ONEOFF, DAILY, WEEKLY
    val onDate: LocalDate = LocalDate.now(),
    val onTime: LocalTime = LocalTime.now().plusMinutes(5),
    val offDate: LocalDate = LocalDate.now(),
    val offTime: LocalTime = LocalTime.now().plusMinutes(15),
    val setOffSchedule: Boolean = true
)

class ScheduleCreateViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduleCreateUiState())
    val uiState: StateFlow<ScheduleCreateUiState> = _uiState.asStateFlow()

    private val api get() = BackendProvider.roomApi

    fun updateName(name: String) = _uiState.update { it.copy(name = name) }
    fun updateType(type: String) = _uiState.update { it.copy(type = type) }
    fun updateOnDate(date: LocalDate) = _uiState.update { it.copy(onDate = date) }
    fun updateOnTime(time: LocalTime) = _uiState.update { it.copy(onTime = time) }
    fun updateOffDate(date: LocalDate) = _uiState.update { it.copy(offDate = date) }
    fun updateOffTime(time: LocalTime) = _uiState.update { it.copy(offTime = time) }
    fun toggleSetOff(enabled: Boolean) = _uiState.update { it.copy(setOffSchedule = enabled) }

    fun saveSchedule(deviceId: String?, roomId: Int?) {
        val state = _uiState.value
        if (state.name.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Name is required") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching {
                // Create ON schedule
                val onTimeStr = state.onTime.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                val onDateStr = if (state.type == "ONEOFF") state.onDate.toString() else null
                
                val onRes = api.createSchedule(ScheduleCreateDto(
                    name = "${state.name} (ON)",
                    deviceId = deviceId,
                    roomId = roomId,
                    scheduleType = state.type,
                    timeOfDay = onTimeStr,
                    scheduledDate = onDateStr,
                    action = "ON"
                ))
                if (!onRes.isSuccessful) throw Exception("Failed to save ON schedule")

                // Optionally create OFF schedule
                if (state.setOffSchedule) {
                    val offTimeStr = state.offTime.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                    val offDateStr = if (state.type == "ONEOFF") state.offDate.toString() else null
                    val offRes = api.createSchedule(ScheduleCreateDto(
                        name = "${state.name} (OFF)",
                        deviceId = deviceId,
                        roomId = roomId,
                        scheduleType = state.type,
                        timeOfDay = offTimeStr,
                        scheduledDate = offDateStr,
                        action = "OFF"
                    ))
                    if (!offRes.isSuccessful) throw Exception("Failed to save OFF schedule")
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

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
}

package com.example.smartmotorapp.ui.detail

sealed interface SensorEvent {
    data class ToggleWaterSensor(val isEnabled: Boolean) : SensorEvent
    data class ToggleAutoStart(val isEnabled: Boolean) : SensorEvent
    data class ToggleAutoStop(val isEnabled: Boolean) : SensorEvent
    data class ToggleDryRun(val isEnabled: Boolean) : SensorEvent
    data class ToggleOverload(val isEnabled: Boolean) : SensorEvent
    data class TogglePhase(val isEnabled: Boolean) : SensorEvent
    data class UpdateDryRunThreshold(val value: Float) : SensorEvent
    data class UpdateOverloadThreshold(val value: Float) : SensorEvent
}
package com.example.smartmotorapp.data.remote

import com.google.gson.annotations.SerializedName

data class DeviceDto(
    @SerializedName("id") val id: String,
    @SerializedName("online") val online: Boolean,
    @SerializedName("last_seen") val lastSeen: String?,
    @SerializedName("last_state") val lastState: String?,
    @SerializedName("last_power") val lastPower: Double?,
)

data class DevicesResponseDto(
    @SerializedName("devices") val devices: List<DeviceDto>,
)

data class CommandRequestDto(
    @SerializedName("action") val action: String,
)

data class CommandStatusDto(
    @SerializedName("command_id") val commandId: String,
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("action") val action: String,
    @SerializedName("status") val status: String,
)

/** Response for GET /health. Used to detect backend connectivity. */
data class HealthResponseDto(
    @SerializedName("status") val status: String,
)

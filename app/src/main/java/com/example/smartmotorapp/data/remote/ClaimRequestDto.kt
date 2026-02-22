package com.example.smartmotorapp.data.remote

import com.google.gson.annotations.SerializedName

data class ClaimRequestDto(
    @SerializedName("device_id")
    val deviceId: String
)
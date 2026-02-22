package com.example.smartmotorapp.data.remote

import com.google.gson.annotations.SerializedName

data class ActivityDto(
    @SerializedName("id") val id: Int,
    @SerializedName("activity_type") val activityType: String,
    @SerializedName("device_id") val deviceId: String?,
    @SerializedName("timestamp") val timestamp: String,
    @SerializedName("details") val details: String?
)

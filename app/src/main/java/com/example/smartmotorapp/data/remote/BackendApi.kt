package com.example.smartmotorapp.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Retrofit API for FastAPI backend (authenticated). No Tuya SDK — all calls go to backend.
 * Use AuthApi for login/register/refresh (no token).
 */
interface BackendApi {

    /** Health check. Call first to detect "Backend offline". No auth required. */
    @GET("health")
    suspend fun health(): Response<HealthResponseDto>

    @GET("devices")
    suspend fun getDevices(): Response<DevicesResponseDto>

    @GET("devices/{deviceId}")
    suspend fun getDevice(@Path("deviceId") deviceId: String): Response<DeviceDto>

    @GET("devices/{deviceId}/events")
    suspend fun getDeviceEvents(@Path("deviceId") deviceId: String): Response<List<EventDto>>

    @POST("devices/claim")
    suspend fun claimDevice(@Body body: ClaimRequestDto): Response<ClaimResponseDto>

    @POST("devices/{deviceId}/command")
    suspend fun sendCommand(
        @Path("deviceId") deviceId: String,
        @Body body: CommandRequestDto,
    ): Response<CommandStatusDto>

    @GET("commands/{commandId}")
    suspend fun getCommandStatus(
        @Path("commandId") commandId: String,
    ): Response<CommandStatusDto>
}

data class ClaimResponseDto(
    @com.google.gson.annotations.SerializedName("status") val status: String,
    @com.google.gson.annotations.SerializedName("device_id") val deviceId: String,
)

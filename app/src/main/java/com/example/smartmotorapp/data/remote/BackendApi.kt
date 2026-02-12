package com.example.smartmotorapp.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Retrofit API for FastAPI backend. No Tuya SDK — all calls go to backend.
 */
interface BackendApi {

    /** Health check. Call first to detect "Backend offline". */
    @GET("health")
    suspend fun health(): Response<HealthResponseDto>

    @GET("devices")
    suspend fun getDevices(): Response<DevicesResponseDto>

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

package com.example.smartmotorapp.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
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

    @GET("rooms/")
    suspend fun getRooms(): Response<List<RoomDto>>

    @GET("rooms/{roomId}/devices")
    suspend fun getRoomDevices(@Path("roomId") roomId: Int): Response<List<DeviceDto>>

    @POST("rooms/{roomId}/devices")
    suspend fun addDeviceToRoom(
        @Path("roomId") roomId: Int,
        @Body body: Map<String, String>,
    ): Response<Unit>

    @DELETE("rooms/{roomId}/devices/{deviceId}")
    suspend fun removeDeviceFromRoom(
        @Path("roomId") roomId: Int,
        @Path("deviceId") deviceId: String,
    ): Response<Unit>

    @POST("rooms/{roomId}/command")
    suspend fun controlRoom(
        @Path("roomId") roomId: Int,
        @Body body: Map<String, String>,
    ): Response<Unit>

    @GET("devices/{deviceId}")
    suspend fun getDevice(@Path("deviceId") deviceId: String): Response<DeviceDto>

    @GET("devices/{deviceId}/events")
    suspend fun getDeviceEvents(@Path("deviceId") deviceId: String): Response<List<EventDto>>

    @POST("devices/claim")
    suspend fun claimDevice(@Body body: ClaimRequestDto): Response<ClaimResponseDto>

    @DELETE("devices/{deviceId}")
    suspend fun removeDevice(@Path("deviceId") deviceId: String): Response<Unit>

    @POST("devices/{deviceId}/command")
    suspend fun sendCommand(
        @Path("deviceId") deviceId: String,
        @Body body: CommandRequestDto,
    ): Response<CommandStatusDto>

    @GET("commands/{commandId}")
    suspend fun getCommandStatus(
        @Path("commandId") commandId: String,
    ): Response<CommandStatusDto>

    @GET("activities")
    suspend fun getActivities(): Response<List<ActivityDto>>
}

data class ClaimResponseDto(
    @com.google.gson.annotations.SerializedName("status") val status: String,
    @com.google.gson.annotations.SerializedName("device_id") val deviceId: String,
)

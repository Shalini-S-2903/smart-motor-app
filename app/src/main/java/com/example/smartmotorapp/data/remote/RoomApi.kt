package com.example.smartmotorapp.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface RoomApi {

    @GET("/rooms")
    suspend fun getRooms(): retrofit2.Response<List<RoomDto>>

    @POST("/rooms")
    suspend fun createRoom(@Body room: RoomCreateDto): retrofit2.Response<RoomDto>

    @GET("/rooms/{roomId}")
    suspend fun getRoom(@Path("roomId") roomId: Int): retrofit2.Response<RoomDto>

    @DELETE("/rooms/{roomId}")
    suspend fun deleteRoom(@Path("roomId") roomId: Int): retrofit2.Response<Unit>

    @GET("/rooms/{roomId}/devices")
    suspend fun getRoomDevices(@Path("roomId") roomId: Int): retrofit2.Response<List<DeviceDto>>

    @POST("/rooms/{roomId}/devices")
    suspend fun addDeviceToRoom(
        @Path("roomId") roomId: Int,
        @Body device: RoomDeviceAddDto
    ): retrofit2.Response<Unit>

    @DELETE("/rooms/{roomId}/devices/{deviceId}")
    suspend fun removeDeviceFromRoom(
        @Path("roomId") roomId: Int,
        @Path("deviceId") deviceId: String
    ): retrofit2.Response<Unit>

    @GET("/rooms/schedules")
    suspend fun getSchedules(): retrofit2.Response<List<ScheduleDto>>

    @POST("/rooms/schedules")
    suspend fun createSchedule(@Body schedule: ScheduleCreateDto): retrofit2.Response<ScheduleDto>

    @PUT("/rooms/schedules/{scheduleId}")
    suspend fun updateSchedule(
        @Path("scheduleId") scheduleId: Int,
        @Body schedule: ScheduleCreateDto
    ): retrofit2.Response<ScheduleDto>

    @DELETE("/rooms/schedules/{scheduleId}")
    suspend fun deleteSchedule(@Path("scheduleId") scheduleId: Int): retrofit2.Response<Unit>
}

data class RoomDto(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("is_active") val isActive: Boolean,
    @SerializedName("device_count") val deviceCount: Int
)

data class RoomCreateDto(
    val name: String,
    val description: String? = null
)

data class RoomDeviceAddDto(
    val deviceId: String
)

data class ScheduleDto(
    val id: Int,
    val name: String,
    val description: String?,
    val roomId: Int?,
    val deviceId: String?,
    val scheduleType: String,
    val timeOfDay: String,
    val daysOfWeek: String?,
    val action: String,
    val isActive: Boolean,
    val createdAt: String,
    val lastRun: String?
)

data class ScheduleCreateDto(
    val name: String,
    val description: String? = null,
    val roomId: Int? = null,
    val deviceId: String? = null,
    val scheduleType: String,
    val timeOfDay: String,
    val daysOfWeek: String? = null,
    val action: String
)
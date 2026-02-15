package com.example.smartmotorapp.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Auth endpoints (no Bearer token required).
 * Used for login, register, refresh.
 */
interface AuthApi {

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequestDto): Response<RegisterResponseDto>

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequestDto): Response<TokenResponseDto>

    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshRequestDto): Response<TokenResponseDto>
}

data class RegisterResponseDto(
    @com.google.gson.annotations.SerializedName("id") val id: Int,
    @com.google.gson.annotations.SerializedName("email") val email: String,
)

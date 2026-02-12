package com.example.smartmotorapp.di

import com.example.smartmotorapp.data.remote.RefreshRequestDto
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.create

/**
 * OkHttp interceptor that:
 * 1. Adds Authorization: Bearer <access_token> to every request.
 * 2. On 401: calls refresh, saves new tokens, retries the request once.
 */
class AuthInterceptor(
    private val tokenStore: com.example.smartmotorapp.data.TokenStore,
    private val authApi: com.example.smartmotorapp.data.remote.AuthApi,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val token = tokenStore.getAccessToken()
        val request = if (!token.isNullOrBlank()) {
            original.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            original
        }

        var response = chain.proceed(request)

        if (response.code == 401) {
            val refreshed = runBlocking {
                val refreshToken = tokenStore.getRefreshToken()
                if (refreshToken.isNullOrBlank()) return@runBlocking false
                val res = authApi.refresh(RefreshRequestDto(refreshToken))
                if (res.isSuccessful) {
                    val body = res.body()
                    if (body != null) {
                        tokenStore.saveTokens(body.accessToken, body.refreshToken)
                        true
                    } else false
                } else false
            }
            if (refreshed) {
                response.close()
                val newToken = tokenStore.getAccessToken()
                val retryRequest = original.newBuilder()
                    .header("Authorization", "Bearer $newToken")
                    .build()
                response = chain.proceed(retryRequest)
            }
        }

        return response
    }
}

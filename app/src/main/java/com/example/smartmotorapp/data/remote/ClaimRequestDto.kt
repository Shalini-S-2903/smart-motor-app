package com.example.smartmotorapp.data.remote

import com.google.gson.annotations.SerializedName

data class ClaimRequestDto(
    @SerializedName("pair_code")
    val pairCode: String
)
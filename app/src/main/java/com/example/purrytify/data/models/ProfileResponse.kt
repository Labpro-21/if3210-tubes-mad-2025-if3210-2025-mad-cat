package com.example.purrytify.data.models

import com.google.gson.annotations.SerializedName

data class ProfileResponse(
    val id: Int?,
    val username: String?,
    val email: String?,
    @SerializedName("profilePhoto")
    val profilePhoto: String?,
    val location: String?, // country code
    val createdAt: String?,
    val updatedAt: String?
)
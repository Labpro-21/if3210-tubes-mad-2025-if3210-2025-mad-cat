package com.example.purrytify.data.models

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class ProfileResponse(
    val id: Int?,
    val username: String?,
    val email: String?,
    @SerializedName("profilePhoto")
    val profilePhoto: String?,
    val location: String?, // country code
    val createdAt: String?,
    val updatedAt: String?
) : Parcelable
package org.example.kotlinai.dto.response

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
)

data class UserInfoResponse(
    val id: Long,
    val email: String,
    val name: String,
    val profileImageUrl: String?,
)

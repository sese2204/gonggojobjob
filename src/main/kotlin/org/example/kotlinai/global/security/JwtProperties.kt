package org.example.kotlinai.global.security

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "jwt")
data class JwtProperties(
    val secret: String = "",
    val accessTokenExpiration: Long = 1800000,   // 30 minutes
    val refreshTokenExpiration: Long = 604800000, // 7 days
)

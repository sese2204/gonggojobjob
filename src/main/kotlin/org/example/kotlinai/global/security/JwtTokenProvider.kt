package org.example.kotlinai.global.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import java.util.Date
import javax.crypto.SecretKey

@Component
class JwtTokenProvider(
    private val jwtProperties: JwtProperties,
) {
    private val key: SecretKey by lazy {
        Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray())
    }

    fun generateAccessToken(userId: Long, email: String): String =
        generateToken(userId, email, jwtProperties.accessTokenExpiration)

    fun generateRefreshToken(userId: Long, email: String): String =
        generateToken(userId, email, jwtProperties.refreshTokenExpiration)

    fun getUserId(token: String): Long =
        parseClaims(token).subject.toLong()

    fun getEmail(token: String): String =
        parseClaims(token)["email"] as String

    fun validate(token: String): Boolean =
        runCatching { parseClaims(token) }.isSuccess

    private fun generateToken(userId: Long, email: String, expiration: Long): String {
        val now = Date()
        return Jwts.builder()
            .subject(userId.toString())
            .claim("email", email)
            .issuedAt(now)
            .expiration(Date(now.time + expiration))
            .signWith(key)
            .compact()
    }

    private fun parseClaims(token: String): Claims =
        Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
}

package org.example.kotlinai.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.example.kotlinai.dto.request.RefreshTokenRequest
import org.example.kotlinai.dto.response.TokenResponse
import org.example.kotlinai.dto.response.UserInfoResponse
import org.example.kotlinai.global.security.JwtTokenProvider
import org.example.kotlinai.repository.UserRepository
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Auth", description = "인증 API")
@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val jwtTokenProvider: JwtTokenProvider,
    private val userRepository: UserRepository,
) {

    @Operation(summary = "현재 로그인 유저 정보 조회")
    @GetMapping("/me")
    fun me(@AuthenticationPrincipal userId: Long): UserInfoResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { NoSuchElementException("유저를 찾을 수 없습니다.") }
        return UserInfoResponse(
            id = user.id,
            email = user.email,
            name = user.name,
            profileImageUrl = user.profileImageUrl,
        )
    }

    @Operation(summary = "Access Token 갱신")
    @PostMapping("/refresh")
    fun refresh(@RequestBody request: RefreshTokenRequest): TokenResponse {
        require(jwtTokenProvider.validate(request.refreshToken)) { "유효하지 않은 Refresh Token입니다." }

        val userId = jwtTokenProvider.getUserId(request.refreshToken)
        val email = jwtTokenProvider.getEmail(request.refreshToken)

        return TokenResponse(
            accessToken = jwtTokenProvider.generateAccessToken(userId, email),
            refreshToken = jwtTokenProvider.generateRefreshToken(userId, email),
        )
    }
}

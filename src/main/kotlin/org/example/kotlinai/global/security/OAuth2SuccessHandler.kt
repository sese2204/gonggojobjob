package org.example.kotlinai.global.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder

@Component
class OAuth2SuccessHandler(
    private val jwtTokenProvider: JwtTokenProvider,
    @Value("\${oauth2.redirect-uri:http://localhost:5173/oauth/callback}")
    private val redirectUri: String,
) : SimpleUrlAuthenticationSuccessHandler() {

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication,
    ) {
        val oAuth2User = authentication.principal as OAuth2User
        val userId = oAuth2User.attributes["userId"] as Long
        val email = oAuth2User.attributes["email"] as String

        val accessToken = jwtTokenProvider.generateAccessToken(userId, email)
        val refreshToken = jwtTokenProvider.generateRefreshToken(userId, email)

        val targetUrl = UriComponentsBuilder.fromUriString(redirectUri)
            .queryParam("accessToken", accessToken)
            .queryParam("refreshToken", refreshToken)
            .build()
            .toUriString()

        redirectStrategy.sendRedirect(request, response, targetUrl)
    }
}

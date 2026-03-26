package org.example.kotlinai.global.security

import org.example.kotlinai.entity.User
import org.example.kotlinai.repository.UserRepository
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Service

@Service
class CustomOAuth2UserService(
    private val userRepository: UserRepository,
) : DefaultOAuth2UserService() {

    override fun loadUser(userRequest: OAuth2UserRequest): OAuth2User {
        val oAuth2User = super.loadUser(userRequest)
        val attributes = oAuth2User.attributes

        val provider = "GOOGLE"
        val providerId = attributes["sub"] as String
        val email = attributes["email"] as String
        val name = attributes["name"] as String
        val profileImageUrl = attributes["picture"] as? String

        val user = userRepository.findByProviderAndProviderId(provider, providerId)
            .orElseGet {
                userRepository.save(
                    User(
                        email = email,
                        name = name,
                        provider = provider,
                        providerId = providerId,
                        profileImageUrl = profileImageUrl,
                    )
                )
            }

        // Update profile if changed
        if (user.name != name || user.profileImageUrl != profileImageUrl) {
            user.name = name
            user.profileImageUrl = profileImageUrl
            userRepository.save(user)
        }

        val mutableAttributes = attributes.toMutableMap()
        mutableAttributes["userId"] = user.id

        return DefaultOAuth2User(
            oAuth2User.authorities,
            mutableAttributes,
            "email",
        )
    }
}

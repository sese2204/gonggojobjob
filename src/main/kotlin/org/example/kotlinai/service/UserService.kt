package org.example.kotlinai.service

import org.example.kotlinai.dto.request.CreateUserRequest
import org.example.kotlinai.dto.response.UserResponse
import org.example.kotlinai.entity.User
import org.example.kotlinai.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class UserService(
    private val userRepository: UserRepository,
) {

    @Transactional
    fun createUser(request: CreateUserRequest): UserResponse {
        require(!userRepository.existsByEmail(request.email)) { "이미 존재하는 이메일입니다: ${request.email}" }

        val user = userRepository.save(User(email = request.email, name = request.name))
        return user.toResponse()
    }

    fun getUser(id: Long): UserResponse {
        val user = userRepository.findById(id)
            .orElseThrow { NoSuchElementException("유저를 찾을 수 없습니다: $id") }
        return user.toResponse()
    }
}

fun User.toResponse() = UserResponse(id = id, email = email, name = name)

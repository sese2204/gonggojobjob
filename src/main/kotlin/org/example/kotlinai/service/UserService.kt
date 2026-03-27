package org.example.kotlinai.service

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

    fun getUser(id: Long): UserResponse {
        val user = userRepository.findById(id)
            .orElseThrow { NoSuchElementException("유저를 찾을 수 없습니다: $id") }
        return user.toResponse()
    }
}

fun User.toResponse() = UserResponse(id = id, email = email, name = name)

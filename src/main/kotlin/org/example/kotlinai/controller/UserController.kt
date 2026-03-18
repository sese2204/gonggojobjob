package org.example.kotlinai.controller

import org.example.kotlinai.dto.request.CreateUserRequest
import org.example.kotlinai.dto.response.UserResponse
import org.example.kotlinai.service.UserService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/users")
class UserController(
    private val userService: UserService,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createUser(@RequestBody request: CreateUserRequest): UserResponse =
        userService.createUser(request)

    @GetMapping("/{id}")
    fun getUser(@PathVariable id: Long): UserResponse =
        userService.getUser(id)
}

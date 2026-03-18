package org.example.kotlinai.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.example.kotlinai.dto.request.CreateUserRequest
import org.example.kotlinai.dto.response.UserResponse
import org.example.kotlinai.service.UserService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@Tag(name = "User", description = "유저 관리 API")
@RestController
@RequestMapping("/api/users")
class UserController(
    private val userService: UserService,
) {

    @Operation(summary = "유저 생성", description = "새로운 유저를 생성합니다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createUser(@RequestBody request: CreateUserRequest): UserResponse =
        userService.createUser(request)

    @Operation(summary = "유저 조회", description = "ID로 유저를 조회합니다.")
    @GetMapping("/{id}")
    fun getUser(@PathVariable id: Long): UserResponse =
        userService.getUser(id)
}

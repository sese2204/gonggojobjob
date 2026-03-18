package org.example.kotlinai.controller

import org.example.kotlinai.dto.request.CreateUserRequest
import org.example.kotlinai.dto.response.UserResponse
import org.example.kotlinai.service.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@Tag(name = "Users", description = "사용자 관리 API")
@RestController
@RequestMapping("/api/users")
class UserController(
    private val userService: UserService,
) {

    @Operation(summary = "사용자 생성")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createUser(@RequestBody request: CreateUserRequest): UserResponse =
        userService.createUser(request)

    @Operation(summary = "사용자 조회")
    @GetMapping("/{id}")
    fun getUser(@PathVariable id: Long): UserResponse =
        userService.getUser(id)
}

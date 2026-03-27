package org.example.kotlinai.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.example.kotlinai.dto.request.CheerRequest
import org.example.kotlinai.dto.response.CheerResponse
import org.example.kotlinai.service.CheerService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@Tag(name = "Cheer", description = "응원글 API")
@RestController
@RequestMapping("/api/cheers")
class CheerController(
    private val cheerService: CheerService,
) {

    @Operation(summary = "응원글 작성", description = "닉네임과 내용으로 응원글을 작성합니다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody request: CheerRequest): CheerResponse =
        cheerService.create(request)

    @Operation(summary = "응원글 목록 조회", description = "최신순으로 응원글 목록을 조회합니다.")
    @GetMapping
    fun getAll(
        @PageableDefault(size = 20) pageable: Pageable,
    ): Page<CheerResponse> = cheerService.getAll(pageable)
}

package org.example.kotlinai.service

import org.example.kotlinai.dto.request.CheerRequest
import org.example.kotlinai.dto.response.CheerResponse
import org.example.kotlinai.entity.Cheer
import org.example.kotlinai.repository.CheerRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class CheerService(
    private val cheerRepository: CheerRepository,
) {

    @Transactional
    fun create(request: CheerRequest): CheerResponse {
        require(request.nickname.isNotBlank()) { "닉네임을 입력해주세요." }
        require(request.nickname.length <= 20) { "닉네임은 20자 이내로 입력해주세요." }
        require(request.content.isNotBlank()) { "내용을 입력해주세요." }
        require(request.content.length <= 500) { "내용은 500자 이내로 입력해주세요." }

        val cheer = cheerRepository.save(
            Cheer(
                nickname = request.nickname.trim(),
                content = request.content.trim(),
            )
        )
        return cheer.toResponse()
    }

    fun getAll(pageable: Pageable): Page<CheerResponse> =
        cheerRepository.findAllByOrderByCreatedAtDesc(pageable).map { it.toResponse() }
}

fun Cheer.toResponse() = CheerResponse(
    id = id,
    nickname = nickname,
    content = content,
    createdAt = createdAt,
)

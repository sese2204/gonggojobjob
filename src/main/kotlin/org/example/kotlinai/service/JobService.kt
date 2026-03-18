package org.example.kotlinai.service

import org.example.kotlinai.dto.request.JobSearchRequest
import org.example.kotlinai.dto.response.JobResponse
import org.example.kotlinai.dto.response.JobSearchResponse
import org.example.kotlinai.entity.Job
import org.example.kotlinai.repository.JobRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
@Transactional(readOnly = true)
class JobService(
    private val jobRepository: JobRepository,
) {

    fun searchJobs(request: JobSearchRequest): JobSearchResponse {
        require(request.tags.isNotEmpty() || request.query.isNotBlank()) {
            "태그 또는 검색어를 입력해주세요"
        }

        val reason = buildReason(request.tags, request.query)
        val jobs = jobRepository.findTop10ByOrderByCollectedAtDesc()
            .map { it.toResponse(reason) }

        val totalCount = jobRepository.count()
        val newTodayCount = jobRepository.countByCollectedAtAfter(
            LocalDate.now().atStartOfDay()
        )

        return JobSearchResponse(
            jobs = jobs,
            totalCount = totalCount,
            newTodayCount = newTodayCount,
        )
    }

    private fun buildReason(tags: List<String>, query: String): String {
        val hasTags = tags.isNotEmpty()
        val hasQuery = query.isNotBlank()
        return when {
            hasTags && hasQuery ->
                "선택하신 ${tags.joinToString(", ")} 키워드와 \"$query\" 조건에 맞는 최신 공고입니다."
            hasTags ->
                "선택하신 ${tags.joinToString(", ")} 키워드와 관련된 최신 공고입니다."
            else ->
                "\"$query\" 관련 최신 공고입니다."
        }
    }
}

// TODO: Replace match: 0 with AI-computed score when AI integration is implemented
fun Job.toResponse(reason: String) = JobResponse(
    id = id.toString(),
    title = title,
    company = company,
    match = 0,
    reason = reason,
    url = url,
)

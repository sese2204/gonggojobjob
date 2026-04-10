package org.example.kotlinai.entity

enum class RecommendationCategory(
    val displayName: String,
    val type: RecommendationType,
    val searchTags: List<String>,
) {
    // Job categories
    IT_DEV("IT/개발", RecommendationType.JOB, listOf("개발자", "소프트웨어", "엔지니어", "IT", "대기업", "신입", "정규직")),
    BUSINESS("경영/사무", RecommendationType.JOB, listOf("경영", "기획", "인사", "총무", "사무", "대기업", "정규직")),
    MARKETING("마케팅/광고", RecommendationType.JOB, listOf("마케팅", "광고", "브랜드", "홍보", "콘텐츠", "대기업", "정규직")),
    DESIGN("디자인", RecommendationType.JOB, listOf("디자인", "UI", "UX", "그래픽", "영상", "대기업", "정규직")),
    SALES("영업/고객관리", RecommendationType.JOB, listOf("영업", "고객", "세일즈", "CS", "대기업", "정규직")),

    // Activity categories
    IT_CONTEST("IT/SW 공모전", RecommendationType.ACTIVITY, listOf("IT", "소프트웨어", "해커톤", "앱", "개발")),
    MARKETING_CONTEST("마케팅/기획 공모전", RecommendationType.ACTIVITY, listOf("마케팅", "기획", "광고", "브랜드")),
    VOLUNTEER("봉사활동", RecommendationType.ACTIVITY, listOf("봉사", "사회공헌", "재능기부")),
    GLOBAL("해외탐방/인턴", RecommendationType.ACTIVITY, listOf("해외", "인턴", "글로벌", "교환")),
    ACADEMIC("학술/논문", RecommendationType.ACTIVITY, listOf("학술", "논문", "연구", "학회")),
    ;

    companion object {
        val jobCategories: List<RecommendationCategory> by lazy {
            entries.filter { it.type == RecommendationType.JOB }
        }

        val activityCategories: List<RecommendationCategory> by lazy {
            entries.filter { it.type == RecommendationType.ACTIVITY }
        }
    }
}

enum class RecommendationType {
    JOB, ACTIVITY
}

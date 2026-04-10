package org.example.kotlinai.entity

enum class RecommendationCategory(
    val displayName: String,
    val type: RecommendationType,
    val searchTags: List<String>,
) {
    // Job categories
    BACKEND("백엔드 개발", RecommendationType.JOB, listOf("백엔드", "Java", "Spring", "서버")),
    FRONTEND("프론트엔드 개발", RecommendationType.JOB, listOf("프론트엔드", "React", "Vue", "JavaScript")),
    DATA_AI("데이터/AI", RecommendationType.JOB, listOf("데이터", "머신러닝", "AI", "Python")),
    MOBILE("모바일 개발", RecommendationType.JOB, listOf("모바일", "iOS", "Android", "Flutter")),
    DEVOPS("DevOps/인프라", RecommendationType.JOB, listOf("DevOps", "AWS", "클라우드", "인프라")),

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

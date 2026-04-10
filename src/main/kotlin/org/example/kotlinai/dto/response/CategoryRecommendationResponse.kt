package org.example.kotlinai.dto.response

import java.time.LocalDate

data class CategoryRecommendationResponse(
    val jobCategories: List<JobCategoryGroup>,
    val activityCategories: List<ActivityCategoryGroup>,
    val generatedAt: LocalDate?,
)

data class JobCategoryGroup(
    val category: String,
    val displayName: String,
    val jobs: List<JobRecommendationItem>,
)

data class ActivityCategoryGroup(
    val category: String,
    val displayName: String,
    val activities: List<ActivityRecommendationItem>,
)

data class JobRecommendationItem(
    val jobListingId: Long,
    val title: String,
    val company: String,
    val url: String,
    val matchScore: Int,
    val reason: String,
)

data class ActivityRecommendationItem(
    val activityListingId: Long,
    val title: String,
    val organizer: String,
    val category: String?,
    val startDate: String?,
    val endDate: String?,
    val url: String,
    val matchScore: Int,
    val reason: String,
)

package com.ddgo.app.domain.model

/**
 * gym resolve 결과 domain 모델.
 *
 * 역할:
 * - 선택한 장소가 DDGo gym과 어떻게 매칭되었는지,
 *   그리고 해당 gym의 grade 목록이 무엇인지 UI에 전달합니다.
 */
data class ResolvedGym(
    val matched: Boolean,
    val gymId: Int,
    val gradeSource: String,
    val matchStatus: String,
    val needsReview: Boolean,
    val gym: GymSummary,
    val grades: List<GymGrade>
)

/**
 * UI에서 사용할 gym 요약 정보.
 */
data class GymSummary(
    val id: Int,
    val displayName: String,
    val region: String?,
    val logoBucket: String?,
    val logoObjectKey: String?,
    val brandLogoBucket: String?,
    val brandLogoObjectKey: String?
)

/**
 * UI에서 사용할 gym grade 정보.
 */
data class GymGrade(
    val gymGradeId: Int,
    val colorName: String,
    val sortOrder: Int,
    val colorHex: String?,
    val gradeLabel: String?
)

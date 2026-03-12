package com.ddgo.app.domain.model

/**
 * 클라이밍 시도 영상의 특정 시점에 대한 분석 피드백.
 *
 * 서버 attempt_feedbacks 테이블과 1:1 대응합니다.
 * MVP에서는 UploadViewModel에서 플레이스홀더 데이터를 사용합니다.
 *
 * @param index   화면에 표시할 번호 (1, 2, 3, ...)
 * @param timeMs  영상 내 해당 분석 시점 (밀리초)
 * @param description  피드백 텍스트 (서버 응답값)
 */
data class AnalysisPoint(
    val index: Int,
    val timeMs: Long,
    val description: String
)

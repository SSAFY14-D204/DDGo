package com.ddgo.app.feature.analysis.model

/**
 * 메인 분석 탭 내부의 화면 전환 상태입니다.
 *
 * 역할:
 * - 대시보드, 챌린지 상세, 시도 상세 중 어떤 화면을 보여줄지 ViewModel이 명확하게 관리합니다.
 * - mapper가 아니라 feature 모델에 두어 화면 전환 책임을 UI 상태 영역에 가깝게 유지합니다.
 */
enum class AnalysisScreenState {
    Dashboard,
    AllChallenges,
    ChallengeDetail,
    AttemptDetail
}

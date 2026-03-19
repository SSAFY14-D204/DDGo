package com.ddgo.app.domain.repository

import com.ddgo.app.domain.model.AnalysisChallengeSnapshot

/**
 * 분석 화면 원본 데이터를 제공하는 Repository 계약입니다.
 *
 * 역할:
 * - 챌린지/시도 분석 화면이 사용할 원본 데이터를 feature가 아닌 domain 계약으로 노출합니다.
 * - 현재는 목업 데이터를 반환하지만, 이후 실제 API 연동 시 구현체만 교체할 수 있도록 경계를 유지합니다.
 */
interface AnalysisRepository {
    suspend fun getAnalysisSnapshots(): Result<List<AnalysisChallengeSnapshot>>
}

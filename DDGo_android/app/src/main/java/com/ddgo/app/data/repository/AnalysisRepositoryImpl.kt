package com.ddgo.app.data.repository

import com.ddgo.app.domain.mock.AnalysisMockFixtures
import com.ddgo.app.domain.model.AnalysisChallengeSnapshot
import com.ddgo.app.domain.repository.AnalysisRepository
import javax.inject.Inject

/**
 * 분석 화면용 Repository 구현체입니다.
 *
 * 역할:
 * - 아직 분석 API가 정리되지 않은 동안 mock fixture를 반환해 화면 구조와 상태 흐름을 먼저 검증합니다.
 * - 이후 API가 준비되면 이 구현체에서만 원본 소스를 교체하고, feature 계층은 그대로 유지할 수 있습니다.
 */
class AnalysisRepositoryImpl @Inject constructor() : AnalysisRepository {

    override suspend fun getAnalysisSnapshots(): Result<List<AnalysisChallengeSnapshot>> {
        return Result.success(AnalysisMockFixtures.challengeSnapshots)
    }
}

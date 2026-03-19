package com.ddgo.app.domain.usecase

import com.ddgo.app.domain.model.AnalysisChallengeSnapshot
import com.ddgo.app.domain.repository.AnalysisRepository
import javax.inject.Inject

/**
 * 분석 화면에 필요한 챌린지 원본 데이터를 가져오는 UseCase입니다.
 *
 * 역할:
 * - ViewModel이 data 구현체를 직접 알지 않고 분석 데이터만 요청할 수 있도록 중간 계층을 제공합니다.
 * - 현재는 mock 기반 결과를 그대로 전달하지만, 이후 필터링이나 정렬 정책이 생기면 이 계층에서 확장할 수 있습니다.
 */
class GetAnalysisSnapshotsUseCase @Inject constructor(
    private val repository: AnalysisRepository
) {
    suspend operator fun invoke(): Result<List<AnalysisChallengeSnapshot>> {
        return repository.getAnalysisSnapshots()
    }
}

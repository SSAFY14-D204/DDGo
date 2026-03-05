package com.ddgo.app.domain.usecase

import com.ddgo.app.domain.model.Hold
import com.ddgo.app.domain.model.Pose
import javax.inject.Inject

/**
 * 클라이밍 실패 시점을 계산하는 UseCase.
 *
 * UseCase 규칙:
 * - 단 하나의 비즈니스 로직만 담당합니다 (Single Responsibility)
 * - Android 의존성이 없습니다 (순수 Kotlin)
 * - Repository에 의존하지 않고, 파라미터로 받은 데이터를 연산합니다
 *
 * 판정 알고리즘 (TODO: 실제 알고리즘으로 교체 필요):
 * - 포즈 시퀀스에서 신체 중심이 급격히 하강하는 시점을 '낙하'로 판정
 * - 해당 시점의 frameTimeMs를 반환
 *
 * @return 실패 시점(ms). 성공(완등)이면 null 반환
 */
class ExtractFailClipUseCase @Inject constructor() {

    operator fun invoke(poses: List<Pose>, holds: List<Hold>): Long? {
        if (poses.isEmpty()) return null

        // TODO: 실제 실패 시점 감지 알고리즘 구현
        // 예시: 연속된 프레임에서 hip 랜드마크(idx=23,24)의 y 좌표가
        //       급격히 증가(아래로 이동)하는 시점을 찾음
        val hipLandmarkIndices = setOf(23, 24) // 왼쪽/오른쪽 엉덩이

        for (i in 1 until poses.size) {
            val prev = poses[i - 1].landmarks.filter { it.index in hipLandmarkIndices }
            val curr = poses[i].landmarks.filter { it.index in hipLandmarkIndices }

            val prevAvgY = prev.map { it.y }.average()
            val currAvgY = curr.map { it.y }.average()

            // y 좌표가 0.15 이상 급증 = 낙하로 판정
            if (currAvgY - prevAvgY > 0.15f) {
                return poses[i].frameTimeMs
            }
        }

        return null // 낙하 없음 = 성공
    }
}

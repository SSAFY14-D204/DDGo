package com.ddgo.app.domain.usecase

import com.ddgo.app.domain.model.NearbyPlace
import com.ddgo.app.domain.model.ResolvedGym
import com.ddgo.app.domain.repository.GymRepository
import javax.inject.Inject

/**
 * gym resolve 유스케이스.
 *
 * 역할:
 * - 사용자가 선택한 장소가 유효한지 최소 검증
 * - repository를 통해 서버 resolve 호출
 */
class ResolveGymUseCase @Inject constructor(
    private val gymRepository: GymRepository
) {

    /**
     * 선택한 장소 이름이 비어 있지 않은지 확인한 뒤 resolve를 수행합니다.
     */
    suspend operator fun invoke(place: NearbyPlace): Result<ResolvedGym> {
        if (place.placeName.isBlank()) {
            return Result.failure(Exception("Selected place name is blank."))
        }

        return gymRepository.resolveGym(place)
    }
}

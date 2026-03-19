package com.ddgo.app.domain.usecase

import com.ddgo.app.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * 신체 정보 저장/수정 유스케이스입니다.
 *
 * 역할:
 * - 프로필 화면에서 입력한 성별, 키, 몸무게, 윙스팬을 전달합니다.
 * - 온보딩 미완료 사용자도 같은 유스케이스로 최초 입력이 가능하도록 통합합니다.
 */
class UpdateProfileUseCase @Inject constructor(
    private val repository: AuthRepository
) {

    /** 서버에 신체 정보를 저장하거나 수정합니다. */
    suspend operator fun invoke(
        sex: String,
        heightCm: Float,
        weightKg: Float,
        wingspanCm: Float
    ): Result<Unit> {
        return repository.updateProfile(
            sex = sex,
            heightCm = heightCm,
            weightKg = weightKg,
            wingspanCm = wingspanCm
        )
    }
}

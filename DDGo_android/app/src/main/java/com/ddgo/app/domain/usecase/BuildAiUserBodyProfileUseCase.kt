package com.ddgo.app.domain.usecase

import com.ddgo.app.domain.model.AiCalibrationCompat
import com.ddgo.app.domain.model.AiUserBodyProfile
import com.ddgo.app.domain.model.AiUserProfile
import com.ddgo.app.domain.model.User
import javax.inject.Inject

/**
 * 앱 사용자 프로필을 AI 서버용 user_body payload로 변환합니다.
 */
class BuildAiUserBodyProfileUseCase @Inject constructor() {

    operator fun invoke(
        user: User,
        allowMissingWeight: Boolean = false
    ): Result<AiUserBodyProfile> {
        val heightCm = user.heightCm
            ?: return Result.failure(IllegalStateException("heightCm is required for AI analysis."))
        val weightKg = user.weightKg
            ?: if (allowMissingWeight) {
                null
            } else {
                return Result.failure(IllegalStateException("weightKg is required for AI analysis."))
            }
        val wingspanCm = user.wingspanCm ?: heightCm

        val heightM = heightCm / 100.0
        val wingspanM = wingspanCm / 100.0
        val armScale = wingspanM / 1.75
        val legScale = heightM / 1.75

        val upperArmM = 0.3118 * armScale
        val forearmM = 0.3118 * armScale
        val thighM = 0.4001 * legScale
        val shinM = 0.39 * legScale
        val shoulderWidthM = 0.34 * armScale

        val calibrationCompat = AiCalibrationCompat(
            upperArmM = upperArmM,
            forearmM = forearmM,
            thighM = thighM,
            shinM = shinM,
            shoulderWidthM = shoulderWidthM,
            wingspanM = wingspanM,
            leftUpperArmM = upperArmM,
            rightUpperArmM = upperArmM,
            leftForearmM = forearmM,
            rightForearmM = forearmM,
            leftThighM = thighM,
            rightThighM = thighM,
            leftShinM = shinM,
            rightShinM = shinM,
            bodyMassKg = weightKg?.toDouble()
        )

        return Result.success(
            AiUserBodyProfile(
                userProfile = AiUserProfile(
                    sex = user.sex,
                    heightCm = heightCm,
                    weightKg = weightKg,
                    wingspanCm = wingspanCm
                ),
                calibrationCompat = calibrationCompat
            )
        )
    }
}

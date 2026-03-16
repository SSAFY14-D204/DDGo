package com.ddgo.app.di

import com.ddgo.app.data.ml.mediapipe.PoseEstimatorImpl
import com.ddgo.app.data.ml.persondetect.PersonDetectorImpl
import com.ddgo.app.data.ml.yolo.HoldDetectorImpl
import com.ddgo.app.domain.repository.HoldDetector
import com.ddgo.app.domain.repository.PersonDetector
import com.ddgo.app.domain.repository.PoseEstimator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * AI/ML 모델 의존성을 제공하는 Hilt 모듈.
 *
 * AI를 '또 하나의 데이터 소스'로 취급합니다.
 * - PoseEstimator:  "비디오 주면 Pose 반환해" (MediaPipe)
 * - HoldDetector:   "비디오 주면 Hold 반환해" (YOLO/TFLite)
 * - PersonDetector: "비디오 주면 최적 프레임 시간 반환해" (person_detect TFLite)
 *
 * 새 AI 모델 추가 시:
 *   1. domain/repository/YourAiInterface.kt 인터페이스 생성
 *   2. data/ml/yourmodel/YourAiImpl.kt 구현체 생성
 *   3. 이 모듈에 Binds 함수 추가
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class MlModule {

    @Binds
    @Singleton
    abstract fun bindPoseEstimator(
        impl: PoseEstimatorImpl
    ): PoseEstimator

    @Binds
    @Singleton
    abstract fun bindHoldDetector(
        impl: HoldDetectorImpl
    ): HoldDetector

    @Binds
    @Singleton
    abstract fun bindPersonDetector(
        impl: PersonDetectorImpl
    ): PersonDetector
}

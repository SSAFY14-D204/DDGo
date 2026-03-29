package com.ddgo.app.di

import com.ddgo.app.data.repository.AuthRepositoryImpl
import com.ddgo.app.data.repository.AiAnalysisRepositoryImpl
import com.ddgo.app.data.repository.AnalysisRepositoryImpl
import com.ddgo.app.data.repository.AttemptRepositoryImpl
import com.ddgo.app.data.repository.CalendarRepositoryImpl
import com.ddgo.app.data.repository.ChallengeRepositoryImpl
import com.ddgo.app.data.repository.CommunityRepositoryImpl
import com.ddgo.app.data.repository.GymRepositoryImpl
import com.ddgo.app.data.repository.UploadRepositoryImpl
import com.ddgo.app.domain.repository.AiAnalysisRepository
import com.ddgo.app.domain.repository.AnalysisRepository
import com.ddgo.app.domain.repository.AttemptRepository
import com.ddgo.app.domain.repository.AuthRepository
import com.ddgo.app.domain.repository.CalendarRepository
import com.ddgo.app.domain.repository.ChallengeRepository
import com.ddgo.app.domain.repository.CommunityRepository
import com.ddgo.app.domain.repository.GymRepository
import com.ddgo.app.domain.repository.UploadRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Repository 인터페이스(domain)와 구현체(data)를 바인딩하는 Hilt 모듈.
 *
 * 새 Repository를 추가할 때:
 *   1. domain/repository/YourRepository.kt 인터페이스 생성
 *   2. data/repository/YourRepositoryImpl.kt 구현체 생성
 *   3. 이 모듈에 Binds 함수 추가
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAnalysisRepository(
        impl: AnalysisRepositoryImpl
    ): AnalysisRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        impl: AuthRepositoryImpl
    ): AuthRepository

    @Binds
    @Singleton
    abstract fun bindAiAnalysisRepository(
        impl: AiAnalysisRepositoryImpl
    ): AiAnalysisRepository

    @Binds
    @Singleton
    abstract fun bindUploadRepository(
        impl: UploadRepositoryImpl
    ): UploadRepository

    /**
     * GymRepository 인터페이스를 GymRepositoryImpl 구현체에 바인딩합니다.
     *
     * 규칙:
     * - feature/domain은 구현체를 몰라야 하므로 interface 기준으로 주입합니다.
     */
    @Binds
    @Singleton
    abstract fun bindGymRepository(
        impl: GymRepositoryImpl
    ): GymRepository

    @Binds
    @Singleton
    abstract fun bindChallengeRepository(
        impl: ChallengeRepositoryImpl
    ): ChallengeRepository

    @Binds
    @Singleton
    abstract fun bindAttemptRepository(
        impl: AttemptRepositoryImpl
    ): AttemptRepository

    @Binds
    @Singleton
    abstract fun bindCalendarRepository(
        impl: CalendarRepositoryImpl
    ): CalendarRepository

    @Binds
    @Singleton
    abstract fun bindCommunityRepository(
        impl: CommunityRepositoryImpl
    ): CommunityRepository
}

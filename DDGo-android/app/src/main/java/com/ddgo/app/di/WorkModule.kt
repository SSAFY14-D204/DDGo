package com.ddgo.app.di

import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * WorkManager 의존성을 제공하는 Hilt 모듈.
 *
 * UploadViewModel이 WorkManager 인스턴스를 직접 받을 수 있도록 제공합니다.
 * Worker에 Hilt 주입 = HiltWorkerFactory가 필요하며,
 * DDGoApplication에서 Configuration.Provider를 통해 등록됩니다.
 */
@Module
@InstallIn(SingletonComponent::class)
object WorkModule {

    @Provides
    @Singleton
    fun provideWorkManager(
        @ApplicationContext context: Context
    ): WorkManager = WorkManager.getInstance(context)
}

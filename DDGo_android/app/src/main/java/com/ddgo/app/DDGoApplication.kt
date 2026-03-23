package com.ddgo.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.kakao.sdk.common.KakaoSdk
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * 앱 진입점.
 *
 * @HiltAndroidApp: Hilt 의존성 주입 컨테이너를 초기화합니다.
 * Configuration.Provider: WorkManager를 Hilt와 함께 사용하기 위해 직접 초기화합니다.
 *   → AndroidManifest.xml에서 기본 WorkManager 초기화를 제거했기 때문에 필수입니다.
 */
@HiltAndroidApp
class DDGoApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.KAKAO_NATIVE_APP_KEY.isNotBlank()) {
            KakaoSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}

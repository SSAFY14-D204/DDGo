import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

android {
    namespace = "com.ddgo.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ddgo.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 💡 API 서버 주소 환경 변수 주입
        buildConfigField(
            "String",
            "BASE_URL",
            "\"${localProperties.getProperty("api.base.url") ?: "https://j14d204.p.ssafy.io/api/"}\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Release APK: ARM 전용 (APK 크기 최소화)
            ndk {
                abiFilters.add("arm64-v8a")
                abiFilters.add("armeabi-v7a")
            }
        }
        debug {
            // x86_64 제외 → 에뮬레이터가 arm64-v8a 번역 레이어(berberis)로 실행
            // → MediaPipe arm64 JNI 정상 동작
            ndk {
                abiFilters.add("arm64-v8a")
                abiFilters.add("armeabi-v7a")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // 💡 AI 모델 파일(.tflite, .task)은 압축하지 않아야 mmap으로 빠르게 로드됨
    androidResources {
        noCompress += listOf("tflite", "task")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // JUnit Jupiter 여러 JAR에 중복 포함된 라이선스 파일 충돌 방지
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/LICENSE-notice.md"
        }
    }
}

dependencies {
    // 1. Android Core & UI (Compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material:material-icons-extended")

    // 2. DI & Navigation (Hilt)
    implementation(libs.hilt.android)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.androidx.compose.foundation)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.navigation.compose)

    // 3. Network (Retrofit + Serialization)
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization.converter)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)

    // 4. Local Storage (Security & DataStore)
    implementation(libs.security.crypto.ktx)
    implementation(libs.androidx.datastore.preferences)

    // 5. 🤖 On-Device AI (MediaPipe & YOLO/TFLite)
    implementation(libs.mediapipe.tasks.vision)
    implementation(libs.tensorflow.lite)
    implementation(libs.tensorflow.lite.gpu)

    // 6. ⚙️ Background Work (WorkManager)
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // 7. 🎥 Media & Image (Media3 & Coil)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
    implementation("com.github.wseemann:FFmpegMediaMetadataRetriever-core:1.0.19")
    implementation("com.github.wseemann:FFmpegMediaMetadataRetriever-native:1.0.19")
    // Desugaring (Java 8+ API 지원)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.mockk.android)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
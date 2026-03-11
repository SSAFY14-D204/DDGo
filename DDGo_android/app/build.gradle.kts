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
        // 💡 MediaPipe 및 Java 8+ API 최적화를 위한 MinSDK 26
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 🦾 MuJoCo JNI 빌드 옵션 — CMake는 ARM 전용 (MuJoCo x86_64 미지원)
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DANDROID_PLATFORM=android-28"  // qsort_r은 API 28+에서 제공됨
                )
                // 💡 빌드 대상 라이브러리만 명시하여 qhull 등 부가적인 실행 파일 빌드 제외
                targets.add("ddgo_mujoco")
                // MuJoCo는 ARM 전용 빌드 (x86_64 에뮬레이터에서도 berberis로 실행되나
                // MuJoCo는 SVE를 사용하지 않아 문제없음)
                abiFilters("arm64-v8a", "armeabi-v7a")
            }
        }

        // 💡 API 서버 주소 환경 변수 주입
        buildConfigField(
            "String",
            "BASE_URL",
            "\"${localProperties.getProperty("api.base.url") ?: "https://api.ddgo.com/"}\""
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
        // debug: ndk abiFilters 제한 없음
        // → TFLite/FFmpeg x86_64 라이브러리 포함 → 에뮬레이터에서 berberis 없이 네이티브 실행
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

    // 🦾 CMake 연동 (MuJoCo JNI)
    externalNativeBuild {
        cmake {
            path = file("CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // JUnit Jupiter 여러 JAR에 중복 포함된 라이선스 파일 충돌 방지
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/LICENSE-notice.md"
        }
        jniLibs {
            // 여러 라이브러리에서 중복될 수 있는 C++ STL 충돌 방지
            pickFirsts += listOf("**/libc++_shared.so")
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
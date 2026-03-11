# YOLOv8 TFLite 모델 교체 및 Android 이식 가이드

## 목차

1. [프로젝트 구조 이해](#1-프로젝트-구조-이해)
2. [모델 교체 방법](#2-모델-교체-방법)
3. [다른 Android 프로젝트로 이식하기](#3-다른-android-프로젝트로-이식하기)
4. [로직 통합 방법](#4-로직-통합-방법)
5. [전체 코드 예시](#5-전체-코드-예시)
6. [트러블슈팅](#6-트러블슈팅)

---

## 1. 프로젝트 구조 이해

```
DDgo_AI_yolo/
└── app/src/main/
    ├── assets/
    │   ├── best_float32.tflite          ← 구 모델 (512×512 입력)
    │   └── best_float32_v8_640.tflite   ← 현재 사용 모델 (640×640 입력)
    └── java/com/example/myapplication2/
        ├── YoloV8TFLite.kt    ← TFLite 추론 엔진 (핵심)
        ├── OverlayView.kt     ← 바운딩박스 그리기 + Detection 데이터 클래스
        ├── ImageProxyExt.kt   ← CameraX ImageProxy → Bitmap 변환
        └── MainActivity.kt    ← UI + 카메라/이미지/영상 분석 로직
```

### 각 파일 역할

| 파일 | 역할 | 이식 필요 여부 |
|------|------|---------------|
| `YoloV8TFLite.kt` | 모델 로드, 추론, NMS 처리 | **필수** |
| `OverlayView.kt` | 검출 결과 화면에 그리기 | 필요 시 |
| `ImageProxyExt.kt` | 카메라 프레임 변환 | 카메라 사용 시 필수 |
| `MainActivity.kt` | 전체 UI 및 분석 흐름 | 로직만 발췌하여 통합 |

---

## 2. 모델 교체 방법

### 2-1. assets 폴더에 새 모델 추가

새 `.tflite` 파일을 아래 경로에 복사합니다.

```
app/src/main/assets/새모델파일.tflite
```

### 2-2. YoloV8TFLite.kt 수정

`YoloV8TFLite.kt` 하단 `companion object`에서 두 곳을 수정합니다.

```kotlin
// YoloV8TFLite.kt

class YoloV8TFLite(
    context: Context,
    private val inputSize: Int = 640,   // ← 모델 입력 해상도에 맞게 변경 (구 모델: 512)
    private val confThres: Float = 0.35f,
    private val iouThres: Float = 0.45f
) {
    // ...

    companion object {
        private const val DEFAULT_MODEL_ASSET = "best_float32_v8_640.tflite"  // ← 파일명 변경
        // ...
    }
}
```

> **입력 해상도 확인 방법**
> 모델 파일명에 `640`이 있으면 640×640, 없으면 Netron(https://netron.app)에서 모델을 열어 Input shape를 확인합니다.

### 2-3. (선택) 생성자에서 직접 지정

파일명 대신 생성자 인자로 넘기는 방식으로도 가능합니다.

```kotlin
// 모델 파일명과 inputSize를 생성자에서 직접 지정
val yolo = YoloV8TFLite(
    context = this,
    inputSize = 640,
    // confThres, iouThres 는 기본값 사용
)
```

단, 현재 `YoloV8TFLite`는 내부에서 `DEFAULT_MODEL_ASSET` 상수를 사용하므로,
다른 모델을 동적으로 바꾸려면 생성자에 `modelAsset: String` 파라미터를 추가해야 합니다.

```kotlin
class YoloV8TFLite(
    context: Context,
    private val modelAsset: String = DEFAULT_MODEL_ASSET,  // 추가
    private val inputSize: Int = 640,
    private val confThres: Float = 0.35f,
    private val iouThres: Float = 0.45f
) {
    init {
        val model = loadModelFile(context, modelAsset)  // DEFAULT_MODEL_ASSET 대신 modelAsset 사용
        // ...
    }
}
```

---

## 3. 다른 Android 프로젝트로 이식하기

### 3-1. 복사할 파일 목록

아래 파일들을 타 프로젝트로 복사합니다.

```
# 복사 원본 (DDgo_AI_yolo)
app/src/main/assets/best_float32_v8_640.tflite
app/src/main/java/com/example/myapplication2/YoloV8TFLite.kt
app/src/main/java/com/example/myapplication2/OverlayView.kt
app/src/main/java/com/example/myapplication2/ImageProxyExt.kt

# 복사 대상 (타 프로젝트)
app/src/main/assets/best_float32_v8_640.tflite      ← assets 폴더에 그대로 복사
app/src/main/java/com/yourpackage/YoloV8TFLite.kt   ← 패키지 경로에 맞게 이동
app/src/main/java/com/yourpackage/OverlayView.kt
app/src/main/java/com/yourpackage/ImageProxyExt.kt
```

### 3-2. 패키지 선언 수정

복사한 각 `.kt` 파일 상단의 패키지명을 타 프로젝트에 맞게 변경합니다.

```kotlin
// 변경 전
package com.example.myapplication2

// 변경 후
package com.yourcompany.yourapp   // 타 프로젝트 패키지명으로 교체
```

### 3-3. build.gradle.kts 의존성 추가

타 프로젝트 `app/build.gradle.kts`의 `dependencies` 블록에 추가합니다.

```kotlin
dependencies {
    // TensorFlow Lite (필수)
    implementation("org.tensorflow:tensorflow-lite:2.17.0")

    // CameraX (카메라 실시간 추론 사용 시)
    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // Coroutines (영상 분석 비동기 처리 시)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
}
```

> Groovy DSL(`build.gradle`) 사용 시 `implementation(...)` 대신 `implementation '...'` 형식으로 작성합니다.

### 3-4. AndroidManifest.xml 권한 추가

```xml
<manifest ...>

    <!-- 카메라 권한 (실시간 카메라 추론 시) -->
    <uses-permission android:name="android.permission.CAMERA" />

    <!-- 동영상/이미지 갤러리 접근 권한 (Android 13 이상) -->
    <uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />

    <!-- Android 12 이하 -->
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
        android:maxSdkVersion="32" />

    <application ...>
        ...
    </application>
</manifest>
```

### 3-5. layout XML에 OverlayView 추가

바운딩박스를 화면에 표시하려면 레이아웃에 `OverlayView`를 추가합니다.
PreviewView(카메라 화면) 또는 ImageView 위에 겹쳐서 배치합니다.

```xml
<!-- activity_main.xml 또는 fragment_xxx.xml -->
<FrameLayout
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <!-- 카메라 프리뷰 또는 이미지뷰 -->
    <androidx.camera.view.PreviewView
        android:id="@+id/previewView"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

    <!-- 바운딩박스 오버레이 (위에 겹침) -->
    <com.yourcompany.yourapp.OverlayView
        android:id="@+id/overlayView"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

</FrameLayout>
```

> `com.yourcompany.yourapp.OverlayView` 부분은 실제 패키지명으로 변경하세요.

---

## 4. 로직 통합 방법

`MainActivity.kt`에 있는 로직은 성격에 따라 두 가지로 나뉩니다.

### A. 재사용 가능한 순수 분석 로직

UI와 무관하므로 그대로 복사해서 사용합니다.

| 메서드 | 역할 |
|--------|------|
| `selectTopFrameCandidates()` | 영상에서 베스트 프레임 최대 3개 추출 |
| `computeHoldAreaSum()` | 검출된 홀드의 화면 면적 합산 |
| `computeLaplacianVariance()` | 프레임 블러 정도 계산 |
| `computeBrightness()` | 프레임 평균 밝기 계산 |
| `computeQualityPenalty()` | 블러·밝기 기반 품질 페널티 계산 |
| `mapDetectionsToOverlay()` | Detection 좌표를 화면(OverlayView) 좌표로 변환 |
| `resizeForAnalysis()` | Bitmap을 분석용 최대 너비로 리사이즈 |
| `ensureArgb8888()` | Bitmap 포맷을 ARGB_8888로 보장 |
| `FrameCandidate` data class | 프레임 후보 데이터 구조 |
| `FrameScoreLog` data class | 프레임 점수 로그 데이터 구조 |

### B. UI에 종속된 코드 (수정 필요)

타 프로젝트의 View 구조에 맞게 수정합니다.

```kotlin
// 원본 (DDgo_AI_yolo - MainActivity 기준)
binding.overlayView.setDetections(detections)
binding.statusText.text = "분석 중..."
binding.mediaImageView.setImageBitmap(bitmap)

// 타 프로젝트에서 수정 예시
overlayView.setDetections(detections)         // ViewBinding 변수명 변경
statusTextView.text = "분석 중..."
resultImageView.setImageBitmap(bitmap)
```

### C. 권장 통합 방식

#### 방식 1: 기존 Activity/Fragment에 직접 통합 (빠름)

A 그룹 메서드들을 기존 Activity/Fragment에 붙여넣고,
`binding.*` 참조만 타 프로젝트 View에 맞게 수정합니다.

#### 방식 2: 헬퍼 클래스로 분리 (권장)

분석 로직을 별도 클래스로 분리하면 Activity가 깔끔해집니다.

```kotlin
// VideoFrameAnalyzer.kt
class VideoFrameAnalyzer(
    private val detector: YoloV8TFLite
) {
    companion object {
        private const val VIDEO_SAMPLE_INTERVAL_MS = 500L
        private const val VIDEO_ANALYSIS_MAX_WIDTH = 640
        private const val TOP_FRAME_COUNT = 3
        private const val HOLD_COUNT_WEIGHT = 0.2f
        private const val BRIGHTNESS_THRESHOLD = 0.25f
        private const val BLUR_VARIANCE_THRESHOLD = 120f
        private const val BRIGHTNESS_PENALTY_WEIGHT = 0.8f
        private const val BLUR_PENALTY_WEIGHT = 1.2f
    }

    suspend fun selectTopFrames(
        context: Context,
        uri: Uri,
        onProgress: (Int) -> Unit = {}
    ): List<FrameCandidate> {
        // MainActivity의 selectTopFrameCandidates() 로직 그대로 이동
    }

    // computeHoldAreaSum, computeLaplacianVariance 등 private 메서드 포함
}
```

Activity에서는 간결하게 호출만 합니다.

```kotlin
// YourActivity.kt
private val analyzer by lazy { VideoFrameAnalyzer(yolo!!) }

private fun analyzeVideo(uri: Uri) {
    lifecycleScope.launch {
        val candidates = withContext(Dispatchers.Default) {
            analyzer.selectTopFrames(context = this@YourActivity, uri = uri) { progress ->
                statusTextView.text = "분석 중... $progress%"
            }
        }
        // UI 업데이트
        showCandidates(candidates)
    }
}
```

---

## 5. 전체 코드 예시

### 5-1. YoloV8TFLite 초기화 및 기본 사용

```kotlin
class YourActivity : AppCompatActivity() {

    private var yolo: YoloV8TFLite? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ...

        // 초기화
        runCatching {
            yolo = YoloV8TFLite(this)  // inputSize=640, confThres=0.35, iouThres=0.45 기본값
        }.onFailure {
            Toast.makeText(this, "모델 로드 실패", Toast.LENGTH_SHORT).show()
        }
    }
}
```

### 5-2. 카메라 실시간 추론

```kotlin
val analysis = ImageAnalysis.Builder()
    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
    .build()

analysis.setAnalyzer(cameraExecutor) { imageProxy ->
    val bitmap = imageProxy.toBitmap()  // ImageProxyExt.kt 확장함수
    val detections = yolo!!.detect(
        bitmap,
        overlayView.width,
        overlayView.height
    )
    runOnUiThread {
        overlayView.setDetections(detections)
    }
    imageProxy.close()
}
```

### 5-3. 이미지 분석

```kotlin
fun analyzeImage(uri: Uri) {
    val bitmap = /* uri로부터 Bitmap 디코딩 */
    val detections = yolo!!.detect(bitmap, bitmap.width, bitmap.height)

    imageView.setImageBitmap(bitmap)
    overlayView.post {
        // 이미지뷰 크기에 맞게 좌표 변환
        val scaleX = overlayView.width.toFloat() / bitmap.width
        val scaleY = overlayView.height.toFloat() / bitmap.height
        val mapped = detections.map { d ->
            Detection(
                RectF(
                    d.box.left * scaleX,
                    d.box.top * scaleY,
                    d.box.right * scaleX,
                    d.box.bottom * scaleY
                ),
                d.score
            )
        }
        overlayView.setDetections(mapped)
    }
}
```

### 5-4. 영상 베스트 프레임 추출

```kotlin
fun analyzeVideo(uri: Uri) {
    lifecycleScope.launch {
        val topCandidates = withContext(Dispatchers.Default) {
            selectTopFrameCandidates(uri, yolo!!)  // MainActivity에서 복사한 메서드
        }

        // 최고 점수 프레임 자동 선택
        val best = topCandidates.maxByOrNull { it.finalScore } ?: return@launch
        imageView.setImageBitmap(best.bitmap)

        val detections = yolo!!.detect(best.bitmap, best.bitmap.width, best.bitmap.height)
        overlayView.setDetections(detections)
    }
}
```

### 5-5. Detection 결과 구조

```kotlin
// Detection 데이터 클래스 (OverlayView.kt에 정의됨)
data class Detection(val box: RectF, val score: Float)

// 사용 예시
for (detection in detections) {
    val box = detection.box       // RectF (left, top, right, bottom) - 픽셀 단위
    val score = detection.score   // Float (0.0 ~ 1.0 신뢰도)

    Log.d("YOLO", "홀드 감지: score=$score, box=$box")
}
```

---

## 6. 트러블슈팅

### 모델이 로드되지 않는 경우

- `assets/` 폴더에 `.tflite` 파일이 있는지 확인
- 파일명이 `DEFAULT_MODEL_ASSET` 상수와 정확히 일치하는지 확인 (대소문자 구분)
- `app/build.gradle.kts`에 아래 설정이 있는지 확인:
  ```kotlin
  android {
      sourceSets {
          getByName("main") {
              assets.srcDirs("src/main/assets")
          }
      }
  }
  ```
  (일반적으로 자동 인식되므로 생략 가능)

### 검출이 안 되는 경우

- `confThres` 값을 낮춰봅니다 (기본 `0.35f` → `0.2f`).
- 로그에서 `maxYoloV8Score` 값을 확인합니다. 0에 가까우면 모델이 해당 클래스를 인식 못 하는 상황.
- 입력 이미지가 `ARGB_8888` 형식인지 확인합니다 (`ensureArgb8888()` 사용).

### 속도가 느린 경우

- `VIDEO_ANALYSIS_MAX_WIDTH`를 줄여 영상 분석 해상도를 낮춥니다 (기본 640).
- `VIDEO_SAMPLE_INTERVAL_MS`를 늘려 샘플링 간격을 줄입니다 (기본 500ms → 1000ms).
- GPU 델리게이트 사용:
  ```kotlin
  // build.gradle.kts에 추가
  implementation("org.tensorflow:tensorflow-lite-gpu:2.17.0")

  // YoloV8TFLite.kt init 블록에서 옵션 추가
  val options = Interpreter.Options().apply {
      addDelegate(GpuDelegate())
  }
  interpreter = Interpreter(model, options)
  ```

### NMS 후에도 박스가 너무 많은 경우

- `confThres`를 높이거나 `iouThres`를 낮춥니다.
  ```kotlin
  val yolo = YoloV8TFLite(
      context = this,
      confThres = 0.5f,   // 더 높은 신뢰도만 통과
      iouThres = 0.35f    // 더 적극적으로 겹치는 박스 제거
  )
  ```

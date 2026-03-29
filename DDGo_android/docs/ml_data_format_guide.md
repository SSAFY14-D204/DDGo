# DDGo Android ML 데이터 포맷 및 연동 가이드

본 문서는 DDGo 프로젝트 내에서 사용되는 AI 모델(MediaPipe Pose, YOLO Hold Detection)의 출력 데이터 형태와, 이를 추후 물리 시뮬레이터 및 기타 로직에서 어떻게 불러와 사용해야 하는지에 대한 가이드를 제공합니다.

## 1. MediaPipe Pose 출력 데이터 형태

MediaPipe는 사람의 관절 및 신체 부위를 33개의 3D 좌표(Landmark)로 추출합니다. 추출된 데이터는 도메인 모델인 `Pose`와 `PoseLandmark` 객체로 매핑됩니다.

### 데이터 구조 (`com.ddgo.app.domain.model.Pose`)
```kotlin
data class Pose(
    val frameTimeMs: Long,             // 프레임 타임스탬프 (비디오 추출 시, 밀리초 단위)
    val landmarks: List<PoseLandmark>  // 33개의 신체 부위 랜드마크 리스트
)

data class PoseLandmark(
    val index: Int,  // MediaPipe 랜드마크 인덱스 (0~32)
    val x: Float,    // 화면 해상도 기준 0.0 ~ 1.0으로 정규화된 X 좌표
    val y: Float,    // 화면 해상도 기준 0.0 ~ 1.0으로 정규화된 Y 좌표
    val z: Float     // 엉덩이 중앙점 기준의 정규화된 깊이(원근) 값
)
```

### MediaPipe 33개 관절 랜드마크 인덱스 매핑 (Full Body)
물리 시뮬레이터에서 랜드마크 데이터를 활용할 때, 각 데이터의 `index` 값을 통해 해당 부위가 어떤 신체와 매핑되어 있는지 검증할 수 있습니다. 아래는 MediaPipe Pose의 공식 인덱스 가이드입니다.

| 인덱스 | 부위명 | 인덱스 | 부위명 | 인덱스 | 부위명 |
| :---: | :--- | :---: | :--- | :---: | :--- |
| **0** | 코 (Nose) | **11** | 왼쪽 어깨 (Left shoulder) | **22** | 오른쪽 엄지 (Right thumb) |
| **1** | 왼쪽 눈 안쪽 (Left eye inner) | **12** | 오른쪽 어깨 (Right shoulder) | **23** | 왼쪽 엉덩이 (Left hip) |
| **2** | 왼쪽 눈 (Left eye) | **13** | 왼쪽 팔꿈치 (Left elbow) | **24** | 오른쪽 엉덩이 (Right hip) |
| **3** | 왼쪽 눈 바깥쪽 (Left eye outer) | **14** | 오른쪽 팔꿈치 (Right elbow) | **25** | 왼쪽 무릎 (Left knee) |
| **4** | 오른쪽 눈 안쪽 (Right eye inner) | **15** | 왼쪽 손목 (Left wrist) | **26** | 오른쪽 무릎 (Right knee) |
| **5** | 오른쪽 눈 (Right eye) | **16** | 오른쪽 손목 (Right wrist) | **27** | 왼쪽 발목 (Left ankle) |
| **6** | 오른쪽 눈 바깥쪽 (Right eye outer) | **17** | 왼쪽 새끼손가락 (Left pinky) | **28** | 오른쪽 발목 (Right ankle) |
| **7** | 왼쪽 귀 (Left ear) | **18** | 오른쪽 새끼손가락 (Right pinky) | **29** | 왼쪽 발뒤꿈치 (Left heel) |
| **8** | 오른쪽 귀 (Right ear) | **19** | 왼쪽 검지 (Left index) | **30** | 오른쪽 발뒤꿈치 (Right heel) |
| **9** | 입 왼쪽 (Mouth left) | **20** | 오른쪽 검지 (Right index) | **31** | 왼쪽 발끝 (Left foot index) |
| **10**| 입 오른쪽 (Mouth right) | **21** | 왼쪽 엄지 (Left thumb) | **32** | 오른쪽 발끝 (Right foot index) |

### 주요 관절 인덱스 (물리 시뮬레이터에서 주로 사용할 손 좌표)
- **왼손:** 15(손목), 17(새끼손가락 끝), 19(검지 끝), 21(엄지 끝)
- **오른손:** 16(손목), 18(새끼손가락 끝), 20(검지 끝), 22(엄지 끝)

---

## 2. YOLO BBox 및 Segmentation 데이터 형태

YOLO 모델은 암벽에 부착된 홀드(Hold)의 위치와 형태, 색상 정보를 감지하여 도메인 모델인 `Hold` 객체로 반환합니다.

### 데이터 구조 (`com.ddgo.app.domain.model.Hold`)
```kotlin
data class Hold(
    val boundingBox: BoundingBox, // 홀드의 사각형 경계 영역
    val confidence: Float,        // 모델 검출 신뢰도 (0.0 ~ 1.0)
    val polygon: List<Point>,     // 정밀한 형태를 나타내는 세그멘테이션 외곽선 포인트 리스트
    val colorLabel: String,       // 색상 분류 결과 (예: "red", "blue")
    val colorScore: Float         // 색상 분류 신뢰도
)

data class BoundingBox(
    val left: Float,   // 화면 기준 0.0 ~ 1.0 으로 정규화된 X 최소값
    val top: Float,    // 화면 기준 0.0 ~ 1.0 으로 정규화된 Y 최소값
    val right: Float,  // 화면 기준 0.0 ~ 1.0 으로 정규화된 X 최대값
    val bottom: Float  // 화면 기준 0.0 ~ 1.0 으로 정규화된 Y 최대값
)

data class Point(
    val x: Float, // 0.0 ~ 1.0 정규화 X
    val y: Float  // 0.0 ~ 1.0 정규화 Y
)
```

---

## 3. 데이터 활용 가이드 (물리 엔진 / 시뮬레이터 연동)

이 데이터들을 소비하여 "어떤 손이 어떤 홀드를 잡고 있는지(교차하는지)" 구해야 한다면, 다음과 같은 파이프라인으로 접근하여 데이터를 불러옵니다.

### Step 1: 의존성 주입을 통한 예측기 준비 (Hilt 사용)
데이터를 산출해 내거나 소비할 컨트롤러 또는 서비스 영역에서, `PoseEstimator`와 `HoldDetector` 인터페이스를 의존성 주입(DI) 받아 사용합니다. 실제 구현체인 `PoseEstimatorImpl`과 `HoldDetectorImpl`이 자동으로 주입됩니다.

```kotlin
import javax.inject.Inject

class GraspDetectionService @Inject constructor(
    private val poseEstimator: PoseEstimator,
    private val holdDetector: HoldDetector
) {
    // ...
}
```

### Step 2: 프레임에서 도메인 데이터 추출
원하는 시점이나 프레임(이미지 비트맵)에 대하여 모델을 호출해 데이터를 가져옵니다. 

```kotlin
// 1. 이미지 프레임 상의 모든 홀드 데이터 추출
val holds: List<Hold> = holdDetector.detectFromFrame(bitmap)

// 2. 단일 프레임에서의 물리적인 포즈 데이터 추출
// (동영상의 일괄 추출이 필요할 경우 estimateFromVideo() 메서드 고려)
val poseLandmarks: List<PoseLandmark> = poseEstimator.estimateFromFrame(bitmap)
val pose = Pose(frameTimeMs = System.currentTimeMillis(), landmarks = poseLandmarks)
```

### Step 3: 데이터 좌표계의 통합 및 교차(AABB) 판별
MediaPipe(Pose)와 YOLO(Hold) **모두 화면 가로/세로 기준 0.0 ~ 1.0 사이로 정규화(Normalized)된 좌표를 반환**합니다. (예: `x=0.5`라면 화면 정중앙)
따라서 두 데이터 간의 해상도나 화면 비율 스케일링을 별도로 거치지 않아도 직관적으로 Bounding Box 내부에 손 좌표계가 위치하는지 검사할 수 있습니다.

```kotlin
fun findGrasp(pose: Pose, holds: List<Hold>) {
    // 물리 엔진 교차 판별을 위해 양손의 검지만 예시로 추출
    val leftIndexFinger = pose.landmarks.find { it.index == 19 }

    if (leftIndexFinger != null) {
        // 손가락의 x, y가 BBox의 left~right, top~bottom 사이에 있는지 검사
        val targetHold = holds.find { hold ->
            val box = hold.boundingBox
            (leftIndexFinger.x in box.left..box.right) && 
            (leftIndexFinger.y in box.top..box.bottom)
        }

        if (targetHold != null) {
            // [연동 부분]
            // 찾아낸 Hold 인스턴스와 손 좌표(leftIndexFinger)를 
            // 물리 시뮬레이터 객체에 이벤트로 전송합니다.
            sendToPhysicsSimulator(
                handType = "LEFT_HAND", 
                holdColor = targetHold.colorLabel,
                contactX = leftIndexFinger.x, 
                contactY = leftIndexFinger.y
            )
        }
    }
}
```

### Step 4: 추가적인 정밀 최적화 (Advanced)
현재 제공되는 `Hold` 도메인 모델에서는 사각형 `boundingBox` 이외에도, YOLO Segmentation으로 추론된 `polygon: List<Point>` 데이터를 함께 제공합니다. 사각형을 기준으로 한 충돌 검사가 다소 여유롭거나 오차가 있다면, **Ray Casting(Point-in-Polygon)** 알고리즘을 사용해 손 랜드마크가 다각형 내부 영역에 속해 있는지 더 치밀하게 체크할 수 있습니다.

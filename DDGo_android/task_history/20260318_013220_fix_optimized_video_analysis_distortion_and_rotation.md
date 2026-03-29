# Task History: Optimized Video Analysis Distortion & Rotation Fix

- **날짜**: 2026-03-18
- **작업자**: Antigravity (AI Assistant)
- **대상 파일**: 
    - `OptimizedPrePoseVideoAnalyzer.kt`
    - `PrePoseVideoAnalyzer.kt`
    - `PrePoseLandmarkerScreen.kt`

## 1. 만났던 문제점 (Challenges)

### A. 이미지 깨짐 및 비정상 출력
최적화 모드 도입 시 `MediaCodec`의 출력을 `ImageReader`로 받는 과정에서 이미지가 형체를 알아볼 수 없게 겹치거나 깨지는 현상이 발생했습니다.

### B. 비율 왜곡 (Aspect Ratio Distortion)
1:2 비율(세로형) 영상을 넣었을 때 2:1(가로형) 버퍼에 강제로 맞춰지며 이미지가 위아래로 눌리는("눌림") 현상이 발생했습니다. 이는 비디오의 인코딩 규격만 보고 버퍼를 생성했기 때문입니다.

### C. 방향 오류 (Orientation Issues)
영상이 똑바로 서 있지 않고 왼쪽으로 90도 돌아가 있거나 상하가 반전되는 문제가 있었습니다. OpenGL의 `rotateM` 함수가 반시계 방향(CCW)으로 동작하는 특성과 Android 비디오 회전 메타데이터의 시계 방향(CW) 특성이 충돌했습니다.

### D. 캡처 누락 및 성능 저하
복잡한 회전 로직 최적화 도중 5초 간격의 디버그 이미지 캡처가 멈추거나, CPU에서 픽셀을 직접 돌리려 할 때 성능이 급격히 떨어지는 문제가 있었습니다.

---

## 2. 해결 방법 (Solutions)

### A. OpenGL EGL 하드웨어 스케일링
`MediaCodec` -> `SurfaceTexture` -> `OpenGL` -> `ImageReader` 파이프라인을 구축하여 전처리를 GPU에서 수행하도록 설계했습니다.

### B. 시각적 해상도 동적 스왑 (Visual-Aware Dimensions)
비디오의 `rotationDegrees` 메타데이터를 분석하여 90도/270도 회전 시 `targetW`와 `targetH`를 자동으로 스왑하도록 수정했습니다. 이를 통해 세로형 영상은 세로형 버퍼(360x640)에 담기게 되어 비율 왜곡을 근본적으로 해결했습니다.

### C. MVP 행렬 기반 GPU 사전 회전
OpenGL 셰이더에 **MVP(Model-View-Projection) 행렬**을 도입했습니다.
- `rotateM(mvpMatrix, 0, -rotationDegrees + 90f, 0f, 0f, 1f)`를 적용하여 픽셀을 버퍼에 뿌릴 때 이미 원하는 방향으로 돌려서 렌더링합니다.
- MediaPipe에는 `setRotationDegrees(0)`을 전달하여 중복 연산을 방지했습니다.

### D. Stride-Safe 픽셀 추출
기기마다 다른 메모리 정렬(`rowStride`) 문제를 해결하기 위해, 비트맵 생성 시 `copyPixelsFromBuffer`를 한 줄 단위로 수행하는 로직을 구현하여 왜곡 없는 캡처를 보장했습니다.

---

## 3. 앞으로의 가이드 (Future Recommendations)

1. **버퍼 규격 확인**: 새로운 비디오 분석 기능을 추가할 때는 반드시 `MediaFormat.KEY_ROTATION`을 체크하여 `ImageReader`의 가로/세로 비율을 시각적 방향에 맞춰야 합니다.
2. **GPU 가속 우선**: 픽셀 회전이나 스케일링은 CPU(`Bitmap` 연산)보다 OpenGL 셰이더(`MVP Matrix`)에서 처리하는 것이 훨씬 효율적입니다.
3. **Stride 주의**: `android.media.Image`에서 직접 픽셀을 뽑을 때는 항상 `plane.rowStride`와 `width * pixelStride`가 일치하는지 확인하고, 다를 경우 한 줄씩 복사하는 안전 로직을 사용해야 합니다.
4. **디버깅 가시성 유지**: 이번에 구현한 **5초 간격 캡처 로직**처럼, 전처리 파이프라인 중간 단계의 이미지를 UI에 노출하면 이후 문제 발생 시 원인 파악이 매우 빠릅니다.

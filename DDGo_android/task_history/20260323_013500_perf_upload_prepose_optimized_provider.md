# Task History: Upload Pre-Pose 최적화 경로 이식 성능 기록

- **날짜**: 2026-03-23
- **작업자**: Codex (AI Assistant)
- **주제**: upload pre-pose에 optimized surface decode 경로 이식 후 실제 로그 기준 성능 비교
- **관련 파일**
  - `app/src/main/java/com/ddgo/app/data/ml/mediapipe/UploadOptimizedPrePoseVideoAnalyzer.kt`
  - `app/src/main/java/com/ddgo/app/data/ml/mediapipe/OptimizedPrePoseVideoAnalysisProvider.kt`
  - `app/src/main/java/com/ddgo/app/data/ml/mediapipe/PrePoseAnalysisSupport.kt`
  - `app/src/main/java/com/ddgo/app/data/ml/mediapipe/SequentialPoseVideoAnalyzer.kt`
  - `app/src/main/java/com/ddgo/app/di/MlModule.kt`

## 1. 작업 목적

기존 upload pre-pose는 `SequentialPoseVideoAnalyzer`를 통해 다음 경로를 탔습니다.

- `decoder.getOutputImage()`
- `Image -> NV21/YUV -> JPEG -> Bitmap`
- `Bitmap rotate/scale`
- `BitmapImageBuilder`
- `PoseLandmarker.detectForVideo()`

이 경로는 debug의 optimized analyzer보다 훨씬 무거웠고, 실제 업로드에서는 pre-pose가 40초대까지 길어지는 병목으로 나타났습니다.

이번 작업에서는 debug의 아이디어를 그대로 UI로 노출하지 않고, **upload 전용 optimized provider**를 `main`에 추가해 아래처럼 바꿨습니다.

- `MediaCodec(surface output)`
- `SurfaceTexture`
- `EGL`
- `ImageReader(RGBA)`
- `BitmapImageBuilder`
- `PoseLandmarker.detectForVideo()`

즉, **MediaPipe 입력 전의 프레임 공급 경로만 가볍게 바꾸고**, 결과 계약인 `PrePoseVideoAnalysisResult`는 그대로 유지했습니다.

## 2. 이전 로그 기준 속도

이전 기준 로그에서 pre-pose 구간은 다음과 같았습니다.

- `PREPOSE_RUNNING`
  - 시작 시각: `00:30:12.773`
- `PREPOSE_READY`
  - 완료 시각: `00:30:59.379`

### 계산

- 총 소요 시간: `46.606초`

### 해석

- 이 시점의 upload pre-pose는 기존 sequential 경로를 타고 있었고,
- `processedFrameCount=628`, `poseCount=533` 수준을 처리하는데 약 46.6초가 걸렸습니다.

## 3. 최적화 이후 실제 로그 기준 속도

최적화 이식 후 같은 흐름의 실제 로그는 다음과 같았습니다.

### optimized provider 경로 사용 확인

- `01:28:18.172`
  - `UPLOAD_PREPOSE_PROVIDER_OPTIMIZED_BEGIN`
- `01:28:32.247`
  - `UPLOAD_PREPOSE_PROVIDER_OPTIMIZED_SUCCESS`
  - `elapsedMs=14075`
  - `poseCount=534`
  - `processedFrameCount=628`

### pre-pose 실행 구간

- `PREPOSE_RUNNING`
  - 시작 시각: `01:28:18.172`
- `PREPOSE_READY`
  - 완료 시각: `01:28:32.356`

### 계산

- 총 소요 시간: `14.184초`

## 4. 전후 비교

### 속도 비교

- 이전 pre-pose:
  - `PREPOSE_RUNNING -> PREPOSE_READY`
  - 약 `46.606초`
- 이번 pre-pose:
  - `01:28:18.172 -> 01:28:32.356`
  - 약 `14.184초`

즉 pre-pose 자체는:

- 약 `32.422초` 단축
- 약 `69.6%` 감소
- 체감상 약 `3.3배` 빨라진 셈입니다

## 5. 왜 빨라졌는가

속도 개선의 핵심 원인은 **GPU on/off 토글 자체보다 프레임 공급 경로를 바꾼 것**입니다.

### 이전

- `Image -> YUV/JPEG -> Bitmap -> rotate/scale`
- 큰 중간 bitmap 생성 비용이 큼
- upload는 `MAX_INFERENCE_DIMENSION_PX = 640`

### 이후

- `SurfaceTexture + EGL + ImageReader(RGBA)`
- 초기에 작은 inference surface로 맞춘 뒤 바로 MediaPipe에 공급
- upload optimized 경로는 `maxInferenceDimensionPx = 384`
- sequential fallback은 그대로 유지

### 중요한 점

이번 개선은 단순히 프레임을 덜 돌려서 생긴 개선이 아닙니다.

- 이전: `processedFrameCount=628`, `poseCount=533`
- 이후: `processedFrameCount=628`, `poseCount=534`

즉, **처리한 프레임 수는 거의 그대로인데 wall-clock만 크게 줄었습니다.**
이건 프레임 공급과 전처리 비용이 실제 병목이었다는 뜻입니다.

## 6. 실제 로그에서 확인된 사실

### 좋은 점

- optimized 경로가 실제로 사용됨
  - `UPLOAD_PREPOSE_PROVIDER_OPTIMIZED_BEGIN`
  - `UPLOAD_PREPOSE_PROVIDER_OPTIMIZED_SUCCESS`
- sequential fallback은 타지 않음
  - `UPLOAD_PREPOSE_PROVIDER_SEQUENTIAL_FALLBACK_*` 로그 없음
- pre-pose는 1회만 실행됨
  - `PREPOSE_RUNNING` 1회
  - `PREPOSE_READY` 1회
- final AI도 중복 실행되지 않음

### 여전히 남아 있는 점

pre-pose는 빨라졌지만, 전체 체감 병목이 완전히 사라진 것은 아닙니다.

- `HOLD_PRECOMPUTE_DONE elapsedMs=22317`
- `PREPOSE_READY elapsedMs=14184`

즉 이번 실행 기준으로는:

- hold precompute: 약 `22.3초`
- pre-pose: 약 `14.2초`

현재 구조상 pre-pose는 `hold precompute`가 끝난 뒤에 시작되므로, **다음 큰 병목은 hold precompute 쪽**입니다.

## 7. 결론

이번 작업은 실제 로그 기준으로 성공했습니다.

- upload pre-pose optimized provider가 실제로 동작함
- fallback 없이 optimized success로 완료됨
- `46.606초 -> 14.184초`로 단축됨
- 약 `32.4초` 절감, 약 `69.6%` 감소

한 줄로 정리하면:

> upload pre-pose는 debug optimized 아이디어를 main 계약에 맞게 이식한 뒤, 실제 로그 기준으로 약 46.6초에서 14.2초로 줄었다.

## 8. 다음 확인 포인트

- `PREPOSE_RUNNING -> PREPOSE_READY`를 기준으로 계속 측정할 것
  - `PREPOSE_PENDING_MARKED -> READY`는 hold 대기 시간이 섞여 오해를 만든다
- 다음 최적화 1순위는 hold precompute 병목 검토
- 정확도 회귀가 없는지
  - `AttemptResult`
  - `Hold reach`
  - `Final AI`
  - `processedFrameCount / poseCount`
  를 계속 같이 볼 것

## 9. 원문 로그 기록

아래 로그는 실제 비교에 사용한 원문 라인들입니다.

### A. 이전 기준 로그 원문

```text
2026-03-23 00:30:12.771 26763-26763 UploadAiTrace           com.ddgo.app                         D  [AI_TRACE] event=PREPOSE_START_AFTER_HOLD_TERMINAL generation=2 playbackUri=primary_08d37a19-8b34-4a98-b165-ec20738b10f5.mp4 status=hold_ready
2026-03-23 00:30:12.771 26763-26763 UploadAiTrace           com.ddgo.app                         D  [AI_TRACE] event=PREPOSE_QUEUE_ENQUEUED generation=2 playbackUri=primary_08d37a19-8b34-4a98-b165-ec20738b10f5.mp4 status=new_pending taskId=1
2026-03-23 00:30:12.772 26763-26763 UploadAiTrace           com.ddgo.app                         D  [AI_TRACE] event=PREPOSE_BATCH_STATE generation=2 playbackUri=primary_08d37a19-8b34-4a98-b165-ec20738b10f5.mp4 totalCount=1 pendingCount=1 runningCount=0 readyCount=0 failedCount=0
2026-03-23 00:30:12.772 26763-26763 UploadAiTrace           com.ddgo.app                         D  [AI_TRACE] event=PREPOSE_WORKER_KICKED generation=2 playbackUri=primary_08d37a19-8b34-4a98-b165-ec20738b10f5.mp4 status=worker_start
2026-03-23 00:30:12.773 26763-26797 UploadAiTrace           com.ddgo.app                         D  [AI_TRACE] event=PREPOSE_RUNNING generation=2 playbackUri=primary_08d37a19-8b34-4a98-b165-ec20738b10f5.mp4 status=running taskId=1
2026-03-23 00:30:12.773 26763-26797 UploadAiTrace           com.ddgo.app                         D  [AI_TRACE] event=PREPOSE_BATCH_STATE generation=2 playbackUri=primary_08d37a19-8b34-4a98-b165-ec20738b10f5.mp4 totalCount=1 pendingCount=0 runningCount=1 readyCount=0 failedCount=0
2026-03-23 00:30:59.379 26763-26796 UploadAiTrace           com.ddgo.app                         D  [AI_TRACE] event=PREPOSE_READY generation=2 playbackUri=primary_08d37a19-8b34-4a98-b165-ec20738b10f5.mp4 status=ready elapsedMs=46607 poseCount=533 processedFrameCount=628 hasAiPoseSequence=true
2026-03-23 00:30:59.379 26763-26796 UploadAiTrace           com.ddgo.app                         D  [AI_TRACE] event=PREPOSE_BATCH_STATE generation=2 playbackUri=primary_08d37a19-8b34-4a98-b165-ec20738b10f5.mp4 totalCount=1 pendingCount=0 runningCount=0 readyCount=1 failedCount=0
```

### B. 최적화 이후 로그 원문

```text
2026-03-23 01:28:18.170   613-613   UploadAiTrace           com.ddgo.app                         D  [AI_TRACE] event=PREPOSE_START_AFTER_HOLD_TERMINAL generation=2 playbackUri=primary_df282f72-2bba-4d95-a403-10f5eec6c48f.mp4 status=hold_ready
2026-03-23 01:28:18.171   613-613   UploadAiTrace           com.ddgo.app                         D  [AI_TRACE] event=PREPOSE_QUEUE_ENQUEUED generation=2 playbackUri=primary_df282f72-2bba-4d95-a403-10f5eec6c48f.mp4 status=new_pending taskId=1
2026-03-23 01:28:18.171   613-613   UploadAiTrace           com.ddgo.app                         D  [AI_TRACE] event=PREPOSE_BATCH_STATE generation=2 playbackUri=primary_df282f72-2bba-4d95-a403-10f5eec6c48f.mp4 totalCount=1 pendingCount=1 runningCount=0 readyCount=0 failedCount=0
2026-03-23 01:28:18.171   613-613   UploadAiTrace           com.ddgo.app                         D  [AI_TRACE] event=PREPOSE_WORKER_KICKED generation=2 playbackUri=primary_df282f72-2bba-4d95-a403-10f5eec6c48f.mp4 status=worker_start
2026-03-23 01:28:18.172   613-678   UploadAiTrace           com.ddgo.app                         D  [AI_TRACE] event=PREPOSE_RUNNING generation=2 playbackUri=primary_df282f72-2bba-4d95-a403-10f5eec6c48f.mp4 status=running taskId=1
2026-03-23 01:28:18.172   613-678   UploadAiTrace           com.ddgo.app                         D  [AI_TRACE] event=PREPOSE_BATCH_STATE generation=2 playbackUri=primary_df282f72-2bba-4d95-a403-10f5eec6c48f.mp4 totalCount=1 pendingCount=0 runningCount=1 readyCount=0 failedCount=0
2026-03-23 01:28:18.172   613-678   UploadAiTrace           com.ddgo.app                         D  [AI_TRACE] event=UPLOAD_PREPOSE_PROVIDER_OPTIMIZED_BEGIN playbackUri=primary_df282f72-2bba-4d95-a403-10f5eec6c48f.mp4 analysisFpsLimit=10 maxInferenceDimensionPx=384
2026-03-23 01:28:32.247   613-678   UploadAiTrace           com.ddgo.app                         D  [AI_TRACE] event=UPLOAD_PREPOSE_PROVIDER_OPTIMIZED_SUCCESS playbackUri=primary_df282f72-2bba-4d95-a403-10f5eec6c48f.mp4 status=success elapsedMs=14075 poseCount=534 processedFrameCount=628
2026-03-23 01:28:32.356   613-678   UploadAiTrace           com.ddgo.app                         D  [AI_TRACE] event=PREPOSE_READY generation=2 playbackUri=primary_df282f72-2bba-4d95-a403-10f5eec6c48f.mp4 status=ready elapsedMs=14184 poseCount=534 processedFrameCount=628 hasAiPoseSequence=true
2026-03-23 01:28:32.356   613-678   UploadAiTrace           com.ddgo.app                         D  [AI_TRACE] event=PREPOSE_BATCH_STATE generation=2 playbackUri=primary_df282f72-2bba-4d95-a403-10f5eec6c48f.mp4 totalCount=1 pendingCount=0 runningCount=0 readyCount=1 failedCount=0
```

### C. 원문 로그 기준 계산 메모

- 이전
  - 시작: `00:30:12.773`
  - 완료: `00:30:59.379`
  - 소요: `46.606초`
- 이후
  - 시작: `01:28:18.172`
  - 완료: `01:28:32.356`
  - 소요: `14.184초`

- 차이
  - `32.422초` 단축
  - 약 `69.6%` 감소

# Task History: Hold Mask Resize 1차 최적화 전후 비교

- 날짜: 2026-03-23
- 작업자: Codex (AI Assistant)
- 주제: `scaleMaskToOriginal(...)` 1차 저위험 최적화 전후 성능 비교
- 관련 파일
  - `app/src/main/java/com/ddgo/app/data/ml/common/TFLiteInferenceUtils.kt`
  - `app/src/test/java/com/ddgo/app/data/ml/common/TFLiteInferenceUtilsTest.kt`

## 1. 작업 목적

이번 1차 최적화의 목적은 YOLO hold 탐지 품질을 최대한 유지한 채, `HOLD_YOLO_MASK_RESIZE` 구간의 명시적인 비효율만 줄이는 것이었습니다.

적용한 방향:

- `cropped FloatArray` 제거
- full-resolution `resized FloatArray` 제거
- crop + bilinear interpolation + threshold를 한 번의 루프로 수행
- detection 공통 crop geometry / resize lookup 재사용
- polygon 의미, threshold, downstream 계약은 유지

즉, **알고리즘 의미는 바꾸지 않고 hot path의 중간 메모리 churn만 줄이는 1차 최적화**였습니다.

## 2. 비교 기준

비교는 2026-03-23의 직전 상세 로그와, 1차 최적화 적용 후 로그를 기준으로 했습니다.

비교에 사용한 공통 신호:

- `bestTimeUs=6255`
- `originalWidth=1080`
- `originalHeight=1920`
- `rawHoldCount=67`
- `filteredHoldCount=9`

위 수치가 동일하게 나타나므로, **거의 동일한 유형의 케이스를 비교한 것으로 판단**했습니다.

## 3. 전후 수치 비교

### 핵심 결과

- 판정: **품질 유지 / 속도 개선 실패**
- 품질 신호
  - 이전: `rawHoldCount=67`, `allHoldCount=67`, `filteredHoldCount=9`
  - 이후: `rawHoldCount=67`, `allHoldCount=67`, `filteredHoldCount=9`
- 속도 신호
  - `HOLD_YOLO_MASK_RESIZE_DONE`: 악화
  - `HOLD_YOLO_MASK_POLYGON_DONE`: 악화
  - `HOLD_YOLO_DONE`: 악화
  - `HOLD_PRECOMPUTE_DONE`: 악화

### 수치 비교 표

| 항목 | 개선 전 | 1차 후 | 변화량 | 해석 |
|---|---:|---:|---:|---|
| `HOLD_PERSON_DETECT_DONE` | `239ms` | `172ms` | `-67ms` | 보조 구간, 병목 아님 |
| `HOLD_BEST_FRAME_EXTRACT_SUCCESS` | `337ms` | `384ms` | `+47ms` | 보조 구간, 병목 아님 |
| `HOLD_YOLO_INFERENCE_DONE` | `132ms` | `132ms` | `0ms` | 모델 추론은 동일 |
| `HOLD_YOLO_MASK_BUILD_DONE` | `2912ms` | `3387ms` | `+475ms` | 소폭 악화 |
| `HOLD_YOLO_MASK_RESIZE_DONE` | `13382ms` | `15609ms` | `+2227ms` | 주요 병목, 더 느려짐 |
| `HOLD_YOLO_POLYGON_TRACE_DONE` | `123ms` | `65ms` | `-58ms` | 작아졌지만 영향 작음 |
| `HOLD_YOLO_MASK_POLYGON_DONE` | `16417ms` | `19063ms` | `+2646ms` | 후처리 전체 악화 |
| `HOLD_YOLO_DONE` | `16668ms` | `19267ms` | `+2599ms` | YOLO 전체 악화 |
| `HOLD_CLASSIFY_ALL_DONE` | `4351ms` | `3489ms` | `-862ms` | 분류는 오히려 빨라짐 |
| `HOLD_PRECOMPUTE_DONE` | `21601ms` | `23317ms` | `+1716ms` | hold precompute 전체 악화 |

### 퍼센트 기준

- `HOLD_YOLO_MASK_RESIZE_DONE`
  - `13.382초 -> 15.609초`
  - `+2.227초`
  - 약 `16.6%` 악화
- `HOLD_YOLO_MASK_POLYGON_DONE`
  - `16.417초 -> 19.063초`
  - `+2.646초`
  - 약 `16.1%` 악화
- `HOLD_YOLO_DONE`
  - `16.668초 -> 19.267초`
  - `+2.599초`
  - 약 `15.6%` 악화
- `HOLD_PRECOMPUTE_DONE`
  - `21.601초 -> 23.317초`
  - `+1.716초`
  - 약 `7.9%` 악화

## 4. 병목 재판정

1차 최적화 이후에도 가장 큰 병목은 여전히 `HOLD_YOLO_MASK_RESIZE_DONE`입니다.

1차 후 기준:

- `HOLD_PRECOMPUTE_DONE = 23.317초`
- `HOLD_YOLO_MASK_POLYGON_DONE = 19.063초`
- `HOLD_YOLO_MASK_RESIZE_DONE = 15.609초`

비율로 보면:

- `MASK_RESIZE`는 `HOLD_PRECOMPUTE_DONE`의 약 `66.9%`
- `MASK_RESIZE`는 `MASK_POLYGON_DONE`의 약 `81.9%`

즉, **가장 큰 병목은 여전히 per-detection full-resolution resize**입니다.

## 5. 해석

이번 1차는 중간 배열을 줄였지만, 근본 구조는 그대로였습니다.

현재 구조:

```text
input mask
-> crop 영역 계산
-> original full-resolution 기준 bilinear interpolation
-> original full-resolution Boolean mask
-> polygon trace
```

즉, detection마다 **1080x1920 전체 해상도를 다시 훑는 구조** 자체는 바뀌지 않았습니다.

이번 결과가 악화된 이유에 대한 해석:

- full-frame resize 자체를 제거하지 못함
- direct path로 바꾸면서 per-pixel index lookup과 boolean write는 그대로 남음
- memory access pattern이 더 좋아지지 않았고, 오히려 실측상 불리했을 가능성 큼

중요한 점:

- 모델 품질 신호는 유지됨
- `rawHoldCount`, `allHoldCount`, `filteredHoldCount`는 동일
- 따라서 **정확도 회귀는 보이지 않지만, 성능 ROI는 없었다**

## 6. 결론

이번 1차 최적화는 다음처럼 정리할 수 있습니다.

- 품질 유지: 성공
- 속도 개선: 실패
- 병목 제거: 실패

한 줄 결론:

> `scaleMaskToOriginal(...)`의 중간 배열 제거만으로는 충분한 개선이 나오지 않았고, 실측상 `HOLD_YOLO_MASK_RESIZE`는 오히려 더 느려졌다.

따라서 다음 단계는 원래 논의했던 대로, **2차 `bbox-local + capped resolution` 방향을 검토하는 것이 타당**합니다.

## 7. 원문 로그 발췌

### A. 1차 개선 전 원문

```text
2026-03-23 13:55:24.458  9291-9356  UploadAiTrace  D  [AI_TRACE] event=HOLD_PERSON_DETECT_DONE generation=2 playbackUri=primary_3344285a-3aac-446e-8ea3-8a4c5677245d.mp4 elapsedMs=239 requestedPlaybackUri=file:///data/user/0/com.ddgo.app/cache/primary_3344285a-3aac-446e-8ea3-8a4c5677245d.mp4 bestTimeUs=6255
2026-03-23 13:55:24.800  9291-9356  UploadAiTrace  D  [AI_TRACE] event=HOLD_BEST_FRAME_EXTRACT_SUCCESS generation=2 playbackUri=primary_3344285a-3aac-446e-8ea3-8a4c5677245d.mp4 elapsedMs=337 requestedBestTimeUs=6255 resolvedBestTimeUs=6255 durationUs=62000000 mode=closest attemptIndex=1
2026-03-23 13:55:24.962  9291-9356  UploadAiTrace  D  [AI_TRACE] event=HOLD_YOLO_INFERENCE_DONE elapsedMs=132 modelSize=640 outputCount=2
2026-03-23 13:55:41.451  9291-9356  UploadAiTrace  D  [AI_TRACE] event=HOLD_YOLO_MASK_BUILD_DONE elapsedMs=2912 keptCount=67 detectionCount=67 timingMode=accumulated_per_detection
2026-03-23 13:55:41.451  9291-9356  UploadAiTrace  D  [AI_TRACE] event=HOLD_YOLO_MASK_RESIZE_DONE elapsedMs=13382 keptCount=67 detectionCount=67 originalWidth=1080 originalHeight=1920 timingMode=accumulated_per_detection
2026-03-23 13:55:41.451  9291-9356  UploadAiTrace  D  [AI_TRACE] event=HOLD_YOLO_POLYGON_TRACE_DONE elapsedMs=123 keptCount=67 detectionCount=67 originalWidth=1080 originalHeight=1920 timingMode=accumulated_per_detection
2026-03-23 13:55:41.452  9291-9356  UploadAiTrace  D  [AI_TRACE] event=HOLD_YOLO_MASK_POLYGON_DONE elapsedMs=16417 keptCount=67 detectionCount=67 originalWidth=1080 originalHeight=1920
2026-03-23 13:55:41.468  9291-9356  UploadAiTrace  D  [AI_TRACE] event=HOLD_YOLO_DONE generation=2 playbackUri=primary_3344285a-3aac-446e-8ea3-8a4c5677245d.mp4 elapsedMs=16668 bestTimeUs=6255 rawHoldCount=67
2026-03-23 13:55:45.820  9291-9356  UploadAiTrace  D  [AI_TRACE] event=HOLD_CLASSIFY_ALL_DONE generation=2 playbackUri=primary_3344285a-3aac-446e-8ea3-8a4c5677245d.mp4 elapsedMs=4351 bestTimeUs=6255 rawHoldCount=67 allHoldCount=67
2026-03-23 13:55:45.821  9291-9291  UploadAiTrace  D  [AI_TRACE] event=HOLD_PRECOMPUTE_DONE generation=2 playbackUri=primary_3344285a-3aac-446e-8ea3-8a4c5677245d.mp4 status=ready elapsedMs=21601 rawHoldCount=67 allHoldCount=67
2026-03-23 13:55:48.166  9291-9291  UploadAiTrace  D  [AI_TRACE] event=HOLD_FILTER_APPLIED generation=2 playbackUri=primary_3344285a-3aac-446e-8ea3-8a4c5677245d.mp4 targetColor=yellow allHoldCount=67 filteredHoldCount=9
```

### B. 1차 개선 후 원문

```text
2026-03-23 14:56:03.175  9291-9357  UploadAiTrace  D  [AI_TRACE] event=HOLD_PERSON_DETECT_DONE generation=2 playbackUri=primary_56b34b4e-915a-406f-bc66-dfd8adc9882e.mp4 elapsedMs=172 requestedPlaybackUri=file:///data/user/0/com.ddgo.app/cache/primary_56b34b4e-915a-406f-bc66-dfd8adc9882e.mp4 bestTimeUs=6255
2026-03-23 14:56:03.562  9291-9357  UploadAiTrace  D  [AI_TRACE] event=HOLD_BEST_FRAME_EXTRACT_SUCCESS generation=2 playbackUri=primary_56b34b4e-915a-406f-bc66-dfd8adc9882e.mp4 elapsedMs=384 requestedBestTimeUs=6255 resolvedBestTimeUs=6255 durationUs=62000000 mode=closest attemptIndex=1
2026-03-23 14:56:03.729  9291-9357  UploadAiTrace  D  [AI_TRACE] event=HOLD_YOLO_INFERENCE_DONE elapsedMs=132 modelSize=640 outputCount=2
2026-03-23 14:56:22.814  9291-9357  UploadAiTrace  D  [AI_TRACE] event=HOLD_YOLO_MASK_BUILD_DONE elapsedMs=3387 keptCount=67 detectionCount=67 timingMode=accumulated_per_detection
2026-03-23 14:56:22.814  9291-9357  UploadAiTrace  D  [AI_TRACE] event=HOLD_YOLO_MASK_RESIZE_DONE elapsedMs=15609 keptCount=67 detectionCount=67 originalWidth=1080 originalHeight=1920 timingMode=accumulated_per_detection
2026-03-23 14:56:22.814  9291-9357  UploadAiTrace  D  [AI_TRACE] event=HOLD_YOLO_POLYGON_TRACE_DONE elapsedMs=65 keptCount=67 detectionCount=67 originalWidth=1080 originalHeight=1920 timingMode=accumulated_per_detection
2026-03-23 14:56:22.814  9291-9357  UploadAiTrace  D  [AI_TRACE] event=HOLD_YOLO_MASK_POLYGON_DONE elapsedMs=19063 keptCount=67 detectionCount=67 originalWidth=1080 originalHeight=1920
2026-03-23 14:56:22.830  9291-9357  UploadAiTrace  D  [AI_TRACE] event=HOLD_YOLO_DONE generation=2 playbackUri=primary_56b34b4e-915a-406f-bc66-dfd8adc9882e.mp4 elapsedMs=19267 bestTimeUs=6255 rawHoldCount=67
2026-03-23 14:56:26.319  9291-9357  UploadAiTrace  D  [AI_TRACE] event=HOLD_CLASSIFY_ALL_DONE generation=2 playbackUri=primary_56b34b4e-915a-406f-bc66-dfd8adc9882e.mp4 elapsedMs=3489 bestTimeUs=6255 rawHoldCount=67 allHoldCount=67
2026-03-23 14:56:26.320  9291-9291  UploadAiTrace  D  [AI_TRACE] event=HOLD_PRECOMPUTE_DONE generation=2 playbackUri=primary_56b34b4e-915a-406f-bc66-dfd8adc9882e.mp4 status=ready elapsedMs=23317 rawHoldCount=67 allHoldCount=67
2026-03-23 14:56:26.324  9291-9291  UploadAiTrace  D  [AI_TRACE] event=HOLD_FILTER_APPLIED generation=2 playbackUri=primary_56b34b4e-915a-406f-bc66-dfd8adc9882e.mp4 targetColor=yellow allHoldCount=67 filteredHoldCount=9
```

## 8. 2A BBOX_LOCAL Result

### Summary

- decision: **quality kept / speed improved significantly**
- quality signals stayed stable
  - phase1 optimized: `rawHoldCount=67`, `allHoldCount=67`, `filteredHoldCount=9`
  - 2A bbox-local: `rawHoldCount=67`, `allHoldCount=67`, `filteredHoldCount=9`
- the previous full-frame resize stage was effectively removed
  - before: `HOLD_YOLO_MASK_RESIZE_DONE = 15.609s`
  - after: `HOLD_YOLO_LOCAL_MASK_DONE = 0.033s`

### Comparison vs phase1 optimized build

| metric | phase1 optimized | 2A bbox-local | delta | note |
|---|---:|---:|---:|---|
| `HOLD_PERSON_DETECT_DONE` | `172ms` | `302ms` | `+130ms` | auxiliary stage, not the bottleneck |
| `HOLD_BEST_FRAME_EXTRACT_SUCCESS` | `384ms` | `335ms` | `-49ms` | auxiliary stage |
| `HOLD_YOLO_INFERENCE_DONE` | `132ms` | `177ms` | `+45ms` | model inference is still small |
| `HOLD_YOLO_MASK_BUILD_DONE` | `3387ms` | `2453ms` | `-934ms` | mask build also got lighter |
| `HOLD_YOLO_MASK_RESIZE_DONE` / `HOLD_YOLO_LOCAL_MASK_DONE` | `15609ms` | `33ms` | `-15576ms` | full-frame resize replaced with local mask path |
| `HOLD_YOLO_POLYGON_TRACE_DONE` | `65ms` | `168ms` | `+103ms` | slightly higher, still tiny |
| `HOLD_YOLO_MASK_POLYGON_DONE` | `19063ms` | `2658ms` | `-16405ms` | major bottleneck removed |
| `HOLD_YOLO_DONE` | `19267ms` | `2994ms` | `-16273ms` | YOLO total time collapsed |
| `HOLD_CLASSIFY_ALL_DONE` | `3489ms` | `2321ms` | `-1168ms` | downstream also improved |
| `HOLD_PRECOMPUTE_DONE` | `23317ms` | `5957ms` | `-17360ms` | hold precompute improved dramatically |

### Percentage view

- `HOLD_YOLO_MASK_POLYGON_DONE`
  - `19.063s -> 2.658s`
  - `-16.405s`
  - about **86.1% faster**
- `HOLD_YOLO_DONE`
  - `19.267s -> 2.994s`
  - `-16.273s`
  - about **84.5% faster**
- `HOLD_PRECOMPUTE_DONE`
  - `23.317s -> 5.957s`
  - `-17.360s`
  - about **74.5% faster**
- replacement of the resize stage
  - `15.609s -> 0.033s`
  - `-15.576s`
  - about **99.8% faster**

### Comparison vs original baseline

The 2A result is not only better than the phase1 optimized build, but also clearly better than the original baseline measured at `13:55`.

- `HOLD_YOLO_MASK_POLYGON_DONE`
  - `16.417s -> 2.658s`
  - `-13.759s`
  - about **83.8% faster**
- `HOLD_YOLO_DONE`
  - `16.668s -> 2.994s`
  - `-13.674s`
  - about **82.0% faster**
- `HOLD_PRECOMPUTE_DONE`
  - `21.601s -> 5.957s`
  - `-15.644s`
  - about **72.4% faster**

### Interpretation

The 1st optimization only reduced memory churn, but it kept the same complexity center.  
The 2A bbox-local path changed the complexity center itself:

```text
before:
input mask
-> full original-resolution mask
-> bbox crop
-> polygon

after:
input mask
-> bbox-local mask
-> polygon
-> original normalized points
```

This is why the largest bottleneck moved from the old resize stage to a much smaller local-mask path.  
In this run, the largest observed local mask size was only:

- `bboxWidthPx=196`
- `bboxHeightPx=227`

which is far smaller than the previous full-frame `1080x1920` path.

### C. 2A BBOX_LOCAL raw log excerpt

```text
2026-03-23 15:53:10.134 23005-23071 UploadAiTrace D [AI_TRACE] event=HOLD_YOLO_MASK_POLYGON_BEGIN keptCount=67 originalWidth=1080 originalHeight=1920 mode=BBOX_LOCAL
2026-03-23 15:53:10.134 23005-23071 UploadAiTrace D [AI_TRACE] event=HOLD_YOLO_LOCAL_MASK_BEGIN keptCount=67 originalWidth=1080 originalHeight=1920 timingMode=accumulated_per_detection mode=BBOX_LOCAL
2026-03-23 15:53:12.792 23005-23071 UploadAiTrace D [AI_TRACE] event=HOLD_YOLO_MASK_BUILD_DONE elapsedMs=2453 keptCount=67 detectionCount=67 timingMode=accumulated_per_detection
2026-03-23 15:53:12.792 23005-23071 UploadAiTrace D [AI_TRACE] event=HOLD_YOLO_LOCAL_MASK_DONE elapsedMs=33 keptCount=67 detectionCount=67 bboxWidthPx=196 bboxHeightPx=227 localMaskWidth=196 localMaskHeight=227 timingMode=accumulated_per_detection mode=BBOX_LOCAL dimensionAggregation=max_observed
2026-03-23 15:53:12.792 23005-23071 UploadAiTrace D [AI_TRACE] event=HOLD_YOLO_POLYGON_TRACE_DONE elapsedMs=168 keptCount=67 detectionCount=67 originalWidth=1080 originalHeight=1920 timingMode=accumulated_per_detection mode=BBOX_LOCAL
2026-03-23 15:53:12.792 23005-23071 UploadAiTrace D [AI_TRACE] event=HOLD_YOLO_MASK_POLYGON_DONE elapsedMs=2658 keptCount=67 detectionCount=67 originalWidth=1080 originalHeight=1920 mode=BBOX_LOCAL
2026-03-23 15:53:12.802 23005-23071 UploadAiTrace D [AI_TRACE] event=HOLD_YOLO_DONE generation=2 playbackUri=primary_a77f8d74-3ebb-4f8c-8af6-44581bc73ed6.mp4 elapsedMs=2994 bestTimeUs=6255 rawHoldCount=67
2026-03-23 15:53:15.123 23005-23071 UploadAiTrace D [AI_TRACE] event=HOLD_CLASSIFY_ALL_DONE generation=2 playbackUri=primary_a77f8d74-3ebb-4f8c-8af6-44581bc73ed6.mp4 elapsedMs=2321 bestTimeUs=6255 rawHoldCount=67 allHoldCount=67
2026-03-23 15:53:15.124 23005-23005 UploadAiTrace D [AI_TRACE] event=HOLD_PRECOMPUTE_DONE generation=2 playbackUri=primary_a77f8d74-3ebb-4f8c-8af6-44581bc73ed6.mp4 status=ready elapsedMs=5957 rawHoldCount=67 allHoldCount=67
2026-03-23 15:53:20.457 23005-23005 UploadAiTrace D [AI_TRACE] event=HOLD_FILTER_APPLIED generation=2 playbackUri=primary_a77f8d74-3ebb-4f8c-8af6-44581bc73ed6.mp4 targetColor=yellow allHoldCount=67 filteredHoldCount=9
```

## 9. Why 2A Became Much Faster

This section records **what changed mathematically, algorithmically, and in code structure** so the speedup is explainable, not just observed.

### 9.1 Old path vs new path

Old full-frame path in `TFLiteInferenceUtils.kt`:

```text
proto tensor
-> buildInputSpaceMask(...)
-> 640x640 inputMask
-> scaleMaskToOriginal(...)
-> 1080x1920 full-frame Boolean mask
-> buildNormalizedPolygon(...)
-> extractLargestComponentMask(...)
-> bbox crop
-> contour / polygon
```

New 2A bbox-local path:

```text
proto tensor
-> buildInputSpaceMask(...)
-> 640x640 inputMask
-> buildNormalizedPolygonFromLocalMask(...)
-> bbox-local Boolean mask only
-> contour / polygon
-> map polygon points back to original normalized coordinates
```

The important difference is that the old path **expanded every detection to the entire original frame first**, while the new path expands **only the bbox area that will actually be used for polygon tracing**.

### 9.2 The main mathematical reason

The old bottleneck was dominated by work proportional to:

```text
O(keptDetections * originalWidth * originalHeight)
```

Because every kept detection paid for a full original-resolution reconstruction:

- `originalWidth = 1080`
- `originalHeight = 1920`
- full-frame pixels per detection = `2,073,600`

With `67` kept detections, the old path had a worst-case per-stage work scale around:

```text
67 * 2,073,600 = 138,931,200 pixel positions
```

The new bbox-local path changes the dominant work to:

```text
O(sum of bbox areas for kept detections)
```

In the measured run, the largest observed local mask was:

- `bboxWidthPx = 196`
- `bboxHeightPx = 227`
- local pixels = `44,492`

Even using that largest observed box as a rough upper-bound example:

```text
2,073,600 / 44,492 ≈ 46.6x
```

So the new path is operating on an area that is roughly **46x smaller** than the old full-frame path for a box of that size.

If all 67 detections had been as large as that maximum observed box, the rough local-mask work scale would be:

```text
67 * 44,492 = 2,980,964 pixel positions
```

That is still dramatically smaller than `138,931,200`.

### 9.3 What changed in code

The speedup did **not** come from changing the model.

Things that stayed the same:

- same YOLO segmentation model
- same `buildInputSpaceMask(...)`
- same confidence and NMS flow
- same threshold rule: `>= 0.5f`
- same contour extraction logic
- same polygon fallback contract

What changed in code:

1. `runSegmentationInference(...)` now branches by `SegmentationPolygonMode`
   - baseline: `FULL_FRAME_BASELINE`
   - new default: `BBOX_LOCAL`

2. The baseline path still exists
   - `inputMask -> scaleMaskToOriginal(...) -> buildNormalizedPolygon(...)`
   - this was kept for rollback and comparison

3. The new path uses `buildNormalizedPolygonFromLocalMask(...)`
   - it resolves the bbox on original pixel space
   - creates only a bbox-sized local mask
   - traces polygon in that local coordinate system
   - maps polygon points back to original normalized coordinates

4. `scaleMaskToOriginal(...)` is no longer on the hot path for the default mode
   - that is why `HOLD_YOLO_MASK_RESIZE_DONE` disappeared from the critical path
   - and `HOLD_YOLO_LOCAL_MASK_DONE = 33ms` replaced a previous `15.609s`

### 9.4 What changed algorithmically

The old path did unnecessary work in this order:

1. sample segmentation into `inputMask`
2. upscale to the full original frame
3. threshold the full frame
4. later crop back to bbox inside `extractLargestComponentMask(...)`

That means the code was effectively doing:

```text
bbox-sized meaning
-> full-frame reconstruction
-> bbox crop again
```

The new path removes that waste:

1. sample segmentation into `inputMask`
2. use the bbox directly to define the local reconstruction region
3. threshold only that local region
4. run contour extraction directly there
5. only remap final polygon points back to original coordinates

This is the key algorithmic win:

- reconstruct **only the area of interest**
- transform **only the final geometry**
- avoid building data that downstream never uses

### 9.5 Why quality stayed stable

This optimization was intentionally conservative.

Quality was preserved because:

1. The segmentation model output itself did not change
   - same proto tensor
   - same mask coefficients
   - same `buildInputSpaceMask(...)`

2. The interpolation math still stayed bilinear
   - the path changed **where** interpolation is evaluated
   - not the basic interpolation rule itself

3. The binary threshold stayed the same
   - `interpolated >= 0.5f`

4. The contour logic was reused
   - `buildPolygonPoints(...)`
   - `traceLargestBoundaryPolygon(...)`
   - `extractLargestComponentMask(...)`
   - simplify / fallback behavior remained intact

5. Only the coordinate space changed
   - polygon was traced in local mask coordinates
   - then converted back by `mapPolygonPointsToOriginalNormalized(...)`

This is why the observed quality signals remained stable:

- `rawHoldCount = 67`
- `allHoldCount = 67`
- `filteredHoldCount = 9`

### 9.6 Data reuse and memory behavior

The old path was wasteful not only in arithmetic, but also in memory traffic.

Before:

- full original-resolution Boolean mask was materialized per detection
- the code paid for repeated writes across a `1080x1920` grid
- then downstream cropped a smaller bbox region out of that full mask

After:

- the code allocates a local `BooleanArray(bounds.width * bounds.height)` only
- it no longer writes a full-frame segmentation mask for each detection
- it directly consumes the local mask in the tracing pipeline

Also, the code still reuses precomputed geometry:

- `MaskResizePlan`
  - `x0Indices`
  - `x1Indices`
  - `xWeights`
  - `yWeights`
  - `sourceRowOffsets0`
  - `sourceRowOffsets1`

So the 2A path combines:

- **coordinate reuse** from the phase1 work
- **smaller target domain** from bbox-local reconstruction

This combination is what made the speedup large.

### 9.7 Why some downstream stages also became faster

Two secondary improvements showed up in logs:

- `HOLD_YOLO_MASK_BUILD_DONE`
  - `3387ms -> 2453ms`
- `HOLD_CLASSIFY_ALL_DONE`
  - `3489ms -> 2321ms`

These were not the primary target, but they likely improved because:

1. the CPU was no longer blocked by long full-frame resize work
2. memory bandwidth pressure was lower
3. less temporary data was pushed through the hold detection pipeline

So the main gain came from deleting the resize bottleneck, and the rest of the pipeline benefited from reduced contention.

### 9.8 Final explanation in one sentence

The speedup happened because the code stopped reconstructing a **full 1080x1920 segmentation mask for every detection** and instead reconstructed **only the bbox-sized region that polygon extraction actually needs**, while keeping the same model, threshold, and contour semantics.

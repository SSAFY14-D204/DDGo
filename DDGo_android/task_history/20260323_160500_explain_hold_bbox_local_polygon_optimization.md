# Hold BBOX-Local Polygon 최적화 설명

- 작성일: 2026-03-23
- 목적: hold segmentation 후처리가 왜 빨라졌는지 Notion에 기록할 수 있도록 구조, 알고리즘, 수학적 이유를 설명
- 관련 파일:
  - `app/src/main/java/com/ddgo/app/data/ml/common/TFLiteInferenceUtils.kt`
  - `task_history/20260323_150500_perf_hold_mask_resize_phase1_comparison.md`

## 1. 한 줄 요약

이번 최적화의 핵심은 **detection마다 원본 전체 해상도 mask를 복원하던 방식을 없애고, 실제로 polygon 추출에 필요한 bbox 영역만 local mask로 복원하도록 바꾼 것**입니다.

즉,

```text
기존:
inputMask -> full original mask -> bbox crop -> polygon

변경 후:
inputMask -> bbox-local mask -> polygon -> original normalized points
```

로 바뀌었습니다.

---

## 2. 용어 정리

### 2.1 bbox

bbox는 홀드가 대략 어디 있는지를 나타내는 사각형입니다.

예시:

```text
x1 = 500
y1 = 800
x2 = 696
y2 = 1027
```

이 경우 bbox의 크기는:

- width = 196
- height = 227

즉 bbox는 "이 홀드는 대충 이 네모 안에 있다"는 정보입니다.

### 2.2 polygon

polygon은 홀드의 실제 외곽선을 이루는 점들의 집합입니다.

예시:

```text
(510, 812), (535, 808), (620, 818), ...
```

즉 bbox는 대략적인 위치이고, polygon은 실제 모양입니다.

### 2.3 inputMask

inputMask는 detection 1개에 대한 segmentation mask입니다.

- 타입: `FloatArray`
- 크기: 보통 `640x640`
- 의미: 각 픽셀이 hold일 가능성

중요한 점:

- inputMask는 원본 이미지 크기 mask가 아닙니다.
- YOLO 입력 좌표계 기준 mask입니다.
- detection bbox 바깥쪽은 대부분 `0`으로 지워진 상태입니다.

즉 inputMask는 "detection A에 대한 홀드 모양 정보가 담긴 640x640 픽셀 배열"이라고 보면 됩니다.

---

## 3. 기존 로직

기존 detection 1개당 흐름은 아래와 같았습니다.

### Step 1. `buildInputSpaceMask(...)`

YOLO segmentation 출력에서 detection 하나를 위한 `inputMask(640x640)`를 만듭니다.

```text
proto tensor + mask coefficients
-> detection A용 inputMask(640x640)
```

### Step 2. `scaleMaskToOriginal(...)`

이 `640x640 inputMask`를 원본 해상도 기준 full mask로 복원합니다.

예시:

```text
640x640 inputMask
-> 1080x1920 full original mask
```

이 단계가 핵심 병목이었습니다.

### Step 3. `buildNormalizedPolygon(...)`

full original mask를 이용해 polygon을 만듭니다.

그런데 내부 로직을 보면 실제로는:

- bbox 범위를 다시 구하고
- `extractLargestComponentMask(...)`에서 bbox 부분만 잘라서
- 그 crop된 영역에서 contour를 추적합니다

즉 실제 흐름은:

```text
inputMask(640x640)
-> full original mask(1080x1920)
-> bbox crop
-> polygon
```

입니다.

---

## 4. 기존 로직이 왜 느렸는가

핵심은 **bbox만 필요했는데 full-frame mask를 먼저 만들었다**는 점입니다.

예를 들어:

- 원본 이미지: `1080x1920`
- detection A bbox: `196x227`

실제로 polygon을 만들 때 필요한 건 detection A bbox 안쪽입니다.

그런데 기존 코드에서는 detection A 하나를 위해:

- `1080 * 1920 = 2,073,600` 픽셀 전체를 계산한 뒤
- 나중에 그중 bbox 부분만 사용했습니다

즉, 필요한 건 bbox 영역인데 계산은 전체 화면 기준으로 했습니다.

이걸 그림으로 표현하면:

```text
필요한 영역: bbox 내부
실제 계산: 원본 전체 프레임
```

---

## 5. 정확히 무엇이 비쌌는가

비쌌던 건 좌표 4개를 바꾸는 일이 아닙니다.

비쌌던 것은 detection마다:

- 원본 전체 픽셀 격자를 순회하고
- 각 픽셀이 inputMask의 어디에 해당하는지 계산하고
- bilinear interpolation을 수행하고
- threshold를 적용하고
- Boolean mask에 기록하는 작업

입니다.

즉 비용의 본질은:

```text
숫자 좌표 변환 비용
```

이 아니라

```text
원본 전체 해상도에 대한 per-pixel mask reconstruction 비용
```

입니다.

---

## 6. 개선 로직

개선 후 detection 1개당 흐름은 아래와 같습니다.

### Step 1. `buildInputSpaceMask(...)`

이 단계는 그대로 유지합니다.

```text
proto tensor + mask coefficients
-> detection A용 inputMask(640x640)
```

### Step 2. bbox를 먼저 확정

원본 기준 bbox를 먼저 구합니다.

예:

```text
x1 = 500
y1 = 800
x2 = 696
y2 = 1027
```

### Step 3. `buildLocalMaskFromInputMask(...)`

이제 full original mask를 만들지 않고,

- bbox width
- bbox height

만큼의 local mask만 만듭니다.

예:

```text
local mask size = 196 x 227
```

이 local mask의 각 픽셀은:

- bbox 내부의 원본 픽셀 위치를 기준으로
- 그 위치가 inputMask(640x640)에서 어디에 해당하는지 찾고
- bilinear interpolation으로 값을 읽고
- threshold(`>= 0.5f`)를 적용해서 채웁니다

즉 "bbox에 해당하는 부분만 복원"합니다.

### Step 4. local mask에서 polygon 추출

polygon은 이제 full-frame mask가 아니라 local mask에서 바로 추출합니다.

```text
local mask
-> contour
-> polygon
```

### Step 5. polygon 점만 원본 좌표로 변환

local polygon은 bbox 내부 좌표계에 있으므로,
마지막에만 원본 normalized 좌표로 변환합니다.

즉 바뀐 흐름은:

```text
inputMask(640x640)
-> bbox-local mask
-> polygon
-> original normalized points
```

입니다.

---

## 7. 수학적으로 왜 빨라졌는가

### 7.1 기존 복잡도

기존 병목은 거의 다음과 같은 형태였습니다.

```text
O(keptDetections * originalWidth * originalHeight)
```

이번 로그 기준:

- kept detections = 67
- original width = 1080
- original height = 1920

즉 full-frame 기준 픽셀 수는:

```text
1080 * 1920 = 2,073,600
```

### 7.2 개선 후 복잡도

개선 후에는 full-frame이 아니라 bbox 영역 기준이므로:

```text
O(sum of bbox areas)
```

이번 측정에서 local mask 최대 크기는:

- width = 196
- height = 227

즉 픽셀 수는:

```text
196 * 227 = 44,492
```

full-frame과 비교하면:

```text
2,073,600 / 44,492 ≈ 46.6
```

즉 detection 1개 기준으로도 처리 영역이 대략 46배 줄어든 셈입니다.

---

## 8. 코드적으로 무엇이 바뀌었는가

변경 핵심:

- `SegmentationPolygonMode` 추가
  - `FULL_FRAME_BASELINE`
  - `BBOX_LOCAL`
- 기본 경로를 `BBOX_LOCAL`로 전환
- baseline 경로는 그대로 유지

새 helper:

- `buildNormalizedPolygonFromLocalMask(...)`
- `buildLocalMaskFromInputMask(...)`
- `mapPolygonPointsToOriginalNormalized(...)`

즉 "전체 mask 복원"과 "polygon 추출" 사이에 있던 비효율적인 full-frame 경로를,
"bbox-local 복원" 경로로 대체한 것입니다.

---

## 9. 데이터 재활용 관점

이번 최적화는 단순히 계산 영역만 줄인 게 아니라, 기존에 만들어둔 변환 계획도 재활용합니다.

예:

- `MaskResizePlan`
  - `x0Indices`
  - `x1Indices`
  - `xWeights`
  - `yWeights`
  - `sourceRowOffsets0`
  - `sourceRowOffsets1`

즉:

- 좌표계 변환에 필요한 lookup은 계속 재사용하고
- 계산 범위만 full-frame에서 bbox-local로 줄였습니다

그래서 단순한 "임시 최적화"가 아니라, **기존 변환 계획 재활용 + 계산 영역 축소**가 동시에 일어났습니다.

---

## 10. 왜 품질은 유지됐는가

중요한 점은 이번 변경이 모델 품질 자체를 건드린 게 아니라는 것입니다.

유지된 것:

- 같은 YOLO segmentation 모델
- 같은 `buildInputSpaceMask(...)`
- 같은 threshold(`>= 0.5f`)
- 같은 contour / polygon fallback 로직
- 같은 downstream 소비 계약

즉 바뀐 것은:

- polygon을 만들기 전에 full-frame mask를 먼저 만들지 않는 것

뿐입니다.

그래서 품질 지표도 안정적으로 유지되었습니다.

실제 로그:

- `rawHoldCount = 67`
- `allHoldCount = 67`
- `filteredHoldCount = 9`

---

## 11. 실제 성능 결과

이번 `BBOX_LOCAL` 적용 후:

- `HOLD_YOLO_MASK_POLYGON_DONE`
  - `19.063초 -> 2.658초`
  - `16.405초` 단축
  - 약 `86.1%` 개선

- `HOLD_YOLO_DONE`
  - `19.267초 -> 2.994초`
  - `16.273초` 단축
  - 약 `84.5%` 개선

- `HOLD_PRECOMPUTE_DONE`
  - `23.317초 -> 5.957초`
  - `17.360초` 단축
  - 약 `74.5%` 개선

- old resize stage replacement
  - `HOLD_YOLO_MASK_RESIZE_DONE = 15.609초`
  - `HOLD_YOLO_LOCAL_MASK_DONE = 0.033초`

---

## 12. 최종 결론

이번 최적화는 모델을 바꿔서 빨라진 것이 아닙니다.

핵심은:

- detection마다 원본 전체 해상도 mask를 만들던 방식에서
- detection의 bbox 영역에 필요한 local mask만 만들도록 바꾼 것

입니다.

즉,

```text
기존:
"bbox 안이 궁금한데 전체 화면 mask를 먼저 만든다"

개선 후:
"bbox 안이 궁금하니 bbox 크기만큼만 mask를 만든다"
```

이 차이 때문에 hold segmentation 후처리 속도가 크게 개선되었습니다.

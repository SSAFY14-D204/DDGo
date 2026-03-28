# Android CoM Overlay Migration Report

## 1. 목적

이 문서는 [mujoco/AUDIT_07_COM_COORDINATE_SCALE.md](/C:/ssafy/project-second/S14P21D204/mujoco/AUDIT_07_COM_COORDINATE_SCALE.md)를 바탕으로, Android에서 `physics_result.frames[].com_position_m`를 MediaPipe pose overlay에 어떻게 붙이는 것이 가장 안전하고 점진적인지 정리한 migration 보고서다.

핵심 전제:

- `com_position_m`는 화면 픽셀 좌표가 아니다.
- `com_position_m`는 `MediaPipe -> MuJoCo 축변환 -> 신체 치수 기반 scale -> world offset -> MuJoCo fitting`을 거친 **MuJoCo world-space meter 계열 값**이다.
- 따라서 Android에서 해야 할 일은 **정확 투영**이 아니라, **해석 가능한 overlay migration**이다.

---

## 2. 현재 상태

### 2-1. Android가 이미 가진 것

- 현재 `AttemptVideoSection`은 overlay에 필요한 render state를 외부에 넘길 수 있다.
- 현재 render state에는 아래 정보가 있다.
  - `displayedPositionMs`
  - `videoContentRect`
  - `currentOverlayFrame`
  - `currentOverlayPose`
- 근거:
  - [AttemptVideoSection.kt](/C:/ssafy/project-second/S14P21D204/DDGo_android/app/src/main/java/com/ddgo/app/feature/climbing/upload/ui/shared/organism/AttemptVideoSection.kt)

### 2-2. 현재 debug 구현이 이미 하는 것

- debug 화면에서는 `torsoAnchor = avg(11, 12, 23, 24)` 방식으로 몸통 기준 anchor를 만든다.
- body/contact/support badge도 landmark 평균점 기반으로 anchor를 잡고 있다.
- 즉 CoM migration의 v1 방향은 이미 실험 구현이 있는 상태다.
- 근거:
  - [UploadPhysicsDebugData.kt](/C:/ssafy/project-second/S14P21D204/DDGo_android/app/src/debug/java/com/ddgo/app/feature/debug/UploadPhysicsDebugData.kt)
  - [UploadPhysicsOverlayDebugScreen.kt](/C:/ssafy/project-second/S14P21D204/DDGo_android/app/src/debug/java/com/ddgo/app/feature/debug/UploadPhysicsOverlayDebugScreen.kt)

### 2-3. 현재 서버가 주는 것

- 안정적으로 있는 값
  - `com_position_m`
  - `support_stability`
  - `body_loads`
  - `estimated_contact_forces_n`
- 현재 complete API에서 기본 제공되지 않는 것으로 보는 값
  - `root_position_m`
  - `support_center_m`
  - `com_support_offset_m`
  - `wall_axis`
  - `wall_plane_value`

즉 지금 Android는 **CoM 절대 world 좌표는 받지만, 그 좌표를 화면에 정확 재투영할 보조값은 부족한 상태**다.

---

## 3. migration 원칙

### 3-1. 목표

- 사용자가 “이 프레임의 무게중심이 몸통 기준으로 어떤 상태인지”를 직관적으로 이해하게 만든다.
- support/body load/contact force와 함께 읽히는 보조 지표로 CoM을 사용한다.

### 3-2. 금지

- `com_position_m`를 픽셀 좌표처럼 직접 그리기
- `x/y/z`를 정규화 좌표처럼 쓰기
- 카메라 좌표라고 가정하고 2D 점 위치를 강하게 이동시키기

### 3-3. 권장

- `torsoAnchor`를 기준점으로 사용
- CoM은 “몸통에 붙은 HUD”로 시작
- `x`는 depth/벽 방향 정보로 사용
- `y/z`는 위치 보정보다 **설명/미세 이동** 정도로만 사용

---

## 4. 권장 migration 단계

## Phase 1. 정식 V1: TorsoAnchor HUD

### 목표

- 가장 안전한 방식으로 production에 올릴 수 있는 첫 버전

### 기준점

```text
torsoAnchor2D =
avg(
  leftShoulder(11),
  rightShoulder(12),
  leftHip(23),
  rightHip(24)
)
```

### 표시 방식

- CoM 점은 `torsoAnchor2D` 근처에 고정해서 그린다.
- 점 바로 옆 badge에 아래를 표시한다.
  - `CoM x`
  - `CoM y`
  - `CoM z`
- support 안정성이 함께 있으면 badge 톤을 바꾼다.
  - `inside_support=true`: 안정 톤
  - `inside_support=false`: 경고 톤

### 왜 이 방식이 맞는가

- 현재 서버 값만으로도 가능하다.
- 사용자가 “몸통 중심 기준 CoM 정보”로 자연스럽게 이해할 수 있다.
- 잘못된 정밀 투영 착시를 만들지 않는다.

### Android 구현 권장

- 현재 debug의 `torsoAnchor` 계산 로직을 main/shared helper로 올린다.
- `UploadPhysicsDebugData.kt`의 anchor 계산 일부를 공용 overlay util로 추출한다.
- production UI에서는 숫자 badge를 간소화한다.

### 장점

- 구현 난이도 낮음
- 해석 오류 낮음
- 서버 추가 변경 없음

### 단점

- CoM 점 자체가 실제 몸 안에서 이동하는 느낌은 약하다

---

## Phase 1.5: TorsoAnchor HUD + 상태 기반 시각 변화

### 목표

- V1을 유지하면서 “움직임 감”을 조금 더 준다

### 방식

- 점 위치는 여전히 torsoAnchor 근처에 둔다.
- 대신 아래를 CoM 값으로 바꾼다.
  - `x`: 점 크기, 외곽선 두께, alpha, `wall/depth` 텍스트
  - `y`: 좌우 편향 텍스트 또는 배지 정렬
  - `z`: 배지 상하 오프셋 또는 높이 라벨

### 권장 매핑

- `x`가 커질수록
  - 외곽선 두께 증가
  - 색상 채도 증가
  - `"depth +"` 또는 `"wall dist"` 보조 텍스트 표시
- `z`가 높아질수록
  - 점 위 badge를 약간 위로 띄움
  - `"high CoM"` 같은 상태 문구 노출 가능
- `y`는 좌우 치우침 해석에만 사용
  - 예: `"left bias"`, `"right bias"`

### 이유

- `x`는 지금 로직상 depth/벽 방향 성격이 강하다.
- `y/z`는 support와 같이 보면 상대 해석이 가능하다.
- 점을 과하게 움직이지 않으면서 정보량만 늘릴 수 있다.

---

## Phase 2. 제한적 상대 투영

### 전제

이 단계는 **서버가 추가 보조값을 내려줄 때만** 권장한다.

필요한 값:

- `root_position_m`
- `com_support_offset_m` 또는 `support_center_m`
- `wall_axis`
- `wall_plane_value`
- 가능하면 `scale_m_per_local`
- 가능하면 `offset_world`
- 가장 좋으면 `root_quat` 또는 `axis_forward/axis_left/axis_up`

### 목표

- CoM 점을 torsoAnchor 주변에서 **조금 움직이게** 만들어 시각적으로 더 자연스럽게 보이게 한다.

### 권장 계산

1. Android pose에서 body 2D basis를 만든다.

```text
shoulderMid2D = avg(LS, RS)
hipMid2D = avg(LH, RH)
up2D = normalize(shoulderMid2D - hipMid2D)
left2D = normalize(LS - RS)
```

2. 서버 world 값으로 CoM 상대량을 만든다.

```text
delta = com_position_m - root_position_m
```

또는

```text
delta = com_support_offset_m
```

3. 2D 위치는 `y/z` 성분만 약하게 반영한다.

```text
screenPos =
torsoAnchor2D
+ left2D * (deltaLeft * ky)
- up2D   * (deltaUp   * kz)
```

4. `x`는 계속 depth 표현으로만 사용한다.

### 중요한 제한

- `ky`, `kz`는 작게 둔다.
- 이 단계도 “정확 투영”이 아니라 “상대적 시각 보정”이다.
- 점이 landmark 골격 바깥으로 크게 나가면 안 된다.

### 권장 초기 상수

- `ky = 40dp/m` 내외
- `kz = 40dp/m` 내외
- 최대 이동량 clamp
  - `|dx| <= 18dp`
  - `|dy| <= 18dp`

이유:

- 현재 CoM 값은 meter 계열이지만 정밀 실측은 아니다.
- clamp가 없으면 작은 추정 오차도 시각적으로 과장된다.

---

## Phase 3. 정밀 투영형 overlay

### 결론

- 지금은 비권장

### 이유

- 카메라 intrinsic/extrinsic이 없다
- complete API에 root/world transform 보조값이 부족하다
- gate1 reprojection error가 아직 커서 정밀 계측 신뢰도가 낮다

따라서 다음 해석은 아직 하면 안 된다.

- “이 점이 실제 영상 속 CoM 픽셀 위치다”
- “이 프레임 CoM이 영상에서 정확히 여기 있다”

---

## 5. 최종 권장안

### production 권장안

- **V1 + V1.5 조합**으로 간다.

즉:

- 기준점은 `torsoAnchor`
- CoM 점은 torsoAnchor 주변 고정
- `x/y/z`는 badge 텍스트와 미세한 스타일 변화로 해석
- support_stability와 묶어서 안정/경고 상태를 보여준다

이 방식이 가장 안전한 이유:

- 서버 응답만으로 가능
- 현재 debug 구현을 거의 재사용할 수 있음
- 잘못된 “정확 투영” 인상을 주지 않음

### 서버 보강 후 권장안

- 서버가 `root_position_m`, `com_support_offset_m`, `wall_axis`, `wall_plane_value`, `root_quat` 계열을 내려주면 **Phase 2**로 올린다.
- 그래도 이 단계는 “제한적 상대 투영”으로 유지한다.

---

## 6. UI 문구/해석 정책

### 사용자에게 보여줄 문구

- `CoM 높이`
- `좌우 치우침`
- `벽 방향 이동`
- `지지영역 안/밖`

### 피해야 할 문구

- `정확한 무게중심 위치`
- `실제 화면 좌표`
- `실측 거리`

### 추천 상태 라벨

- `안정`
- `주의`
- `불안정`

이 상태 라벨은 아래 조합으로 만들면 된다.

- `support_stability.inside_support`
- `support_stability.stability_margin_m`
- `analysis_confidence`

---

## 7. Android 구현 제안

### 7-1. 공용 helper로 올릴 것

- `torsoAnchor` 계산
- body anchor 계산
- contact anchor 계산
- CoM badge formatter

현재 debug에 있는 다음 성격의 코드를 production으로 올리는 방향이 적절하다.

- pose landmark 평균 anchor 계산
- current frame physics snapshot 파싱
- support/contact 상태 badge 조립

### 7-2. production 노출 순서

1. debug에서 현재 방식 검증
2. upload result 또는 final analysis의 DEV 옵션으로 노출
3. 사용자용 overlay는 간소화된 badge만 남기기

### 7-3. 서버에 요청할 추가 필드

우선순위:

1. `root_position_m`
2. `com_support_offset_m`
3. `support_center_m`
4. `wall_axis`
5. `wall_plane_value`
6. `root_quat` 또는 `axis_forward/axis_left/axis_up`

---

## 8. Acceptance Criteria

### V1 기준

- CoM badge가 모든 프레임에서 torso 부근에 안정적으로 붙어 있어야 한다.
- skeleton이나 hold overlay를 가리지 않아야 한다.
- support 상태와 함께 볼 때 해석이 자연스러워야 한다.
- frame scrub 중 badge가 튀지 않아야 한다.

### V1.5 기준

- `x` 변화가 depth/벽 방향 정보처럼 느껴져야 한다.
- `z` 변화가 상승/하강 해석과 크게 어긋나지 않아야 한다.
- 시각 변화는 있어도 사용자가 “정확 위치 투영”으로 오해하지 않아야 한다.

### Phase 2 기준

- 점 이동량이 torso 주변의 작은 범위로 제한돼야 한다.
- support/body load 변화와 CoM의 상대 이동이 같이 읽혀야 한다.
- clamp를 제거하지 않는다.

---

## 9. 최종 결론

Android에서 CoM을 MediaPipe pose에 붙이는 가장 좋은 migration 전략은 아래와 같다.

1. **지금은 `torsoAnchor HUD`를 정식안으로 채택한다.**
2. `x/y/z`는 위치 자체보다 **설명과 미세 스타일 변화**에 우선 사용한다.
3. 서버가 보조 world 값을 더 내려주기 전까지는 **정확 투영을 시도하지 않는다.**
4. 이후에도 목표는 “정확 재투영”이 아니라 **사용자에게 해석 가능한 상대 overlay**로 유지한다.

즉, 현 단계에서 가장 맞는 방향은:

**“CoM을 몸통 중심에 붙은 설명형 overlay로 마이그레이션하고, support/body load와 함께 읽히는 UI로 설계한다.”**

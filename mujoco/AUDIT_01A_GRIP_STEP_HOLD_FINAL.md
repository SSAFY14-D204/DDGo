# Audit 01A. Grip/Step 재검증 (`hold_final.json`, `audit.mp4`)

선정 이유: **문제용 hold subset과 새 클라이머 조건을 반영했을 때도 Grip/Step이 downstream 물리량의 기반으로 쓸 만한지 먼저 확인해야 하기 때문입니다.**

---

## 이번 라운드에서 바뀐 점

- 홀드 입력을 기존 전체/세그멘테이션 기반 파일 대신 [hold_final.json](C:/ssafy/project-2/S14P21D204/mujoco/hold_final.json)로 교체
- 분석 영상은 [audit.mp4](C:/ssafy/project-2/S14P21D204/mujoco/video/audit.mp4) 기준으로 변경
- 사용자 프로필을 다음 값으로 교체
  - 키: `167cm`
  - 몸무게: `75kg`
  - 윙스팬: `168cm`
- 발 `STEP`에 대해 아래 두 값을 분리
  - `contact_presence_confidence`
  - `hold_identity_confidence`

---

## 입력 준비 결과

- 입력 생성 스크립트 추가:
  - [prepare_benchmark_inputs_hold_final.py](C:/ssafy/project-2/S14P21D204/mujoco/json_service_benchmark/prepare_benchmark_inputs_hold_final.py)
- 생성 결과:
  - [holds_polygon.json](C:/ssafy/project-2/S14P21D204/mujoco/json_service_benchmark/benchmark_inputs/audit_final/holds_polygon.json)
  - [pose3d_sequence.json](C:/ssafy/project-2/S14P21D204/mujoco/json_service_benchmark/benchmark_inputs/audit_final/pose3d_sequence.json)
  - [user_body.json](C:/ssafy/project-2/S14P21D204/mujoco/json_service_benchmark/benchmark_inputs/audit_final/user_body.json)
  - [benchmark_input_manifest.json](C:/ssafy/project-2/S14P21D204/mujoco/json_service_benchmark/benchmark_inputs/audit_final/benchmark_input_manifest.json)

subset 결과:
- route hold 수: `11`
- `hold_final.json`에 명시적 `start/end` 정보는 없음
- 따라서 현재는 **hold subset 적용만 완료**, `start/end hold 활용`은 보류

---

## 기준 리포트

- [json_service_benchmark_report_audit_final_corrected.json](C:/ssafy/project-2/S14P21D204/mujoco/json_service_benchmark/json_service_benchmark_report_audit_final_corrected.json)

---

## 핵심 결과

### 전체 파이프라인 상태

- `fit_mean_error_m = 0.0749`
- `recovery_ratio = 0.9693`
- `dynamic_sequence_gate.passed = false`

### pose mode

- `backfilled_initial = 14`
- `fitted = 30`
- `interpolated = 29`
- `frozen_glitch = 1851`

해석:
- 새 영상/새 조건에서는 **frozen_glitch가 지나치게 많아서 현재 전체 physics 해석은 신뢰 불가**
- 다만 Grip/Step 상태 자체는 프레임 전반에서 계속 계산되므로, hold subset 효과와 발 신뢰도 분리 자체는 관찰 가능

### hold state 요약

- `left_hand`
  - `FREE = 897`
  - `REACH = 298`
  - `GRIP = 729`
- `right_hand`
  - `FREE = 686`
  - `REACH = 212`
  - `GRIP = 1026`
- `left_foot`
  - `FREE = 531`
  - `REACH = 141`
  - `STEP = 1252`
- `right_foot`
  - `FREE = 631`
  - `REACH = 348`
  - `STEP = 945`

### 발 신뢰도 분리 결과

- `left_foot.contact_presence_confidence_counts`
  - `high = 1195`
  - `medium = 62`
  - `low = 81`
  - `none = 586`
- `left_foot.hold_identity_confidence_counts`
  - `high = 1055`
  - `medium = 155`
  - `low = 183`
  - `none = 531`

- `right_foot.contact_presence_confidence_counts`
  - `high = 419`
  - `medium = 380`
  - `low = 324`
  - `none = 801`
- `right_foot.hold_identity_confidence_counts`
  - `high = 140`
  - `medium = 610`
  - `low = 543`
  - `none = 631`

해석:
- 왼발은 `STEP 존재`와 `hold identity`가 모두 비교적 강하게 잡히는 프레임이 많음
- 오른발은 `STEP 존재`는 보이나, **정확히 어느 홀드를 디디는지에 대한 신뢰도는 중/저가 많음**
- 즉 이번 분리의 목적은 달성됨:
  - `딛고 있다`와
  - `정확히 이 홀드다`
  를 같은 수준으로 보지 않게 됨

---

## 예시 프레임 해석

리포트 안에는 `limb_states`가 포함되므로 아래처럼 직접 해석 가능함.

### low identity 예시

- `frame_index = 239`
- `left_foot.state = STEP`
- `left_foot.contact_presence_confidence = low`
- `left_foot.hold_identity_confidence = low`
- `left_foot.inside_polygon = false`

의미:
- 발이 뭔가를 디디는 동작은 보이지만
- 현재 선택된 hold id는 과신하면 안 되는 프레임

### high identity 예시

- `frame_index = 228`
- `left_foot.state = STEP`
- `left_foot.contact_presence_confidence = high`
- `left_foot.hold_identity_confidence = high`
- `left_foot.inside_polygon = true`

의미:
- 발이 실제 polygon 내부에 들어가 있고
- hold id도 비교적 자신 있게 볼 수 있는 프레임

---

## 판정

### 1. 문제용 hold subset 적용

- 판정: `Pass`
- 이유:
  - `hold_final.json`의 11개 hold만 사용하도록 입력 준비 완료
  - 기존 전체 hold 대비 후보군이 명확해짐

### 2. 시작/끝 hold 정보 활용

- 판정: `Blocked`
- 이유:
  - 현재 [hold_final.json](C:/ssafy/project-2/S14P21D204/mujoco/hold_final.json)에는 명시적 `start/end` 필드가 없음
  - 스크립트는 optional 지원 상태로 열어뒀지만, 실제 데이터는 아직 비어 있음

### 3. 발 `STEP` 존재 여부 vs hold identity 신뢰도 분리

- 판정: `Pass`
- 이유:
  - 리포트에 `contact_presence_confidence`와 `hold_identity_confidence`가 모두 노출됨
  - 왼발/오른발에서 두 값이 다르게 나오는 사례를 실제로 확인함

### 4. 새 시퀀스에서 Grip/Step 기반 전체 physics 해석

- 판정: `Fail`
- 이유:
  - `recovery_ratio = 0.9693`
  - `frozen_glitch = 1851`
  - 현재는 새 영상 기준 pose/fitting 안정화가 먼저 필요

---

## 이번 라운드 결론

- **hold subset 적용은 성공**
- **발 STEP의 존재 여부와 hold identity 신뢰도 분리는 성공**
- **start/end hold 활용은 현재 데이터 부재로 보류**
- 다만 새 영상 [audit.mp4](C:/ssafy/project-2/S14P21D204/mujoco/video/audit.mp4) 기준 전체 physics 파이프라인은 recovery가 지나치게 높아, 다음 우선순위는 `Grip/Step` 추가 보정보다 `pose/fitting 안정화` 쪽임

---

## 다음 액션

1. `audit.mp4`에 대해 왜 `frozen_glitch`가 1851프레임까지 올라가는지 원인 분석
2. 새 입력 기준 `Pose fitting / Recovery` audit 진행
3. start/end hold 정보가 포함된 JSON을 받으면, route semantics 활용 로직 재검증

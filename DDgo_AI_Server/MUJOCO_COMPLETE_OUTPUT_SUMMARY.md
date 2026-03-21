# MuJoCo Complete 결과 요약

이 문서는 안드로이드팀이 분석 화면을 구성할 때 먼저 봐야 하는 **핵심 변수만 빠르게 이해할 수 있도록** 정리한 요약 문서입니다.

상세한 전체 필드 설명은 아래 문서를 참고합니다.
- [MUJOCO_COMPLETE_OUTPUT_SPEC.md](C:/ssafy/project-2/S14P21D204/DDgo_AI_Server/MUJOCO_COMPLETE_OUTPUT_SPEC.md)

---

## 1. 결과가 왜 이렇게 나오는가

MuJoCo Complete 서버는 입력 JSON 3개를 받아서 다음 순서로 결과를 만듭니다.

1. `pose3d_sequence_json`
   - 안드로이드가 보낸 raw MediaPipe 3D 시계열
2. `pose correction`
   - visibility 기반 smoothing
   - low-visibility freeze
   - 간단한 관절 복원
3. `hold tracking`
   - 손/발이 어떤 홀드를 `GRIP / STEP / REACH / FREE` 상태로 사용하고 있는지 판정
4. `MuJoCo fitting`
   - 보정된 사람 자세를 MuJoCo 인체 모델에 맞춤
5. `physics analysis`
   - 관절 부하
   - 무게중심(CoM)
   - 지지 안정성
   - 손/발 추정 접촉력
6. `crux detection`
   - 빠른 후보 또는 물리 기반 후보 계산

즉 결과 JSON은 단순 좌표가 아니라,
- **어떤 홀드를 실제로 쓰고 있는지**
- **몸에 어디 부하가 큰지**
- **무게중심이 안정적인지**
- **어떤 홀드/구간이 크럭스 후보인지**
를 설명하는 결과입니다.

---

## 2. 분석 화면에서 가장 중요한 변수

안드로이드 분석 화면에서는 아래 변수들을 우선적으로 사용하면 됩니다.

### A. 공통 상태/품질

#### `mode`
- 어떤 분석 모드 결과인지
- 값:
  - `fast_crux_detection`
  - `physics_crux_detection`

#### `timings_s.total_s`
- 서버가 이 결과를 만드는 데 걸린 총 시간(초)
- 로딩 UI, 처리시간 표시용

#### `correction_summary`
- 입력 pose가 얼마나 많이 보정되었는지 보는 값
- raw pose 품질 상태 확인용

핵심 하위 변수:
- `filled_from_previous_frame_count`
- `total_low_visibility_joint_count`
- `total_frozen_joint_count`
- `total_reconstructed_joint_count`

---

### B. 빠른 크럭스 화면용

빠른 결과 화면에서는 아래만 있어도 충분합니다.

#### `crux_result.top_candidates`
- 상위 크럭스 후보 목록
- 프론트에서는 보통 top 3만 사용

각 후보의 핵심 변수:
- `hold_id`
  - 홀드 ID
- `total_active_time_s`
  - 전체 활성 시간
- `longest_continuous_dwell_s`
  - 가장 오래 연속으로 머문 시간
- `fast_crux_score`
  - 빠른 크럭스 점수
- `reason_tags`
  - 왜 후보가 됐는지
- `best_segment.start_time_ms`
- `best_segment.end_time_ms`
- `best_segment.duration_s`
- `best_segment.dominant_limbs`
- `best_segment.dominant_modes`

설명:
- `fast`는 **오래 머문 홀드/구간**을 빠르게 뽑는 용도입니다.
- 휴식 홀드가 포함될 수 있으므로, 빠른 안내용 결과로 쓰는 것이 좋습니다.

---

### C. 물리 분석 화면용 핵심 변수

#### `physics_summary`
- 물리 결과의 전체 품질 요약

핵심 하위 변수:
- `fit_mean_error_m`
  - 자세 fitting 평균 오차
- `recovery_ratio`
  - 복구/동결 비율
- `high_confidence_frame_count`
  - 신뢰도 높은 프레임 수
- `ok_contact_force_frame_count`
  - 접촉력 해석이 정상적으로 된 프레임 수
- `point_support_frame_count`
  - 1점 지지 프레임 수

설명:
- 이 값들은 **이번 물리 결과를 얼마나 믿을 수 있는지** 보여주는 품질 지표입니다.

---

### D. 프레임별 분석 화면용 핵심 변수

이 변수들은 `physics_result.frames[]` 안에 있습니다.

#### `frame_index`
- 현재 몇 번째 프레임인지

#### `timestamp_ms`
- 현재 시각(ms)

#### `phase`
- 현재 프레임의 동작 상태
- 값:
  - `dynamic_transition`
  - `loaded_transition`
  - `static_support`
  - `recovery`

설명:
- 사용자에게 “지금 이동 중인지 / 버티는 중인지”를 설명할 때 사용

#### `analysis_confidence`
- 이 프레임 결과 신뢰도
- 값:
  - `high`
  - `low`

설명:
- low 프레임은 UI에서 약하게 표시하거나 제외하는 것이 좋음

#### `active_contact_limbs`
- 현재 실제 지지 중인 limb 목록
- 예:
  - `left_hand`
  - `right_hand`
  - `left_foot`
  - `right_foot`

#### `active_hold_ids`
- 현재 실제 지지 중인 hold ID

설명:
- “지금 어떤 손/발이 어떤 홀드를 쓰고 있는지” 보여줄 때 사용

---

### E. 무게중심 / 안정성 화면용 핵심 변수

#### `com_position_m`
- 현재 프레임의 무게중심 3D 위치
- 형식: `[x, y, z]`

#### `support_stability.inside_support`
- CoM 투영점이 현재 지지 구조 안에 있는지
- 값:
  - `true`
  - `false`

#### `support_stability.stability_margin_m`
- CoM가 지지 경계에서 얼마나 여유가 있는지
- 양수:
  - 지지 구조 안쪽
- 음수:
  - 지지 구조 바깥

#### `support_stability.support_type`
- 현재 지지 구조 유형
- 값:
  - `quad_support`
  - `tri_support`
  - `line_support`
  - `point_support`

설명:
- 이 값들은 “현재 자세가 안정적인지”를 설명하는 핵심 변수입니다.
- 특히 아래 조합이 중요합니다.
  - `inside_support = true` + `margin > 0`
    - 비교적 안정
  - `inside_support = false` + `margin < 0`
    - 불안정

---

### F. 관절/신체 부하 화면용 핵심 변수

#### `joint_loads`
- 현재 프레임의 모든 관절 부하
- key가 관절 이름, value가 부하 값

예:
- `abdomen_x`
- `hip_y_left`
- `knee_right`

#### `top_joint_loads`
- 현재 프레임에서 가장 큰 관절 부하 상위 목록

핵심 하위 변수:
- `joint`
- `abs_qfrc_inverse`
- `signed_qfrc_inverse`

#### `body_loads`
- 현재 프레임의 신체 부위별 부하 proxy

하위 key:
- `core`
- `left_arm`
- `right_arm`
- `left_leg`
- `right_leg`

설명:
- 관절별 상세 설명은 `joint_loads`
- 사용자에게 직관적으로 보여주기에는 `body_loads`가 더 적합합니다
- 예:
  - “현재 코어 부담이 가장 큽니다”
  - “왼쪽 다리 부담이 커졌습니다”

---

### G. 손발 반력/접촉력 화면용 핵심 변수

#### `estimated_contact_forces_n`
- 현재 프레임의 손/발 추정 접촉력

하위 key:
- `left_hand`
- `right_hand`
- `left_foot`
- `right_foot`

각 limb 구조:
- `mode`
- `position_xyz`
- `force_xyz`
- `force_norm_n`
- `normal_force_n`
- `tangential_force_n`

#### `contact_force_status`
- 현재 프레임 접촉력 해석 상태
- 값:
  - `ok`
  - `high_residual`
  - `no_active_contacts`

#### `contact_force_relative_residual`
- 현재 접촉력 추정의 잔차
- 낮을수록 좋음

설명:
- `estimated_contact_forces_n`은 현재 단계에서 **실측 반력**이 아니라 **추정값(proxy)** 입니다.
- 따라서 UI에서는 반드시 아래와 같이 같이 봐야 합니다.
  - `contact_force_status == ok`
  - `analysis_confidence == high`

---

### H. 물리 기반 크럭스 화면용

#### `crux_result.top_candidates`
- 물리 기반 top 크럭스 후보

핵심 하위 변수:
- `hold_id`
- `physics_crux_score`
- `total_active_time_s`
- `longest_continuous_dwell_s`
- `reason_tags`
- `best_segment.start_time_ms`
- `best_segment.end_time_ms`
- `best_segment.duration_s`
- `best_segment.mean_total_body_load`
- `best_segment.mean_core_load`
- `best_segment.mean_negative_margin_cm`
- `best_segment.mean_load_shift_proxy`
- `best_segment.ok_fraction`

설명:
- 이 결과는 “오래 머문 홀드”만 보는 것이 아니라,
  - 체류 시간
  - 몸 부하
  - 불안정성
  - 하중 이동
를 같이 반영한 결과입니다.

---

## 3. 결과가 의미하는 것

### `fast` 결과가 의미하는 것
- 아주 빠르게 반환되는 크럭스 후보
- grip/step 체류 시간 중심
- 사용자에게 먼저 보여줄 수 있는 1차 후보

적합한 용도:
- 분석 시작 직후 빠른 피드백
- “가장 오래 머문 홀드” 안내

주의:
- 휴식 홀드가 포함될 수 있음

### `physics` 결과가 의미하는 것
- MuJoCo fitting + inverse dynamics까지 포함한 상세 결과
- 관절 부하, 무게중심, 안정성, 하중 이동까지 반영

적합한 용도:
- 상세 분석 화면
- 크럭스 설명 화면
- 왜 어려운지 설명하는 텍스트 생성

---

## 4. 분석 화면에서 추천하는 최소 조합

### 빠른 결과 화면
- `crux_result.top_candidates`
- `timings_s.total_s`
- `correction_summary.total_frozen_joint_count`

### 상세 물리 분석 화면
- `physics_summary`
- `frames[].phase`
- `frames[].analysis_confidence`
- `frames[].active_hold_ids`
- `frames[].body_loads`
- `frames[].support_stability.inside_support`
- `frames[].support_stability.stability_margin_m`
- `frames[].com_position_m`
- `frames[].estimated_contact_forces_n`
- `frames[].contact_force_status`

### 크럭스 설명 화면
- `crux_result.top_candidates`
- `best_segment.duration_s`
- `best_segment.mean_total_body_load`
- `best_segment.mean_core_load`
- `best_segment.mean_negative_margin_cm`
- `best_segment.mean_load_shift_proxy`
- `reason_tags`

---

## 5. UI 해석 가이드

### 안정성
- `inside_support = true`이고 `stability_margin_m > 0`
  - 안정적
- `inside_support = false`
  - CoM가 지지 구조 밖
  - 불안정 가능성 높음

### 접촉력
- `contact_force_status = ok`
  - 상대적으로 믿을 만함
- `contact_force_status = high_residual`
  - 접촉력 해석 오차 큼
- `contact_force_status = no_active_contacts`
  - active contact 없음

### 신뢰도
- `analysis_confidence = high`
  - 적극 활용 가능
- `analysis_confidence = low`
  - 보조 정보로만 사용 권장

---

## 6. 한 줄 요약

- `fast`는 **빠른 후보**
- `physics`는 **설명 가능한 상세 분석**
- 분석 화면에서는
  - `phase`
  - `analysis_confidence`
  - `active_hold_ids`
  - `body_loads`
  - `support_stability`
  - `estimated_contact_forces_n`
  - `crux_result.top_candidates`
를 중심으로 보면 됩니다.

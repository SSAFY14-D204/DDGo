# MuJoCo Complete 출력 JSON 전체 변수 설명

이 문서는 [DDgo_AI_Server](C:/ssafy/project-2/S14P21D204/DDgo_AI_Server)의 MuJoCo Complete FastAPI가 반환하는 응답 JSON의 **변수 이름을 회의 중 바로 이해할 수 있게** 설명한 문서입니다.

대상 엔드포인트:
- `POST /api/v1/mujoco-complete/analyze/fast`
- `POST /api/v1/mujoco-complete/analyze/physics`

문서 원칙:
- 변수명을 그대로 적고, 바로 아래에 의미를 설명합니다.
- `physics` 응답 기준으로 가장 자세히 적었습니다.
- 일부 값은 `proxy(근사 추정값)`이므로 실측값처럼 해석하면 안 됩니다.

## 0. 먼저 알아둘 해석 원칙

### `fast` 응답
- 빠른 `crux(크럭스)` 후보용 응답
- dwell time(머문 시간), hold engagement(홀드 사용) 중심

### `physics` 응답
- MuJoCo `pose fitting(자세 맞춤)`과 `inverse dynamics(역동역학)`까지 포함한 상세 응답
- `CoM(무게중심)`, `support(지지)`, `body load(부위 부하)`, `contact force(추정 반력)`가 포함됨

### `proxy(근사 추정값)` 주의
- `estimated_contact_forces_n`
- `body_loads`
- `joint_loads`
- `stability_margin_m`
이 값들은 직접 센서로 측정한 값이 아니라 모델 기반 추정치입니다.

## 최근 반영 변경사항
이번 반영으로 physics 응답에 아래 항목이 추가되거나 의미가 보강되었습니다.

- `physics_result.contact_force_display_smoothing`
  - 표시용 `estimated_contact_forces_n(추정 손발 반력)`에 적용한 `temporal smoothing(시간 축 안정화)` 설정과 집계
- `physics_result.frames[].estimated_contact_forces_raw_n`
  - smoothing 전 원시 반력
- `physics_result.frames[].estimated_contact_forces_n`
  - smoothing 후 표시용 반력
- `physics_result.frames[].contact_force_status`
  - `ok / high_residual / no_active_contacts`
- `physics_result.frames[].contact_force_relative_residual`
  - 반력 설명 오차 비율
- `physics_result.frames[].limb_states`
  - `presence vs identity(디디고 있음 vs 정확한 홀드 식별)` 관련 정보 포함
- `physics_result.frames[].freeze_reason`
  - `Pose fitting / Recovery(자세 맞춤 / 복구)`에서 freeze가 발생한 이유
- `physics_result.frames[].target_jump_mean_m`, `target_jump_max_m`
  - `target jump(목표 자세 프레임 간 급격한 변화량)` 디버깅용 값
- `physics_result.frames[].high_step_seed`
  - `high-step(발을 높이 드는 구간)`에서 `partial seed injection(부분 초기값 주입)`이 적용된 정보

## 1. 공통 최상위 필드

### `schema_version`
- 응답 JSON 스키마 버전
- 예: `"1.0.0"`

### `mode`
- 현재 응답이 어떤 분석 경로에서 나왔는지
- 값:
  - `fast_crux_detection`
  - `physics_crux_detection`

### `timings_s`
- 서버 내부 단계별 처리 시간

#### `timings_s.correction_s`
- raw pose 3D를 보정하는 데 걸린 시간(초)

#### `timings_s.hold_tracking_s`
- `fast` 모드에서 hold tracking(홀드 추적)에 걸린 시간(초)

#### `timings_s.physics_pipeline_s`
- `physics` 모드에서 MuJoCo 전체 계산에 걸린 시간(초)

#### `timings_s.crux_scoring_s`
- `crux score(크럭스 점수)` 계산에 걸린 시간(초)

#### `timings_s.total_s`
- 해당 API 요청 전체 처리 시간(초)

### `correction_summary`
- 입력 pose에 적용한 `pose correction(포즈 보정)` 결과 요약

#### `correction_summary.config`
- 보정 파라미터 설정값 모음

##### `visibility_low_threshold`
- landmark visibility(가시성)가 낮다고 보는 기준값

##### `visibility_missing_threshold`
- landmark가 거의 사라졌다고 보는 기준값

##### `freeze_frames`
- landmark를 이전 프레임 값으로 잠깐 유지하는 최대 프레임 수

##### `ema_alpha_torso`
- torso(몸통) landmark smoothing(스무딩) 강도

##### `ema_alpha_major`
- major joint(주요 관절) smoothing 강도

##### `ema_alpha_distal`
- distal joint(말단 관절) smoothing 강도

##### `max_segment_error_ratio`
- 신체 길이 보정 허용 오차 비율

#### `correction_summary.frame_count`
- correction(보정) 대상 프레임 수

#### `correction_summary.filled_from_previous_frame_count`
- 이전 프레임 값으로 채운 프레임 수

#### `correction_summary.total_low_visibility_joint_count`
- visibility가 낮아서 보수적으로 처리된 joint(관절) 수 총합

#### `correction_summary.total_frozen_joint_count`
- freeze(이전값 유지)된 joint 수 총합

#### `correction_summary.total_reconstructed_joint_count`
- 길이 제약 등으로 복원된 joint 수 총합

---

## 2. `fast` 응답 전체 구조

구조 예시:

```json
{
  "schema_version": "1.0.0",
  "mode": "fast_crux_detection",
  "video_metadata": {},
  "timings_s": {},
  "correction_summary": {},
  "hold_state_summary": {},
  "crux_result": {}
}
```

### `video_metadata`
- 입력 영상 메타데이터
- 주요 필드:
  - `frame_width`
  - `frame_height`
  - `fps`
  - `total_frames`
  - `processed_frames`
  - `frame_step`

#### `video_metadata.frame_width`
- 영상 가로 해상도(px)

#### `video_metadata.frame_height`
- 영상 세로 해상도(px)

#### `video_metadata.fps`
- 입력 프레임 속도(frames per second)

#### `video_metadata.total_frames`
- 입력 전체 프레임 수

#### `video_metadata.processed_frames`
- 실제 분석에 사용한 프레임 수

#### `video_metadata.frame_step`
- 몇 프레임마다 하나씩 샘플링했는지

### `hold_state_summary`
- limb(손발)별 상태 집계
- 하위 key:
  - `left_hand`
  - `right_hand`
  - `left_foot`
  - `right_foot`

#### `hold_state_summary.<limb>.<state>`
- 특정 limb가 특정 상태였던 프레임 수
- 상태 예:
  - `FREE`
  - `REACH`
  - `GRIP`
  - `STEP`
  - `RELEASE`

### `crux_result`
- 빠른 크럭스 후보 결과

#### `crux_result.logic`
- 사용한 크럭스 로직 이름

#### `crux_result.candidate_count`
- 전체 후보 hold 수

#### `crux_result.top_candidates`
- 상위 크럭스 후보 목록

#### `crux_result.all_candidates`
- 전체 크럭스 후보 목록

### `crux_result.top_candidates[]` 또는 `all_candidates[]`

#### `hold_id`
- 크럭스 후보 hold ID

#### `segment_count`
- 해당 hold가 연속 구간으로 등장한 횟수

#### `engagement_count`
- 해당 hold가 실제로 `GRIP / STEP`으로 사용된 횟수

#### `total_active_time_s`
- 그 hold가 전체 시도 동안 활성 상태였던 총 시간

#### `longest_continuous_dwell_s`
- 가장 길게 연속으로 머문 시간

#### `fast_crux_score`
- 빠른 크럭스 점수

#### `reason_tags`
- 왜 크럭스 후보로 뽑혔는지 설명하는 태그

#### `best_segment`
- 가장 대표적인 구간

##### `best_segment.start_frame`
- 대표 구간 시작 프레임

##### `best_segment.end_frame`
- 대표 구간 끝 프레임

##### `best_segment.start_time_ms`
- 대표 구간 시작 시각(ms)

##### `best_segment.end_time_ms`
- 대표 구간 끝 시각(ms)

##### `best_segment.duration_s`
- 대표 구간 길이(초)

##### `best_segment.dominant_limbs`
- 그 구간에서 주로 쓰인 limb 목록

##### `best_segment.dominant_modes`
- 그 구간에서 주로 쓰인 상태 목록

---

## 3. `physics` 응답 최상위 구조

구조 예시:

```json
{
  "schema_version": "1.0.0",
  "mode": "physics_crux_detection",
  "timings_s": {},
  "correction_summary": {},
  "physics_summary": {},
  "physics_pipeline_benchmark_timings_s": {},
  "crux_result": {},
  "physics_result": {}
}
```

### `physics_summary`
- physics 결과를 한눈에 보는 요약 카드 값

#### `physics_summary.fit_mean_error_m`
- 전체 프레임 평균 `pose fitting error(자세 맞춤 오차)` (m)

#### `physics_summary.recovery_ratio`
- `recovery / frozen(복구 / 동결)` 프레임 비율
- 낮을수록 좋음

#### `physics_summary.processed_frames`
- 실제 physics 계산 대상 프레임 수

#### `physics_summary.high_confidence_frame_count`
- `analysis_confidence = high`인 프레임 수

#### `physics_summary.ok_contact_force_frame_count`
- `contact_force_status = ok`인 프레임 수

#### `physics_summary.point_support_frame_count`
- `point_support(1점 지지)`로 판정된 프레임 수

### `physics_pipeline_benchmark_timings_s`
- physics 내부 단계별 시간

#### `load_inputs_s`
- 입력 로딩 시간

#### `prepare_model_s`
- personalized model(개인화 모델) 준비 시간

#### `fit_sequence_s`
- pose fitting(자세 맞춤) 계산 시간

#### `inverse_dynamics_s`
- inverse dynamics(역동역학) 계산 시간

#### `serialize_s`
- 결과 JSON 직렬화 시간

#### `total_s`
- physics 내부 전체 시간

### `crux_result`
- 물리 기반 크럭스 결과

#### `physics_crux_score`
- 물리 기반 크럭스 점수

#### `best_segment.mean_total_body_load`
- 대표 구간 평균 전체 body load(신체 부위 부하 총합)

#### `best_segment.mean_core_load`
- 대표 구간 평균 core load(코어 부하)

#### `best_segment.mean_negative_margin_cm`
- 대표 구간에서 음수 안정 여유가 얼마나 컸는지(cm)

#### `best_segment.mean_load_shift_proxy`
- 대표 구간에서 하중 이동이 얼마나 컸는지 보는 proxy 값

#### `best_segment.ok_fraction`
- 대표 구간 프레임 중 `contact_force_status = ok` 비율

---

## 4. `physics_result` 전체 변수 설명

### `physics_result.schema_version`
- 내부 physics 결과 스키마 버전

### `physics_result.mode`
- 내부 physics 결과 모드 이름

### `physics_result.inputs`
- 분석에 사용한 입력 경로 정보

#### `physics_result.inputs.holds_json`
- hold JSON 경로

#### `physics_result.inputs.pose3d_sequence_json`
- pose sequence JSON 경로

#### `physics_result.inputs.user_body_json`
- user body JSON 경로

#### `physics_result.inputs.base_xml`
- 기본 MuJoCo 인체 XML 경로

#### `physics_result.inputs.personalized_xml`
- 개인화된 MuJoCo 인체 XML 경로

### `physics_result.model_cache`
- 모델 캐시 관련 정보

#### `physics_result.model_cache.cache_dir`
- 캐시 디렉터리 경로

#### `physics_result.model_cache.personalized_xml_cache_hit`
- 개인화 XML이 캐시에서 재사용되었는지 여부

### `physics_result.personalization`
- 사용자 체형을 모델에 반영한 정보

#### `physics_result.personalization.target_metrics_m`
- 입력과 관측으로부터 추정한 목표 체형 값

#### `physics_result.personalization.applied_metrics_m`
- 실제 XML에 적용된 체형 값

대표 하위 변수 예:
- `body_mass_kg`
- `upper_arm_m`
- `forearm_m`
- `thigh_m`
- `shin_m`
- `shoulder_width_m`
- `torso_length_m`
- `hip_width_m`
- `hand_extension_m`

### `physics_result.video_metadata`
- physics 계산 기준 영상 메타데이터

#### `frame_width`
- 영상 가로 해상도

#### `frame_height`
- 영상 세로 해상도

#### `fps`
- 계산 기준 fps

#### `total_frames`
- 입력 전체 프레임 수

#### `processed_frames`
- 실제 분석에 쓴 프레임 수

#### `frame_step`
- 몇 프레임마다 하나씩 샘플링했는지

#### `fit_frame_step`
- full fitting(정식 자세 맞춤)을 몇 프레임마다 했는지

### `physics_result.fit_optimization`
- fitting 설정과 집계

#### `ik_iterations`
- IK 반복 횟수

#### `retry_high_confidence_only`
- retry(재시도)를 높은 신뢰도 프레임에만 했는지

#### `fitted_frame_count`
- 정상 fitting이 수행된 프레임 수

#### `interpolated_frame_count`
- interpolation(보간)으로 채운 프레임 수

#### `retry_attempt_count`
- 재시도 횟수

#### `retry_applied_count`
- 실제 재시도가 적용된 횟수

#### `high_step_seed_attempt_count`
- `high-step partial seed injection(높은 발 올림 구간 부분 초기값 주입)` 시도 횟수

#### `high_step_seed_applied_count`
- 실제 high-step seed가 적용된 횟수

#### `high_step_seed_frame_indices`
- high-step seed가 적용된 프레임 번호 목록

### `physics_result.pose_mode_counts`
- pose 처리 방식별 프레임 수

#### `fitted`
- 정상 fitting 프레임 수

#### `interpolated`
- 보간 프레임 수

#### `frozen_glitch`
- glitch(이상치)로 판단해 freeze한 프레임 수

#### `frozen_missing`
- pose missing(입력 누락)으로 freeze한 프레임 수

#### `filled_gap`
- gap fill(간격 보정)로 채운 프레임 수

### `physics_result.phase_counts`
- 동작 phase(구간)별 프레임 수

#### `dynamic_transition`
- 동적 전이 구간

#### `loaded_transition`
- 하중 이동 전이 구간

#### `static_support`
- 정적 지지 구간

#### `recovery`
- recovery(복구/동결) 구간

### `physics_result.support_mode_counts`
- support 계산 방식별 프레임 수

#### `active_contacts`
- 실제 active contact(활성 접점) 기반 계산

#### `fallback_all_limbs`
- 접점이 부족해 fallback(대체 규칙)을 쓴 계산

### `physics_result.hold_state_summary`
- limb별 상태 및 전이 요약

### `physics_result.support_stability_summary`
- `CoM / Support / Stability(무게중심 / 지지 / 안정도)` 요약
- 주요 필드:
  - `support_type_counts`
  - `inside_support_count`
  - `outside_support_count`
  - `stability_margin_summary_m`

각 limb 하위 구조:

#### `state_counts`
- 상태별 프레임 수

#### `transition_counts`
- `engage / release` 같은 전이 횟수

### `physics_result.limb_contact_confidence_summary`
- limb별 접촉 신뢰도 집계

하위 key:
- `left_hand`
- `right_hand`
- `left_foot`
- `right_foot`

각 limb 하위 구조:

#### `contact_presence_confidence_counts`
- `contact_presence_confidence(디디고 있음 신뢰도)` 분포

#### `hold_identity_confidence_counts`
- `hold_identity_confidence(정확한 홀드 식별 신뢰도)` 분포

### `physics_result.limb_tracker_debug_summary`
- limb tracker 디버그 집계

#### `route_bias_applied_counts`
- `start/end hold bias(시작/종료 홀드 보조 선택)` 적용 횟수

#### `identity_hysteresis_applied_counts`
- `identity hysteresis(홀드 식별 유지)` 적용 횟수

### `physics_result.freeze_reason_summary`
- freeze 이유별 개수

주요 값 예:
- `target_jump`
- `bad_fit_error`
- `bad_lower_limb_consistency`
- `missing_landmarks`

### `physics_result.joint_load_summary`
- 관절별 load 요약

하위 key는 관절 이름입니다.
- 예:
  - `abdomen_x`
  - `hip_y_left`
  - `knee_right`

각 관절 하위 구조:

#### `mean_abs_qfrc_inverse`
- 절대 generalized force(일반화 힘) 평균

#### `max_abs_qfrc_inverse`
- 절대 generalized force 최대값

#### `p95_abs_qfrc_inverse`
- 절대 generalized force의 95퍼센타일

### `physics_result.body_load_summary`
- 신체 부위별 load proxy 요약

하위 key:
- `core`
- `left_arm`
- `right_arm`
- `left_leg`
- `right_leg`

각 부위 하위 구조:

#### `mean_abs_load_proxy`
- 평균 부하 proxy

#### `max_abs_load_proxy`
- 최대 부하 proxy

#### `p95_abs_load_proxy`
- 95퍼센타일 부하 proxy

### `physics_result.contact_force_distribution_summary`
- 손발 반력 분배 요약

#### `status_counts`
- `ok / high_residual / no_active_contacts` 개수

#### `limb_force_summary`
- limb별 force norm(힘 크기) 요약

각 limb 하위 구조:

##### `mean_force_norm_n`
- 평균 힘 크기

##### `max_force_norm_n`
- 최대 힘 크기

##### `p95_force_norm_n`
- 95퍼센타일 힘 크기

#### `relative_residual_summary`
- 반력 설명 오차 비율 요약

##### `mean`
- 평균 오차 비율

##### `median`
- 중앙값 오차 비율

##### `max`
- 최대 오차 비율

### `physics_result.contact_force_display_smoothing`
- 표시용 반력 smoothing 요약

#### `alpha`
- smoothing 강도

#### `max_gap_ms`
- 시간 간격이 이 값보다 크면 smoothing을 끊는 기준

#### `smoothed_frame_count`
- smoothing이 적용된 프레임 수

#### `smoothed_limb_count`
- smoothing이 적용된 limb 수 총합

### `physics_result.support_stability_summary`
- 안정도 요약

#### `support_type_counts`
- `quad_support / tri_support / line_support / point_support` 개수

#### `inside_support_count`
- `inside_support = true` 프레임 수

#### `outside_support_count`
- `inside_support = false` 프레임 수

#### `stability_margin_summary_m`
- 안정 여유 요약

##### `mean_m`
- 평균 안정 여유

##### `median_m`
- 중앙값 안정 여유

##### `min_m`
- 최소 안정 여유

##### `max_m`
- 최대 안정 여유

### `physics_result.dynamic_sequence_gate`
- 전체 시퀀스 품질 게이트

#### `passed`
- 전체 품질 기준 통과 여부

#### `failures`
- 실패 이유 목록

#### `fit_mean_error_m`
- 전체 평균 fitting 오차

#### `recovery_ratio`
- 전체 recovery 비율

---

## 5. `physics_result.frames[]` 프레임 단위 변수 전체 설명

각 프레임은 아래 키를 가집니다.

### 기본 필드

#### `frame_index`
- 프레임 번호

#### `timestamp_ms`
- 프레임 시각(ms)

#### `pose_mode`
- 이 프레임 자세가 어떻게 처리됐는지
- 값 예:
  - `fitted`
  - `interpolated`
  - `frozen_glitch`
  - `frozen_missing`
  - `filled_gap`

#### `phase`
- 동작 구간 분류
- 값 예:
  - `dynamic_transition`
  - `loaded_transition`
  - `static_support`
  - `recovery`

#### `analysis_confidence`
- 프레임 분석 신뢰도
- 값:
  - `high`
  - `low`

### pose / recovery 디버그

#### `freeze_reason`
- freeze가 걸렸다면 이유

#### `target_jump_mean_m`
- 주요 target point(목표 점) 평균 이동량

#### `target_jump_max_m`
- 주요 target point 최대 이동량

#### `high_step_seed`
- high-step seed가 적용된 경우 그 상세 정보

하위 key 예:
- `left`
- `right`

각 side 하위 구조:
- `state`
- `high_step_score`
- `target_knee_flex_deg`
- `previous_knee_flex_deg`
- `knee_gap_deg`

### 접촉 관련

#### `limb_states`
- limb별 상태 상세

하위 key:
- `left_hand`
- `right_hand`
- `left_foot`
- `right_foot`

각 limb 하위 변수:

##### `state`
- 현재 상태
- 예:
  - `FREE`
  - `REACH`
  - `GRIP`
  - `STEP`
  - `RELEASE`

##### `transition`
- 이 프레임에서 발생한 전이
- 예:
  - `engage`
  - `release`
  - `none`

##### `active_hold_id`
- 실제로 활성로 채택된 hold ID

##### `candidate_hold_id`
- 후보 hold ID

##### `distance_px`
- 선택된 hold와 대표 접촉점 사이 거리(px)

##### `speed_px_s`
- limb 대표 접촉점 속도(px/s)

##### `hold_center_px`
- hold 중심 좌표(px)

##### `hold_radius_px`
- hold 반경(px)

##### `inside_polygon`
- representative contact point(대표 접촉점)가 hold polygon 안에 있는지

##### `contact_presence_confidence`
- 접촉이 실제로 존재하는지에 대한 신뢰도
- 값:
  - `none`
  - `low`
  - `medium`
  - `high`

##### `hold_identity_confidence`
- 정확히 이 hold라고 볼 수 있는지에 대한 신뢰도
- 값:
  - `none`
  - `low`
  - `medium`
  - `high`

##### `hold_identity_gap_px`
- 1순위 hold와 2순위 hold 사이의 거리 차이(px)
- 클수록 식별이 더 분명함

##### `route_role`
- route 역할 태그
- 예:
  - `start`
  - `end`
  - `None`

##### `is_start`
- 시작 hold인지 여부

##### `is_end`
- 종료 hold인지 여부

##### `route_bias_applied`
- 시작/종료 hold 보조 선택이 실제 적용됐는지

##### `identity_hysteresis_applied`
- 이전 hold 유지 규칙이 적용됐는지

#### `active_contact_limbs`
- 현재 support 계산에 실제 사용된 limb 목록

#### `active_hold_ids`
- 현재 활성 hold ID만 모아둔 딕셔너리

#### `support_mode`
- support 계산 모드
- 값:
  - `active_contacts`
  - `fallback_all_limbs`

### 안정도 관련

#### `support_stability`
- 지지 안정도 상세

##### `support_type`
- 지지점 형태
- 값:
  - `quad_support`
  - `tri_support`
  - `line_support`
  - `point_support`

##### `support_geometry`
- 기하 형태
- 값:
  - `polygon`
  - `line`
  - `point`

##### `support_point_count`
- 지지점 수

##### `support_points_xyz`
- limb별 3D 지지점 좌표

##### `support_points_yz`
- limb별 YZ 투영 좌표

##### `support_points_xz`
- limb별 XZ 투영 좌표

##### `support_centroid_yz`
- YZ 평면 기준 지지 중심

##### `support_centroid_xz`
- XZ 평면 기준 지지 중심

##### `com_proj_yz`
- CoM의 YZ 투영 좌표

##### `com_proj_xz`
- CoM의 XZ 투영 좌표

##### `inside_support`
- CoM 투영점이 지지 구조 안에 있는지

##### `stability_margin_m`
- 지지 경계까지의 여유 거리
- 양수면 안쪽
- 음수면 바깥

##### `distance_to_support_m`
- 지지 구조까지의 거리

##### `confidence`
- support stability 계산 자체의 신뢰도

#### `com_position_m`
- 무게중심 3D 좌표 `[x, y, z]`

### 부하 관련

#### `joint_loads`
- 관절별 generalized force 값
- key는 관절 이름
- value는 수치

#### `top_joint_loads`
- 부하가 큰 관절 상위 목록

각 원소 변수:

##### `joint`
- 관절 이름

##### `abs_qfrc_inverse`
- 절대 generalized force 값

##### `signed_qfrc_inverse`
- 부호 포함 generalized force 값

#### `body_loads`
- 신체 부위별 부하 proxy

하위 key:
- `core`
- `left_arm`
- `right_arm`
- `left_leg`
- `right_leg`

### 반력 관련

#### `contact_force_confidence_scores`
- limb별 접점 신뢰도 점수
- 내부 분석/디버그용

#### `contact_force_status`
- 반력 분배 결과 상태
- 값:
  - `ok`
  - `high_residual`
  - `no_active_contacts`

#### `contact_force_relative_residual`
- 반력이 현재 자세를 얼마나 잘 설명하는지에 대한 상대 오차
- 낮을수록 좋음

#### `estimated_contact_forces_raw_n`
- smoothing 전 원시 반력

#### `estimated_contact_forces_n`
- smoothing 후 표시용 반력

### `estimated_contact_forces_n.<limb>`
- `left_hand`, `right_hand`, `left_foot`, `right_foot` 중 하나

#### `mode`
- 접촉 상태
- 예:
  - `GRIP`
  - `STEP`
  - `MOVE`

#### `position_xyz`
- contact point(접점) 3D 좌표

#### `force_xyz`
- 추정 힘 벡터 `[x, y, z]`

#### `force_norm_n`
- 전체 힘 크기

#### `normal_force_n`
- 기존 normal force 표현
- 주로 `STEP`에서 의미 있음

#### `tangential_force_n`
- 기존 tangential force 표현
- 주로 `STEP`에서 의미 있음

#### `wall_normal_component_n`
- 벽 법선 방향 성분

#### `compressive_wall_normal_force_n`
- 벽을 미는 압축성 법선 반력
- 발 `STEP` 해석에 가장 중요한 값 중 하나

#### `wall_tangential_force_n`
- 벽 접선 방향 힘 크기

#### `lateral_force_n`
- 좌우 방향 힘 성분

#### `vertical_force_n`
- 상하 방향 힘 성분

#### `confidence_score`
- 접점 신뢰도 점수

#### `regularization_scale`
- solver 정규화 스케일

#### `mode_bias_scale`
- 모드별 bias 스케일

#### `axis_regularization_scale_xyz`
- 축별 정규화 스케일

#### `smoothed_for_display`
- 이 프레임 limb force가 표시용 smoothing을 거쳤는지

---

## 6. 회의에서 바로 보면 좋은 변수 묶음

### 품질 확인용
- `physics_summary.fit_mean_error_m`
- `physics_summary.recovery_ratio`
- `physics_result.dynamic_sequence_gate.passed`
- `frames[].analysis_confidence`

### 안정성 설명용
- `frames[].support_stability.inside_support`
- `frames[].support_stability.stability_margin_m`
- `frames[].support_stability.support_type`
- `frames[].com_position_m`

### 부하 설명용
- `frames[].body_loads`
- `frames[].top_joint_loads`

### 반력 설명용
- `frames[].contact_force_status`
- `frames[].contact_force_relative_residual`
- `frames[].estimated_contact_forces_n.<foot>.compressive_wall_normal_force_n`
- `frames[].estimated_contact_forces_n.<limb>.force_norm_n`

### Grip / Step 설명용
- `frames[].limb_states.<limb>.state`
- `frames[].limb_states.<limb>.active_hold_id`
- `frames[].limb_states.<limb>.contact_presence_confidence`
- `frames[].limb_states.<limb>.hold_identity_confidence`

### 새로 중요도가 올라간 디버그/품질 필드
- `freeze_reason`
- `target_jump_mean_m`
- `target_jump_max_m`
- `high_step_seed`

### 접촉 관련
- `limb_states`
  - limb별 상태 정보
  - 주요 필드:
    - `state`
    - `active_hold_id`
    - `candidate_hold_id`
    - `contact_presence_confidence`
    - `hold_identity_confidence`
    - `route_role`
    - `is_start`
    - `is_end`
    - `route_bias_applied`
    - `identity_hysteresis_applied`
- `active_contact_limbs`
- `active_hold_ids`
- `support_mode`

### 안정도 관련
- `support_stability`
  - 주요 필드:
    - `support_type`
    - `support_geometry`
    - `support_point_count`
    - `support_points_xyz`
    - `support_centroid_yz`
    - `support_centroid_xz`
    - `com_proj_yz`
    - `com_proj_xz`
    - `inside_support`
    - `stability_margin_m`
    - `distance_to_support_m`
    - `confidence`
- `com_position_m`

### 부하 관련
- `joint_loads`
- `top_joint_loads`
- `body_loads`

### 반력 관련
- `contact_force_confidence_scores`
  - 접점 신뢰도 점수
  - 내부 분석/디버그용
- `contact_force_status`
  - `ok / high_residual / no_active_contacts`
- `contact_force_relative_residual`
  - 반력 설명 오차 비율
- `estimated_contact_forces_raw_n`
  - smoothing 전 원시 반력
- `estimated_contact_forces_n`
  - smoothing 후 표시용 반력

### `estimated_contact_forces_n.<limb>`
- limb 예:
  - `left_hand`
  - `right_hand`
  - `left_foot`
  - `right_foot`
- 주요 필드:
  - `mode`
  - `position_xyz`
  - `force_xyz`
  - `force_norm_n`
  - `normal_force_n`
  - `tangential_force_n`
  - `wall_normal_component_n`
  - `compressive_wall_normal_force_n`
  - `wall_tangential_force_n`
  - `lateral_force_n`
  - `vertical_force_n`
  - `confidence_score`
  - `regularization_scale`
  - `mode_bias_scale`
  - `axis_regularization_scale_xyz`
  - `smoothed_for_display`

## 6. 서비스 노출 권장

서비스에 우선 노출하기 좋은 필드:
- `physics_summary`
- `frames[].phase`
- `frames[].analysis_confidence`
- `frames[].active_hold_ids`
- `frames[].body_loads`
- `frames[].support_stability.inside_support`
- `frames[].support_stability.stability_margin_m`
- `frames[].com_position_m`
- `frames[].contact_force_status`
- `frames[].contact_force_relative_residual`
- `frames[].estimated_contact_forces_n`

서비스에 직접 노출하지 말고 내부/디버그용으로 두는 필드:
- `frames[].estimated_contact_forces_raw_n`
- `frames[].contact_force_confidence_scores`
- `frames[].joint_loads`
- `frames[].target_jump_mean_m`
- `frames[].target_jump_max_m`
- `frames[].high_step_seed`

## 7. 서비스 화면에 직접 보여주기 좋은 변수

- `phase`
- `analysis_confidence`
- `active_hold_ids`
- `body_loads`
- `support_stability.inside_support`
- `support_stability.stability_margin_m`
- `com_position_m`
- `contact_force_status`
- `contact_force_relative_residual`
- `estimated_contact_forces_n`

서비스 화면에 바로 보여주기보다는 내부용으로 두는 것이 좋은 변수:
- `joint_loads`
- `estimated_contact_forces_raw_n`
- `contact_force_confidence_scores`
- `target_jump_mean_m`
- `target_jump_max_m`
- `high_step_seed`

---

## 8. 마지막 한 줄 정리

- `fast`는 빠른 크럭스 후보 응답
- `physics`는 자세, 안정도, 부하, 반력까지 포함한 상세 응답
- 회의 중에는 우선
  - `phase`
  - `analysis_confidence`
  - `active_hold_ids`
  - `body_loads`
  - `support_stability`
  - `contact_force_status`
  - `estimated_contact_forces_n`
를 중심으로 보면 됩니다.

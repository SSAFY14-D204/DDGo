# MuJoCo Complete 출력 JSON 명세

이 문서는 `DDgo_AI_Server`의 MuJoCo Complete FastAPI가 반환하는 응답 JSON의 필드를 설명합니다.

대상 엔드포인트:
- `POST /api/v1/mujoco-complete/analyze/fast`
- `POST /api/v1/mujoco-complete/analyze/physics`

주의:
- `fast` 응답은 빠른 크럭스 후보 검출용입니다.
- `physics` 응답은 MuJoCo 물리 분석 결과를 포함합니다.
- `estimated_contact_forces_n`은 현재 단계에서 실측 반력이 아니라 **추정 접촉력 proxy**입니다.

---

## 1. 공통 최상위 필드

### `schema_version: string`
- 응답 스키마 버전
- 예: `"1.0.0"`

### `mode: string`
- 어떤 분석 모드로 생성된 응답인지
- 가능한 값:
  - `fast_crux_detection`
  - `physics_crux_detection`

### `timings_s: object`
- 서버 내부 처리 시간 정보

#### `timings_s.correction_s: number`
- raw pose 3D를 보정하는 데 걸린 시간(초)

#### `timings_s.hold_tracking_s: number`
- `fast` 모드에서 polygon hold 기반 grip/step 판정에 걸린 시간(초)
- `physics` 모드에는 없음

#### `timings_s.physics_pipeline_s: number`
- `physics` 모드에서 MuJoCo 물리 분석 전체에 걸린 시간(초)
- `fast` 모드에는 없음

#### `timings_s.crux_scoring_s: number`
- 크럭스 후보 점수를 계산하는 시간(초)

#### `timings_s.total_s: number`
- 해당 API 요청 전체 처리 시간(초)

### `correction_summary: object`
- raw MediaPipe 3D 좌표 보정 결과 요약

#### `correction_summary.config: object`
- 보정에 사용한 설정값
- 예:
  - `visibility_low_threshold`
  - `visibility_missing_threshold`
  - `freeze_frames`
  - `ema_alpha_torso`
  - `ema_alpha_major`
  - `ema_alpha_distal`
  - `max_segment_error_ratio`

#### `correction_summary.frame_count: integer`
- 보정 대상 프레임 수

#### `correction_summary.filled_from_previous_frame_count: integer`
- 이전 프레임 좌표를 복사해 채운 프레임 수

#### `correction_summary.total_low_visibility_joint_count: integer`
- visibility가 낮아서 신뢰도를 낮게 본 관절 수 누적

#### `correction_summary.total_frozen_joint_count: integer`
- 이전 프레임 값을 유지한 관절 수 누적

#### `correction_summary.total_reconstructed_joint_count: integer`
- 길이 제약 기반으로 복원한 관절 수 누적

---

## 2. `fast` 응답 구조

최상위 구조 예:

```json
{
  "schema_version": "1.0.0",
  "mode": "fast_crux_detection",
  "video_metadata": { ... },
  "timings_s": { ... },
  "correction_summary": { ... },
  "hold_state_summary": { ... },
  "crux_result": { ... }
}
```

### `video_metadata: object`
- 입력 영상 메타데이터

#### `video_metadata.frame_width: integer`
- 영상 가로 픽셀 수

#### `video_metadata.frame_height: integer`
- 영상 세로 픽셀 수

#### `video_metadata.fps: number`
- 초당 프레임 수

#### `video_metadata.total_frames: integer`
- 입력 전체 프레임 수

#### `video_metadata.processed_frames: integer`
- 실제 분석에 사용한 프레임 수

#### `video_metadata.frame_step: integer`
- 몇 프레임마다 하나씩 처리했는지

### `hold_state_summary: object`
- 손/발별 grip/step 상태 개수 요약

하위 키:
- `left_hand`
- `right_hand`
- `left_foot`
- `right_foot`

각 limb 내부 구조:

#### `hold_state_summary.<limb>.<state>: integer`
- 해당 limb가 특정 상태였던 프레임 수
- `state` 예:
  - `FREE`
  - `REACH`
  - `GRIP`
  - `STEP`
  - `RELEASE`

설명:
- 손은 주로 `GRIP`
- 발은 주로 `STEP`

### `crux_result: object`
- 빠른 크럭스 후보 결과

#### `crux_result.logic: string`
- 사용한 크럭스 로직 이름
- 예: `fast_dwell_based`

#### `crux_result.candidate_count: integer`
- 전체 후보 hold 개수

#### `crux_result.top_candidates: array<object>`
- 상위 크럭스 후보 목록

#### `crux_result.all_candidates: array<object>`
- 전체 크럭스 후보 목록

### `crux_result.top_candidates[] / all_candidates[]`

#### `hold_id: integer`
- 홀드 ID

#### `segment_count: integer`
- 이 홀드가 연속 구간으로 등장한 횟수

#### `engagement_count: integer`
- 실제 grip/step으로 활성화된 횟수

#### `total_active_time_s: number`
- 이 홀드가 전체 영상에서 활성 상태였던 총 시간(초)

#### `longest_continuous_dwell_s: number`
- 가장 길게 연속으로 유지된 시간(초)

#### `best_segment: object`
- 이 홀드의 대표 구간

##### `best_segment.start_frame: integer`
- 시작 프레임

##### `best_segment.end_frame: integer`
- 종료 프레임

##### `best_segment.start_time_ms: integer`
- 시작 시각(ms)

##### `best_segment.end_time_ms: integer`
- 종료 시각(ms)

##### `best_segment.duration_s: number`
- 대표 구간 길이(초)

##### `best_segment.dominant_limbs: array<string>`
- 이 홀드에 주로 붙어 있던 limb

##### `best_segment.dominant_modes: array<string>`
- 대표 상태
- 보통 `GRIP` 또는 `STEP`

#### `limb_counts: object`
- limb별 등장 프레임 수
- 예:
  - `left_hand`
  - `right_hand`
  - `left_foot`
  - `right_foot`

#### `mode_counts: object`
- 상태별 등장 프레임 수
- 예:
  - `GRIP`
  - `STEP`

#### `fast_crux_score: number`
- 빠른 크럭스 점수
- 체류 시간 중심으로 계산한 점수

#### `reason_tags: array<string>`
- 이 후보가 뽑힌 이유 태그
- 예:
  - `longest_dwell`
  - `high_total_dwell`

---

## 3. `physics` 응답 구조

최상위 구조 예:

```json
{
  "schema_version": "1.0.0",
  "mode": "physics_crux_detection",
  "timings_s": { ... },
  "correction_summary": { ... },
  "physics_summary": { ... },
  "physics_pipeline_benchmark_timings_s": { ... },
  "crux_result": { ... },
  "physics_result": { ... }
}
```

### `physics_summary: object`
- physics 전체 결과를 간단히 요약한 값

#### `physics_summary.fit_mean_error_m: number`
- 전체 프레임 평균 fitting 오차(m)

#### `physics_summary.recovery_ratio: number`
- 복구/동결 프레임 비율
- 값이 낮을수록 좋음

#### `physics_summary.processed_frames: integer`
- 실제 물리 분석에 사용된 프레임 수

#### `physics_summary.high_confidence_frame_count: integer`
- 신뢰도가 높은 프레임 수

#### `physics_summary.ok_contact_force_frame_count: integer`
- 접촉력 추정이 정상(`ok`)으로 나온 프레임 수

#### `physics_summary.point_support_frame_count: integer`
- 1점 지지로 판정된 프레임 수

### `physics_pipeline_benchmark_timings_s: object`
- physics 파이프라인 내부 단계별 시간

#### `load_inputs_s`
- 입력 파싱 시간

#### `prepare_model_s`
- personalized model 준비 시간

#### `fit_sequence_s`
- 전 프레임 fitting 시간

#### `inverse_dynamics_s`
- MuJoCo 역동역학 계산 시간

#### `serialize_s`
- 결과 직렬화 시간

#### `total_s`
- physics 내부 전체 시간

### `crux_result`
- 구조는 `fast`와 동일하나 점수 필드가 다름

차이:
- `physics_crux_score`
- `best_segment` 안에 물리 기반 feature가 추가됨

#### `physics_crux_score: number`
- dwell + body load + instability + load shift를 조합한 크럭스 점수

#### `best_segment.mean_total_body_load: number`
- 대표 구간의 평균 전체 body load

#### `best_segment.mean_core_load: number`
- 대표 구간의 평균 코어 load

#### `best_segment.mean_negative_margin_cm: number`
- 대표 구간에서 지지 안정성이 얼마나 나빴는지(cm)
- 클수록 불안정

#### `best_segment.mean_load_shift_proxy: number`
- 대표 구간에서 하중 이동이 얼마나 컸는지 보는 proxy 값

#### `best_segment.mean_confidence_weight: number`
- 대표 구간 프레임 신뢰도 평균 가중치

#### `best_segment.ok_fraction: number`
- 대표 구간 프레임 중 contact force 상태가 `ok`인 비율

#### `best_segment.segment_crux_score: number`
- 대표 구간 단위 점수

---

## 4. `physics_result` 상세 구조

`physics_result`는 프레임별 물리 분석 전체 결과입니다.

### `physics_result.schema_version: string`
- 내부 physics 결과 스키마 버전

### `physics_result.mode: string`
- 내부 physics 모드 이름

### `physics_result.inputs: object`
- 분석에 사용한 입력/모델 경로 정보

#### `inputs.holds_json`
- 홀드 입력 경로

#### `inputs.pose3d_sequence_json`
- pose 입력 경로

#### `inputs.user_body_json`
- 사용자 신체 치수 입력 경로

#### `inputs.base_xml`
- 기본 인체 XML 경로

#### `inputs.personalized_xml`
- 개인화된 XML 경로

### `physics_result.model_cache: object`
- personalized XML 캐시 정보

#### `model_cache.cache_dir`
- 캐시 디렉토리 경로

#### `model_cache.personalized_xml_cache_hit: boolean`
- personalized XML을 캐시에서 재사용했는지

### `physics_result.personalization: object`
- 개인화 모델에 적용한 치수 정보

#### `personalization.target_metrics_m`
- pose 시퀀스에서 추정한 목표 신체 치수

#### `personalization.applied_metrics_m`
- 실제 모델에 적용한 신체 치수

주요 하위 필드 예:
- `body_mass_kg`
- `upper_arm_m`
- `forearm_m`
- `thigh_m`
- `shin_m`
- `shoulder_width_m`
- `torso_length_m`
- `hip_width_m`
- `hand_extension_m`

### `physics_result.video_metadata: object`
- 영상 메타데이터

#### `video_metadata.frame_width`
#### `video_metadata.frame_height`
#### `video_metadata.fps`
#### `video_metadata.total_frames`
#### `video_metadata.processed_frames`
#### `video_metadata.frame_step`
#### `video_metadata.fit_frame_step`

### `physics_result.fit_optimization: object`
- fitting 최적화 설정 및 실제 사용 결과

#### `ik_iterations: integer`
- IK 반복 횟수

#### `retry_high_confidence_only: boolean`
- 재시도를 고신뢰 프레임에만 적용했는지

#### `fitted_frame_count: integer`
- 실제 full fitting을 수행한 프레임 수

#### `interpolated_frame_count: integer`
- 보간으로 채운 프레임 수

#### `retry_attempt_count: integer`
- 재시도 시도 횟수

#### `retry_applied_count: integer`
- 실제 재시도가 적용된 횟수

### `physics_result.pose_mode_counts: object`
- 프레임 처리 방식 개수 요약

가능한 키:
- `fitted`
- `interpolated`
- `frozen_glitch`
- `frozen_missing`
- `filled_gap`

### `physics_result.phase_counts: object`
- 프레임 phase 개수 요약

가능한 키:
- `dynamic_transition`
- `loaded_transition`
- `static_support`
- `recovery`

### `physics_result.support_mode_counts: object`
- support 계산 방식 요약

가능한 키:
- `active_contacts`
- `fallback_all_limbs`

### `physics_result.hold_state_summary: object`
- 손/발 상태 요약

하위 키:
- `left_hand`
- `right_hand`
- `left_foot`
- `right_foot`

각 limb 구조:

#### `state_counts: object`
- 상태별 프레임 수

#### `transition_counts: object`
- 전이 이벤트 수
- 예:
  - `engage`
  - `release`

### `physics_result.joint_load_summary: object`
- 관절별 generalized load 요약

각 key는 관절 이름이며 값 구조는 동일합니다.
예:
- `abdomen_x`
- `neck_y`
- `shoulder1_left`
- `hip_y_right`
- `knee_left`
- `ankle_x_right`

각 관절 값 구조:

#### `mean_abs_qfrc_inverse: number`
- 절대 generalized load 평균

#### `max_abs_qfrc_inverse: number`
- 절대 generalized load 최대값

#### `p95_abs_qfrc_inverse: number`
- 절대 generalized load 95퍼센타일

### `physics_result.body_load_summary: object`
- 신체 부위별 load proxy 요약

하위 키:
- `core`
- `left_arm`
- `right_arm`
- `left_leg`
- `right_leg`

각 값 구조:

#### `mean_abs_load_proxy: number`
- 평균 부하 proxy

#### `max_abs_load_proxy: number`
- 최대 부하 proxy

#### `p95_abs_load_proxy: number`
- 95퍼센타일 부하 proxy

### `physics_result.contact_force_distribution_summary: object`
- 손발 추정 접촉력 요약

#### `status_counts: object`
- 프레임별 contact force 상태 개수
- 가능한 키:
  - `ok`
  - `high_residual`
  - `no_active_contacts`

#### `limb_force_summary: object`
- limb별 힘 크기 통계

하위 키:
- `left_hand`
- `right_hand`
- `left_foot`
- `right_foot`

각 값 구조:
- `mean_force_norm_n`
- `max_force_norm_n`
- `p95_force_norm_n`

#### `relative_residual_summary: object`
- 접촉력으로 설명하지 못한 잔차 비율 요약
- `mean`
- `median`
- `max`

### `physics_result.support_stability_summary: object`
- 지지 안정성 요약

#### `support_type_counts: object`
- 지지점 개수 유형별 프레임 수
- 가능한 키:
  - `quad_support`
  - `tri_support`
  - `line_support`
  - `point_support`

#### `inside_support_count: integer`
- CoM 투영점이 지지 구조 내부에 있던 프레임 수

#### `outside_support_count: integer`
- CoM 투영점이 지지 구조 외부에 있던 프레임 수

#### `stability_margin_summary_m: object`
- 지지 안정성 margin 요약
- `mean_m`
- `median_m`
- `min_m`
- `max_m`

### `physics_result.dynamic_sequence_gate: object`
- 전체 시퀀스 품질 게이트

#### `passed: boolean`
- 기본 품질 기준 통과 여부

#### `failures: array<string>`
- 실패한 기준 목록

#### `fit_mean_error_m: number`
- 전체 평균 fitting 오차

#### `recovery_ratio: number`
- 복구/동결 프레임 비율

---

## 5. `physics_result.frames[]` 프레임별 상세 필드

각 프레임은 하나의 분석 결과입니다.

### 기본 정보

#### `frame_index: integer`
- 프레임 번호

#### `timestamp_ms: integer`
- 프레임 시각(ms)

#### `pose_mode: string`
- 이 프레임 자세가 어떻게 생성됐는지
- 가능한 값:
  - `fitted`
  - `interpolated`
  - `frozen_glitch`
  - `frozen_missing`
  - `filled_gap`

#### `phase: string`
- 프레임 동작 상태
- 가능한 값:
  - `dynamic_transition`
  - `loaded_transition`
  - `static_support`
  - `recovery`

#### `analysis_confidence: string`
- 분석 신뢰도
- 가능한 값:
  - `high`
  - `low`

### 접촉 상태

#### `active_contact_limbs: array<string>`
- 이 프레임에서 실제 지지점으로 사용한 limb 목록

#### `active_hold_ids: object`
- limb별 활성 hold ID
- 예:
  - `left_hand: 12`
  - `right_foot: 31`

#### `support_mode: string`
- support 계산 방식
- 가능한 값:
  - `active_contacts`
  - `fallback_all_limbs`

### `support_stability: object`
- 지지 안정성 상세 정보

#### `support_type: string`
- 지지 구조 유형
- 가능한 값:
  - `quad_support`
  - `tri_support`
  - `line_support`
  - `point_support`

#### `support_geometry: string`
- 지지 구조 형태
- 가능한 값:
  - `polygon`
  - `line`
  - `point`

#### `support_point_count: integer`
- 지지점 개수

#### `support_points_xyz: object`
- limb별 지지점 3D 좌표

#### `support_points_yz: object`
- limb별 YZ 투영 좌표

#### `support_points_xz: object`
- limb별 XZ 투영 좌표

#### `support_centroid_yz: array<number>`
- YZ 평면에서의 지지 중심

#### `support_centroid_xz: array<number>`
- XZ 평면에서의 지지 중심

#### `com_proj_yz: array<number>`
- CoM의 YZ 평면 투영점

#### `com_proj_xz: array<number>`
- CoM의 XZ 평면 투영점

#### `inside_support: boolean`
- CoM 투영점이 현재 지지 구조 안에 있는지

#### `stability_margin_m: number`
- 지지 경계까지의 margin
- 양수: 내부
- 음수: 외부

#### `distance_to_support_m: number`
- 지지 구조까지의 거리 절대값

#### `hull_vertices_yz: array<array<number>>`
- polygon/line 지지 구조의 YZ 꼭짓점 목록
- `point_support`에서는 없을 수 있음

#### `confidence: number`
- support_stability 자체 신뢰도

### 부하 / 물리 결과

#### `joint_loads: object`
- 관절별 generalized load
- key는 관절 이름
- value는 **부호가 있는 수치**

예:
- `abdomen_x`
- `hip_y_left`
- `knee_right`

#### `top_joint_loads: array<object>`
- 절대 부하가 큰 관절 상위 목록

각 항목:
- `joint`
- `abs_qfrc_inverse`
- `signed_qfrc_inverse`

#### `body_loads: object`
- 신체 부위별 load proxy
- key:
  - `core`
  - `left_arm`
  - `right_arm`
  - `left_leg`
  - `right_leg`

#### `com_position_m: array<number>`
- CoM의 3D 위치
- 형식: `[x, y, z]`
- 단위: `m`

### `estimated_contact_forces_n: object`
- limb별 추정 접촉력
- 실측 반력이 아니라 현재 로직의 추정값

하위 key:
- `left_hand`
- `right_hand`
- `left_foot`
- `right_foot`

각 limb 구조:

#### `mode: string`
- `GRIP` 또는 `STEP`

#### `position_xyz: array<number>`
- 접촉점 3D 위치

#### `force_xyz: array<number>`
- 추정 접촉력 벡터

#### `force_norm_n: number`
- 힘 벡터 크기

#### `normal_force_n: number | null`
- STEP인 경우 법선 방향 힘
- GRIP인 경우 현재는 `null`

#### `tangential_force_n: number | null`
- STEP인 경우 접선 방향 힘
- GRIP인 경우 현재는 `null`

### `contact_force_status: string | null`
- 프레임 contact force 해석 상태
- 가능한 값:
  - `ok`
  - `high_residual`
  - `no_active_contacts`

### `contact_force_relative_residual: number | null`
- 필요한 전체 wrench와 추정 접촉력이 얼마나 차이 나는지 나타내는 상대 잔차
- 낮을수록 좋음

---

## 6. 안드로이드팀이 특히 중요하게 볼 필드

빠른 UI 반영용:
- `mode`
- `timings_s.total_s`
- `crux_result.top_candidates`

물리 결과 표시용:
- `physics_summary`
- `physics_result.frames[].com_position_m`
- `physics_result.frames[].joint_loads`
- `physics_result.frames[].body_loads`
- `physics_result.frames[].estimated_contact_forces_n`
- `physics_result.frames[].support_stability`
- `physics_result.frames[].analysis_confidence`
- `physics_result.frames[].contact_force_status`

신뢰도 판단용:
- `physics_summary.recovery_ratio`
- `physics_result.dynamic_sequence_gate`
- `analysis_confidence`
- `contact_force_status`

---

## 7. 해석 시 주의사항

- `fast` 응답은 빠른 후보 제시용입니다.
- `fast_crux_score`는 체류 시간 기반이므로, 휴식 홀드가 상위에 올 수 있습니다.
- `physics_crux_score`는 더 설명 가능하지만 계산 시간이 더 걸립니다.
- `estimated_contact_forces_n`은 현재 단계에서 실측 반력이 아니라 proxy입니다.
- `analysis_confidence = low` 프레임은 서비스 UI에서 약하게 보여주거나 제외하는 것이 좋습니다.
- `contact_force_status = high_residual`인 프레임은 접촉력 해석을 강하게 믿지 않는 것이 좋습니다.

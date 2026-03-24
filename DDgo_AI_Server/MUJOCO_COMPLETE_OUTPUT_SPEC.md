# MuJoCo Complete 출력 JSON 명세

이 문서는 [DDgo_AI_Server](C:/ssafy/project-2/S14P21D204/DDgo_AI_Server)의 MuJoCo Complete FastAPI가 반환하는 응답 JSON 구조를 설명합니다.

대상 엔드포인트:
- `POST /api/v1/mujoco-complete/analyze/fast`
- `POST /api/v1/mujoco-complete/analyze/physics`

핵심 원칙:
- 요청 구조는 변경되지 않았습니다.
- 기존 응답 키는 유지합니다.
- 이번 반영에서는 physics 응답에 **반력 관련 필드가 추가**되었습니다.

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

### `schema_version: string`
- 응답 스키마 버전

### `mode: string`
- 응답 생성 모드
- 값:
  - `fast_crux_detection`
  - `physics_crux_detection`

### `timings_s: object`
- 전체 처리 시간 집계
- 주요 필드:
  - `correction_s`
  - `hold_tracking_s` 또는 `physics_pipeline_s`
  - `crux_scoring_s`
  - `total_s`

### `correction_summary: object`
- `pose correction(포즈 보정)` 결과 요약
- 주요 필드:
  - `frame_count`
  - `filled_from_previous_frame_count`
  - `total_low_visibility_joint_count`
  - `total_frozen_joint_count`
  - `total_reconstructed_joint_count`
  - `config`

## 2. `fast` 응답 구조

최상위 구조:

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

### `hold_state_summary`
- limb(손발)별 `GRIP / STEP / REACH / FREE / RELEASE` 상태 개수 요약
- 하위 key:
  - `left_hand`
  - `right_hand`
  - `left_foot`
  - `right_foot`

### `crux_result`
- 빠른 `crux(크럭스)` 후보 결과
- 주요 필드:
  - `logic`
  - `candidate_count`
  - `top_candidates`
  - `all_candidates`

### `crux_result.top_candidates[]`
- 주요 필드:
  - `hold_id`
  - `segment_count`
  - `engagement_count`
  - `total_active_time_s`
  - `longest_continuous_dwell_s`
  - `fast_crux_score`
  - `reason_tags`
  - `best_segment`

## 3. `physics` 응답 구조

최상위 구조:

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
- physics 결과 핵심 요약
- 주요 필드:
  - `fit_mean_error_m`
  - `recovery_ratio`
  - `processed_frames`
  - `high_confidence_frame_count`
  - `ok_contact_force_frame_count`
  - `point_support_frame_count`

### `physics_pipeline_benchmark_timings_s`
- physics 내부 단계별 시간
- 주요 필드:
  - `load_inputs_s`
  - `prepare_model_s`
  - `fit_sequence_s`
  - `inverse_dynamics_s`
  - `serialize_s`
  - `total_s`

### `crux_result`
- 물리 기반 `crux(크럭스)` 후보
- 주요 필드:
  - `top_candidates`
  - `all_candidates`
- 각 candidate의 대표 필드:
  - `hold_id`
  - `physics_crux_score`
  - `total_active_time_s`
  - `longest_continuous_dwell_s`
  - `reason_tags`
  - `best_segment.mean_total_body_load`
  - `best_segment.mean_core_load`
  - `best_segment.mean_negative_margin_cm`
  - `best_segment.mean_load_shift_proxy`
  - `best_segment.ok_fraction`

## 4. `physics_result` 상세 구조

### `physics_result.inputs`
- 분석에 사용한 입력 경로 정보
- 주요 필드:
  - `holds_json`
  - `pose3d_sequence_json`
  - `user_body_json`
  - `base_xml`
  - `personalized_xml`

### `physics_result.model_cache`
- 모델 캐시 정보
- 주요 필드:
  - `cache_dir`
  - `personalized_xml_cache_hit`

### `physics_result.personalization`
- 개인화 모델에 적용한 체형 정보
- 주요 필드:
  - `target_metrics_m`
  - `applied_metrics_m`

### `physics_result.video_metadata`
- physics 파이프라인 기준 영상 메타데이터
- 주요 필드:
  - `frame_width`
  - `frame_height`
  - `fps`
  - `total_frames`
  - `processed_frames`
  - `frame_step`
  - `fit_frame_step`

### `physics_result.fit_optimization`
- fitting 설정 및 집계
- 주요 필드:
  - `ik_iterations`
  - `retry_high_confidence_only`
  - `fitted_frame_count`
  - `interpolated_frame_count`
  - `retry_attempt_count`
  - `retry_applied_count`
  - `high_step_seed_attempt_count`
  - `high_step_seed_applied_count`
  - `high_step_seed_frame_indices`

### `physics_result.pose_mode_counts`
- `fitted / interpolated / frozen_glitch / frozen_missing / filled_gap` 개수

### `physics_result.phase_counts`
- `dynamic_transition / loaded_transition / static_support / recovery` 개수

### `physics_result.support_mode_counts`
- `active_contacts / fallback_all_limbs` 개수

### `physics_result.hold_state_summary`
- limb별 상태 및 전이 집계

### `physics_result.limb_contact_confidence_summary`
- limb별 `contact_presence_confidence(디디고 있음 신뢰도)`와
  `hold_identity_confidence(정확한 홀드 식별 신뢰도)` 집계

### `physics_result.limb_tracker_debug_summary`
- `route bias(시작/종료 홀드 보조 선택)`와
  `identity hysteresis(홀드 식별 유지)` 적용 집계

### `physics_result.freeze_reason_summary`
- freeze 이유 요약
- 예:
  - `target_jump`
  - `bad_fit_error`
  - `bad_lower_limb_consistency`

### `physics_result.joint_load_summary`
- 관절별 generalized load(일반화 힘) 요약

### `physics_result.body_load_summary`
- 부위별 load proxy(부하 근사값) 요약
- 하위 key:
  - `core`
  - `left_arm`
  - `right_arm`
  - `left_leg`
  - `right_leg`

### `physics_result.contact_force_distribution_summary`
- 손발 반력 분배 요약
- 주요 필드:
  - `status_counts`
  - `limb_force_summary`
  - `relative_residual_summary`

### `physics_result.contact_force_display_smoothing`
- 새로 추가된 표시용 smoothing 정보
- 주요 필드:
  - `alpha`
  - `max_gap_ms`
  - `smoothed_frame_count`
  - `smoothed_limb_count`

### `physics_result.support_stability_summary`
- `CoM / Support / Stability(무게중심 / 지지 / 안정도)` 요약
- 주요 필드:
  - `support_type_counts`
  - `inside_support_count`
  - `outside_support_count`
  - `stability_margin_summary_m`

### `physics_result.dynamic_sequence_gate`
- 전체 시퀀스 품질 게이트
- 주요 필드:
  - `passed`
  - `failures`
  - `fit_mean_error_m`
  - `recovery_ratio`

## 5. `physics_result.frames[]` 프레임 상세

### 기본 정보
- `frame_index`
- `timestamp_ms`
- `pose_mode`
- `phase`
- `analysis_confidence`

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

## 7. 해석 시 주의사항

- `estimated_contact_forces_n`는 **실측 반력**이 아니라 `proxy(근사 추정값)`입니다.
- `contact_force_status = ok`이고 `analysis_confidence = high`인 프레임에서 우선 해석하는 것이 좋습니다.
- `body_loads`는 서비스용 상대 지표로 적합합니다.
- `joint_loads`는 내부 분석용으로 보는 것이 안전합니다.
- `tri_support(3점 지지)` 구간의 `inside_support = false`는 절대 실패 판정보다 상대 안정도 해석에 가깝습니다.

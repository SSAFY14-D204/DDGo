# MuJoCo Complete 결과 요약

이 문서는 안드로이드팀이 분석 화면을 구성할 때 **무엇을 먼저 보고, 어떤 필드를 우선 쓰면 되는지** 빠르게 이해할 수 있도록 정리한 요약 문서입니다.

상세 명세는 [MUJOCO_COMPLETE_OUTPUT_SPEC.md](C:/ssafy/project-2/S14P21D204/DDgo_AI_Server/MUJOCO_COMPLETE_OUTPUT_SPEC.md)를 참고합니다.

## 이번 반영에서 바뀐 점
이번 runtime 반영으로 physics 응답에 아래 변화가 생겼습니다.

- `Grip / Step(손잡기 / 발디딤 상태)`가 더 안정화됐습니다.
  - `presence vs identity(디디고 있음 vs 정확한 홀드 식별)` 분리
  - `start/end hold(시작/종료 홀드)` 보조
  - `light hysteresis(가벼운 이전 홀드 유지)`
- `high-step(발을 높이 드는 구간)`에서 무릎을 더 자연스럽게 굽히도록 보정했습니다.
- 반력 응답이 더 해석 가능해졌습니다.
  - `estimated_contact_forces_raw_n(원시 반력)` 추가
  - `estimated_contact_forces_n(표시용 smoothing 반력)` 유지
  - `contact_force_status`, `contact_force_relative_residual` 추가
  - limb별 `wall_normal / tangential / vertical` 성분 추가
- physics 결과에 `contact_force_display_smoothing(표시용 반력 smoothing 정보)`가 추가됐습니다.

## 분석 화면에서 가장 먼저 볼 필드

### 1. 공통 상태
- `mode`
- `timings_s.total_s`
- `correction_summary`

### 2. 빠른 결과 화면
- `crux_result.top_candidates`
- `hold_state_summary`

빠른 결과에서 주로 볼 값:
- `hold_id`
- `total_active_time_s`
- `longest_continuous_dwell_s`
- `fast_crux_score`
- `best_segment.duration_s`

### 3. physics 상세 화면
- `physics_summary`
- `physics_result.frames[].phase`
- `physics_result.frames[].analysis_confidence`
- `physics_result.frames[].active_hold_ids`
- `physics_result.frames[].body_loads`
- `physics_result.frames[].support_stability`
- `physics_result.frames[].com_position_m`
- `physics_result.frames[].contact_force_status`
- `physics_result.frames[].estimated_contact_forces_n`

## 화면별 추천 해석

### A. 안정도 화면
핵심 필드:
- `frames[].support_stability.inside_support`
- `frames[].support_stability.stability_margin_m`
- `frames[].support_stability.support_type`
- `frames[].com_position_m`

해석:
- `inside_support = true`이고 `stability_margin_m > 0`이면 비교적 안정
- `quad_support(4점 지지)`는 신뢰도가 높음
- `tri_support(3점 지지)`는 절대 판정보다 상대 안정도 지표로 해석

### B. 부하 화면
핵심 필드:
- `frames[].body_loads`
- `frames[].top_joint_loads`
- `physics_summary.fit_mean_error_m`
- `physics_summary.recovery_ratio`

해석:
- 서비스에는 `body_loads(신체 부위 부하)`를 우선 사용
- `joint_loads(관절 부하)`는 내부 분석용
- `body_loads`는 뉴턴값이 아니라 `proxy(근사 부하 지표)`로 해석

### C. 반력 화면
핵심 필드:
- `frames[].contact_force_status`
- `frames[].contact_force_relative_residual`
- `frames[].estimated_contact_forces_n`

서비스 표시 권장 하위 필드:
- 공통
  - `mode`
  - `force_norm_n`
  - `vertical_force_n`
  - `smoothed_for_display`
- 발 `STEP`
  - `compressive_wall_normal_force_n`
  - `wall_tangential_force_n`

해석:
- `contact_force_status = ok`일 때 우선 사용
- `high_residual`이면 설명력이 낮은 프레임
- `estimated_contact_forces_n`는 실측값이 아니라 표시용 추정값

## 사용자에게 설명하기 좋은 문장 예시

### 자세 / 안정도
- `현재는 4점 지지로 비교적 안정적인 구간입니다.`
- `무게중심이 지지 영역 밖으로 나가 불안정한 구간입니다.`

### 부하
- `코어 부담이 큰 자세입니다.`
- `오른다리 사용 비중이 큰 구간입니다.`
- `양팔 지지 의존이 높은 구간입니다.`

### 반력
- `왼발이 벽을 강하게 밀고 있는 구간입니다.`
- `오른손 지지력이 크게 증가한 구간입니다.`
- `현재 프레임의 반력 해석 신뢰도는 낮습니다.`

## 이번에 실제로 달라진 응답 필드

새로 추가되거나 의미가 보강된 핵심 필드:
- `physics_result.contact_force_display_smoothing`
- `frames[].freeze_reason`
- `frames[].target_jump_mean_m`
- `frames[].target_jump_max_m`
- `frames[].high_step_seed`
- `frames[].limb_states.contact_presence_confidence`
- `frames[].limb_states.hold_identity_confidence`
- `frames[].limb_states.route_bias_applied`
- `frames[].limb_states.identity_hysteresis_applied`
- `frames[].estimated_contact_forces_raw_n`
- `frames[].estimated_contact_forces_n.<limb>.compressive_wall_normal_force_n`
- `frames[].estimated_contact_forces_n.<limb>.wall_tangential_force_n`
- `frames[].estimated_contact_forces_n.<limb>.vertical_force_n`
- `frames[].estimated_contact_forces_n.<limb>.smoothed_for_display`

## 최종 요약

- `fast`는 빠른 크럭스 후보를 보여주는 응답입니다.
- `physics`는 자세, 안정도, 부하, 반력까지 포함한 상세 응답입니다.
- 이번 반영으로 physics 응답은 **하체 자세, Grip/Step, 반력 표시 안정성**이 개선됐습니다.
- 안드로이드 화면에서는
  - `phase`
  - `analysis_confidence`
  - `active_hold_ids`
  - `body_loads`
  - `support_stability`
  - `contact_force_status`
  - `estimated_contact_forces_n`
를 중심으로 구성하는 것이 가장 적절합니다.

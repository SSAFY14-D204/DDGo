# Physics Result JSON Spec

파일 목적:

- 입력 3종 JSON을 사용해 계산한 동적 물리 해석 결과를 반환한다.
- 서비스 응답과 벤치마크 결과 저장에 공통으로 사용한다.

파일명 권장:

- `physics_result.json`
- 벤치마크 환경에서는 `json_service_benchmark_report.json`

스키마 버전:

- `1.0.0`

## 최상위 구조

```json
{
  "schema_version": "1.0.0",
  "mode": "json_only_service_benchmark",
  "inputs": {},
  "benchmark_timings_s": {},
  "model_cache": {},
  "personalization": {},
  "video_metadata": {},
  "pose_mode_counts": {},
  "phase_counts": {},
  "support_mode_counts": {},
  "hold_state_summary": {},
  "joint_load_summary": {},
  "body_load_summary": {},
  "contact_force_distribution_summary": {},
  "support_stability_summary": {},
  "dynamic_sequence_gate": {},
  "frames": []
}
```

## 설계 원칙

이 파일은 두 층으로 나뉜다.

1. 상단 요약
- 시간
- 품질 게이트
- 전체 분포 요약

2. 프레임별 결과
- 관절 부하
- CoM
- 추정 손/발 반력

## 최상위 필드 명세

### 1. `schema_version`

- 타입: `string`
- 필수: `Y`
- 고정값: `1.0.0`

### 2. `mode`

- 타입: `string`
- 필수: `Y`
- 권장값:
  - `json_only_service_benchmark`
  - 향후 서비스 운영 모드면 별도 모드명 가능

### 3. `inputs`

- 타입: `object`
- 필수: `Y`

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `holds_json` | string | Y | 사용한 홀드 JSON 경로 |
| `pose3d_sequence_json` | string | Y | 사용한 pose JSON 경로 |
| `user_body_json` | string | Y | 사용한 user body JSON 경로 |
| `base_xml` | string | N | 템플릿 XML 경로 |
| `personalized_xml` | string | N | 실제 personalized XML 경로 |

### 4. `benchmark_timings_s`

- 타입: `object`
- 필수: `Y`

| 필드 | 타입 | 필수 | 단위 | 설명 |
| --- | --- | --- | --- | --- |
| `load_inputs_s` | number | Y | s | 입력 JSON 로드 시간 |
| `prepare_model_s` | number | Y | s | personalization/model 준비 시간 |
| `fit_sequence_s` | number | Y | s | 프레임 fitting 시간 |
| `inverse_dynamics_s` | number | Y | s | inverse dynamics 계산 시간 |
| `serialize_s` | number | Y | s | 결과 JSON 직렬화 시간 |
| `total_s` | number | Y | s | 전체 처리 시간 |

### 5. `model_cache`

- 타입: `object`
- 필수: `Y`

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `cache_dir` | string | Y | personalized XML cache 경로 |
| `personalized_xml_cache_hit` | boolean | Y | warm start 여부 |

### 6. `personalization`

- 타입: `object`
- 필수: `Y`

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `target_metrics_m` | object | Y | pose 시퀀스에서 추정한 목표 체형 지표 |
| `applied_metrics_m` | object | Y | 실제 personalized model에 적용한 체형 지표 |

### 7. `video_metadata`

- 타입: `object`
- 필수: `Y`

| 필드 | 타입 | 필수 | 단위 | 설명 |
| --- | --- | --- | --- | --- |
| `frame_width` | integer | Y | px | 기준 영상 가로 크기 |
| `frame_height` | integer | Y | px | 기준 영상 세로 크기 |
| `fps` | number | Y | frame/s | 기준 fps |
| `total_frames` | integer | Y | frame | 원본 전체 프레임 수 |
| `processed_frames` | integer | Y | frame | 실제 계산한 프레임 수 |
| `frame_step` | integer | Y | frame | 계산 간격 |

### 8. 요약 카운트/요약 필드

- 타입: `object`
- 필수: `Y`

포함 필드:

- `pose_mode_counts`
- `phase_counts`
- `support_mode_counts`
- `hold_state_summary`
- `joint_load_summary`
- `body_load_summary`
- `contact_force_distribution_summary`
- `support_stability_summary`
- `dynamic_sequence_gate`

이 필드들은 운영 모니터링과 품질 게이트에 사용한다.

## 프레임별 `frames[]` 명세

각 프레임 객체는 아래 필드를 가져야 한다.

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `frame_index` | integer | Y | 원본 프레임 인덱스 |
| `timestamp_ms` | integer | Y | 프레임 시각 |
| `pose_mode` | string | Y | `fitted`, `frozen_glitch`, `frozen_missing` 등 |
| `phase` | string | Y | `static_support`, `loaded_transition`, `dynamic_transition`, `recovery` |
| `analysis_confidence` | string | Y | `high`, `medium`, `low` |
| `active_contact_limbs` | array<string> | Y | 현재 active support limb 목록 |
| `active_hold_ids` | object | Y | limb별 연결된 hold ID |
| `support_mode` | string | Y | `active_contacts` 또는 `fallback_all_limbs` |
| `support_stability` | object | Y | support polygon/line/point 기반 안정성 결과 |
| `joint_loads` | object | Y | 관절별 generalized force |
| `top_joint_loads` | array<object> | Y | 큰 관절 부하 상위 k개 |
| `body_loads` | object | Y | 코어/좌우 팔/좌우 다리 요약 부하 |
| `com_position_m` | array<number> | Y | `[x, y, z]` |
| `estimated_contact_forces_n` | object | Y | 손/발별 추정 반력 |
| `contact_force_status` | string | Y | `ok`, `high_residual`, `no_active_contacts` |
| `contact_force_relative_residual` | number \| null | Y | contact force residual |

## 핵심 출력 3종의 의미

### 1. `joint_loads`

- 관절별 generalized force
- 단위: MuJoCo qfrc_inverse 기준
- 서비스에서는 절대값보다 상대 비교와 구간 변화에 더 적합함

예:

```json
"joint_loads": {
  "abdomen_x": -2642.34,
  "hip_y_left": 41.25,
  "shoulder_shrug_right": 39.95
}
```

### 2. `com_position_m`

- 타입: `[x, y, z]`
- 단위: `m`
- personalized mass distribution을 반영한 CoM

예:

```json
"com_position_m": [0.071, -0.011, 1.227]
```

### 3. `estimated_contact_forces_n`

- limb별 추정 반력
- 현재 단계에서는 **proxy** 성격
- 절대 실측 반력이 아니라 상태 기반 force distribution 결과임

각 limb 객체:

| 필드 | 타입 | 필수 | 단위 | 설명 |
| --- | --- | --- | --- | --- |
| `mode` | string | Y | - | `GRIP` 또는 `STEP` |
| `position_xyz` | array<number> | Y | m | contact site 위치 |
| `force_xyz` | array<number> | Y | N | 추정 힘 벡터 |
| `force_norm_n` | number | Y | N | 힘 크기 |
| `normal_force_n` | number \| null | Y | N | `STEP`일 때 벽 법선 성분 |
| `tangential_force_n` | number \| null | Y | N | `STEP`일 때 접선 성분 |

예:

```json
"estimated_contact_forces_n": {
  "left_hand": {
    "mode": "GRIP",
    "position_xyz": [0.155, 0.557, 2.070],
    "force_xyz": [8.99, 23.80, 356.67],
    "force_norm_n": 357.58,
    "normal_force_n": null,
    "tangential_force_n": null
  },
  "left_foot": {
    "mode": "STEP",
    "position_xyz": [0.689, 0.130, 0.533],
    "force_xyz": [-43.67, 3.12, 34.80],
    "force_norm_n": 55.93,
    "normal_force_n": 43.67,
    "tangential_force_n": 34.94
  }
}
```

## 품질 해석 규칙

서비스에서 강하게 사용할 프레임 조건 권장:

1. `analysis_confidence = high`
2. `contact_force_status = ok`
3. `phase = static_support` 또는 `loaded_transition`

낮은 신뢰도로 봐야 하는 경우:

1. `pose_mode != fitted`
2. `phase = recovery`
3. `support_mode = fallback_all_limbs`
4. `contact_force_status = high_residual`
5. `support_stability.support_type = point_support`

## 예시

```json
{
  "frame_index": 437,
  "timestamp_ms": 14568,
  "pose_mode": "fitted",
  "phase": "static_support",
  "analysis_confidence": "high",
  "active_contact_limbs": ["left_hand", "right_hand", "left_foot", "right_foot"],
  "active_hold_ids": {
    "left_hand": 12,
    "right_hand": 21,
    "left_foot": 4,
    "right_foot": 7
  },
  "support_mode": "active_contacts",
  "support_stability": {
    "support_type": "quad_support",
    "inside_support": true,
    "stability_margin_m": 0.0948
  },
  "joint_loads": {
    "abdomen_x": -2642.34,
    "abdomen_y": 772.16,
    "abdomen_z": -377.49
  },
  "body_loads": {
    "core": 3806.35,
    "left_arm": 53.46,
    "right_arm": 55.87,
    "left_leg": 48.18,
    "right_leg": 77.52
  },
  "com_position_m": [0.071, -0.011, 1.227],
  "estimated_contact_forces_n": {
    "left_hand": {
      "mode": "GRIP",
      "position_xyz": [0.155, 0.557, 2.070],
      "force_xyz": [8.99, 23.80, 356.67],
      "force_norm_n": 357.58,
      "normal_force_n": null,
      "tangential_force_n": null
    }
  },
  "contact_force_status": "ok",
  "contact_force_relative_residual": 0.00047
}
```

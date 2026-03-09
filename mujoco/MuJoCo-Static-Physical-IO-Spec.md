# MuJoCo Static Physical IO Spec

## 목적

클라이밍 자세 1프레임에 대해 MuJoCo 기반 정적 물리 분석을 수행하고, 프론트엔드/백엔드와 주고받아야 할 입력/출력 데이터를 정의한다.

현재 스펙은 다음 범위를 기준으로 한다.

- 입력 자세: MediaPipe 33개 `pose_world_landmarks`
- 입력 접점: 손/발에 디텍트된 홀드 위치
- 분석 방식: `weld` 기반 정적 고정 + `mj_inverse`
- 출력 물리량: 관절 부하, 신체 부위별 부하, CoM, 손/발 반발력

## 범위와 주의사항

- 현재 반발력은 `실제 마찰(contact + friction)`이 아니라 `가상 홀드 anchor + weld` 기반 반력이다.
- 따라서 `reaction_forces`는 "현재 자세를 유지하기 위해 홀드에서 받아야 하는 반력"으로 해석해야 한다.
- `friction_loss`, `slip detection`, 실제 접촉 마찰 안정성은 아직 포함하지 않는다.

## 입력 데이터 정의

프론트 또는 백엔드가 MuJoCo 분석 서비스에 전달해야 하는 최소 입력 계약은 아래와 같다.

### Top-level Request

```json
{
  "analysis_mode": "static_posture",
  "request_id": "uuid-or-trace-id",
  "user_biometrics": {
    "height_m": 1.75,
    "weight_kg": 68.0
  },
  "pose_source": {
    "provider": "mediapipe",
    "coordinate_type": "pose_world_landmarks",
    "landmark_count": 33
  },
  "pose_world_landmarks": [
    { "x": 0.0, "y": 0.0, "z": 0.0 }
  ],
  "detected_hold_contacts": {
    "left_wrist": {
      "hold_id": "hold_lh_01",
      "position": { "x": 0.16, "y": 0.25, "z": 1.84 }
    },
    "right_wrist": {
      "hold_id": "hold_rh_02",
      "position": { "x": 0.31, "y": -0.25, "z": 1.66 }
    },
    "left_ankle": {
      "hold_id": "hold_lf_03",
      "position": { "x": 0.09, "y": 0.34, "z": 0.08 }
    },
    "right_ankle": {
      "hold_id": "hold_rf_04",
      "position": { "x": 0.03, "y": -0.57, "z": 0.04 }
    }
  },
  "calibration_ref": {
    "enabled": true,
    "calibration_id": "user-calibration-001"
  },
  "debug_options": {
    "return_pose_debug": true,
    "return_image_debug": false
  }
}
```

### 필드 설명

- `analysis_mode`
  - 현재 값: `static_posture`
  - 향후 `video_sequence`, `failure_analysis` 등으로 확장 가능
- `request_id`
  - 추적용 식별자
- `user_biometrics.height_m`
  - 현재 필수
  - 자세 스케일링 및 개인화 캘리브레이션 참조용
- `user_biometrics.weight_kg`
  - 현재 선택
  - 향후 질량 분포 추정 개선용
- `pose_source`
  - 포즈 공급자와 좌표계 메타 정보
- `pose_world_landmarks`
  - MediaPipe 33개 3D 점
  - 각 점은 `{x, y, z}` 구조
  - 단위는 MediaPipe world 좌표계 기준
- `detected_hold_contacts`
  - 현재 프론트/백엔드가 "이 손/발이 어떤 홀드에 붙어 있다"고 판단한 결과
  - 키는 `left_wrist`, `right_wrist`, `left_ankle`, `right_ankle`
  - 각 접점은 `hold_id`와 3D `position` 포함
- `calibration_ref`
  - 사용자 개인화 비율/길이 보정 사용 여부
- `debug_options`
  - 응답에서 디버그 정보 포함 여부

### 입력 필수/선택 요약

- 필수
  - `analysis_mode`
  - `user_biometrics.height_m`
  - `pose_world_landmarks`
  - `detected_hold_contacts.left_wrist`
  - `detected_hold_contacts.right_wrist`
  - `detected_hold_contacts.left_ankle`
  - `detected_hold_contacts.right_ankle`
- 선택
  - `request_id`
  - `user_biometrics.weight_kg`
  - `calibration_ref`
  - `debug_options`

## 출력 데이터 정의

### Top-level Response

```json
{
  "request_id": "uuid-or-trace-id",
  "analysis_mode": "static_posture",
  "summary_metrics": {
    "total_body_stress_avg": 44.8,
    "com_stability": 0.0196
  },
  "body_part_loads": {
    "right_arm": {},
    "left_arm": {},
    "right_leg": {},
    "left_leg": {},
    "core": {}
  },
  "detailed_joint_loads": [],
  "joint_torques": [],
  "reaction_forces": [],
  "com_position": [0.0, 0.0, 0.0],
  "support_center_position": [0.0, 0.0, 0.0],
  "active_contact_points": [],
  "com_stability_margin_m": 0.0,
  "reach_errors": [],
  "pose_debug": {},
  "image_debug": {},
  "meta": {}
}
```

## 출력 필드 상세

### `summary_metrics`

- `total_body_stress_avg`
  - 전체 관절 load percentage 평균
  - 단위: `%`
- `com_stability`
  - 현재 구현은 `com_stability_margin_m`와 동일 의미
  - CoM과 활성 접점 중심 간 거리
  - 단위: `m`

### `body_part_loads`

신체 부위별 집계 결과.

- 그룹
  - `right_arm`
  - `left_arm`
  - `right_leg`
  - `left_leg`
  - `core`
- 각 그룹 필드
  - `avg_load_percentage`
  - `max_load_percentage`
  - `peak_joint`
  - `joint_ids`

예시:

```json
{
  "left_arm": {
    "avg_load_percentage": 79.05,
    "max_load_percentage": 100.0,
    "peak_joint": {
      "joint_id": "elbow_left",
      "load_percentage": 100.0,
      "torque": 120.0,
      "torque_limit": 120.0
    },
    "joint_ids": [
      "shoulder1_left",
      "shoulder2_left",
      "shoulder3_left",
      "elbow_left"
    ]
  }
}
```

### `detailed_joint_loads`

각 관절별 상세 부하.

각 항목 필드:

- `joint_id`
- `torque`
- `torque_limit`
- `load_percentage`

프론트엔드에서는 상위 3개 강조 표시, 백엔드에서는 전량 저장 권장.

### `joint_torques`

- `qfrc_inverse` 기반 관절 토크 원본
- 각 항목: `joint_id`, `torque`

### `reaction_forces`

손/발 4개 접점에 대한 반력.

각 항목 필드:

- `limb_id`
  - `left_wrist`, `right_wrist`, `left_ankle`, `right_ankle`
- `body_name`
- `force_vector`
  - 3D 힘 벡터
  - 단위: `N`
- `force_magnitude_n`
- `torque_vector`
  - 3D 모멘트
  - 단위: `N·m`
- `torque_magnitude_nm`
- `anchor_position`
- `mocap_target`
- `reach_error_m`

주의:

- 현재 `reaction_forces`는 `weld` 기반 anchor 반력이다.
- 실제 마찰력 그 자체는 아니다.

### `com_position`

- 전신 CoM 위치 `[x, y, z]`
- 단위: `m`

### `support_center_position`

- 활성 손/발 접점 4개의 중심 위치 `[x, y, z]`
- 단위: `m`

### `active_contact_points`

- 현재 고정된 손/발 목표 위치 목록
- 단위: `m`

### `com_stability_margin_m`

- CoM과 접점 중심 사이 거리
- 현재는 접점 평면 기준 거리
- 단위: `m`
- 값이 작을수록 접점 중심에 가깝다

### `reach_errors`

- 손/발 target과 실제 MuJoCo limb body 위치 차이
- 각 항목: `anchor_id`, `body_name`, `reach_error_m`

### `pose_debug`

백엔드 검증/개발용 디버그 데이터.

- `mapped_world_points`
- `ik_mapped_world_points`
- `effector_targets_world`
- `analytical_body_positions`
- `post_ik_body_positions`
- `joint_targets_analytical_rad`
- `joint_qpos_post_ik_rad`

운영 단계에서는 응답에서 제외 가능.

### `image_debug`

이미지 reprojection 비교용 디버그 데이터.

- `mediapipe_pixels`
- `target_projected_pixels`
- `post_ik_projected_pixels`
- 각종 픽셀 오차

운영 단계에서는 응답에서 제외 가능.

### `meta`

실행 메타 정보.

- `xml_path`
- `calibration_json`
- `analysis_joints`
- `scale_factor`
- `ik_stats`
- `weld_active_limbs`
- `constraint_generalized_force_norm`
- `constraint_generalized_force_max`
- `gravity`

## 프론트엔드 권장 사용 필드

프론트는 아래 필드 위주로 사용 권장:

- `summary_metrics.total_body_stress_avg`
- `summary_metrics.com_stability`
- `body_part_loads`
- `detailed_joint_loads` 상위 3개
- `reaction_forces`의 `force_magnitude_n`
- `com_position`
- `com_stability_margin_m`

권장 UI 예시:

- 좌/우 팔, 좌/우 다리, 코어 부하 막대
- 상위 3개 과부하 관절 배지
- 손/발 반발력 숫자 또는 막대
- CoM 위치와 support center 상대 표시

## 백엔드 권장 저장 필드

백엔드는 아래 필드를 원본 보존 권장:

- 전체 요청 입력 JSON
- `summary_metrics`
- `body_part_loads`
- `detailed_joint_loads`
- `reaction_forces`
- `reach_errors`
- `com_position`
- `com_stability_margin_m`
- `meta`

## 현재 구현 기준 파일

- 정적 분석 스크립트
  - `mujoco/static_load_extract/static_posture_physics.py`
- 공용 물리 유틸
  - `mujoco/static_load_extract/physics_worker.py`
- 샘플 입력
  - `mujoco/static_load_extract/sample_pose_world.json`
- 샘플 출력
  - `mujoco/static_load_extract/static_posture_analysis.json`

## 향후 확장 예정

- 실제 contact + friction 기반 `friction_loss` 분석
- 동영상 시퀀스 기반 `t_fail` 검출
- 실패 원인 분류
  - `STRENGTH_LIMIT`
  - `FRICTION_LOSS`
  - `BALANCE_DISRUPTION`
- 서비스용 경량 응답 포맷과 디버그 응답 포맷 분리

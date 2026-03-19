# User Body JSON Spec

파일 목적:

- 사용자의 정적 신체 치수와 질량 정보를 전달한다.
- personalized human model 생성의 기본 입력으로 사용한다.

파일명 권장:

- `user_body.json`

스키마 버전:

- `1.0.0`

## 최상위 구조

```json
{
  "schema_version": "1.0.0",
  "source": {
    "type": "t_pose_image_estimation",
    "image_path": "string",
    "task_model_path": "string"
  },
  "user_profile": {
    "height_m": 1.75,
    "height_cm": 175.0,
    "weight_kg": 80.0
  },
  "static_biometrics": {},
  "calibration_compat": {},
  "pixel_lengths": {},
  "landmarks_px": {},
  "world_landmarks_sample": {}
}
```

## 필드 명세

### 1. `schema_version`

- 타입: `string`
- 필수: `Y`
- 고정값: `1.0.0`

### 2. `source`

- 타입: `object`
- 필수: `Y`

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `type` | string | Y | 현재 권장값은 `t_pose_image_estimation` |
| `image_path` | string | N | T-pose 이미지 경로 |
| `task_model_path` | string | N | 사용한 MediaPipe task model 경로 |

### 3. `user_profile`

- 타입: `object`
- 필수: `Y`

| 필드 | 타입 | 필수 | 단위 | 설명 |
| --- | --- | --- | --- | --- |
| `height_m` | number | Y | m | 사용자 키 |
| `height_cm` | number | Y | cm | 사용자 키 |
| `weight_kg` | number | Y | kg | 사용자 몸무게 |

### 4. `static_biometrics`

- 타입: `object`
- 필수: `Y`

| 필드 | 타입 | 필수 | 단위 | 설명 |
| --- | --- | --- | --- | --- |
| `left_upper_arm_m` | number | Y | m | 왼쪽 상완 길이 |
| `right_upper_arm_m` | number | Y | m | 오른쪽 상완 길이 |
| `left_forearm_m` | number | Y | m | 왼쪽 전완 길이 |
| `right_forearm_m` | number | Y | m | 오른쪽 전완 길이 |
| `left_thigh_m` | number | Y | m | 왼쪽 대퇴 길이 |
| `right_thigh_m` | number | Y | m | 오른쪽 대퇴 길이 |
| `left_shin_m` | number | Y | m | 왼쪽 하퇴 길이 |
| `right_shin_m` | number | Y | m | 오른쪽 하퇴 길이 |
| `shoulder_width_m` | number | Y | m | 어깨 너비 |
| `hip_width_m` | number | Y | m | 골반 너비 |
| `torso_length_m` | number | Y | m | 몸통 길이 |
| `wingspan_raw_m` | number | N | m | 원시 윙스팬 |
| `wingspan_extra_m` | number | N | m | 추가 보정치 |
| `wingspan_m` | number | N | m | 보정 적용 윙스팬 |
| `scale_m_per_px` | number | N | m/px | 이미지 스케일 |

### 5. `calibration_compat`

- 타입: `object`
- 필수: `Y`
- 설명:
  - 기존 MuJoCo 파이프라인이 바로 재사용할 수 있는 호환 필드 모음

필수 권장 필드:

| 필드 | 타입 | 필수 | 단위 | 설명 |
| --- | --- | --- | --- | --- |
| `body_mass_kg` | number | Y | kg | 사용자 몸무게 |
| `upper_arm_m` | number | Y | m | 좌우 평균 상완 길이 |
| `forearm_m` | number | Y | m | 좌우 평균 전완 길이 |
| `thigh_m` | number | Y | m | 좌우 평균 대퇴 길이 |
| `shin_m` | number | Y | m | 좌우 평균 하퇴 길이 |
| `shoulder_width_m` | number | Y | m | 어깨 너비 |
| `hip_width_m` | number | Y | m | 골반 너비 |
| `torso_length_m` | number | Y | m | 몸통 길이 |

좌우 개별 필드도 함께 포함하는 것을 권장한다.

### 6. `pixel_lengths`, `landmarks_px`, `world_landmarks_sample`

- 타입: `object`
- 필수: `N`
- 설명:
  - 디버그와 추적용 부가 정보
  - 서비스 필수 입력은 아니지만, 재현성과 검증에 유용하다

## 검증 규칙

1. `height_m > 0`, `weight_kg > 0`
2. 모든 길이 필드는 `> 0`
3. 좌우 길이가 과도하게 다르면 재검토 필요
4. `height_m`와 `height_cm`는 일관되어야 한다

권장 범위 체크:

- `torso_length_m`: 대체로 `0.40 ~ 0.80m`
- `shoulder_width_m`: 대체로 `0.25 ~ 0.60m`
- `upper_arm_m`, `forearm_m`, `thigh_m`, `shin_m`: 모두 양수

## 예시

```json
{
  "schema_version": "1.0.0",
  "source": {
    "type": "t_pose_image_estimation",
    "image_path": "C:/project/mujoco/video/fullbody_dg.png",
    "task_model_path": "C:/project/mujoco/custom_skeleton_verify/pose_landmarker_lite.task"
  },
  "user_profile": {
    "height_m": 1.75,
    "height_cm": 175.0,
    "weight_kg": 80.0
  },
  "static_biometrics": {
    "left_upper_arm_m": 0.249,
    "right_upper_arm_m": 0.249,
    "left_forearm_m": 0.260,
    "right_forearm_m": 0.260,
    "left_thigh_m": 0.397,
    "right_thigh_m": 0.397,
    "left_shin_m": 0.353,
    "right_shin_m": 0.353,
    "shoulder_width_m": 0.386,
    "hip_width_m": 0.200,
    "torso_length_m": 0.556
  },
  "calibration_compat": {
    "body_mass_kg": 80.0,
    "upper_arm_m": 0.249,
    "forearm_m": 0.260,
    "thigh_m": 0.397,
    "shin_m": 0.353,
    "shoulder_width_m": 0.386,
    "hip_width_m": 0.200,
    "torso_length_m": 0.556
  }
}
```

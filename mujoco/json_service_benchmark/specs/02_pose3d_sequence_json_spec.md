# Pose3D Sequence JSON Spec

파일 목적:

- 영상 전체 프레임의 MediaPipe Pose 3D 시계열을 전달한다.
- 런타임 서버는 이 파일만으로 자세 fitting, 동적 inverse dynamics, contact-aware force 추정을 수행한다.

파일명 권장:

- `pose3d_sequence.json`

스키마 버전:

- `1.0.0`

## 최상위 구조

```json
{
  "schema_version": "1.0.0",
  "source": {
    "type": "video_mediapipe_pose_landmarker_lite",
    "video_path": "string",
    "task_model_path": "string"
  },
  "video_metadata": {
    "frame_width": 1080,
    "frame_height": 1920,
    "fps": 30.0,
    "total_frames": 964,
    "duration_ms": 32137,
    "frame_step": 1,
    "processed_frames": 964
  },
  "frames": []
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
| `type` | string | Y | 현재 권장값은 `video_mediapipe_pose_landmarker_lite` |
| `video_path` | string | N | 원본 영상 경로 |
| `task_model_path` | string | N | MediaPipe task model 경로 |

### 3. `video_metadata`

- 타입: `object`
- 필수: `Y`

| 필드 | 타입 | 필수 | 단위 | 설명 |
| --- | --- | --- | --- | --- |
| `frame_width` | integer | Y | px | 영상 가로 크기 |
| `frame_height` | integer | Y | px | 영상 세로 크기 |
| `fps` | number | Y | frame/s | 영상 fps |
| `total_frames` | integer | Y | frame | 원본 영상 전체 프레임 수 |
| `duration_ms` | integer | Y | ms | 원본 영상 전체 길이 |
| `frame_step` | integer | Y | frame | 추출 시 사용한 프레임 간격 |
| `processed_frames` | integer | Y | frame | 실제 저장된 프레임 수 |

### 4. `frames`

- 타입: `array<object>`
- 필수: `Y`
- 순서: `frame_index` 오름차순

각 프레임 객체:

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `frame_index` | integer | Y | 원본 영상 기준 프레임 인덱스 |
| `timestamp_ms` | integer | Y | 해당 프레임 시각 |
| `pose_detected` | boolean | Y | Pose 검출 성공 여부 |
| `pose_world_landmarks` | array \| null | 조건부 | `pose_detected=true`이면 33개 필수 |
| `pose_landmarks` | array \| null | 조건부 | `pose_detected=true`이면 33개 필수 |

## 랜드마크 객체 명세

### 1. `pose_world_landmarks[i]`

- 타입: `object`
- 길이: 정확히 `33`
- 단위: MediaPipe world coordinate

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `x` | number | Y | world x |
| `y` | number | Y | world y |
| `z` | number | Y | world z |
| `visibility` | number | N | landmark visibility |
| `presence` | number | N | landmark presence |

### 2. `pose_landmarks[i]`

- 타입: `object`
- 길이: 정확히 `33`
- 단위: normalized image coordinate

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `x` | number | Y | 0..1 normalized x |
| `y` | number | Y | 0..1 normalized y |
| `z` | number | Y | relative depth |
| `visibility` | number | N | landmark visibility |
| `presence` | number | N | landmark presence |

## 중요한 규칙

1. `pose_detected=false`이면 `pose_world_landmarks`와 `pose_landmarks`는 `null`이어야 한다.
2. `pose_detected=true`이면 두 배열은 모두 **33개 landmark**를 가져야 한다.
3. landmark 순서는 **MediaPipe Pose 33-landmark 인덱스 순서**를 그대로 사용해야 한다.
4. `pose_landmarks`는 반드시 원본 영상 프레임 크기와 같은 좌표계에 대응해야 한다.
5. `timestamp_ms`는 오름차순이어야 한다.

## MediaPipe 33 인덱스

| Index | Name |
| --- | --- |
| 0 | nose |
| 1 | left_eye_inner |
| 2 | left_eye |
| 3 | left_eye_outer |
| 4 | right_eye_inner |
| 5 | right_eye |
| 6 | right_eye_outer |
| 7 | left_ear |
| 8 | right_ear |
| 9 | mouth_left |
| 10 | mouth_right |
| 11 | left_shoulder |
| 12 | right_shoulder |
| 13 | left_elbow |
| 14 | right_elbow |
| 15 | left_wrist |
| 16 | right_wrist |
| 17 | left_pinky |
| 18 | right_pinky |
| 19 | left_index |
| 20 | right_index |
| 21 | left_thumb |
| 22 | right_thumb |
| 23 | left_hip |
| 24 | right_hip |
| 25 | left_knee |
| 26 | right_knee |
| 27 | left_ankle |
| 28 | right_ankle |
| 29 | left_heel |
| 30 | right_heel |
| 31 | left_foot_index |
| 32 | right_foot_index |

## 예시

```json
{
  "schema_version": "1.0.0",
  "source": {
    "type": "video_mediapipe_pose_landmarker_lite",
    "video_path": "C:/project/mujoco/video/주황.mp4",
    "task_model_path": "C:/project/mujoco/custom_skeleton_verify/pose_landmarker_lite.task"
  },
  "video_metadata": {
    "frame_width": 1080,
    "frame_height": 1920,
    "fps": 29.998,
    "total_frames": 964,
    "duration_ms": 32137,
    "frame_step": 1,
    "processed_frames": 964
  },
  "frames": [
    {
      "frame_index": 0,
      "timestamp_ms": 0,
      "pose_detected": true,
      "pose_world_landmarks": [
        { "x": 0.02, "y": -0.73, "z": -0.18 }
      ],
      "pose_landmarks": [
        { "x": 0.50, "y": 0.11, "z": -0.21 }
      ]
    }
  ]
}
```

주의:

- 위 예시는 설명용이라 landmark를 1개만 적었고, 실제 파일은 항상 33개여야 한다.

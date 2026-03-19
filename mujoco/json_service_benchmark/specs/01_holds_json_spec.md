# Holds JSON Spec

파일 목적:

- 클라이밍 벽의 홀드 위치를 영상 프레임 좌표계 기준으로 전달한다.
- 손 `GRIP`, 발 `STEP` 상태 판정의 기준 입력으로 사용한다.

파일명 권장:

- `holds.json`

스키마 버전:

- `1.0.0`

## 최상위 구조

```json
{
  "schema_version": "1.0.0",
  "source": {
    "type": "detections_json",
    "path": "string",
    "legacy_source_file": "string|null"
  },
  "video_metadata": {
    "video_path": "string",
    "frame_width": 1080,
    "frame_height": 1920
  },
  "holds": []
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
| `type` | string | Y | 현재 권장값은 `detections_json` |
| `path` | string | Y | 원본 홀드 검출 파일 경로 |
| `legacy_source_file` | string \| null | N | 기존 검출 파일이 참조하던 이미지/비디오 이름 |

### 3. `video_metadata`

- 타입: `object`
- 필수: `Y`

| 필드 | 타입 | 필수 | 단위 | 설명 |
| --- | --- | --- | --- | --- |
| `video_path` | string | N | - | 기준 영상 경로 |
| `frame_width` | integer | Y | px | 기준 영상 가로 크기 |
| `frame_height` | integer | Y | px | 기준 영상 세로 크기 |

### 4. `holds`

- 타입: `array<object>`
- 필수: `Y`
- 최소 길이: `1`

각 홀드 객체:

| 필드 | 타입 | 필수 | 단위 | 설명 |
| --- | --- | --- | --- | --- |
| `hold_id` | integer | Y | - | 홀드 고유 ID |
| `bbox_px.x1` | number | Y | px | 왼쪽 경계 |
| `bbox_px.y1` | number | Y | px | 위쪽 경계 |
| `bbox_px.x2` | number | Y | px | 오른쪽 경계 |
| `bbox_px.y2` | number | Y | px | 아래쪽 경계 |
| `center_px.x` | number | Y | px | 홀드 중심 x |
| `center_px.y` | number | Y | px | 홀드 중심 y |
| `radius_px` | number | Y | px | 홀드 접근/진입 판정용 반경 |
| `confidence` | number | N | - | 홀드 검출 confidence |

## 좌표계 규칙

- 모든 `bbox_px`, `center_px`, `radius_px`는 **원본 영상 프레임 픽셀 좌표계**를 사용한다.
- 이 좌표계는 `pose3d_sequence.json.video_metadata.frame_width`, `frame_height`와 정확히 일치해야 한다.
- 좌상단이 원점 `(0, 0)`이다.
- `x`는 오른쪽으로 증가, `y`는 아래쪽으로 증가한다.

## 검증 규칙

필수 검증:

1. `bbox_px.x1 < bbox_px.x2`
2. `bbox_px.y1 < bbox_px.y2`
3. `center_px.x`, `center_px.y`가 bbox 내부 또는 근처여야 한다
4. `radius_px > 0`
5. 모든 좌표는 `0 <= x <= frame_width`, `0 <= y <= frame_height` 범위여야 한다

권장 검증:

1. `hold_id`는 파일 내에서 유일해야 한다
2. bbox와 중심 좌표가 같은 벽/같은 영상 기준인지 확인해야 한다

## 예시

```json
{
  "schema_version": "1.0.0",
  "source": {
    "type": "detections_json",
    "path": "C:/project/mujoco/detections.json",
    "legacy_source_file": "orange_wall.png"
  },
  "video_metadata": {
    "video_path": "C:/project/mujoco/video/주황.mp4",
    "frame_width": 1080,
    "frame_height": 1920
  },
  "holds": [
    {
      "hold_id": 21,
      "bbox_px": {
        "x1": 402.0,
        "y1": 296.0,
        "x2": 468.0,
        "y2": 365.0
      },
      "center_px": {
        "x": 435.0,
        "y": 330.5
      },
      "radius_px": 29.7,
      "confidence": 0.93
    }
  ]
}
```

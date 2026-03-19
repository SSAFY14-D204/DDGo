# MuJoCo Complete 서버 연동 가이드

## 1. 목적

이 문서는 `DDgo_AI_Server` 안에 추가한 **MuJoCo Complete 기능**이
무엇을 하는지, 어떤 파일이 추가/변경되었는지, 어떤 구조로 동작하는지를
한글로 정리한 문서입니다.

현재 목표는 다음과 같습니다.

- 안드로이드에서 **입력 JSON 3개**
  - `holds_json`
  - `pose3d_sequence_json`
  - `user_body_json`
  를 보냄
- AI 서버는 서버 내부에서
  - 포즈 보정
  - grip/step 판정
  - MuJoCo fitting
  - inverse dynamics
  - 크럭스 검출
  을 수행함
- 서버는 **출력 JSON 1개**를 반환함

중요한 점:

- 이제 MuJoCo 분석 로직은 **외부 `mujoco` 폴더에 의존하지 않고**
  `DDgo_AI_Server` 내부 런타임 파일만 사용하도록 정리했습니다.
- 즉 EC2에 `DDgo_AI_Server`만 올려도 동작할 수 있게 구조를 바꾼 상태입니다.

---

## 2. 이번 작업에서 변경된 내용

### 2-1. 새로 추가된 API

- `POST /api/v1/mujoco-complete/analyze/fast`
- `POST /api/v1/mujoco-complete/analyze/physics`

의미:

- `fast`
  - 포즈 보정
  - polygon grip/step
  - 체류 시간 기반 빠른 크럭스 후보
- `physics`
  - 포즈 보정
  - MuJoCo 기반 전체 물리 분석
  - 물리량 기반 크럭스 후보

---

## 3. 추가된 파일

### 3-1. FastAPI 진입 파일

- [app/api/mujoco_complete.py](C:/ssafy/project-2/S14P21D204/DDgo_AI_Server/app/api/mujoco_complete.py)
  - `fast`, `physics` endpoint 정의

### 3-2. 요청 스키마

- [app/schemas/mujoco_complete.py](C:/ssafy/project-2/S14P21D204/DDgo_AI_Server/app/schemas/mujoco_complete.py)
  - 입력 JSON 3종과 옵션 파라미터 검증

### 3-3. 서비스 본체

- [app/services/mujoco_complete/service.py](C:/ssafy/project-2/S14P21D204/DDgo_AI_Server/app/services/mujoco_complete/service.py)
  - FastAPI endpoint가 실제로 호출하는 서비스
  - 빠른 분석 / 물리 분석 진입점
  - 홀드 JSON in-memory 파싱

### 3-4. 서비스 패키지 초기화

- [app/services/mujoco_complete/__init__.py](C:/ssafy/project-2/S14P21D204/DDgo_AI_Server/app/services/mujoco_complete/__init__.py)

### 3-5. 서버 내부 런타임 세트

아래 파일들은 원래 `mujoco` 폴더에 있던 로직 중,
실서비스에 필요한 파일만 `DDgo_AI_Server` 내부로 복사해 온 것입니다.

- [runtime/json_service_benchmark/run_json_service_benchmark.py](C:/ssafy/project-2/S14P21D204/DDgo_AI_Server/app/services/mujoco_complete/runtime/json_service_benchmark/run_json_service_benchmark.py)
- [runtime/json_service_benchmark/crux_detection.py](C:/ssafy/project-2/S14P21D204/DDgo_AI_Server/app/services/mujoco_complete/runtime/json_service_benchmark/crux_detection.py)
- [runtime/json_service_benchmark/polygon_hold_contact_state.py](C:/ssafy/project-2/S14P21D204/DDgo_AI_Server/app/services/mujoco_complete/runtime/json_service_benchmark/polygon_hold_contact_state.py)
- [runtime/json_service_benchmark/pose_sequence_correction.py](C:/ssafy/project-2/S14P21D204/DDgo_AI_Server/app/services/mujoco_complete/runtime/json_service_benchmark/pose_sequence_correction.py)
- [runtime/custom_articulated_human/evaluate_static_fit.py](C:/ssafy/project-2/S14P21D204/DDgo_AI_Server/app/services/mujoco_complete/runtime/custom_articulated_human/evaluate_static_fit.py)
- [runtime/custom_articulated_human/personalize_articulated_model.py](C:/ssafy/project-2/S14P21D204/DDgo_AI_Server/app/services/mujoco_complete/runtime/custom_articulated_human/personalize_articulated_model.py)
- [runtime/custom_articulated_human/hold_contact_state.py](C:/ssafy/project-2/S14P21D204/DDgo_AI_Server/app/services/mujoco_complete/runtime/custom_articulated_human/hold_contact_state.py)
- [runtime/custom_articulated_human/support_stability.py](C:/ssafy/project-2/S14P21D204/DDgo_AI_Server/app/services/mujoco_complete/runtime/custom_articulated_human/support_stability.py)
- [runtime/custom_articulated_human/custom_articulated_human.xml](C:/ssafy/project-2/S14P21D204/DDgo_AI_Server/app/services/mujoco_complete/runtime/custom_articulated_human/custom_articulated_human.xml)
- [runtime/custom_skeleton_verify/mediapipe_custom_skeleton_verify.py](C:/ssafy/project-2/S14P21D204/DDgo_AI_Server/app/services/mujoco_complete/runtime/custom_skeleton_verify/mediapipe_custom_skeleton_verify.py)
- [runtime/dynamic_sequence_pipeline/contact_force_distribution.py](C:/ssafy/project-2/S14P21D204/DDgo_AI_Server/app/services/mujoco_complete/runtime/dynamic_sequence_pipeline/contact_force_distribution.py)
- [runtime/dynamic_sequence_pipeline/run_dynamic_sequence_analysis.py](C:/ssafy/project-2/S14P21D204/DDgo_AI_Server/app/services/mujoco_complete/runtime/dynamic_sequence_pipeline/run_dynamic_sequence_analysis.py)

### 3-6. 기타

- [app/api/router.py](C:/ssafy/project-2/S14P21D204/DDgo_AI_Server/app/api/router.py)
  - 새 라우터 등록
- [.gitignore](C:/ssafy/project-2/S14P21D204/DDgo_AI_Server/.gitignore)
  - 런타임 cache, pycache 무시

---

## 4. 변경된 구조

### 4-1. 최종 구조

```text
DDgo_AI_Server/
├─ app/
│  ├─ api/
│  │  ├─ router.py
│  │  └─ mujoco_complete.py
│  ├─ schemas/
│  │  └─ mujoco_complete.py
│  └─ services/
│     └─ mujoco_complete/
│        ├─ __init__.py
│        ├─ service.py
│        └─ runtime/
│           ├─ json_service_benchmark/
│           ├─ custom_articulated_human/
│           ├─ custom_skeleton_verify/
│           └─ dynamic_sequence_pipeline/
├─ physics_worker.py
├─ requirements.txt
└─ MUJOCO_COMPLETE_GUIDE.md
```

### 4-2. 구조 의도

- FastAPI는 `app/api`에서 요청을 받음
- 스키마 검증은 `app/schemas`
- 실제 MuJoCo Complete 진입은 `app/services/mujoco_complete/service.py`
- 실질적인 런타임 계산 코드는 `runtime/` 아래에 모두 모음

즉:

- API 레이어
- 서비스 레이어
- MuJoCo 런타임 레이어

로 나눠 둔 구조입니다.

---

## 5. API 입력 구조

## 5-1. 요청 본문

```json
{
  "holds_json": {
    "holds": []
  },
  "pose3d_sequence_json": {
    "video_metadata": {
      "frame_width": 1080,
      "frame_height": 1920,
      "fps": 30.0,
      "total_frames": 964
    },
    "frames": []
  },
  "user_body_json": {
    "user_profile": {
      "height_m": 1.75,
      "weight_kg": 80.0
    },
    "calibration_compat": {}
  },
  "top_k_crux": 3,
  "frame_step": 2
}
```

### `holds_json`

- route에서 사용하는 홀드 정보
- polygon 형태 지원
- 두 형식 모두 지원
  - 준비된 `holds`
  - raw segmentation `predictions`

### `pose3d_sequence_json`

- 원본 MediaPipe 3D 시계열
- 필수 정보
  - `video_metadata`
  - `frames`
  - 프레임별 `frame_index`, `timestamp_ms`
  - `pose_landmarks`
  - `pose_world_landmarks`
- 권장 정보
  - `visibility`
  - `presence`

### `user_body_json`

- 사용자 키/몸무게
- `calibration_compat` 필수

---

## 6. API 출력 구조

### 6-1. fast 응답

- 포즈 보정 시간
- 홀드 추적 시간
- 크럭스 스코어링 시간
- 홀드 상태 요약
- 빠른 크럭스 후보 top-k

### 6-2. physics 응답

- 포즈 보정 시간
- 물리 파이프라인 시간
- 크럭스 스코어링 시간
- 보정 요약
- 물리 분석 요약
- 전체 `physics_result`
- 물리 기반 크럭스 후보 top-k

---

## 7. 내부 동작 흐름

### 7-1. fast

1. raw pose JSON 수신
2. `pose_sequence_correction.py`로 보정
3. polygon grip/step 판정
4. 체류 시간 기반 크럭스 후보 계산
5. JSON 반환

### 7-2. physics

1. raw pose JSON 수신
2. `pose_sequence_correction.py`로 보정
3. personalized MuJoCo model 준비
4. 전체 fitting
5. inverse dynamics / CoM / support / 추정 반력 계산
6. 물리 기반 크럭스 후보 계산
7. JSON 반환

---

## 8. 로컬 검증 결과

직접 서비스 메서드 호출로 확인했습니다.

### fast

- `analyze_fast(...)`
- `frame_step=2`
- 총 시간 약 `1.66초`

### physics

- `analyze_physics(...)`
- `frame_step=2`
- 총 시간 약 `9.30초`
- 물리 파이프라인 시간 약 `8.63초`

즉:

- 빠른 크럭스 응답은 매우 빠르게 가능
- 물리 기반 응답도 현재 로컬 기준으로 수 초대 처리 가능

---

## 9. 중요한 결정 사항

### 9-1. 외부 `mujoco` 폴더 의존 제거

이전에는 `DDgo_AI_Server`가 외부 `mujoco` 폴더를 참조했습니다.

지금은:

- 필요한 런타임 파일을 `DDgo_AI_Server/app/services/mujoco_complete/runtime`으로 복사
- 서비스는 이 내부 파일만 사용

하도록 바꿨습니다.

즉 EC2에는 `DDgo_AI_Server`만 올려도 됩니다.

### 9-2. 기본 예시 파일은 살려둘 필요 없음

처음 있던 기본 서버 파일들은 구조 예시용이라고 이해했습니다.
현재는 그 위에 MuJoCo Complete 기능을 **추가**한 상태입니다.

필요하면 다음 단계에서

- 기존 예시 endpoint 제거
- MuJoCo Complete 중심으로 서버 정리

까지 진행할 수 있습니다.

---

## 10. 실행 방법

```bash
cd DDgo_AI_Server
pip install -r requirements.txt
uvicorn app.main:app --reload
```

Swagger:

- `http://localhost:8000/docs`

---

## 11. 다음 추천 작업

1. FastAPI 환경 의존성 설치
2. `/docs`에서 실제 요청 검증
3. 안드로이드 요청/응답 형식과 1:1 매핑 확인
4. 필요하면 기존 예시 API 제거 및 서버 구조 정리

# DDGO MuJoCo Complete Server

## 개요

안드로이드 앱에서 전달한 JSON을 입력받아 다음 작업을 수행하는 FastAPI 서버입니다.

- 홀드 좌표 기반 grip/step 판정
- MediaPipe 3D 시계열 보정
- MuJoCo 기반 물리 분석
- 크럭스 후보 계산
- 실시간 세션 기반 분석 수집 및 최종 분석

주요 입력은 아래 3종입니다.

1. `holds_json`
2. `pose3d_sequence_json`
3. `user_body_json`

## 경로 구조

이 서버는 내부 라우팅과 외부 공개 경로가 다릅니다.

- 내부 FastAPI 라우트:
  - `GET /health`
  - `POST /api/v1/...`
- 외부 공개 경로:
  - `GET /ai/health`
  - `POST /ai/api/v1/...`

`Dockerfile` 에서 Uvicorn을 `--root-path /ai` 로 실행하므로, 운영 환경에서는 `/ai` 프리픽스를 포함한 URL로 접근해야 합니다.

## 제공 API

### 배치 분석 API

- `POST /api/v1/mujoco-complete/analyze/fast`
- `POST /api/v1/mujoco-complete/analyze/physics`

### 실시간 분석 API

- `POST /api/v1/mujoco-complete/session/start`
- `POST /api/v1/mujoco-complete/session/{session_id}/pose-chunks`
- `POST /api/v1/mujoco-complete/session/{session_id}/context`
- `POST /api/v1/mujoco-complete/session/{session_id}/finalize`
- `DELETE /api/v1/mujoco-complete/session/{session_id}`

운영 프록시를 통과하는 실제 공개 URL은 모두 `/ai` 가 앞에 붙습니다.

예시:

- `POST /ai/api/v1/mujoco-complete/analyze/fast`
- `POST /ai/api/v1/mujoco-complete/session/start`

## 로컬 실행

```bash
cd DDgo_AI_Server
pip install -r requirements.txt
uvicorn app.main:app --reload
```

로컬 문서:

- `http://localhost:8000/docs`
- `http://localhost:8000/openapi.json`

## 배포 검증

운영 배포 후 아래 항목을 반드시 확인합니다.

```bash
curl -f https://j14d204.p.ssafy.io/ai/health
curl -f https://j14d204.p.ssafy.io/ai/openapi.json
curl -s -o /dev/null -w '%{http_code}' \
  -H 'Content-Type: application/json' \
  -X POST \
  https://j14d204.p.ssafy.io/ai/api/v1/mujoco-complete/analyze/fast \
  --data '{}'
curl -s -o /dev/null -w '%{http_code}' \
  -H 'Content-Type: application/json' \
  -X POST \
  https://j14d204.p.ssafy.io/ai/api/v1/mujoco-complete/session/start \
  --data '{}'
```

기대 결과:

- `/ai/health` -> `200`
- `/ai/openapi.json` -> `/api/v1/mujoco-complete/session/start` 포함
- `analyze/fast` 빈 요청 -> `422`
- `session/start` 빈 요청 -> `422`

## 로컬 검증 스크립트

`validate_fastapi_live.py` 는 서버를 띄운 뒤 아래를 함께 확인합니다.

- Swagger 문서 응답
- 헬스 체크 응답
- 배치 분석 API 실호출
- OpenAPI 에 실시간 세션 시작 라우트 포함 여부
- `session/start` 빈 요청 시 `404` 가 아닌 `422` 반환 여부

실행 예시:

```bash
cd DDgo_AI_Server
python validate_fastapi_live.py
```

검증 결과는 `fastapi_live_validation.json` 에 기록됩니다.

## 참고 문서

- [MUJOCO_COMPLETE_GUIDE.md](C:/ssafy/project-2/S14P21D204/DDgo_AI_Server/MUJOCO_COMPLETE_GUIDE.md)

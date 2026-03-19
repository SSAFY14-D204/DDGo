# DDGO MuJoCo Complete Server

## 개요

이 서버는 안드로이드에서 전달한 JSON 3개를 입력으로 받아,

- 포즈 보정
- polygon 기반 grip/step 판정
- MuJoCo fitting
- inverse dynamics
- 크럭스 검출

을 수행하는 **MuJoCo Complete 전용 FastAPI 서버**입니다.

입력:

1. `holds_json`
2. `pose3d_sequence_json`
3. `user_body_json`

출력:

- fast 분석 결과 JSON
- physics 분석 결과 JSON

## 현재 서버에서 제공하는 API

- `GET /health`
- `POST /api/v1/mujoco-complete/analyze/fast`
- `POST /api/v1/mujoco-complete/analyze/physics`

## 디렉터리 구조

```text
DDgo_AI_Server/
├─ app/
│  ├─ api/
│  │  ├─ router.py
│  │  └─ mujoco_complete.py
│  ├─ core/
│  │  └─ config.py
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
│           ├─ dynamic_sequence_pipeline/
│           └─ dynamic_hold_verify/
├─ requirements.txt
└─ MUJOCO_COMPLETE_GUIDE.md
```

## 실행 방법

```bash
cd DDgo_AI_Server
pip install -r requirements.txt
uvicorn app.main:app --reload
```

Swagger 문서:

- `http://localhost:8000/docs`

## 문서

상세 설명은 아래 문서를 참고하면 됩니다.

- [MUJOCO_COMPLETE_GUIDE.md](C:/ssafy/project-2/S14P21D204/DDgo_AI_Server/MUJOCO_COMPLETE_GUIDE.md)

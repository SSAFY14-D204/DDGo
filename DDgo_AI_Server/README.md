# MuJoCo Humanoid Analysis Server

MediaPipe BlazePose 33개 월드 랜드마크를 받아 **역동역학(Inverse Dynamics)** 기반으로
관절 토크 · 안정성 · 실패 판정을 수행하는 FastAPI 서버입니다.

## 핵심 파일

| 파일 | 역할 |
|------|------|
| `humanoid.xml` | MuJoCo 휴머노이드 모델 (position 액추에이터 23개) |
| `calibration.json` | 사용자 신체 치수 보정 데이터 |
| `physics_worker.py` | 물리 분석 엔진 (`PhysicalLoadAnalyzer`) |

## 프로젝트 구조

```
DDgo_AI_Server/
├── humanoid.xml              # MuJoCo 모델
├── calibration.json          # 신체 치수 보정
├── physics_worker.py         # 물리 분석 엔진
├── app/
│   ├── main.py               # FastAPI 앱 진입점
│   ├── api/
│   │   ├── router.py         # 전체 라우터 통합
│   │   ├── simulation.py     # 물리 분석 엔드포인트
│   │   └── calibration.py    # 캘리브레이션 엔드포인트
│   ├── core/
│   │   ├── config.py         # 환경 변수 기반 설정
│   │   └── mujoco_env.py     # HumanoidPhysicsEngine 싱글톤
│   ├── schemas/
│   │   ├── simulation.py     # 포즈 분석 요청/응답 스키마
│   │   └── calibration.py    # 캘리브레이션 스키마
│   └── services/
│       ├── simulation_service.py   # 물리 분석 서비스
│       └── calibration_service.py  # 캘리브레이션 서비스
├── .env.example
└── requirements.txt
```

## 빠른 시작

```bash
# 1. 의존성 설치
python -m venv .venv
.venv\Scripts\activate          # Windows
pip install -r requirements.txt

# 2. 환경 변수 설정 (선택)
copy .env.example .env

# 3. 서버 실행
uvicorn app.main:app --reload
```

→ [http://localhost:8000/docs](http://localhost:8000/docs)에서 Swagger UI 확인

## API 엔드포인트

| Method | Path | 설명 |
|--------|------|------|
| `GET` | `/health` | 서버 및 MuJoCo 초기화 상태 |
| `POST` | `/api/v1/simulation/load` | 모델 로드 (재로드) |
| `POST` | `/api/v1/simulation/close` | 모델 종료 |
| `POST` | `/api/v1/simulation/analyze` | **배치 포즈 분석** (핵심) |
| `POST` | `/api/v1/simulation/analyze/frame` | 단일 프레임 실시간 분석 |
| `GET` | `/api/v1/simulation/info` | 모델 메타 정보 |
| `GET` | `/api/v1/calibration` | 현재 캘리브레이션 조회 |
| `POST` | `/api/v1/calibration/load` | 파일 경로로 캘리브레이션 로드 |
| `POST` | `/api/v1/calibration/upload` | JSON 파일 직접 업로드 |
| `POST` | `/api/v1/calibration/inline` | JSON 바디로 직접 입력 |

## 분석 요청 예시

```json
POST /api/v1/simulation/analyze
{
  "frames": [
    {
      "timestamp_ms": 0,
      "pose_world_landmarks": [
        {"x": 0.155, "y": -0.486, "z": -0.077},
        ...  // 총 33개
      ]
    }
  ],
  "swap_left_right": false,
  "user_biometrics": { "height_m": 1.75 }
}
```

## 분석 응답 구조

```json
{
  "stability_score": 0.82,
  "contact_efficiency": 1.0,
  "failure_type": null,
  "frame_metrics": [
    {
      "timestamp_ms": 0,
      "com_stability": 0.82,
      "top_stressed_joints": [
        {"joint_id": "knee_right", "ratio": 0.71, "stress_level": "green"}
      ],
      ...
    }
  ]
}
```

## 커스터마이징 포인트

| 파일 | 수정 내용 |
|------|-----------|
| `physics_worker.py` | `_compute_reward`, `_check_done` 등 태스크별 로직 |
| `app/core/config.py` | 분석 파라미터 기본값 |
| `app/api/router.py` | 새 라우터 추가 |
| `.env` | 파일 경로 / 파라미터 오버라이드 |

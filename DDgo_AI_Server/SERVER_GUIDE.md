# MuJoCo Humanoid Analysis Server — 구조 & 가이드

---

## 1. 전체 구조

```
DDgo_AI_Server/
│
├── humanoid.xml              # MuJoCo 휴머노이드 모델 XML
├── calibration.json          # 사용자 신체 치수 보정 데이터
├── physics_worker.py         # 물리 분석 엔진 (핵심 로직)
│
├── app/
│   ├── main.py               # FastAPI 앱 진입점 + lifespan
│   │
│   ├── core/
│   │   ├── config.py         # 환경 변수 설정 (파일 경로, 파라미터)
│   │   └── mujoco_env.py     # HumanoidPhysicsEngine 멀티톤
│   │
│   ├── api/
│   │   ├── router.py         # 전체 라우터 통합 등록
│   │   ├── simulation.py     # 물리 분석 엔드포인트
│   │   └── calibration.py    # 캘리브레이션 엔드포인트
│   │
│   ├── schemas/
│   │   ├── simulation.py     # 포즈 분석 요청/응답 스키마
│   │   └── calibration.py    # 캘리브레이션 스키마
│   │
│   └── services/
│       ├── simulation_service.py   # PhysicsService (분석 비즈니스 로직)
│       └── calibration_service.py  # CalibrationService (보정값 관리)
│
├── .env.example              # 환경 변수 템플릿
└── requirements.txt
```

### 레이어 역할 요약

| 레이어 | 파일 | 역할 |
|--------|------|------|
| **엔진** | `physics_worker.py` | 역동역학 분석, 팔다리 IK, 관절 토크 계산 |
| **코어** | `core/mujoco_env.py` | physics_worker 를 감싸는 멀티톤 엔진 |
| **설정** | `core/config.py` | `.env` 기반 파일 경로·파라미터 |
| **스키마** | `schemas/` | API 입출력 타입 정의 (Pydantic) |
| **서비스** | `services/` | 비즈니스 로직, 형식 변환 |
| **API** | `api/` | HTTP 엔드포인트, 에러 핸들링 |

---

## 2. 실행 방법

```bash
# 1. 가상환경 생성 및 활성화
python -m venv .venv
.venv\Scripts\activate           # Windows
# source .venv/bin/activate      # macOS/Linux

# 2. 의존성 설치
pip install -r requirements.txt

# 3. (선택) 환경 변수 설정
copy .env.example .env
# .env 파일에서 경로·파라미터 수정

# 4. 서버 실행
uvicorn app.main:app --reload    # 개발 (자동 재시작)
uvicorn app.main:app             # 운영
```

서버가 뜨면:
- **Swagger UI** → http://localhost:8000/docs
- **헬스 체크** → http://localhost:8000/health

> 서버 시작 시 `humanoid.xml` + `calibration.json` 이 **자동으로 로드**됩니다.

---

## 3. 데이터 흐름

```
[클라이언트]
    │  POST /api/v1/simulation/analyze
    │  { "frames": [ {timestamp_ms, pose_world_landmarks[33]} ] }
    ▼
[simulation.py]  라우터
    │  AnalysisRequest 검증 (Pydantic)
    ▼
[simulation_service.py]  PhysicsService.analyze_batch()
    │  PoseFrame → physics_worker 호환 dict 변환
    ▼
[mujoco_env.py]  HumanoidPhysicsEngine.analyze_frames()
    │
    ├─ parse_landmarks()           MediaPipe 랜드마크 파싱
    ├─ mp_to_mj()                  좌표계 변환 (MediaPipe → MuJoCo)
    ├─ apply_inverse_depth_correction_to_mapped()   깊이 보정
    ├─ apply_two_link_pose_correction_to_mapped()   IK 보정
    ├─ _extract_joint_pose_targets_from_mapped()    관절각 추출
    ├─ apply_pose_to_model()       MuJoCo qpos 설정 + mj_forward
    └─ PhysicalLoadAnalyzer.analyze_frame()
           ├─ mj_inverse()         역동역학 계산
           ├─ 관절 토크 / 한계 비율 계산
           ├─ CoM 위치 + 안정성 점수
           └─ 실패 판정 (STRENGTH_LIMIT / BALANCE_DISRUPTION)
    ▼
[클라이언트]
    { stability_score, frame_metrics: [ {joint_loads, com_stability, ...} ] }
```

### 단일 프레임 vs 배치

| 엔드포인트 | 용도 | 분석기 상태 |
|------------|------|-------------|
| `POST /analyze` | 여러 프레임 일괄 분석 | 매 요청마다 analyzer 리셋 |
| `POST /analyze/frame` | 단건 실시간 스트리밍 | 호출마다 독립 실행 |

---

## 4. 전체 API 목록

| Method | Path | 설명 |
|--------|------|------|
| `GET` | `/health` | 서버 상태 + 초기화 여부 확인 |
| `GET` | `/docs` | Swagger UI |
| `POST` | `/api/v1/simulation/load` | 모델 재로드 (파라미터 변경 시) |
| `POST` | `/api/v1/simulation/close` | 모델 해제 |
| **`POST`** | **`/api/v1/simulation/analyze`** | **배치 포즈 분석 (핵심)** |
| `POST` | `/api/v1/simulation/analyze/frame` | 단일 프레임 실시간 분석 |
| `GET` | `/api/v1/simulation/info` | 모델 메타 정보 |
| `GET` | `/api/v1/calibration` | 현재 캘리브레이션 조회 |
| `POST` | `/api/v1/calibration/load` | 파일 경로로 캘리브레이션 로드 |
| `POST` | `/api/v1/calibration/upload` | JSON 파일 직접 업로드 |
| `POST` | `/api/v1/calibration/inline` | JSON 바디로 직접 입력 |

---

## 5. MuJoCo 설정 커스터마이징 가이드

### 5-1. 새 XML 모델로 교체

**파일:** `app/core/config.py`
```python
MUJOCO_MODEL_PATH: str = str(_ROOT / "humanoid.xml")  # ← 경로 변경
```
또는 `.env` 파일에서:
```
MUJOCO_MODEL_PATH=C:/path/to/your_model.xml
```

> **주의:** `humanoid.xml` 기준으로 `physics_worker.py`가 특정 body/joint 이름에 의존합니다.
> 새 모델 사용 시 아래 `physics_worker.py` 수정이 필요합니다.

---

### 5-2. 분석 대상 관절 수정

**파일:** `physics_worker.py` (Line 74–98)
```python
DEFAULT_ANALYSIS_JOINTS = [
    "abdomen_z", "abdomen_y", "abdomen_x",
    "hip_x_right", "hip_z_right", "hip_y_right",
    "knee_right", "ankle_y_right", "ankle_x_right",
    # ... 추가/제거
]
```

---

### 5-3. 모델 세그먼트 스케일링 (신체 치수 반영)

서버 환경 변수로 활성화:
```
SCALE_MODEL_SEGMENTS=true
```
또는 API 요청 시:
```json
POST /api/v1/simulation/analyze
{ "scale_model_segments": true, "frames": [...] }
```
> calibration.json 의 `upper_arm_m`, `thigh_m` 등을 읽어 XML 의 geom/body 크기를 동적으로 조정합니다.
> 스케일링 로직: `physics_worker.py` → `apply_segment_scaling_template()` (Line 372)

---

### 5-4. 분석 파라미터 조정

**파일:** `.env` 또는 `app/core/config.py`

| 변수 | 기본값 | 의미 |
|------|--------|------|
| `STRESS_RATIO_THRESHOLD` | `0.8` | 토크 비율 이 값 이상이면 yellow 경고 |
| `STRENGTH_RATIO_THRESHOLD` | `1.0` | 이 값 이상이면 STRENGTH_LIMIT 실패 후보 |
| `STRENGTH_CONSECUTIVE_FRAMES` | `5` | 연속 N프레임 초과 시 실패 판정 |
| `SUPPORT_MARGIN_M` | `0.15` | 안정성 점수 계산 여백 (m) |
| `BALANCE_FAILURE_THRESHOLD` | `0.08` | 안정성 점수 이하 시 BALANCE_DISRUPTION |

---

### 5-5. 홀드(클라이밍) 메타 추가

분석 요청에 `hold_metadata` 추가:
```json
{
  "frames": [...],
  "hold_metadata": {
    "holds": [
      {"x": 0.5, "y": 0.9, "z": 1.2},
      {"x": -0.3, "y": 0.9, "z": 1.5}
    ],
    "hold_radius": 0.08,
    "wall_axis": "y"
  }
}
```
MuJoCo 모델에 `mocap weld` 바디가 자동 추가되어 손/발 접촉을 고정합니다.
관련 코드: `physics_worker.py` → `add_mocap_and_equality()` (Line 1025)

---

### 5-6. 캘리브레이션 교체

**방법 1 — 파일 경로:**
```json
POST /api/v1/calibration/load
{ "path": "calibration.json" }
```

**방법 2 — 직접 업로드:**
```
POST /api/v1/calibration/upload
(multipart/form-data, file=calibration.json)
```

**방법 3 — JSON inline:**
```json
POST /api/v1/calibration/inline
{
  "upper_arm_m": 0.25,
  "forearm_m": 0.26,
  "thigh_m": 0.40,
  "shin_m": 0.35,
  "shoulder_width_m": 0.39,
  "wingspan_m": 1.54
}
```

---

### 5-7. 좌우 반전 (거울 모드)

MediaPipe 카메라가 셀피 방향이면 좌우가 뒤집힙니다.
```json
POST /api/v1/simulation/analyze
{ "swap_left_right": true, "frames": [...] }
```
또는 단일 프레임에서 쿼리 파라미터로:
```
POST /api/v1/simulation/analyze/frame?swap_left_right=true
```

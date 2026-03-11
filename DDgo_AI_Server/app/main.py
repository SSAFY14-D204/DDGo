"""
FastAPI 애플리케이션 진입점.
"""
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.router import api_router
from app.core.config import settings
from app.core.mujoco_env import physics_engine

# ── 로깅 설정 ──────────────────────────────────────────────────────────────
logging.basicConfig(
    level=settings.LOG_LEVEL,
    format="%(asctime)s | %(levelname)s | %(name)s | %(message)s",
)
logger = logging.getLogger(__name__)


# ── 앱 생명주기 ────────────────────────────────────────────────────────────

@asynccontextmanager
async def lifespan(app: FastAPI):
    """서버 시작 / 종료 시 실행되는 생명주기 훅"""
    logger.info("🚀 MuJoCo Humanoid Analysis Server starting...")
    try:
        physics_engine.load()
        logger.info("✅ HumanoidPhysicsEngine loaded successfully.")
    except Exception as e:
        logger.warning(
            f"⚠️  Could not auto-load model on startup: {e}. "
            "Call POST /api/v1/simulation/load to load manually."
        )

    yield  # ← 서버 실행 중

    logger.info("🛑 Shutting down...")
    physics_engine.close()


# ── FastAPI 앱 생성 ────────────────────────────────────────────────────────

app = FastAPI(
    title=settings.APP_NAME,
    version=settings.APP_VERSION,
    description=(
        "## MuJoCo Humanoid 물리 분석 서버\n\n"
        "MediaPipe BlazePose 33개 월드 랜드마크를 받아 "
        "**역동역학(Inverse Dynamics)** 기반으로 관절 토크·안정성·실패 판정을 수행합니다.\n\n"
        "### 핵심 파일\n"
        "- `humanoid.xml` — MuJoCo 휴머노이드 모델\n"
        "- `calibration.json` — 사용자 신체 치수 보정\n"
        "- `physics_worker.py` — 물리 분석 엔진\n\n"
        "### 기본 사용 흐름\n"
        "1. `GET /health` — 서버 상태 확인\n"
        "2. `GET /api/v1/calibration` — 보정값 확인\n"
        "3. `POST /api/v1/simulation/analyze` — 포즈 배치 분석\n"
    ),
    docs_url="/docs",
    redoc_url="/redoc",
    lifespan=lifespan,
)

# ── CORS 미들웨어 ─────────────────────────────────────────────────────────

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.ALLOWED_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ── 라우터 등록 ────────────────────────────────────────────────────────────

app.include_router(api_router, prefix="/api/v1")


# ── 헬스 체크 ──────────────────────────────────────────────────────────────

@app.get("/health", tags=["health"], summary="서버 상태 확인")
def health_check():
    info = physics_engine.get_model_info()
    return {
        "status": "ok",
        "version": settings.APP_VERSION,
        "mujoco_initialized": info.get("initialized", False),
        "model_path": info.get("model_path"),
        "calibration_path": info.get("calibration_path"),
        "n_actuators": info.get("n_actuators"),
    }


# ── 로컬 실행 진입점 ───────────────────────────────────────────────────────

if __name__ == "__main__":
    import uvicorn

    uvicorn.run(
        "app.main:app",
        host=settings.HOST,
        port=settings.PORT,
        reload=settings.DEBUG,
        log_level=settings.LOG_LEVEL.lower(),
    )

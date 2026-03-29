"""MuJoCo Complete 전용 FastAPI 진입점."""

from __future__ import annotations

import logging

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.mujoco_complete import batch_router
from app.api.router import api_router
from app.core.config import settings
from app.core.gzip_request_middleware import GzipRequestMiddleware

logging.basicConfig(
    level=settings.LOG_LEVEL,
    format="%(asctime)s | %(levelname)s | %(name)s | %(message)s",
)

app = FastAPI(
    title="DDGO MuJoCo Complete Server",
    version=settings.APP_VERSION,
    description=(
        "안드로이드에서 전달한 홀드 JSON, MediaPipe 3D 시계열 JSON, 사용자 신체 치수 JSON을 입력받아 "
        "포즈 보정, grip/step 판정, MuJoCo 물리 분석, 크럭스 검출 결과를 반환하는 서버입니다."
    ),
    docs_url="/docs",
    redoc_url="/redoc",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.ALLOWED_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)
app.add_middleware(GzipRequestMiddleware)

app.include_router(api_router, prefix="/api/v1")
app.include_router(batch_router, prefix="/api/v2")


@app.get("/health", tags=["health"], summary="서버 상태 확인")
def health_check() -> dict[str, object]:
    return {
        "status": "ok",
        "service": "mujoco-complete",
        "version": settings.APP_VERSION,
    }


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(
        "app.main:app",
        host=settings.HOST,
        port=settings.PORT,
        reload=settings.DEBUG,
        log_level=settings.LOG_LEVEL.lower(),
    )

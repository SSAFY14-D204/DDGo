"""
Application configuration using Pydantic BaseSettings.
Environment variables are loaded from .env file.
"""
from pathlib import Path
from typing import Optional

from pydantic_settings import BaseSettings

# 프로젝트 루트 (이 파일의 상위 3단계)
_ROOT = Path(__file__).resolve().parents[2]


class Settings(BaseSettings):
    # ── 서버 기본 설정 ──────────────────────────────────────────────────
    APP_NAME: str = "MuJoCo Humanoid Analysis Server"
    APP_VERSION: str = "0.1.0"
    DEBUG: bool = False
    HOST: str = "0.0.0.0"
    PORT: int = 8000
    LOG_LEVEL: str = "INFO"

    # ── CORS 설정 ───────────────────────────────────────────────────────
    ALLOWED_ORIGINS: list[str] = ["*"]

    # ── MuJoCo / 모델 파일 경로 ─────────────────────────────────────────
    # humanoid.xml 이 루트에 위치하므로 기본값으로 자동 해석
    MUJOCO_MODEL_PATH: str = str(_ROOT / "humanoid.xml")
    CALIBRATION_JSON_PATH: Optional[str] = str(_ROOT / "calibration.json")

    # ── 분석 파라미터 기본값 ────────────────────────────────────────────
    STRESS_RATIO_THRESHOLD: float = 0.8
    STRENGTH_RATIO_THRESHOLD: float = 1.0
    STRENGTH_CONSECUTIVE_FRAMES: int = 5
    SUPPORT_MARGIN_M: float = 0.15
    HOLD_LOCK_TOLERANCE_M: float = 0.06
    BALANCE_FAILURE_THRESHOLD: float = 0.08
    SCALE_MODEL_SEGMENTS: bool = False

    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"


# 전역 설정 인스턴스
settings = Settings()

"""
물리 분석 서비스 레이어.
HumanoidPhysicsEngine 을 감싸 API 에서 호출하기 편한 인터페이스를 제공합니다.
"""
from __future__ import annotations

import logging
from pathlib import Path
from typing import Any, Optional

from app.core.mujoco_env import physics_engine
from app.schemas.simulation import (
    AnalysisRequest,
    AnalysisResult,
    EnvInfoResponse,
    FrameMetrics,
    ModelLoadRequest,
)

logger = logging.getLogger(__name__)


class PhysicsService:
    """물리 분석 비즈니스 로직 서비스"""

    # ──────────────────────────────────────────────────────────────────
    # 모델 생명주기
    # ──────────────────────────────────────────────────────────────────

    def load_model(self, request: Optional[ModelLoadRequest] = None) -> dict:
        """
        humanoid.xml 과 캘리브레이션을 로드(재로드)합니다.
        request 가 None 이면 settings 기본값으로 로드합니다.
        """
        payload: dict[str, Any] = {}
        if request:
            if request.calibration_json_path:
                p = Path(request.calibration_json_path)
                if not p.exists():
                    raise FileNotFoundError(f"Calibration JSON not found: {p}")
                payload["calibration_json"] = str(p.resolve())
            payload["scale_model_segments"] = request.scale_model_segments
            payload["stress_ratio_threshold"] = request.stress_ratio_threshold
            payload["strength_ratio_threshold"] = request.strength_ratio_threshold
            payload["strength_consecutive_frames"] = request.strength_consecutive_frames

        physics_engine.load(payload if payload else None)
        return {"success": True, "message": "Model loaded successfully."}

    def close_model(self) -> dict:
        physics_engine.close()
        return {"success": True, "message": "Model closed."}

    # ──────────────────────────────────────────────────────────────────
    # 분석
    # ──────────────────────────────────────────────────────────────────

    def analyze_batch(self, request: AnalysisRequest) -> AnalysisResult:
        """배치 프레임 분석"""
        # 요청을 physics_worker 호환 형식으로 변환
        frames = [self._frame_to_dict(f) for f in request.frames]

        # 칼리브레이션 (캐시에서 가져옴)
        calibration = self._get_calibration_from_engine()

        # hold_metadata 변환
        if request.hold_metadata:
            hold_meta = request.hold_metadata.model_dump()
            physics_engine._payload["hold_metadata"] = hold_meta

        user_height = (
            request.user_biometrics.height_m if request.user_biometrics else 1.75
        )

        raw = physics_engine.analyze_frames(
            frames=frames,
            swap_lr=request.swap_left_right,
            calibration=calibration,
            user_height=user_height,
        )
        return AnalysisResult(**raw)

    def analyze_single(
        self,
        frame_dict: dict[str, Any],
        timestamp_ms: int = 0,
        swap_lr: bool = False,
        user_height: float = 1.75,
    ) -> FrameMetrics:
        """단일 프레임 실시간 분석"""
        calibration = self._get_calibration_from_engine()
        raw = physics_engine.analyze_single_frame(
            frame=frame_dict,
            timestamp_ms=timestamp_ms,
            swap_lr=swap_lr,
            calibration=calibration,
            user_height=user_height,
        )
        return FrameMetrics(**raw)

    # ──────────────────────────────────────────────────────────────────
    # 정보 조회
    # ──────────────────────────────────────────────────────────────────

    def get_env_info(self) -> EnvInfoResponse:
        return EnvInfoResponse(**physics_engine.get_model_info())

    # ──────────────────────────────────────────────────────────────────
    # 내부 헬퍼
    # ──────────────────────────────────────────────────────────────────

    @staticmethod
    def _frame_to_dict(frame) -> dict[str, Any]:
        """PoseFrame 스키마 → physics_worker 호환 dict"""
        return {
            "timestamp_ms": frame.timestamp_ms,
            "pose_world_landmarks": [
                {"x": lm.x, "y": lm.y, "z": lm.z}
                for lm in frame.pose_world_landmarks
            ],
        }

    @staticmethod
    def _get_calibration_from_engine() -> Optional[dict[str, float]]:
        """엔진 payload 에서 캘리브레이션 값을 추출합니다."""
        import json as _json
        cal_path = physics_engine._payload.get("calibration_json")
        if not cal_path:
            return None
        p = Path(cal_path)
        if not p.exists():
            return None
        raw = _json.loads(p.read_text(encoding="utf-8-sig"))
        required = ("upper_arm_m", "forearm_m", "thigh_m", "shin_m", "shoulder_width_m", "wingspan_m")
        missing = [k for k in required if k not in raw]
        if missing:
            logger.warning(f"Calibration JSON missing keys: {missing}")
            return None
        return {k: float(raw[k]) for k in raw if isinstance(raw[k], (int, float))}


# 싱글톤 인스턴스
physics_service = PhysicsService()

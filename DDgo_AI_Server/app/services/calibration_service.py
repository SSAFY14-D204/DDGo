"""
캘리브레이션 서비스.
calibration.json 의 로드, 검증, 제공을 담당합니다.
"""
from __future__ import annotations

import json
import logging
from pathlib import Path
from typing import Optional

from app.core.config import settings
from app.core.mujoco_env import physics_engine
from app.schemas.calibration import CalibrationData, CalibrationResponse

logger = logging.getLogger(__name__)

# 현재 로드된 캘리브레이션 캐시
_current_calibration: Optional[CalibrationData] = None
_current_calibration_path: Optional[str] = None


class CalibrationService:
    """캘리브레이션 파일 관리 서비스"""

    def get_current(self) -> CalibrationResponse:
        """현재 로드된 캘리브레이션 데이터를 반환합니다."""
        if _current_calibration is None:
            # settings 기본 경로에서 자동 로드 시도
            default = settings.CALIBRATION_JSON_PATH
            if default and Path(default).exists():
                return self.load_from_file(default)
            return CalibrationResponse(
                success=False,
                message="No calibration loaded.",
                data=None,
            )
        return CalibrationResponse(
            data=_current_calibration,
            path=_current_calibration_path,
        )

    def load_from_file(self, path: str) -> CalibrationResponse:
        """JSON 파일 경로로 캘리브레이션을 로드합니다."""
        global _current_calibration, _current_calibration_path

        p = Path(path)
        if not p.is_absolute():
            # 상대 경로는 프로젝트 루트 기준으로 해석
            p = (Path(settings.MUJOCO_MODEL_PATH).parent / path).resolve()

        if not p.exists():
            raise FileNotFoundError(f"Calibration file not found: {p}")

        raw = json.loads(p.read_text(encoding="utf-8-sig"))
        cal = CalibrationData(**raw)
        _current_calibration = cal
        _current_calibration_path = str(p)

        # 엔진 payload 업데이트 (다음 모델 로드 시 적용)
        physics_engine._payload["calibration_json"] = str(p)
        logger.info(f"Calibration loaded from: {p}")

        return CalibrationResponse(data=cal, path=str(p))

    def set_inline(self, data: CalibrationData) -> CalibrationResponse:
        """요청 본문으로 직접 캘리브레이션 데이터를 설정합니다."""
        global _current_calibration, _current_calibration_path
        _current_calibration = data
        _current_calibration_path = None

        # 엔진 payload 에 직접 수치값 저장 (파일 없이 사용)
        cal_dict = {
            "upper_arm_m": data.upper_arm_m,
            "forearm_m": data.forearm_m,
            "thigh_m": data.thigh_m,
            "shin_m": data.shin_m,
            "shoulder_width_m": data.shoulder_width_m,
            "wingspan_m": data.wingspan_m,
        }
        if data.height_m:
            cal_dict["height_m"] = data.height_m
        # inline 캘리브레이션은 임시 파일에 저장하여 엔진이 읽을 수 있게 함
        import tempfile, os
        tmp = tempfile.NamedTemporaryFile(
            mode="w", suffix=".json", delete=False, encoding="utf-8"
        )
        json.dump(cal_dict, tmp)
        tmp.close()
        physics_engine._payload["calibration_json"] = tmp.name
        logger.info("Inline calibration set.")

        return CalibrationResponse(data=data, message="Inline calibration applied.")


# 싱글톤 인스턴스
calibration_service = CalibrationService()

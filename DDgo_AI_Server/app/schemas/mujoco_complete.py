from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field


class MujocoCompleteRequest(BaseModel):
    holds_json: dict[str, Any] = Field(..., description="홀드 좌표 JSON")
    pose3d_sequence_json: dict[str, Any] = Field(..., description="원본 MediaPipe 3D 시계열 JSON")
    user_body_json: dict[str, Any] = Field(..., description="사용자 신체 치수 및 보정 JSON")
    top_k_crux: int = Field(default=3, ge=1, le=10, description="반환할 크럭스 후보 개수")
    frame_step: int = Field(default=1, ge=1, description="처리 간격 프레임 수")

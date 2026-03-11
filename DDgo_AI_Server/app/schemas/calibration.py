"""
캘리브레이션 관련 Pydantic 스키마.
"""
from typing import Dict, List, Optional
from pydantic import BaseModel, Field


class PixelLengths(BaseModel):
    upper_arm_left_px: Optional[float] = None
    upper_arm_right_px: Optional[float] = None
    forearm_left_px: Optional[float] = None
    forearm_right_px: Optional[float] = None
    thigh_left_px: Optional[float] = None
    thigh_right_px: Optional[float] = None
    shin_left_px: Optional[float] = None
    shin_right_px: Optional[float] = None
    shoulder_width_px: Optional[float] = None
    wingspan_px: Optional[float] = None
    body_height_px: Optional[float] = None


class SegmentRatios(BaseModel):
    upper_arm: Optional[float] = None
    forearm: Optional[float] = None
    thigh: Optional[float] = None
    shin: Optional[float] = None
    shoulder_width: Optional[float] = None
    wingspan: Optional[float] = None


class CalibrationData(BaseModel):
    """calibration.json 내용 스키마"""
    # 필수 필드 (physics_worker 가 요구)
    upper_arm_m: float = Field(description="상완 길이 (m)")
    forearm_m: float = Field(description="전완 길이 (m)")
    thigh_m: float = Field(description="허벅지 길이 (m)")
    shin_m: float = Field(description="정강이 길이 (m)")
    shoulder_width_m: float = Field(description="어깨 너비 (m)")
    wingspan_m: float = Field(description="팔 벌림 폭 (m)")

    # 선택 필드
    height_m: Optional[float] = Field(default=None, description="신장 (m)")
    scale_m_per_px: Optional[float] = None
    ratios: Optional[SegmentRatios] = None
    pixel_lengths: Optional[PixelLengths] = None
    image_path: Optional[str] = None

    class Config:
        extra = "allow"   # 추가 필드 허용 (landmarks 등)


class CalibrationLoadRequest(BaseModel):
    """파일 경로로 캘리브레이션 로드 요청"""
    path: str = Field(description="calibration.json 절대/상대 경로")


class CalibrationResponse(BaseModel):
    """캘리브레이션 응답"""
    success: bool = True
    message: str = "OK"
    data: Optional[CalibrationData] = None
    path: Optional[str] = None

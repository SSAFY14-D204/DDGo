"""
도메인 스키마 — 포즈 분석 API 요청/응답 모델.
"""
from typing import Any, Dict, List, Optional
from pydantic import BaseModel, Field


# ── 요청 스키마 ──────────────────────────────────────────────────────────────

class PoseLandmark(BaseModel):
    """MediaPipe BlazePose 단일 랜드마크 (world 좌표계)"""
    x: float
    y: float
    z: float
    visibility: Optional[float] = None


class HoldPoint(BaseModel):
    """클라이밍 홀드 위치 (MuJoCo 월드 좌표)"""
    x: float
    y: float
    z: float


class HoldMetadata(BaseModel):
    """홀드 메타 정보"""
    holds: List[HoldPoint] = Field(default_factory=list)
    hold_radius: float = Field(default=0.08, description="홀드 유효 반경 (m)")
    wall_axis: Optional[str] = Field(default=None, description="벽 법선 축 (x/y/z)")


class UserBiometrics(BaseModel):
    """사용자 신체 정보"""
    height_m: float = Field(default=1.75, description="키 (m)")


class PoseFrame(BaseModel):
    """단일 포즈 프레임"""
    timestamp_ms: int = Field(default=0, description="타임스탬프 (ms)")
    pose_world_landmarks: List[PoseLandmark] = Field(
        ..., min_length=33, max_length=33,
        description="MediaPipe BlazePose 33개 월드 랜드마크"
    )


class AnalysisRequest(BaseModel):
    """배치 분석 요청"""
    frames: List[PoseFrame] = Field(..., description="분석할 포즈 프레임 목록")
    swap_left_right: bool = Field(default=False, description="좌우 반전 여부")
    hold_metadata: Optional[HoldMetadata] = None
    user_biometrics: Optional[UserBiometrics] = None
    scale_model_segments: bool = Field(
        default=False, description="calibration 으로 모델 세그먼트 스케일링 여부"
    )


class ModelLoadRequest(BaseModel):
    """모델 로드 요청 (옵션 재설정용)"""
    calibration_json_path: Optional[str] = Field(
        default=None, description="캘리브레이션 JSON 파일 경로"
    )
    scale_model_segments: bool = False
    stress_ratio_threshold: float = 0.8
    strength_ratio_threshold: float = 1.0
    strength_consecutive_frames: int = 5


# ── 응답 스키마 ──────────────────────────────────────────────────────────────

class JointLoad(BaseModel):
    """단일 관절의 하중 정보"""
    joint_id: str
    timestamp_ms: int
    torque: float = Field(description="관절 토크 (N·m)")
    torque_limit: float = Field(description="토크 한계 (N·m)")
    ratio: float = Field(description="토크/한계 비율 (0.0 ~ 1.0+)")
    stress_level: str = Field(description="green / yellow / red")


class ContactInfo(BaseModel):
    """활성 홀드 접촉 정보"""
    limb: str
    hold_index: int
    hold_center: List[float]
    target_distance_m: float
    body_error_m: float
    weld_active: bool


class FrameMetrics(BaseModel):
    """단일 프레임 분석 결과"""
    frame_index: Optional[int] = None
    timestamp_ms: int
    active_hold_contacts: List[ContactInfo] = Field(default_factory=list)
    effective_contact: bool
    contact_count: int
    com_position: List[float] = Field(description="질량 중심 위치 [x, y, z]")
    com_wall_distance_m: float
    com_stability: float = Field(description="안정성 점수 (0.0 ~ 1.0)")
    joint_loads: List[JointLoad]
    joint_ratio_map: Dict[str, float]
    joints_over_threshold: List[JointLoad]
    top_stressed_joints: List[JointLoad]
    limb_reach_error_m: Dict[str, float]
    strength_failure_active: bool
    strength_failure_joint: Optional[str]
    failure_type: Optional[str] = Field(
        default=None, description="STRENGTH_LIMIT / BALANCE_DISRUPTION / null"
    )


class ReachErrorSummary(BaseModel):
    mean_reach_error_m: float
    max_reach_error_m: float


class AnalysisResult(BaseModel):
    """배치 분석 전체 결과"""
    stability_score: float = Field(description="평균 안정성 점수")
    contact_efficiency: float = Field(description="유효 접촉 비율")
    reach_error_summary: ReachErrorSummary
    frame_metrics: List[FrameMetrics]
    failure_type: Optional[str]
    t_fail_timestamp: Optional[int]
    meta: Dict[str, Any] = Field(default_factory=dict)


class EnvInfoResponse(BaseModel):
    """환경 메타 정보 응답"""
    initialized: bool
    model_path: Optional[str]
    calibration_path: Optional[str]
    n_bodies: Optional[int] = None
    n_joints: Optional[int] = None
    n_actuators: Optional[int] = None
    n_qpos: Optional[int] = None
    n_qvel: Optional[int] = None
    timestep: Optional[float] = None
    analysis_joints: Optional[List[str]] = None
    scale_model_segments: Optional[bool] = None

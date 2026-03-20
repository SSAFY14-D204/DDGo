from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field


class MujocoCompleteRequest(BaseModel):
    holds_json: dict[str, Any] = Field(..., description="Hold metadata payload.")
    pose3d_sequence_json: dict[str, Any] = Field(..., description="Pose sequence payload.")
    user_body_json: dict[str, Any] = Field(..., description="User body calibration payload.")
    top_k_crux: int = Field(default=3, ge=1, le=10, description="Maximum number of crux candidates.")
    frame_step: int = Field(default=1, ge=1, description="Frame sampling step.")


class MujocoRealtimeLandmark(BaseModel):
    index: int = Field(..., description="MediaPipe landmark index.")
    x: float = Field(..., description="Normalized or world X coordinate.")
    y: float = Field(..., description="Normalized or world Y coordinate.")
    z: float = Field(..., description="Normalized or world Z coordinate.")
    visibility: float | None = Field(default=None, description="Optional MediaPipe visibility.")
    presence: float | None = Field(default=None, description="Optional MediaPipe presence.")


class MujocoRealtimePoseFrame(BaseModel):
    frame_index: int = Field(..., ge=0, description="Monotonic frame index.")
    timestamp_ms: int = Field(..., ge=0, description="Frame timestamp in milliseconds.")
    pose_detected: bool = Field(default=True, description="Whether a pose was detected.")
    pose_landmarks: list[MujocoRealtimeLandmark] = Field(default_factory=list, description="Normalized landmarks.")
    pose_world_landmarks: list[MujocoRealtimeLandmark] = Field(default_factory=list, description="World landmarks.")


class MujocoRealtimeSessionStartRequest(BaseModel):
    user_body_json: dict[str, Any] = Field(..., description="User body calibration payload.")
    video_metadata: dict[str, Any] = Field(..., description="Video metadata payload.")
    top_k_crux: int = Field(default=3, ge=1, le=10, description="Maximum number of crux candidates.")
    frame_step: int = Field(default=1, ge=1, description="Frame sampling step.")
    mode: str | None = Field(default=None, description="Requested analysis mode.")


class MujocoRealtimePoseChunkRequest(BaseModel):
    frames: list[MujocoRealtimePoseFrame] = Field(default_factory=list, description="Realtime pose frame chunk.")


class MujocoRealtimeContextRequest(BaseModel):
    holds_json: dict[str, Any] = Field(..., description="Hold metadata payload.")


class MujocoRealtimeSessionAckResponse(BaseModel):
    session_id: str = Field(..., description="Realtime session id.")
    status: str = Field(..., description="Session status.")
    message: str = Field(..., description="Ack message.")
    mode: str = Field(..., description="Effective analysis mode.")
    frame_count: int = Field(default=0, description="Stored frame count.")
    last_frame_index: int | None = Field(default=None, description="Last accepted frame index.")
    accepted_frame_count: int = Field(default=0, description="Accepted frame count in this request.")

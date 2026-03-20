from __future__ import annotations

from typing import Any

from fastapi import APIRouter, HTTPException, status

from app.schemas.mujoco_complete import (
    MujocoCompleteRequest,
    MujocoRealtimeContextRequest,
    MujocoRealtimePoseChunkRequest,
    MujocoRealtimeSessionAckResponse,
    MujocoRealtimeSessionStartRequest,
)
from app.services.mujoco_complete import mujoco_complete_service
from app.services.mujoco_complete.realtime_session_store import (
    RealtimeSessionNotFoundError,
    RealtimeSessionStateError,
)

router = APIRouter(prefix="/mujoco-complete", tags=["mujoco-complete"])


@router.post("/analyze/fast", response_model=dict[str, Any], summary="Run fast MuJoCo crux analysis")
def analyze_fast(request: MujocoCompleteRequest) -> dict[str, Any]:
    try:
        return mujoco_complete_service.analyze_fast(
            holds_payload=request.holds_json,
            pose_payload=request.pose3d_sequence_json,
            user_body_payload=request.user_body_json,
            top_k_crux=request.top_k_crux,
            frame_step=request.frame_step,
        )
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc)) from exc
    except Exception as exc:  # pragma: no cover - defensive API guard
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail=str(exc)) from exc


@router.post("/analyze/physics", response_model=dict[str, Any], summary="Run physics MuJoCo crux analysis")
def analyze_physics(request: MujocoCompleteRequest) -> dict[str, Any]:
    try:
        return mujoco_complete_service.analyze_physics(
            holds_payload=request.holds_json,
            pose_payload=request.pose3d_sequence_json,
            user_body_payload=request.user_body_json,
            top_k_crux=request.top_k_crux,
            frame_step=request.frame_step,
        )
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc)) from exc
    except Exception as exc:  # pragma: no cover - defensive API guard
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail=str(exc)) from exc


@router.post(
    "/session/start",
    response_model=MujocoRealtimeSessionAckResponse,
    status_code=status.HTTP_201_CREATED,
    summary="Start a realtime MuJoCo analysis session",
)
def start_realtime_session(request: MujocoRealtimeSessionStartRequest) -> MujocoRealtimeSessionAckResponse:
    try:
        payload = mujoco_complete_service.start_realtime_session(
            user_body_payload=request.user_body_json,
            video_metadata=request.video_metadata,
            top_k_crux=request.top_k_crux,
            frame_step=request.frame_step,
            mode=request.mode,
        )
        return MujocoRealtimeSessionAckResponse(**payload)
    except RealtimeSessionStateError as exc:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc)) from exc
    except Exception as exc:  # pragma: no cover - defensive API guard
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail=str(exc)) from exc


@router.post(
    "/session/{session_id}/pose-chunks",
    response_model=MujocoRealtimeSessionAckResponse,
    summary="Append realtime pose frames",
)
def append_realtime_pose_chunks(
    session_id: str,
    request: MujocoRealtimePoseChunkRequest,
) -> MujocoRealtimeSessionAckResponse:
    try:
        payload = mujoco_complete_service.append_realtime_pose_chunks(
            session_id=session_id,
            frames=[frame.model_dump(mode="json") for frame in request.frames],
        )
        return MujocoRealtimeSessionAckResponse(**payload)
    except RealtimeSessionNotFoundError as exc:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(exc)) from exc
    except RealtimeSessionStateError as exc:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc)) from exc
    except Exception as exc:  # pragma: no cover - defensive API guard
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail=str(exc)) from exc


@router.post(
    "/session/{session_id}/context",
    response_model=MujocoRealtimeSessionAckResponse,
    summary="Attach hold-selection context to a realtime session",
)
def attach_realtime_context(
    session_id: str,
    request: MujocoRealtimeContextRequest,
) -> MujocoRealtimeSessionAckResponse:
    try:
        payload = mujoco_complete_service.attach_realtime_context(
            session_id=session_id,
            holds_payload=request.holds_json,
        )
        return MujocoRealtimeSessionAckResponse(**payload)
    except RealtimeSessionNotFoundError as exc:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(exc)) from exc
    except RealtimeSessionStateError as exc:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc)) from exc
    except Exception as exc:  # pragma: no cover - defensive API guard
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail=str(exc)) from exc


@router.post(
    "/session/{session_id}/finalize",
    response_model=dict[str, Any],
    summary="Finalize a realtime session with the existing batch pipeline",
)
def finalize_realtime_session(session_id: str) -> dict[str, Any]:
    try:
        return mujoco_complete_service.finalize_realtime_session(session_id=session_id)
    except RealtimeSessionNotFoundError as exc:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(exc)) from exc
    except (RealtimeSessionStateError, ValueError) as exc:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc)) from exc
    except Exception as exc:  # pragma: no cover - defensive API guard
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail=str(exc)) from exc


@router.delete(
    "/session/{session_id}",
    response_model=MujocoRealtimeSessionAckResponse,
    summary="Abort and delete a realtime session",
)
def delete_realtime_session(session_id: str) -> MujocoRealtimeSessionAckResponse:
    try:
        payload = mujoco_complete_service.delete_realtime_session(session_id=session_id)
        return MujocoRealtimeSessionAckResponse(**payload)
    except RealtimeSessionNotFoundError as exc:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(exc)) from exc
    except Exception as exc:  # pragma: no cover - defensive API guard
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail=str(exc)) from exc

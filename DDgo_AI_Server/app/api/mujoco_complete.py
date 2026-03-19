from __future__ import annotations

from typing import Any

from fastapi import APIRouter, HTTPException, status

from app.schemas.mujoco_complete import MujocoCompleteRequest
from app.services.mujoco_complete import mujoco_complete_service

router = APIRouter(prefix="/mujoco-complete", tags=["mujoco-complete"])


@router.post(
    "/analyze/fast",
    response_model=dict[str, Any],
    summary="빠른 크럭스 분석",
    description="입력 JSON 3개를 받아 포즈 보정과 폴리곤 기반 grip/step 판정을 수행한 뒤, 체류 시간 기반의 빠른 크럭스 후보를 반환합니다.",
)
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
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc))
    except Exception as exc:
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail=str(exc))


@router.post(
    "/analyze/physics",
    response_model=dict[str, Any],
    summary="물리 기반 크럭스 분석",
    description="입력 JSON 3개를 받아 포즈 보정, MuJoCo 물리 분석, 설명 가능한 크럭스 후보 계산까지 수행합니다.",
)
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
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc))
    except Exception as exc:
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail=str(exc))

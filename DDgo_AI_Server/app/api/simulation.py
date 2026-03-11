"""
시뮬레이션 / 물리 분석 API 라우터.
"""
from typing import Any, Optional

from fastapi import APIRouter, HTTPException, Query, status

from app.schemas.simulation import (
    AnalysisRequest,
    AnalysisResult,
    EnvInfoResponse,
    FrameMetrics,
    ModelLoadRequest,
    PoseFrame,
)
from app.services.simulation_service import physics_service

router = APIRouter(prefix="/simulation", tags=["simulation"])


# ── 모델 생명주기 ──────────────────────────────────────────────────────────

@router.post(
    "/load",
    summary="MuJoCo 모델 로드",
    description=(
        "humanoid.xml 을 로드(또는 재로드)합니다. "
        "요청 바디를 생략하면 .env 기본 설정으로 로드합니다."
    ),
)
def load_model(request: Optional[ModelLoadRequest] = None):
    try:
        return physics_service.load_model(request)
    except FileNotFoundError as e:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail=str(e))


@router.post(
    "/close",
    summary="MuJoCo 모델 종료",
    description="현재 로드된 MuJoCo 모델을 닫고 리소스를 해제합니다.",
)
def close_model():
    return physics_service.close_model()


# ── 분석 엔드포인트 ────────────────────────────────────────────────────────

@router.post(
    "/analyze",
    response_model=AnalysisResult,
    summary="배치 포즈 분석 (핵심)",
    description=(
        "MediaPipe BlazePose 33개 월드 랜드마크 프레임 목록을 받아 "
        "관절 토크 / 안정성 / 실패 판정 등을 배치 분석합니다."
    ),
)
def analyze_batch(request: AnalysisRequest):
    try:
        return physics_service.analyze_batch(request)
    except RuntimeError as e:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=str(e))
    except ValueError as e:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail=str(e))


@router.post(
    "/analyze/frame",
    response_model=FrameMetrics,
    summary="단일 프레임 실시간 분석",
    description=(
        "단일 포즈 프레임을 받아 즉시 물리 분석 결과를 반환합니다. "
        "실시간 스트리밍 시나리오에 사용합니다."
    ),
)
def analyze_single_frame(
    frame: PoseFrame,
    swap_left_right: bool = Query(default=False, description="좌우 반전 여부"),
    user_height_m: float = Query(default=1.75, description="사용자 신장 (m)"),
):
    try:
        frame_dict = {
            "timestamp_ms": frame.timestamp_ms,
            "pose_world_landmarks": [
                {"x": lm.x, "y": lm.y, "z": lm.z}
                for lm in frame.pose_world_landmarks
            ],
        }
        return physics_service.analyze_single(
            frame_dict=frame_dict,
            timestamp_ms=frame.timestamp_ms,
            swap_lr=swap_left_right,
            user_height=user_height_m,
        )
    except RuntimeError as e:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=str(e))
    except ValueError as e:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail=str(e))


# ── 정보 조회 ──────────────────────────────────────────────────────────────

@router.get(
    "/info",
    response_model=EnvInfoResponse,
    summary="현재 모델 메타 정보",
    description="로드된 humanoid 모델의 관절 수, 액추에이터 수, 타임스텝 등 메타 정보를 반환합니다.",
)
def get_env_info():
    return physics_service.get_env_info()

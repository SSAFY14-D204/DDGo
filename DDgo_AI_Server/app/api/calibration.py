"""
캘리브레이션 API 라우터.
"""
from fastapi import APIRouter, HTTPException, UploadFile, File, status

from app.schemas.calibration import (
    CalibrationData,
    CalibrationLoadRequest,
    CalibrationResponse,
)
from app.services.calibration_service import calibration_service

router = APIRouter(prefix="/calibration", tags=["calibration"])


@router.get(
    "",
    response_model=CalibrationResponse,
    summary="현재 캘리브레이션 조회",
    description="현재 로드된 신체 치수 보정 데이터를 반환합니다.",
)
def get_calibration():
    return calibration_service.get_current()


@router.post(
    "/load",
    response_model=CalibrationResponse,
    summary="캘리브레이션 파일 로드",
    description="파일 경로로 calibration.json 을 로드합니다.",
)
def load_calibration(request: CalibrationLoadRequest):
    try:
        return calibration_service.load_from_file(request.path)
    except FileNotFoundError as e:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail=str(e))


@router.post(
    "/upload",
    response_model=CalibrationResponse,
    summary="캘리브레이션 JSON 업로드",
    description="calibration.json 파일을 직접 업로드하여 적용합니다.",
)
async def upload_calibration(file: UploadFile = File(...)):
    import json

    try:
        raw = await file.read()
        data = json.loads(raw.decode("utf-8-sig"))
        cal = CalibrationData(**data)
        return calibration_service.set_inline(cal)
    except (ValueError, KeyError) as e:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail=f"Invalid calibration JSON: {e}",
        )
    except Exception as e:
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail=str(e))


@router.post(
    "/inline",
    response_model=CalibrationResponse,
    summary="캘리브레이션 직접 입력",
    description="신체 치수를 JSON 바디로 직접 입력하여 캘리브레이션을 설정합니다.",
)
def set_inline_calibration(data: CalibrationData):
    try:
        return calibration_service.set_inline(data)
    except Exception as e:
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail=str(e))

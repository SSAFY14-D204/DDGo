"""
API 라우터 통합 모듈.
새로운 라우터를 추가할 때 여기에 include_router 를 추가하세요.
"""
from fastapi import APIRouter

from app.api import simulation, calibration

api_router = APIRouter()

api_router.include_router(simulation.router)
api_router.include_router(calibration.router)

# 추후 라우터 추가 예시:
# from app.api import render
# api_router.include_router(render.router)

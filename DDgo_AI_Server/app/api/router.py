"""MuJoCo Complete 전용 라우터 묶음."""

from fastapi import APIRouter

from app.api import mujoco_complete

api_router = APIRouter()
api_router.include_router(mujoco_complete.router)

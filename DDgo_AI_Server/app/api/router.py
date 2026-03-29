from __future__ import annotations

from fastapi import APIRouter

from app.api.mujoco_complete import batch_router, realtime_router

api_router = APIRouter()
api_router.include_router(batch_router)
api_router.include_router(realtime_router)

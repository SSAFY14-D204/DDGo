"""
HumanoidPhysicsEngine — physics_worker 기반 MuJoCo 분석 엔진 싱글톤.

physics_worker.py의 build_analysis_model / PhysicalLoadAnalyzer /
run_worker 로직을 FastAPI 서버에서 재사용 가능하도록 래핑합니다.
"""
from __future__ import annotations

import logging
from pathlib import Path
from typing import Any, Optional

import mujoco
import numpy as np

# physics_worker 는 루트에 있으므로 직접 임포트
import sys
import os

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
import physics_worker as pw

from app.core.config import settings

logger = logging.getLogger(__name__)


class HumanoidPhysicsEngine:
    """
    MuJoCo Humanoid 물리 분석 엔진 싱글톤.

    - humanoid.xml 로드 및 세그먼트 스케일링 (calibration 적용)
    - 배치 프레임 / 단일 프레임 분석 지원
    """

    def __init__(self) -> None:
        self.model: Optional[mujoco.MjModel] = None
        self.data: Optional[mujoco.MjData] = None
        self.analyzer: Optional[pw.PhysicalLoadAnalyzer] = None
        self._payload: dict[str, Any] = {}
        self._initialized: bool = False

        # 포즈 적용에 필요한 사전 계산 값
        self._qpos_adr: dict[str, int] = {}
        self._joint_limits: dict[str, tuple[float, float]] = {}
        self._torso_from_pelvis: Optional[np.ndarray] = None
        self._pelvis_anchor: Optional[np.ndarray] = None
        self._model_shoulder_width: float = 0.0

    # ──────────────────────────────────────────────────────────────────
    # 초기화 / 해제
    # ──────────────────────────────────────────────────────────────────

    def load(self, payload: Optional[dict[str, Any]] = None) -> None:
        """
        humanoid.xml 을 로드하고 PhysicalLoadAnalyzer 를 초기화합니다.

        Args:
            payload: physics_worker 호환 설정 딕셔너리.
                     None 이면 settings 의 기본값을 사용합니다.
        """
        xml_path = Path(settings.MUJOCO_MODEL_PATH)
        if not xml_path.exists():
            raise FileNotFoundError(f"humanoid.xml not found: {xml_path}")

        # payload 기본값 병합
        p: dict[str, Any] = {
            "stress_ratio_threshold": settings.STRESS_RATIO_THRESHOLD,
            "strength_ratio_threshold": settings.STRENGTH_RATIO_THRESHOLD,
            "strength_consecutive_frames": settings.STRENGTH_CONSECUTIVE_FRAMES,
            "support_margin_m": settings.SUPPORT_MARGIN_M,
            "hold_lock_tolerance_m": settings.HOLD_LOCK_TOLERANCE_M,
            "balance_failure_stability_threshold": settings.BALANCE_FAILURE_THRESHOLD,
            "scale_model_segments": settings.SCALE_MODEL_SEGMENTS,
        }
        if settings.CALIBRATION_JSON_PATH and Path(settings.CALIBRATION_JSON_PATH).exists():
            p["calibration_json"] = settings.CALIBRATION_JSON_PATH
        if payload:
            p.update(payload)

        self._payload = p

        logger.info(f"Loading MuJoCo model: {xml_path}")
        self.model, self.data = pw.build_analysis_model(xml_path, p)
        self.analyzer = pw.PhysicalLoadAnalyzer(self.model, self.data, p)

        # 포즈 적용에 필요한 사전 계산
        joint_ids = {name: pw.joint_id(self.model, name) for name in pw.DEFAULT_ANALYSIS_JOINTS}
        self._qpos_adr = {name: int(self.model.jnt_qposadr[jid]) for name, jid in joint_ids.items()}
        self._joint_limits = {}
        for name, jid in joint_ids.items():
            if bool(self.model.jnt_limited[jid]):
                lo, hi = self.model.jnt_range[jid]
                self._joint_limits[name] = (float(lo), float(hi))
            else:
                self._joint_limits[name] = (-1e9, 1e9)

        torso_bid = pw.body_id(self.model, "torso")
        pelvis_bid = pw.body_id(self.model, "pelvis")
        l_sh_bid = pw.body_id(self.model, "upper_arm_left")
        r_sh_bid = pw.body_id(self.model, "upper_arm_right")

        mujoco.mj_resetData(self.model, self.data)
        mujoco.mj_forward(self.model, self.data)
        self._torso_from_pelvis = self.data.xpos[torso_bid] - self.data.xpos[pelvis_bid]
        self._pelvis_anchor = self.data.xpos[pelvis_bid].copy()
        self._model_shoulder_width = float(
            np.linalg.norm(self.data.xpos[l_sh_bid] - self.data.xpos[r_sh_bid])
        )

        self._initialized = True
        logger.info("HumanoidPhysicsEngine initialized successfully.")

    def close(self) -> None:
        """리소스를 해제합니다."""
        self.model = None
        self.data = None
        self.analyzer = None
        self._initialized = False
        logger.info("HumanoidPhysicsEngine closed.")

    # ──────────────────────────────────────────────────────────────────
    # 분석 메서드
    # ──────────────────────────────────────────────────────────────────

    def analyze_frames(
        self,
        frames: list[dict[str, Any]],
        swap_lr: bool = False,
        calibration: Optional[dict[str, float]] = None,
        user_height: float = 1.75,
    ) -> dict[str, Any]:
        """
        복수의 포즈 프레임을 배치 분석합니다.

        Args:
            frames: pose_world_landmarks 를 포함한 프레임 리스트
            swap_lr: 좌우 반전 여부
            calibration: 신체 치수 보정값 (None 이면 모델 기본값 사용)
            user_height: 사용자 키 (m)

        Returns:
            run_worker 와 동일한 구조의 분석 결과 딕셔너리
        """
        self._check_initialized()

        if not frames:
            raise ValueError("frames 리스트가 비어 있습니다.")

        # 스케일/오프셋 계산 (첫 프레임 기준)
        scale, offset = self._compute_scale_offset(frames[0], calibration, user_height, swap_lr)

        # analyzer 초기화 (누적 상태 리셋)
        self.analyzer = pw.PhysicalLoadAnalyzer(self.model, self.data, self._payload)

        frame_metrics: list[dict[str, Any]] = []
        for index, frame in enumerate(frames):
            timestamp_ms = int(frame.get("timestamp_ms", index * 33))
            metrics = self._process_single_frame(
                frame, timestamp_ms, scale, offset, calibration, swap_lr
            )
            metrics["frame_index"] = index
            frame_metrics.append(metrics)

        return self._build_summary(frame_metrics)

    def analyze_single_frame(
        self,
        frame: dict[str, Any],
        timestamp_ms: int = 0,
        swap_lr: bool = False,
        calibration: Optional[dict[str, float]] = None,
        user_height: float = 1.75,
    ) -> dict[str, Any]:
        """
        단일 프레임을 실시간 분석합니다.

        Returns:
            analyze_frame() 과 동일한 구조의 dict
        """
        self._check_initialized()
        scale, offset = self._compute_scale_offset(frame, calibration, user_height, swap_lr)
        return self._process_single_frame(frame, timestamp_ms, scale, offset, calibration, swap_lr)

    # ──────────────────────────────────────────────────────────────────
    # 정보 조회
    # ──────────────────────────────────────────────────────────────────

    def get_model_info(self) -> dict[str, Any]:
        """현재 로드된 모델의 메타 정보를 반환합니다."""
        if not self._initialized or self.model is None:
            return {
                "initialized": False,
                "model_path": settings.MUJOCO_MODEL_PATH,
                "calibration_path": settings.CALIBRATION_JSON_PATH,
            }
        return {
            "initialized": True,
            "model_path": settings.MUJOCO_MODEL_PATH,
            "calibration_path": self._payload.get("calibration_json"),
            "n_bodies": self.model.nbody,
            "n_joints": self.model.njnt,
            "n_actuators": self.model.nu,
            "n_qpos": self.model.nq,
            "n_qvel": self.model.nv,
            "timestep": float(self.model.opt.timestep),
            "analysis_joints": pw.DEFAULT_ANALYSIS_JOINTS,
            "scale_model_segments": self._payload.get("scale_model_segments", False),
        }

    # ──────────────────────────────────────────────────────────────────
    # 내부 헬퍼
    # ──────────────────────────────────────────────────────────────────

    def _compute_scale_offset(
        self,
        first_frame: dict[str, Any],
        calibration: Optional[dict[str, float]],
        user_height: float,
        swap_lr: bool,
    ) -> tuple[float, np.ndarray]:
        """첫 프레임으로부터 월드 스케일과 오프셋을 계산합니다."""
        landmarks_mp = pw.parse_landmarks(first_frame)
        mapped_local = np.array([pw.mp_to_mj(p) for p in landmarks_mp], dtype=np.float64)
        sw_local = float(np.linalg.norm(
            mapped_local[pw.LEFT_SHOULDER] - mapped_local[pw.RIGHT_SHOULDER]
        ))
        seg_lengths = pw.segment_lengths_local_from_calibration(calibration, sw_local)
        mapped_local = pw.apply_inverse_depth_correction_to_mapped(mapped_local, seg_lengths, swap_lr=swap_lr)
        _, points_local = pw._extract_joint_pose_targets_from_mapped(mapped_local, swap_lr=swap_lr)

        shoulder_width = float(np.linalg.norm(
            points_local["left_shoulder"] - points_local["right_shoulder"]
        ))
        shoulder_scale = self._model_shoulder_width / max(shoulder_width, 1e-6)
        anthropometric_scale = user_height / 1.75
        scale = shoulder_scale if calibration is not None else shoulder_scale * anthropometric_scale
        offset = self._pelvis_anchor - points_local["hip_mid"] * scale
        return scale, offset

    def _process_single_frame(
        self,
        frame: dict[str, Any],
        timestamp_ms: int,
        scale: float,
        offset: np.ndarray,
        calibration: Optional[dict[str, float]],
        swap_lr: bool,
    ) -> dict[str, Any]:
        """하나의 프레임을 처리하여 물리 분석 결과를 반환합니다."""
        landmarks_mp = pw.parse_landmarks(frame)
        mapped_local = np.array([pw.mp_to_mj(p) for p in landmarks_mp], dtype=np.float64)
        sw_local = float(np.linalg.norm(
            mapped_local[pw.LEFT_SHOULDER] - mapped_local[pw.RIGHT_SHOULDER]
        ))
        seg_lengths = pw.segment_lengths_local_from_calibration(calibration, sw_local)
        mapped_local = pw.apply_inverse_depth_correction_to_mapped(mapped_local, seg_lengths, swap_lr=swap_lr)
        mapped_local, _ = pw.apply_two_link_pose_correction_to_mapped(mapped_local, seg_lengths, swap_lr=swap_lr)

        joint_targets, points_local = pw._extract_joint_pose_targets_from_mapped(mapped_local, swap_lr=swap_lr)
        mapped_points = pw.mapped_points_from_local(points_local, scale=scale, offset=offset)

        limb_targets_world = pw.apply_pose_to_model(
            model=self.model,
            data=self.data,
            qpos_adr=self._qpos_adr,
            joint_limits=self._joint_limits,
            joint_targets=joint_targets,
            mapped_points=mapped_points,
            torso_from_pelvis=self._torso_from_pelvis,
        )
        return self.analyzer.analyze_frame(
            timestamp_ms=timestamp_ms,
            limb_targets_world=limb_targets_world,
        )

    @staticmethod
    def _build_summary(frame_metrics: list[dict[str, Any]]) -> dict[str, Any]:
        """배치 분석 결과 요약을 생성합니다."""
        stability_score = float(np.mean([f["com_stability"] for f in frame_metrics])) if frame_metrics else 0.0
        contact_efficiency = float(np.mean([
            1.0 if f["effective_contact"] else 0.0 for f in frame_metrics
        ])) if frame_metrics else 0.0
        reach_values = [
            float(v)
            for f in frame_metrics
            for v in f.get("limb_reach_error_m", {}).values()
        ]
        failure_type = next((f["failure_type"] for f in frame_metrics if f.get("failure_type")), None)
        t_fail = next((f["timestamp_ms"] for f in frame_metrics if f.get("failure_type")), None)

        return {
            "stability_score": stability_score,
            "contact_efficiency": contact_efficiency,
            "reach_error_summary": {
                "mean_reach_error_m": float(np.mean(reach_values)) if reach_values else 0.0,
                "max_reach_error_m": float(np.max(reach_values)) if reach_values else 0.0,
            },
            "frame_metrics": frame_metrics,
            "failure_type": failure_type,
            "t_fail_timestamp": t_fail,
            "meta": {"frames": len(frame_metrics)},
        }

    def _check_initialized(self) -> None:
        if not self._initialized:
            raise RuntimeError(
                "HumanoidPhysicsEngine is not initialized. "
                "Call POST /api/v1/simulation/load first."
            )


# ── 애플리케이션 전역 싱글톤 ────────────────────────────────────────────────
physics_engine = HumanoidPhysicsEngine()

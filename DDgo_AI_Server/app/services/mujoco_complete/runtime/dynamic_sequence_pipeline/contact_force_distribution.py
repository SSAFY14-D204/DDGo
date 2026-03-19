from __future__ import annotations

from collections import defaultdict
from typing import Any

import numpy as np


def _skew(vec: np.ndarray) -> np.ndarray:
    x, y, z = np.asarray(vec, dtype=np.float64)
    return np.array(
        [
            [0.0, -z, y],
            [z, 0.0, -x],
            [-y, x, 0.0],
        ],
        dtype=np.float64,
    )


def _project_step_force(force_xyz: np.ndarray, normal_xyz: np.ndarray, friction_coeff: float) -> np.ndarray:
    force_xyz = np.asarray(force_xyz, dtype=np.float64)
    normal_xyz = np.asarray(normal_xyz, dtype=np.float64)
    normal_mag = float(np.dot(force_xyz, normal_xyz))
    tangent = force_xyz - normal_mag * normal_xyz
    normal_mag = max(0.0, normal_mag)
    tangent_norm = float(np.linalg.norm(tangent))
    tangent_limit = friction_coeff * normal_mag
    if tangent_norm > tangent_limit and tangent_norm > 1e-8:
        tangent *= tangent_limit / tangent_norm
    return normal_mag * normal_xyz + tangent


def _build_wrench_matrix(root_position_xyz: np.ndarray, contact_positions_xyz: list[np.ndarray]) -> np.ndarray:
    contact_count = len(contact_positions_xyz)
    mat = np.zeros((6, 3 * contact_count), dtype=np.float64)
    for idx, contact_pos in enumerate(contact_positions_xyz):
        col = 3 * idx
        mat[0:3, col : col + 3] = np.eye(3, dtype=np.float64)
        arm = np.asarray(contact_pos, dtype=np.float64) - np.asarray(root_position_xyz, dtype=np.float64)
        mat[3:6, col : col + 3] = _skew(arm)
    return mat


def estimate_contact_forces(
    root_position_xyz: np.ndarray,
    required_wrench: np.ndarray,
    contact_positions_xyz: dict[str, np.ndarray],
    contact_modes: dict[str, str],
    friction_coeff: float = 0.8,
    regularization: float = 1e-3,
    iterations: int = 120,
) -> dict[str, Any]:
    if not contact_positions_xyz:
        return {
            "status": "no_active_contacts",
            "contact_count": 0,
            "required_wrench": np.asarray(required_wrench, dtype=np.float64).tolist(),
            "achieved_wrench": [0.0] * 6,
            "wrench_residual": np.asarray(required_wrench, dtype=np.float64).tolist(),
            "relative_residual": None,
            "wall_normal_world": None,
            "contact_forces": {},
        }

    limb_names = list(contact_positions_xyz.keys())
    positions = [np.asarray(contact_positions_xyz[name], dtype=np.float64) for name in limb_names]
    required_wrench = np.asarray(required_wrench, dtype=np.float64)
    root_position_xyz = np.asarray(root_position_xyz, dtype=np.float64)
    wrench_matrix = _build_wrench_matrix(root_position_xyz, positions)

    mean_contact_x = float(np.mean([pos[0] for pos in positions]))
    wall_sign = np.sign(root_position_xyz[0] - mean_contact_x)
    if abs(wall_sign) < 1e-8:
        wall_sign = -1.0
    wall_normal = np.array([wall_sign, 0.0, 0.0], dtype=np.float64)

    lhs = wrench_matrix.T @ wrench_matrix + regularization * np.eye(wrench_matrix.shape[1], dtype=np.float64)
    rhs = wrench_matrix.T @ required_wrench
    force_vec = np.linalg.solve(lhs, rhs)

    spectral = float(np.linalg.norm(wrench_matrix, ord=2))
    step_size = 0.5 / max(spectral * spectral + regularization, 1e-6)

    for _ in range(iterations):
        residual = wrench_matrix @ force_vec - required_wrench
        grad = 2.0 * (wrench_matrix.T @ residual) + 2.0 * regularization * force_vec
        force_vec = force_vec - step_size * grad
        for idx, limb_name in enumerate(limb_names):
            col = 3 * idx
            mode = str(contact_modes.get(limb_name, "MOVE"))
            force_xyz = force_vec[col : col + 3]
            if mode == "STEP":
                force_vec[col : col + 3] = _project_step_force(force_xyz, wall_normal, friction_coeff)
            elif mode != "GRIP":
                force_vec[col : col + 3] = 0.0

    achieved_wrench = wrench_matrix @ force_vec
    wrench_residual = required_wrench - achieved_wrench
    required_norm = float(np.linalg.norm(required_wrench))
    residual_norm = float(np.linalg.norm(wrench_residual))
    relative_residual = None if required_norm < 1e-8 else residual_norm / required_norm

    contact_forces: dict[str, Any] = {}
    for idx, limb_name in enumerate(limb_names):
        col = 3 * idx
        force_xyz = force_vec[col : col + 3].copy()
        mode = str(contact_modes.get(limb_name, "MOVE"))
        normal_n = float(np.dot(force_xyz, wall_normal)) if mode == "STEP" else None
        tangential_norm = None
        if mode == "STEP":
            tangential_vec = force_xyz - float(normal_n) * wall_normal
            tangential_norm = float(np.linalg.norm(tangential_vec))
        contact_forces[limb_name] = {
            "mode": mode,
            "position_xyz": positions[idx].tolist(),
            "force_xyz": force_xyz.tolist(),
            "force_norm_n": float(np.linalg.norm(force_xyz)),
            "normal_force_n": normal_n,
            "tangential_force_n": tangential_norm,
        }

    status = "ok"
    if relative_residual is not None and relative_residual > 0.35:
        status = "high_residual"

    return {
        "status": status,
        "contact_count": len(limb_names),
        "required_wrench": required_wrench.tolist(),
        "achieved_wrench": achieved_wrench.tolist(),
        "wrench_residual": wrench_residual.tolist(),
        "relative_residual": relative_residual,
        "wall_normal_world": wall_normal.tolist(),
        "contact_forces": contact_forces,
    }


def summarize_contact_force_history(frames: list[dict[str, Any]]) -> dict[str, Any]:
    status_counts: dict[str, int] = defaultdict(int)
    limb_force_bucket: dict[str, list[float]] = defaultdict(list)
    residuals: list[float] = []
    for frame in frames:
        payload = frame.get("contact_force_distribution")
        if not payload:
            continue
        status = str(payload.get("status"))
        status_counts[status] += 1
        rel = payload.get("relative_residual")
        if rel is not None and np.isfinite(float(rel)):
            residuals.append(float(rel))
        contact_forces = payload.get("contact_forces", {})
        for limb_name, force_payload in contact_forces.items():
            limb_force_bucket[limb_name].append(float(force_payload.get("force_norm_n", 0.0)))

    limb_force_summary: dict[str, Any] = {}
    for limb_name, values in limb_force_bucket.items():
        arr = np.asarray(values, dtype=np.float64)
        limb_force_summary[limb_name] = {
            "mean_force_norm_n": float(np.mean(arr)),
            "max_force_norm_n": float(np.max(arr)),
            "p95_force_norm_n": float(np.percentile(arr, 95)),
        }

    residual_summary = None
    if residuals:
        arr = np.asarray(residuals, dtype=np.float64)
        residual_summary = {
            "mean": float(np.mean(arr)),
            "median": float(np.median(arr)),
            "max": float(np.max(arr)),
        }

    return {
        "status_counts": dict(status_counts),
        "limb_force_summary": limb_force_summary,
        "relative_residual_summary": residual_summary,
    }

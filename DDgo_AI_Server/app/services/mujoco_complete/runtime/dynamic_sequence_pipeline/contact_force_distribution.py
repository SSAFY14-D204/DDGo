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


def decompose_contact_force(force_xyz: np.ndarray, wall_normal_xyz: np.ndarray) -> dict[str, float]:
    force_xyz = np.asarray(force_xyz, dtype=np.float64)
    wall_normal_xyz = np.asarray(wall_normal_xyz, dtype=np.float64)
    normal_component = float(np.dot(force_xyz, wall_normal_xyz))
    tangent_vec = force_xyz - normal_component * wall_normal_xyz
    return {
        "wall_normal_component_n": normal_component,
        "compressive_wall_normal_force_n": max(0.0, normal_component),
        "wall_tangential_force_n": float(np.linalg.norm(tangent_vec)),
        "lateral_force_n": float(force_xyz[1]),
        "vertical_force_n": float(force_xyz[2]),
    }


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
    contact_confidence_scores: dict[str, float] | None = None,
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
    contact_confidence_scores = contact_confidence_scores or {}

    mean_contact_x = float(np.mean([pos[0] for pos in positions]))
    wall_sign = np.sign(root_position_xyz[0] - mean_contact_x)
    if abs(wall_sign) < 1e-8:
        wall_sign = -1.0
    wall_normal = np.array([wall_sign, 0.0, 0.0], dtype=np.float64)

    regularization_diag = np.zeros(wrench_matrix.shape[1], dtype=np.float64)
    regularization_scales: dict[str, float] = {}
    mode_bias_scales: dict[str, float] = {}
    axis_regularization_scale_xyz: dict[str, list[float]] = {}
    for idx, limb_name in enumerate(limb_names):
        confidence_score = float(np.clip(contact_confidence_scores.get(limb_name, 1.0), 0.25, 1.0))
        mode = str(contact_modes.get(limb_name, "MOVE"))
        # Confidence weighting stays intentionally mild so good support frames do not drift.
        penalty_scale = float(np.interp(confidence_score, [0.25, 0.55, 0.75, 1.0], [1.35, 1.20, 1.08, 1.00]))
        mode_bias_scale = 1.0
        if mode == "STEP":
            mode_bias_scale = 1.0
        elif mode == "GRIP":
            mode_bias_scale = 1.0
        axis_scale = np.ones(3, dtype=np.float64)
        if mode == "STEP":
            # STEP is expected to receive more compressive wall-normal load than tangential load.
            axis_scale = np.array([0.68, 0.92, 0.92], dtype=np.float64)
        elif mode == "GRIP":
            # GRIP remains usable, but wall-normal and vertical dominance should be a bit more expensive.
            axis_scale = np.array([1.22, 1.00, 1.10], dtype=np.float64)

        diag_values = regularization * penalty_scale * mode_bias_scale * axis_scale
        regularization_diag[3 * idx : 3 * idx + 3] = diag_values
        regularization_scales[limb_name] = float(np.mean(diag_values) / max(regularization, 1e-12))
        mode_bias_scales[limb_name] = mode_bias_scale
        axis_regularization_scale_xyz[limb_name] = diag_values.tolist()

    lhs = wrench_matrix.T @ wrench_matrix + np.diag(regularization_diag)
    rhs = wrench_matrix.T @ required_wrench
    force_vec = np.linalg.solve(lhs, rhs)

    spectral = float(np.linalg.norm(wrench_matrix, ord=2))
    step_size = 0.5 / max(spectral * spectral + float(np.max(regularization_diag)), 1e-6)

    for _ in range(iterations):
        residual = wrench_matrix @ force_vec - required_wrench
        grad = 2.0 * (wrench_matrix.T @ residual) + 2.0 * regularization_diag * force_vec
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
        decomposition = decompose_contact_force(force_xyz, wall_normal)
        normal_n = decomposition["compressive_wall_normal_force_n"] if mode == "STEP" else None
        tangential_norm = decomposition["wall_tangential_force_n"] if mode == "STEP" else None
        contact_forces[limb_name] = {
            "mode": mode,
            "position_xyz": positions[idx].tolist(),
            "force_xyz": force_xyz.tolist(),
            "force_norm_n": float(np.linalg.norm(force_xyz)),
            "confidence_score": float(np.clip(contact_confidence_scores.get(limb_name, 1.0), 0.25, 1.0)),
            "mode_bias_scale": float(mode_bias_scales.get(limb_name, 1.0)),
            "regularization_scale": float(regularization_scales.get(limb_name, 1.0)),
            "axis_regularization_scale_xyz": axis_regularization_scale_xyz.get(limb_name, [regularization] * 3),
            "normal_force_n": normal_n,
            "tangential_force_n": tangential_norm,
            **decomposition,
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
        "contact_confidence_scores": {
            limb_name: float(np.clip(contact_confidence_scores.get(limb_name, 1.0), 0.25, 1.0))
            for limb_name in limb_names
        },
        "contact_regularization_scales": regularization_scales,
        "contact_mode_bias_scales": mode_bias_scales,
        "contact_axis_regularization_scale_xyz": axis_regularization_scale_xyz,
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

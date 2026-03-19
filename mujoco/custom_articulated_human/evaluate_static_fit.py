from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

import cv2
import mediapipe as mp
import mujoco
import numpy as np

ROOT = Path(__file__).resolve().parent
CUSTOM_SKELETON_ROOT = ROOT.parent / "custom_skeleton_verify"
DEFAULT_PERSONALIZED_XML = ROOT / "custom_articulated_human_personalized.xml"
DEFAULT_XML = DEFAULT_PERSONALIZED_XML if DEFAULT_PERSONALIZED_XML.exists() else ROOT / "custom_articulated_human.xml"
sys.path.insert(0, str(CUSTOM_SKELETON_ROOT))

from mediapipe_custom_skeleton_verify import (  # noqa: E402
    MetricSkeletonMapper,
    make_landmarker,
)
from physics_worker import load_calibration_json  # noqa: E402

SITE_TARGETS: dict[str, tuple[str, float]] = {
    "pelvis_site": ("pelvis", 2.0),
    "thorax_site": ("thorax", 2.0),
    "left_shoulder_site": ("left_shoulder", 1.4),
    "right_shoulder_site": ("right_shoulder", 1.4),
    "left_elbow_site": ("left_elbow", 1.1),
    "right_elbow_site": ("right_elbow", 1.1),
    "left_hand_site": ("left_hand", 1.3),
    "right_hand_site": ("right_hand", 1.3),
    "left_hip_site": ("left_hip", 1.30),
    "right_hip_site": ("right_hip", 1.30),
    "left_knee_site": ("left_knee", 1.20),
    "right_knee_site": ("right_knee", 1.20),
    "left_ankle_site": ("left_ankle", 1.00),
    "right_ankle_site": ("right_ankle", 1.00),
    "left_foot_site": ("left_foot", 1.25),
    "right_foot_site": ("right_foot", 1.25),
}

POLE_TARGETS: dict[str, tuple[str, float]] = {
    "left_elbow_pole_site": ("left_elbow_pole", 0.55),
    "right_elbow_pole_site": ("right_elbow_pole", 0.55),
    "left_knee_pole_site": ("left_knee_pole", 0.55),
    "right_knee_pole_site": ("right_knee_pole", 0.55),
}

AUX_SITE_TARGETS: dict[str, tuple[str, float]] = {}
AUX_SITE_TARGETS = {
    "left_thigh_lateral_site": ("left_thigh_lateral", 0.42),
    "right_thigh_lateral_site": ("right_thigh_lateral", 0.42),
    "left_knee_forward_site": ("left_knee_forward", 0.12),
    "right_knee_forward_site": ("right_knee_forward", 0.12),
    "left_heel_site": ("left_heel", 0.12),
    "right_heel_site": ("right_heel", 0.12),
}

ROOT_BLEND_ALPHA = 0.35
JOINT_TEMPORAL_REG = 0.18
ROOT_TEMPORAL_REG = 0.03
MAX_DQ_COMPONENT = 0.12
KNEE_TARGET_REG = 0.22
HIP_NEUTRAL_REG = 0.045
HIP_TWIST_REG = 0.140
ANKLE_NEUTRAL_REG = 0.110

PASS_THRESHOLDS_M = {
    "overall_mean_error_m": 0.12,
    "hands_mean_error_m": 0.18,
    "feet_mean_error_m": 0.18,
    "major_joint_mean_error_m": 0.10,
}


def normalize(v: np.ndarray, eps: float = 1e-8) -> np.ndarray:
    arr = np.asarray(v, dtype=np.float64)
    n = float(np.linalg.norm(arr))
    if n < eps:
        return np.zeros_like(arr)
    return arr / n


def quat_from_rotation_matrix(rot: np.ndarray) -> np.ndarray:
    m = np.asarray(rot, dtype=np.float64)
    trace = float(np.trace(m))
    if trace > 0.0:
        s = np.sqrt(trace + 1.0) * 2.0
        return np.array(
            [
                0.25 * s,
                (m[2, 1] - m[1, 2]) / s,
                (m[0, 2] - m[2, 0]) / s,
                (m[1, 0] - m[0, 1]) / s,
            ],
            dtype=np.float64,
        )
    idx = int(np.argmax(np.diag(m)))
    if idx == 0:
        s = np.sqrt(1.0 + m[0, 0] - m[1, 1] - m[2, 2]) * 2.0
        quat = np.array(
            [
                (m[2, 1] - m[1, 2]) / s,
                0.25 * s,
                (m[0, 1] + m[1, 0]) / s,
                (m[0, 2] + m[2, 0]) / s,
            ],
            dtype=np.float64,
        )
    elif idx == 1:
        s = np.sqrt(1.0 + m[1, 1] - m[0, 0] - m[2, 2]) * 2.0
        quat = np.array(
            [
                (m[0, 2] - m[2, 0]) / s,
                (m[0, 1] + m[1, 0]) / s,
                0.25 * s,
                (m[1, 2] + m[2, 1]) / s,
            ],
            dtype=np.float64,
        )
    else:
        s = np.sqrt(1.0 + m[2, 2] - m[0, 0] - m[1, 1]) * 2.0
        quat = np.array(
            [
                (m[1, 0] - m[0, 1]) / s,
                (m[0, 2] + m[2, 0]) / s,
                (m[1, 2] + m[2, 1]) / s,
                0.25 * s,
            ],
            dtype=np.float64,
        )
    return quat / max(np.linalg.norm(quat), 1e-8)


def nlerp_quat(q0: np.ndarray, q1: np.ndarray, alpha: float) -> np.ndarray:
    qa = np.asarray(q0, dtype=np.float64)
    qb = np.asarray(q1, dtype=np.float64)
    if float(np.dot(qa, qb)) < 0.0:
        qb = -qb
    out = (1.0 - alpha) * qa + alpha * qb
    norm = float(np.linalg.norm(out))
    if norm < 1e-8:
        return qa
    return out / norm


def root_pose_from_targets(points: dict[str, np.ndarray]) -> tuple[np.ndarray, np.ndarray]:
    pelvis = np.asarray(points["pelvis"], dtype=np.float64)
    thorax = np.asarray(points["thorax"], dtype=np.float64)
    up_axis = normalize(thorax - pelvis)

    hip_axis = np.asarray(points["left_hip"], dtype=np.float64) - np.asarray(points["right_hip"], dtype=np.float64)
    shoulder_axis = np.asarray(points["left_shoulder"], dtype=np.float64) - np.asarray(points["right_shoulder"], dtype=np.float64)
    left_axis = normalize(0.60 * hip_axis + 0.40 * shoulder_axis)
    if float(np.linalg.norm(left_axis)) < 1e-6:
        left_axis = np.array([0.0, 1.0, 0.0], dtype=np.float64)

    forward_axis = normalize(np.cross(left_axis, up_axis))
    if float(np.linalg.norm(forward_axis)) < 1e-6:
        forward_axis = np.array([1.0, 0.0, 0.0], dtype=np.float64)

    left_axis = normalize(np.cross(up_axis, forward_axis))
    rot = np.column_stack([forward_axis, left_axis, up_axis])
    if np.linalg.det(rot) < 0.0:
        rot[:, 0] *= -1.0
    quat = quat_from_rotation_matrix(rot)
    return pelvis, quat


def clip_qpos_to_joint_ranges(model: mujoco.MjModel, qpos: np.ndarray) -> None:
    for jnt_id in range(model.njnt):
        jnt_type = int(model.jnt_type[jnt_id])
        if jnt_type not in (mujoco.mjtJoint.mjJNT_HINGE, mujoco.mjtJoint.mjJNT_SLIDE):
            continue
        if not bool(model.jnt_limited[jnt_id]):
            continue
        qpos_adr = int(model.jnt_qposadr[jnt_id])
        low = float(model.jnt_range[jnt_id, 0])
        high = float(model.jnt_range[jnt_id, 1])
        qpos[qpos_adr] = float(np.clip(qpos[qpos_adr], low, high))


def build_pole_targets(points: dict[str, np.ndarray]) -> dict[str, np.ndarray]:
    pelvis = np.asarray(points["pelvis"], dtype=np.float64)
    thorax = np.asarray(points["thorax"], dtype=np.float64)
    up_axis = normalize(thorax - pelvis)
    hip_axis = np.asarray(points["left_hip"], dtype=np.float64) - np.asarray(points["right_hip"], dtype=np.float64)
    shoulder_axis = np.asarray(points["left_shoulder"], dtype=np.float64) - np.asarray(points["right_shoulder"], dtype=np.float64)
    left_axis = normalize(0.60 * hip_axis + 0.40 * shoulder_axis)
    if float(np.linalg.norm(left_axis)) < 1e-8:
        left_axis = np.array([0.0, 1.0, 0.0], dtype=np.float64)
    forward_axis = normalize(np.cross(left_axis, up_axis))
    if float(np.linalg.norm(forward_axis)) < 1e-8:
        forward_axis = np.array([1.0, 0.0, 0.0], dtype=np.float64)

    def stance_forward(side: str) -> np.ndarray:
        heel = np.asarray(points[f"{side}_heel"], dtype=np.float64)
        toe = np.asarray(points[f"{side}_foot"], dtype=np.float64)
        foot_vec = toe - heel
        flat = foot_vec - float(np.dot(foot_vec, up_axis)) * up_axis
        if float(np.linalg.norm(flat)) < 1e-8:
            return forward_axis
        flat_norm = float(np.linalg.norm(flat))
        vertical_score = compute_thigh_vertical_score(points, side)
        side_limit = (0.30 - 0.22 * vertical_score) * flat_norm
        side_component = float(np.clip(np.dot(flat, left_axis), -side_limit, side_limit))
        forward_component = max(abs(float(np.dot(flat, forward_axis))), (0.55 + 0.25 * vertical_score) * flat_norm)
        biased = normalize(forward_axis * forward_component + left_axis * side_component)
        if float(np.dot(biased, forward_axis)) < 0.0:
            biased = -biased
        return biased

    def make_pole(anchor_a: np.ndarray, joint: np.ndarray, anchor_b: np.ndarray, fallback: np.ndarray, offset: float) -> np.ndarray:
        chain = anchor_b - anchor_a
        chain_norm = float(np.linalg.norm(chain))
        if chain_norm < 1e-8:
            return joint + normalize(fallback) * offset
        chain_dir = chain / chain_norm
        proj = anchor_a + float(np.dot(joint - anchor_a, chain_dir)) * chain_dir
        pole = joint - proj
        if float(np.linalg.norm(pole)) < 1e-8:
            pole = fallback
        pole = normalize(pole)
        if float(np.dot(pole, fallback)) < 0.0:
            pole = -pole
        return joint + pole * offset

    elbow_left_pref = normalize(0.80 * left_axis - 0.35 * up_axis)
    elbow_right_pref = normalize(-0.80 * left_axis - 0.35 * up_axis)
    left_stance_forward = stance_forward("left")
    right_stance_forward = stance_forward("right")
    knee_left_pref = normalize(0.01 * left_axis + 0.995 * left_stance_forward - 0.03 * up_axis)
    knee_right_pref = normalize(-0.01 * left_axis + 0.995 * right_stance_forward - 0.03 * up_axis)

    return {
        "left_elbow_pole": make_pole(points["left_shoulder"], points["left_elbow"], points["left_hand"], elbow_left_pref, 0.08),
        "right_elbow_pole": make_pole(points["right_shoulder"], points["right_elbow"], points["right_hand"], elbow_right_pref, 0.08),
        "left_knee_pole": make_pole(points["left_hip"], points["left_knee"], points["left_ankle"], knee_left_pref, 0.10),
        "right_knee_pole": make_pole(points["right_hip"], points["right_knee"], points["right_ankle"], knee_right_pref, 0.10),
    }


def build_aux_targets(points: dict[str, np.ndarray]) -> dict[str, np.ndarray]:
    pelvis = np.asarray(points["pelvis"], dtype=np.float64)
    thorax = np.asarray(points["thorax"], dtype=np.float64)
    up_axis = normalize(thorax - pelvis)
    hip_axis = np.asarray(points["left_hip"], dtype=np.float64) - np.asarray(points["right_hip"], dtype=np.float64)
    shoulder_axis = np.asarray(points["left_shoulder"], dtype=np.float64) - np.asarray(points["right_shoulder"], dtype=np.float64)
    left_axis = normalize(0.60 * hip_axis + 0.40 * shoulder_axis)
    if float(np.linalg.norm(left_axis)) < 1e-8:
        left_axis = np.array([0.0, 1.0, 0.0], dtype=np.float64)
    forward_axis = normalize(np.cross(left_axis, up_axis))
    if float(np.linalg.norm(forward_axis)) < 1e-8:
        forward_axis = np.array([1.0, 0.0, 0.0], dtype=np.float64)

    def stance_forward(side: str) -> np.ndarray:
        heel = np.asarray(points[f"{side}_heel"], dtype=np.float64)
        toe = np.asarray(points[f"{side}_foot"], dtype=np.float64)
        foot_vec = toe - heel
        flat = foot_vec - float(np.dot(foot_vec, up_axis)) * up_axis
        if float(np.linalg.norm(flat)) < 1e-8:
            return forward_axis
        flat_norm = float(np.linalg.norm(flat))
        vertical_score = compute_thigh_vertical_score(points, side)
        side_limit = (0.30 - 0.22 * vertical_score) * flat_norm
        side_component = float(np.clip(np.dot(flat, left_axis), -side_limit, side_limit))
        forward_component = max(abs(float(np.dot(flat, forward_axis))), (0.55 + 0.25 * vertical_score) * flat_norm)
        biased = normalize(forward_axis * forward_component + left_axis * side_component)
        if float(np.dot(biased, forward_axis)) < 0.0:
            biased = -biased
        return biased

    def make_thigh_lateral(hip: np.ndarray, knee: np.ndarray, side_sign: float) -> np.ndarray:
        thigh = np.asarray(knee, dtype=np.float64) - np.asarray(hip, dtype=np.float64)
        thigh_len = float(np.linalg.norm(thigh))
        if thigh_len < 1e-8:
            thigh_dir = np.array([0.0, 0.0, -1.0], dtype=np.float64)
        else:
            thigh_dir = thigh / thigh_len
        side_name = "left" if side_sign > 0 else "right"
        vertical_score = compute_thigh_vertical_score(points, side_name)
        side_forward = stance_forward(side_name)
        outward = (1.0 - 0.85 * vertical_score) * side_sign * left_axis + 0.08 * side_forward
        lateral = outward - float(np.dot(outward, thigh_dir)) * thigh_dir
        if float(np.linalg.norm(lateral)) < 1e-8:
            lateral = np.cross(thigh_dir, side_forward)
        lateral = normalize(lateral)
        return np.asarray(hip, dtype=np.float64) + 0.46 * thigh + lateral * 0.055

    def make_knee_forward(side: str) -> np.ndarray:
        knee = np.asarray(points[f"{side}_knee"], dtype=np.float64)
        bend_score = compute_knee_flex_score(points, side)
        vertical_score = compute_thigh_vertical_score(points, side)
        side_forward = stance_forward(side)
        offset = 0.038 + 0.060 * bend_score + 0.055 * vertical_score
        return knee + side_forward * offset - up_axis * (0.004 * bend_score)

    return {
        "left_thigh_lateral": make_thigh_lateral(points["left_hip"], points["left_knee"], 1.0),
        "right_thigh_lateral": make_thigh_lateral(points["right_hip"], points["right_knee"], -1.0),
        "left_knee_forward": make_knee_forward("left"),
        "right_knee_forward": make_knee_forward("right"),
        "left_heel": np.asarray(points["left_heel"], dtype=np.float64),
        "right_heel": np.asarray(points["right_heel"], dtype=np.float64),
    }


def compute_knee_flexion_target(points: dict[str, np.ndarray], side: str) -> float:
    hip = np.asarray(points[f"{side}_hip"], dtype=np.float64)
    knee = np.asarray(points[f"{side}_knee"], dtype=np.float64)
    ankle = np.asarray(points[f"{side}_ankle"], dtype=np.float64)
    thigh = hip - knee
    shank = ankle - knee
    thigh_norm = float(np.linalg.norm(thigh))
    shank_norm = float(np.linalg.norm(shank))
    if thigh_norm < 1e-8 or shank_norm < 1e-8:
        return 0.0
    angle = float(np.arccos(np.clip(np.dot(thigh / thigh_norm, shank / shank_norm), -1.0, 1.0)))
    return -(np.pi - angle)


def compute_knee_flex_score(points: dict[str, np.ndarray], side: str) -> float:
    target = abs(compute_knee_flexion_target(points, side))
    return float(np.clip((target - np.deg2rad(10.0)) / np.deg2rad(45.0), 0.0, 1.0))


def compute_thigh_vertical_score(points: dict[str, np.ndarray], side: str) -> float:
    pelvis = np.asarray(points["pelvis"], dtype=np.float64)
    thorax = np.asarray(points["thorax"], dtype=np.float64)
    up_axis = normalize(thorax - pelvis)
    hip = np.asarray(points[f"{side}_hip"], dtype=np.float64)
    knee = np.asarray(points[f"{side}_knee"], dtype=np.float64)
    thigh = knee - hip
    thigh_norm = float(np.linalg.norm(thigh))
    if thigh_norm < 1e-8:
        return 0.0
    verticality = abs(float(np.dot(thigh / thigh_norm, up_axis)))
    return float(np.clip((verticality - 0.60) / 0.30, 0.0, 1.0))


def enforce_lower_limb_forward_consistency(points: dict[str, np.ndarray]) -> dict[str, np.ndarray]:
    adjusted = {key: np.asarray(value, dtype=np.float64).copy() for key, value in points.items()}
    pelvis = adjusted["pelvis"]
    thorax = adjusted["thorax"]
    up_axis = normalize(thorax - pelvis)
    hip_axis = adjusted["left_hip"] - adjusted["right_hip"]
    shoulder_axis = adjusted["left_shoulder"] - adjusted["right_shoulder"]
    left_axis = normalize(0.60 * hip_axis + 0.40 * shoulder_axis)
    if float(np.linalg.norm(left_axis)) < 1e-8:
        left_axis = np.array([0.0, 1.0, 0.0], dtype=np.float64)
    forward_axis = normalize(np.cross(left_axis, up_axis))
    if float(np.linalg.norm(forward_axis)) < 1e-8:
        forward_axis = np.array([1.0, 0.0, 0.0], dtype=np.float64)

    for side, side_sign in (("left", 1.0), ("right", -1.0)):
        ankle = adjusted[f"{side}_ankle"]
        heel = adjusted[f"{side}_heel"]
        toe = adjusted[f"{side}_foot"]
        toe_rel = toe - ankle
        heel_rel = heel - ankle
        flat = (toe - heel) - float(np.dot(toe - heel, up_axis)) * up_axis
        if float(np.linalg.norm(flat)) < 1e-8:
            stance_forward = forward_axis
        else:
            flat_norm = float(np.linalg.norm(flat))
            side_component = float(np.dot(flat, left_axis))
            forward_component = max(abs(float(np.dot(flat, forward_axis))), 0.60 * flat_norm)
            stance_forward = normalize(forward_axis * forward_component + left_axis * side_component)
            if float(np.dot(stance_forward, forward_axis)) < 0.0:
                stance_forward = -stance_forward

        vertical_score = compute_thigh_vertical_score(adjusted, side)
        mean_side = 0.5 * (float(np.dot(toe_rel, left_axis)) + float(np.dot(heel_rel, left_axis)))
        mean_side = float(np.clip(mean_side, -0.012, 0.012))
        toe_forward = max(
            0.11,
            abs(float(np.dot(toe_rel, stance_forward))),
            (0.86 + 0.10 * vertical_score) * float(np.linalg.norm(toe_rel - float(np.dot(toe_rel, up_axis)) * up_axis)),
        )
        heel_back = max(0.04, abs(float(np.dot(heel_rel, stance_forward))), 0.32 * toe_forward)
        toe_up = float(np.dot(toe_rel, up_axis))
        heel_up = float(np.dot(heel_rel, up_axis))

        side_offset = left_axis * mean_side + left_axis * (0.006 * side_sign)
        adjusted[f"{side}_foot"] = ankle + stance_forward * toe_forward + up_axis * toe_up + side_offset
        adjusted[f"{side}_heel"] = ankle - stance_forward * heel_back + up_axis * heel_up + side_offset

    return adjusted


def build_dynamic_site_weights(points: dict[str, np.ndarray]) -> dict[str, float]:
    pelvis = np.asarray(points["pelvis"], dtype=np.float64)
    thorax = np.asarray(points["thorax"], dtype=np.float64)
    up_axis = normalize(thorax - pelvis)
    weights: dict[str, float] = {}

    for side in ("left", "right"):
        heel = np.asarray(points[f"{side}_heel"], dtype=np.float64)
        toe = np.asarray(points[f"{side}_foot"], dtype=np.float64)
        heel_height = float(np.dot(heel, up_axis))
        toe_height = float(np.dot(toe, up_axis))
        heel_above_toe = heel_height - toe_height
        toe_stand_score = float(np.clip((heel_above_toe - 0.015) / 0.05, 0.0, 1.0))

        knee_bend_score = compute_knee_flex_score(points, side)
        thigh_vertical_score = compute_thigh_vertical_score(points, side)

        weights[f"{side}_knee_site"] = 1.28 + 0.62 * knee_bend_score + 0.10 * thigh_vertical_score
        weights[f"{side}_ankle_site"] = 1.00 + 0.22 * knee_bend_score
        weights[f"{side}_foot_site"] = 1.05 + 0.20 * toe_stand_score + 0.12 * knee_bend_score
        weights[f"{side}_heel_site"] = 0.10 + 0.22 * (1.0 - toe_stand_score) * knee_bend_score
        weights[f"{side}_thigh_lateral_site"] = max(
            0.08,
            0.12 * (1.0 - 0.88 * knee_bend_score) * (1.0 - 0.92 * thigh_vertical_score),
        )
        weights[f"{side}_knee_forward_site"] = 0.24 + 0.32 * knee_bend_score + 0.34 * thigh_vertical_score

    return weights


def compute_toe_stand_score(points: dict[str, np.ndarray], side: str) -> float:
    pelvis = np.asarray(points["pelvis"], dtype=np.float64)
    thorax = np.asarray(points["thorax"], dtype=np.float64)
    up_axis = normalize(thorax - pelvis)
    heel = np.asarray(points[f"{side}_heel"], dtype=np.float64)
    toe = np.asarray(points[f"{side}_foot"], dtype=np.float64)
    heel_height = float(np.dot(heel, up_axis))
    toe_height = float(np.dot(toe, up_axis))
    heel_above_toe = heel_height - toe_height
    return float(np.clip((heel_above_toe - 0.015) / 0.05, 0.0, 1.0))


def build_joint_priors(
    model: mujoco.MjModel,
    target_points: dict[str, np.ndarray],
) -> list[tuple[int, int, float, float]]:
    joint_targets: list[tuple[int, int, float, float]] = []
    joint_ids = {
        name: mujoco.mj_name2id(model, mujoco.mjtObj.mjOBJ_JOINT, name)
        for name in (
            "hip_x_left",
            "hip_x_right",
            "hip_z_left",
            "hip_z_right",
            "knee_left",
            "knee_right",
            "ankle_y_left",
            "ankle_y_right",
            "ankle_x_left",
            "ankle_x_right",
        )
    }

    for side in ("left", "right"):
        knee_name = f"knee_{side}"
        knee_jid = joint_ids[knee_name]
        qpos_adr = int(model.jnt_qposadr[knee_jid])
        dof_adr = int(model.jnt_dofadr[knee_jid])
        low = float(model.jnt_range[knee_jid, 0])
        high = float(model.jnt_range[knee_jid, 1])
        target = float(np.clip(compute_knee_flexion_target(target_points, side), low, high))
        flex_score = compute_knee_flex_score(target_points, side)
        vertical_score = compute_thigh_vertical_score(target_points, side)
        min_forward_flex = -np.deg2rad(4.0 + 12.0 * vertical_score)
        target = min(target, min_forward_flex)
        knee_abs = abs(target)
        if knee_abs > np.deg2rad(10.0):
            weight = KNEE_TARGET_REG * (1.0 + 0.6 * flex_score)
            joint_targets.append((qpos_adr, dof_adr, target, weight))
        else:
            joint_targets.append((qpos_adr, dof_adr, target, KNEE_TARGET_REG * (0.18 + 0.35 * vertical_score)))

        # Keep hip abduction/adduction from taking over crouch. Also add a mild
        # axial-twist prior when the thigh is close to vertical so the knee and
        # foot keep facing broadly forward instead of rotating sideways/backward.
        hip_x_jid = joint_ids[f"hip_x_{side}"]
        hip_x_qpos_adr = int(model.jnt_qposadr[hip_x_jid])
        hip_x_dof_adr = int(model.jnt_dofadr[hip_x_jid])
        joint_targets.append((hip_x_qpos_adr, hip_x_dof_adr, 0.0, HIP_NEUTRAL_REG * (1.0 + 0.65 * flex_score)))
        hip_z_jid = joint_ids[f"hip_z_{side}"]
        hip_z_qpos_adr = int(model.jnt_qposadr[hip_z_jid])
        hip_z_dof_adr = int(model.jnt_dofadr[hip_z_jid])
        hip_z_weight = HIP_TWIST_REG * (0.55 + 1.75 * vertical_score) * (1.0 - 0.10 * flex_score)
        joint_targets.append((hip_z_qpos_adr, hip_z_dof_adr, 0.0, hip_z_weight))

        toe_stand_score = compute_toe_stand_score(target_points, side)
        ankle_y_jid = joint_ids[f"ankle_y_{side}"]
        ankle_y_qpos_adr = int(model.jnt_qposadr[ankle_y_jid])
        ankle_y_dof_adr = int(model.jnt_dofadr[ankle_y_jid])
        ankle_y_weight = ANKLE_NEUTRAL_REG * (1.0 - 0.72 * toe_stand_score) * (1.0 - 0.80 * flex_score)
        if ankle_y_weight > 1e-6:
            joint_targets.append((ankle_y_qpos_adr, ankle_y_dof_adr, 0.0, ankle_y_weight))

        ankle_x_jid = joint_ids[f"ankle_x_{side}"]
        ankle_x_qpos_adr = int(model.jnt_qposadr[ankle_x_jid])
        ankle_x_dof_adr = int(model.jnt_dofadr[ankle_x_jid])
        joint_targets.append((ankle_x_qpos_adr, ankle_x_dof_adr, 0.0, ANKLE_NEUTRAL_REG * 0.65))

    return joint_targets


def lower_limb_forward_consistency_metrics(
    model: mujoco.MjModel,
    data: mujoco.MjData,
    site_ids: dict[str, int],
) -> dict[str, dict[str, float]]:
    pelvis_id = mujoco.mj_name2id(model, mujoco.mjtObj.mjOBJ_BODY, "pelvis")
    pelvis_forward = normalize(data.xmat[pelvis_id].reshape(3, 3)[:, 0].copy())

    def per_side(side: str) -> dict[str, float]:
        hip = data.site_xpos[site_ids[f"{side}_hip_site"]].copy()
        knee = data.site_xpos[site_ids[f"{side}_knee_site"]].copy()
        ankle = data.site_xpos[site_ids[f"{side}_ankle_site"]].copy()
        heel = data.site_xpos[site_ids[f"{side}_heel_site"]].copy()
        foot = data.site_xpos[site_ids[f"{side}_foot_site"]].copy()
        foot_forward = normalize(foot - heel)

        thigh_dir = normalize(knee - hip)
        shank_dir = normalize(ankle - knee)
        extension_dir = -thigh_dir
        bend_vec = shank_dir - float(np.dot(shank_dir, extension_dir)) * extension_dir
        bend_dir = normalize(bend_vec)
        foot_proj = foot_forward - float(np.dot(foot_forward, extension_dir)) * extension_dir
        foot_dir = normalize(foot_proj)

        return {
            "bend_alignment_dot": float(np.dot(bend_dir, foot_dir)),
            "bend_norm": float(np.linalg.norm(bend_vec)),
            "foot_vs_pelvis_forward_dot": float(np.dot(foot_forward, pelvis_forward)),
        }

    return {
        "left": per_side("left"),
        "right": per_side("right"),
    }


def sample_frame_indices(frame_count: int, sample_count: int) -> list[int]:
    if frame_count <= 0:
        return []
    sample_count = max(1, min(frame_count, sample_count))
    return sorted({int(round(v)) for v in np.linspace(0, frame_count - 1, sample_count)})


def fit_static_pose(
    model: mujoco.MjModel,
    data: mujoco.MjData,
    site_ids: dict[str, int],
    target_points: dict[str, np.ndarray],
    seed_qpos: np.ndarray | None,
    iterations: int,
    damping: float,
) -> dict[str, object]:
    target_points = enforce_lower_limb_forward_consistency(target_points)
    mujoco.mj_resetData(model, data)
    if seed_qpos is not None:
        data.qpos[:] = seed_qpos
    root_pos, root_quat = root_pose_from_targets(target_points)
    if seed_qpos is not None:
        root_pos = (1.0 - ROOT_BLEND_ALPHA) * seed_qpos[0:3] + ROOT_BLEND_ALPHA * root_pos
        root_quat = nlerp_quat(seed_qpos[3:7], root_quat, ROOT_BLEND_ALPHA)
    data.qpos[0:3] = root_pos
    data.qpos[3:7] = root_quat
    data.qvel[:] = 0.0
    clip_qpos_to_joint_ranges(model, data.qpos)
    mujoco.mj_forward(model, data)

    pole_targets = build_pole_targets(target_points)
    aux_targets = build_aux_targets(target_points)
    dynamic_site_weights = build_dynamic_site_weights(target_points)
    temporal_targets: list[tuple[int, int, float, float]] = []
    if seed_qpos is not None:
        for jnt_id in range(model.njnt):
            jnt_type = int(model.jnt_type[jnt_id])
            if jnt_type not in (mujoco.mjtJoint.mjJNT_HINGE, mujoco.mjtJoint.mjJNT_SLIDE):
                continue
            qpos_adr = int(model.jnt_qposadr[jnt_id])
            dof_adr = int(model.jnt_dofadr[jnt_id])
            jnt_name = mujoco.mj_id2name(model, mujoco.mjtObj.mjOBJ_JOINT, jnt_id) or ""
            weight = ROOT_TEMPORAL_REG if jnt_name.startswith("abdomen") or jnt_name.startswith("neck") else JOINT_TEMPORAL_REG
            temporal_targets.append((qpos_adr, dof_adr, float(seed_qpos[qpos_adr]), weight))
    temporal_targets.extend(build_joint_priors(model, target_points))

    last_err_norm = None
    for _ in range(iterations):
        err_blocks: list[np.ndarray] = []
        jac_blocks: list[np.ndarray] = []
        for site_name, (target_key, base_weight) in SITE_TARGETS.items():
            weight = float(dynamic_site_weights.get(site_name, base_weight))
            site_id = site_ids[site_name]
            current = data.site_xpos[site_id].copy()
            target = np.asarray(target_points[target_key], dtype=np.float64)
            err = (target - current) * weight
            jacp = np.zeros((3, model.nv), dtype=np.float64)
            jacr = np.zeros((3, model.nv), dtype=np.float64)
            mujoco.mj_jacSite(model, data, jacp, jacr, site_id)
            err_blocks.append(err)
            jac_blocks.append(jacp * weight)

        for site_name, (target_key, weight) in POLE_TARGETS.items():
            site_id = site_ids[site_name]
            current = data.site_xpos[site_id].copy()
            target = np.asarray(pole_targets[target_key], dtype=np.float64)
            err = (target - current) * weight
            jacp = np.zeros((3, model.nv), dtype=np.float64)
            jacr = np.zeros((3, model.nv), dtype=np.float64)
            mujoco.mj_jacSite(model, data, jacp, jacr, site_id)
            err_blocks.append(err)
            jac_blocks.append(jacp * weight)

        for site_name, (target_key, base_weight) in AUX_SITE_TARGETS.items():
            weight = float(dynamic_site_weights.get(site_name, base_weight))
            site_id = site_ids[site_name]
            current = data.site_xpos[site_id].copy()
            target = np.asarray(aux_targets[target_key], dtype=np.float64)
            err = (target - current) * weight
            jacp = np.zeros((3, model.nv), dtype=np.float64)
            jacr = np.zeros((3, model.nv), dtype=np.float64)
            mujoco.mj_jacSite(model, data, jacp, jacr, site_id)
            err_blocks.append(err)
            jac_blocks.append(jacp * weight)

        for qpos_adr, dof_adr, target_qpos, weight in temporal_targets:
            err = np.array([(target_qpos - float(data.qpos[qpos_adr])) * weight], dtype=np.float64)
            jac = np.zeros((1, model.nv), dtype=np.float64)
            jac[0, dof_adr] = weight
            err_blocks.append(err)
            jac_blocks.append(jac)

        err_vec = np.concatenate(err_blocks, axis=0)
        err_norm = float(np.linalg.norm(err_vec))
        last_err_norm = err_norm
        if err_norm < 1e-4:
            break

        jac = np.vstack(jac_blocks)
        lhs = jac.T @ jac + damping * np.eye(model.nv, dtype=np.float64)
        rhs = jac.T @ err_vec
        dq = np.linalg.solve(lhs, rhs)
        max_component = float(np.max(np.abs(dq)))
        if max_component > MAX_DQ_COMPONENT:
            dq *= MAX_DQ_COMPONENT / max_component

        mujoco.mj_integratePos(model, data.qpos, dq, 1.0)
        clip_qpos_to_joint_ranges(model, data.qpos)
        data.qvel[:] = 0.0
        mujoco.mj_forward(model, data)

    per_target_errors: dict[str, float] = {}
    for site_name, (target_key, _) in SITE_TARGETS.items():
        site_id = site_ids[site_name]
        current = data.site_xpos[site_id].copy()
        target = np.asarray(target_points[target_key], dtype=np.float64)
        per_target_errors[target_key] = float(np.linalg.norm(target - current))

    error_values = np.array(list(per_target_errors.values()), dtype=np.float64)
    lower_limb_consistency = lower_limb_forward_consistency_metrics(model, data, site_ids)
    return {
        "qpos": data.qpos.copy(),
        "mean_error_m": float(np.mean(error_values)),
        "max_error_m": float(np.max(error_values)),
        "per_target_errors_m": per_target_errors,
        "final_error_norm": float(last_err_norm or 0.0),
        "lower_limb_consistency": lower_limb_consistency,
    }


def summarize_site_errors(sample_reports: list[dict[str, object]]) -> dict[str, dict[str, float]]:
    per_key: dict[str, list[float]] = {}
    for report in sample_reports:
        errors = report["per_target_errors_m"]
        for key, value in errors.items():
            per_key.setdefault(key, []).append(float(value))

    summary: dict[str, dict[str, float]] = {}
    for key, values in per_key.items():
        arr = np.asarray(values, dtype=np.float64)
        summary[key] = {
            "mean_error_m": float(np.mean(arr)),
            "median_error_m": float(np.median(arr)),
            "p95_error_m": float(np.percentile(arr, 95)),
            "max_error_m": float(np.max(arr)),
        }
    return summary


def gate_decision(site_summary: dict[str, dict[str, float]], sample_reports: list[dict[str, object]]) -> dict[str, object]:
    failures: list[str] = []
    overall_mean = float(np.mean([float(report["mean_error_m"]) for report in sample_reports])) if sample_reports else 1e9
    if overall_mean > PASS_THRESHOLDS_M["overall_mean_error_m"]:
        failures.append("overall_mean_error_above_12cm")

    hand_keys = ("left_hand", "right_hand")
    hand_mean = float(np.mean([site_summary[key]["mean_error_m"] for key in hand_keys if key in site_summary])) if site_summary else 1e9
    if hand_mean > PASS_THRESHOLDS_M["hands_mean_error_m"]:
        failures.append("hands_mean_error_above_18cm")

    foot_keys = ("left_foot", "right_foot")
    foot_mean = float(np.mean([site_summary[key]["mean_error_m"] for key in foot_keys if key in site_summary])) if site_summary else 1e9
    if foot_mean > PASS_THRESHOLDS_M["feet_mean_error_m"]:
        failures.append("feet_mean_error_above_18cm")

    major_joint_keys = (
        "left_shoulder",
        "right_shoulder",
        "left_elbow",
        "right_elbow",
        "left_hip",
        "right_hip",
        "left_knee",
        "right_knee",
    )
    major_mean = float(np.mean([site_summary[key]["mean_error_m"] for key in major_joint_keys if key in site_summary])) if site_summary else 1e9
    if major_mean > PASS_THRESHOLDS_M["major_joint_mean_error_m"]:
        failures.append("major_joint_mean_error_above_10cm")

    return {
        "passed": len(failures) == 0,
        "failures": failures,
        "overall_mean_error_m": overall_mean,
        "hands_mean_error_m": hand_mean,
        "feet_mean_error_m": foot_mean,
        "major_joint_mean_error_m": major_mean,
    }


def evaluate_video(
    xml_path: Path,
    video_path: Path,
    task_path: Path,
    calibration: dict[str, float] | None,
    sample_count: int,
    ik_iterations: int,
    damping: float,
) -> dict[str, object]:
    model = mujoco.MjModel.from_xml_path(str(xml_path.resolve()))
    data = mujoco.MjData(model)
    required_sites = tuple(SITE_TARGETS.keys()) + tuple(POLE_TARGETS.keys()) + tuple(AUX_SITE_TARGETS.keys())
    site_ids = {
        site_name: mujoco.mj_name2id(model, mujoco.mjtObj.mjOBJ_SITE, site_name)
        for site_name in required_sites
    }

    cap = cv2.VideoCapture(str(video_path))
    if not cap.isOpened():
        raise FileNotFoundError(f"Unable to open video: {video_path}")
    frame_count = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    sample_indices = sample_frame_indices(frame_count, sample_count)
    sample_set = set(sample_indices)

    landmarker = make_landmarker(task_path)
    mapper = MetricSkeletonMapper(calibration)
    sample_reports: list[dict[str, object]] = []
    prev_qpos: np.ndarray | None = None
    frame_idx = 0
    processed = 0
    while True:
        ok, frame_bgr = cap.read()
        if not ok:
            break

        timestamp_ms = int(round((frame_idx / max(cap.get(cv2.CAP_PROP_FPS), 1.0)) * 1000.0))
        frame_rgb = cv2.cvtColor(frame_bgr, cv2.COLOR_BGR2RGB)
        mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=frame_rgb)
        result = landmarker.detect_for_video(mp_image, timestamp_ms)
        if not result.pose_world_landmarks:
            if frame_idx in sample_set:
                sample_reports.append(
                    {
                        "frame_index": frame_idx,
                        "timestamp_ms": timestamp_ms,
                        "detected": False,
                    }
                )
            frame_idx += 1
            continue

        target_points = mapper.map_frame(result.pose_world_landmarks[0])
        if frame_idx not in sample_set:
            frame_idx += 1
            continue

        fit = fit_static_pose(model, data, site_ids, target_points, prev_qpos, ik_iterations, damping)
        prev_qpos = fit["qpos"].copy()
        fit.pop("qpos")
        fit.update(
            {
                "frame_index": frame_idx,
                "timestamp_ms": timestamp_ms,
                "detected": True,
            }
        )
        sample_reports.append(fit)
        processed += 1
        frame_idx += 1

    cap.release()
    landmarker.close()

    valid_reports = [report for report in sample_reports if report.get("detected")]
    site_summary = summarize_site_errors(valid_reports)
    gate = gate_decision(site_summary, valid_reports) if valid_reports else {
        "passed": False,
        "failures": ["no_detected_sample_frames"],
        "overall_mean_error_m": None,
        "hands_mean_error_m": None,
        "feet_mean_error_m": None,
        "major_joint_mean_error_m": None,
    }

    return {
        "xml": str(xml_path.resolve()),
        "video": str(video_path.resolve()),
        "sample_frame_indices": sample_indices,
        "sample_reports": sample_reports,
        "site_error_summary": site_summary,
        "gate1_static_fit": gate,
        "processed_sample_frames": processed,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Evaluate static fitting quality of the custom articulated human model.")
    parser.add_argument("--xml", type=Path, default=DEFAULT_XML)
    parser.add_argument("--input-video", type=Path, default=ROOT.parent / "video" / "주황.mp4")
    parser.add_argument("--task-model", type=Path, default=CUSTOM_SKELETON_ROOT / "pose_landmarker_lite.task")
    parser.add_argument("--calibration-json", type=Path, default=CUSTOM_SKELETON_ROOT / "calibration.json")
    parser.add_argument("--sample-count", type=int, default=8)
    parser.add_argument("--ik-iters", type=int, default=60)
    parser.add_argument("--ik-damping", type=float, default=1e-3)
    parser.add_argument("--output", type=Path, default=ROOT / "gate1_static_fit_report.json")
    args = parser.parse_args()

    calibration = load_calibration_json(args.calibration_json)
    report = evaluate_video(
        xml_path=args.xml,
        video_path=args.input_video,
        task_path=args.task_model,
        calibration=calibration,
        sample_count=args.sample_count,
        ik_iterations=args.ik_iters,
        damping=args.ik_damping,
    )
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(report["gate1_static_fit"], ensure_ascii=False, indent=2))
    print(f"[OK] Wrote {args.output.resolve()}")


if __name__ == "__main__":
    main()

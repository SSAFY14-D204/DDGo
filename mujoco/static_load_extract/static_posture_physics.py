from __future__ import annotations

import argparse
import json
import math
import time
from pathlib import Path
from typing import Any

import cv2
import mediapipe as mp
import mujoco
import mujoco.viewer
import numpy as np
from mediapipe.tasks import python
from mediapipe.tasks.python import vision

from physics_worker import (
    DEBUG_MARKER_SPECS,
    DEFAULT_ANALYSIS_JOINTS,
    LIMB_TO_BODY,
    LIMB_TO_EQUALITY,
    LIMB_TO_MOCAP_BODY,
    actuator_joint_torque_limits,
    apply_pose_to_model,
    body_id,
    build_analysis_model,
    compute_com,
    equality_id,
    extract_joint_pose_targets,
    frame_from_segments,
    joint_id,
    load_calibration_json,
    mapped_points_from_local,
    mp_to_mj,
    normalize,
    infer_forefoot_contact,
    infer_palm_contact,
    segment_lengths_local_from_calibration,
    apply_inverse_depth_correction_to_mapped,
    quat_error_rotvec,
    quat_from_axes,
    _extract_joint_pose_targets_from_mapped,
)


OPTIONAL_SHOULDER3_JOINTS = ("shoulder3_right", "shoulder3_left")
PROJECTION_LANDMARKS = {
    "left_shoulder": 11,
    "right_shoulder": 12,
    "left_elbow": 13,
    "right_elbow": 14,
    "left_wrist": 15,
    "right_wrist": 16,
    "left_hip": 23,
    "right_hip": 24,
    "left_knee": 25,
    "right_knee": 26,
    "left_ankle": 27,
    "right_ankle": 28,
}
COMPARISON_POINT_NAMES = [
    "left_elbow",
    "right_elbow",
    "left_wrist",
    "right_wrist",
    "left_knee",
    "right_knee",
    "left_ankle",
    "right_ankle",
]
OVERLAY_COLORS = {
    "mediapipe": (64, 220, 64),
    "target": (255, 220, 0),
    "post_ik": (64, 64, 255),
}
DEBUG_MARKER_TARGETS = {
    "debug_marker_shoulder_left": "left_shoulder",
    "debug_marker_shoulder_right": "right_shoulder",
    "debug_marker_elbow_left": "left_elbow",
    "debug_marker_elbow_right": "right_elbow",
    "debug_marker_knee_left": "left_knee",
    "debug_marker_knee_right": "right_knee",
}
BODY_PART_GROUPS = {
    "right_arm": ("shoulder1_right", "shoulder2_right", "shoulder3_right", "elbow_right"),
    "left_arm": ("shoulder1_left", "shoulder2_left", "shoulder3_left", "elbow_left"),
    "right_leg": ("hip_x_right", "hip_z_right", "hip_y_right", "knee_right", "ankle_y_right", "ankle_x_right"),
    "left_leg": ("hip_x_left", "hip_z_left", "hip_y_left", "knee_left", "ankle_y_left", "ankle_x_left"),
    "core": ("abdomen_z", "abdomen_y", "abdomen_x"),
}


def make_landmarker(task_path: Path) -> vision.PoseLandmarker:
    options = vision.PoseLandmarkerOptions(
        base_options=python.BaseOptions(model_asset_path=str(task_path)),
        running_mode=vision.RunningMode.IMAGE,
        num_poses=1,
        min_pose_detection_confidence=0.5,
        min_pose_presence_confidence=0.5,
        min_tracking_confidence=0.5,
    )
    return vision.PoseLandmarker.create_from_options(options)


def detect_pose_landmarks(image_path: Path, task_path: Path) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    frame = cv2.imread(str(image_path))
    if frame is None:
        raise FileNotFoundError(f"Could not read image: {image_path}")

    rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
    mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb)
    with make_landmarker(task_path) as landmarker:
        result = landmarker.detect(mp_image)

    if not result.pose_world_landmarks or not result.pose_landmarks:
        raise RuntimeError("Pose detection did not return both 2D and world landmarks")

    world_landmarks = result.pose_world_landmarks[0]
    image_landmarks = result.pose_landmarks[0]
    world = np.array([[float(p.x), float(p.y), float(p.z)] for p in world_landmarks], dtype=np.float64)
    image = np.array([[float(p.x), float(p.y)] for p in image_landmarks], dtype=np.float64)
    return world, image, frame


def build_debug_projection_landmarks(image_landmarks_px: np.ndarray) -> np.ndarray:
    debug_points = image_landmarks_px.copy()
    left_palm = infer_palm_contact(
        debug_points[15],
        debug_points[13],
        debug_points[19],
        debug_points[17],
        debug_points[21],
    )
    right_palm = infer_palm_contact(
        debug_points[16],
        debug_points[14],
        debug_points[20],
        debug_points[18],
        debug_points[22],
    )
    left_forefoot = infer_forefoot_contact(debug_points[29], debug_points[31])
    right_forefoot = infer_forefoot_contact(debug_points[30], debug_points[32])
    debug_points[15] = left_palm
    debug_points[16] = right_palm
    debug_points[27] = left_forefoot
    debug_points[28] = right_forefoot
    return debug_points


def set_weld_active(model: mujoco.MjModel, data: mujoco.MjData, equality_ids: dict[str, int], limb: str, active: bool) -> None:
    eq_id = equality_ids[limb]
    if hasattr(data, "eq_active"):
        data.eq_active[eq_id] = 1 if active else 0
    if hasattr(model, "eq_active0"):
        model.eq_active0[eq_id] = 1 if active else 0


def flip_limb_targets_forward_axis(
    limb_targets_world: dict[str, np.ndarray],
    pelvis_world: np.ndarray,
) -> dict[str, np.ndarray]:
    flipped: dict[str, np.ndarray] = {}
    pivot_x = float(pelvis_world[0])
    for limb, target in limb_targets_world.items():
        corrected = np.asarray(target, dtype=np.float64).copy()
        corrected[0] = 2.0 * pivot_x - corrected[0]
        flipped[limb] = corrected
    return flipped


def show_static_viewer(model: mujoco.MjModel, data: mujoco.MjData) -> None:
    with mujoco.viewer.launch_passive(model, data) as viewer:
        while viewer.is_running():
            viewer.sync()
            time.sleep(1.0 / 60.0)


def set_debug_marker_positions(
    model: mujoco.MjModel,
    data: mujoco.MjData,
    points: dict[str, np.ndarray],
) -> dict[str, list[float]]:
    marker_positions: dict[str, list[float]] = {}
    for marker_name, point_name in DEBUG_MARKER_TARGETS.items():
        bid = mujoco.mj_name2id(model, mujoco.mjtObj.mjOBJ_BODY, marker_name)
        if bid < 0:
            continue
        mocap_id = int(model.body_mocapid[bid])
        if mocap_id < 0:
            continue
        target = np.asarray(points[point_name], dtype=np.float64)
        data.mocap_pos[mocap_id] = target
        marker_positions[point_name] = [float(v) for v in target]
    return marker_positions


def recompute_quat_targets(points: dict[str, Any]) -> dict[str, np.ndarray]:
    up_axis = normalize(np.asarray(points["axis_up"], dtype=np.float64))
    left_axis = normalize(np.asarray(points["axis_left"], dtype=np.float64))
    forward_axis = normalize(np.asarray(points["axis_forward"], dtype=np.float64))

    ls = np.asarray(points["left_shoulder"], dtype=np.float64)
    rs = np.asarray(points["right_shoulder"], dtype=np.float64)
    le = np.asarray(points["left_elbow"], dtype=np.float64)
    re = np.asarray(points["right_elbow"], dtype=np.float64)
    lh = np.asarray(points["left_hip"], dtype=np.float64)
    rh = np.asarray(points["right_hip"], dtype=np.float64)
    lk = np.asarray(points["left_knee"], dtype=np.float64)
    rk = np.asarray(points["right_knee"], dtype=np.float64)
    la = np.asarray(points["left_ankle"], dtype=np.float64)
    ra = np.asarray(points["right_ankle"], dtype=np.float64)
    lhand_target = np.asarray(points.get("left_palm_contact", points["left_hand_tip"]), dtype=np.float64)
    rhand_target = np.asarray(points.get("right_palm_contact", points["right_hand_tip"]), dtype=np.float64)

    larm_x, larm_y, larm_z = frame_from_segments(le - ls, lhand_target - le, up_axis)
    rarm_x, rarm_y, rarm_z = frame_from_segments(re - rs, rhand_target - re, up_axis)
    lthigh_x, lthigh_y, lthigh_z = frame_from_segments(lk - lh, la - lk, forward_axis)
    rthigh_x, rthigh_y, rthigh_z = frame_from_segments(rk - rh, ra - rk, forward_axis)

    return {
        "torso": quat_from_axes(forward_axis, left_axis, up_axis),
        "upper_arm_left": quat_from_axes(larm_x, larm_y, larm_z),
        "upper_arm_right": quat_from_axes(rarm_x, rarm_y, rarm_z),
        "thigh_left": quat_from_axes(lthigh_x, lthigh_y, lthigh_z),
        "thigh_right": quat_from_axes(rthigh_x, rthigh_y, rthigh_z),
    }


def build_consistent_ik_points(mapped_points: dict[str, Any]) -> dict[str, Any]:
    pivot_x = float(np.asarray(mapped_points["hip_mid"], dtype=np.float64)[0])
    out: dict[str, Any] = {}
    for key, value in mapped_points.items():
        if key == "quat_targets":
            continue
        if isinstance(value, np.ndarray) and value.shape == (3,):
            corrected = np.asarray(value, dtype=np.float64).copy()
            if key.startswith("axis_"):
                corrected[0] *= -1.0
            else:
                corrected[0] = 2.0 * pivot_x - corrected[0]
            out[key] = corrected
        else:
            out[key] = value
    out["quat_targets"] = recompute_quat_targets(out)
    return out


def build_effector_targets(mapped_points: dict[str, Any]) -> dict[str, np.ndarray]:
    return {
        "left_wrist": np.asarray(mapped_points["left_palm_contact"], dtype=np.float64).copy(),
        "right_wrist": np.asarray(mapped_points["right_palm_contact"], dtype=np.float64).copy(),
        "left_ankle": np.asarray(mapped_points["left_forefoot_contact"], dtype=np.float64).copy(),
        "right_ankle": np.asarray(mapped_points["right_forefoot_contact"], dtype=np.float64).copy(),
        "left_elbow": np.asarray(mapped_points["left_elbow"], dtype=np.float64).copy(),
        "right_elbow": np.asarray(mapped_points["right_elbow"], dtype=np.float64).copy(),
        "left_knee": np.asarray(mapped_points["left_knee"], dtype=np.float64).copy(),
        "right_knee": np.asarray(mapped_points["right_knee"], dtype=np.float64).copy(),
    }


def serialize_points(points: dict[str, np.ndarray], names: list[str]) -> dict[str, list[float]]:
    return {name: [float(v) for v in np.asarray(points[name], dtype=np.float64)] for name in names}


def capture_body_positions(model: mujoco.MjModel, data: mujoco.MjData) -> dict[str, np.ndarray]:
    body_names = {
        "pelvis": "pelvis",
        "left_shoulder": "upper_arm_left",
        "right_shoulder": "upper_arm_right",
        "left_elbow": "lower_arm_left",
        "right_elbow": "lower_arm_right",
        "left_wrist": "palm_contact_left",
        "right_wrist": "palm_contact_right",
        "left_knee": "shin_left",
        "right_knee": "shin_right",
        "left_ankle": "forefoot_contact_left",
        "right_ankle": "forefoot_contact_right",
    }
    return {key: data.xpos[body_id(model, body_name)].copy() for key, body_name in body_names.items()}


def compute_position_errors(
    targets: dict[str, np.ndarray],
    actual_positions: dict[str, np.ndarray],
) -> dict[str, float]:
    return {
        key: float(np.linalg.norm(actual_positions[key] - targets[key]) * 100.0)
        for key in targets
        if key in actual_positions
    }


def maybe_update_constraint_wrenches(model: mujoco.MjModel, data: mujoco.MjData) -> None:
    if hasattr(mujoco, "mj_rnePostConstraint"):
        mujoco.mj_rnePostConstraint(model, data)


def build_detailed_joint_loads(
    model: mujoco.MjModel,
    analysis_joints: list[str],
    joint_ids: dict[str, int],
    torque_limits: dict[str, float],
    data: mujoco.MjData,
) -> tuple[list[dict[str, Any]], list[dict[str, Any]], float]:
    joint_torques: list[dict[str, Any]] = []
    detailed_joint_loads: list[dict[str, Any]] = []
    percentages: list[float] = []
    for joint_name in analysis_joints:
        jid = joint_ids[joint_name]
        dofadr = int(model.jnt_dofadr[jid])
        torque = float(data.qfrc_inverse[dofadr])
        limit = float(max(torque_limits.get(joint_name, 1.0), 1e-6))
        percentage = abs(torque) / limit * 100.0
        joint_torques.append({"joint_id": joint_name, "torque": torque})
        detailed_joint_loads.append(
            {
                "joint_id": joint_name,
                "torque": torque,
                "torque_limit": limit,
                "load_percentage": percentage,
            }
        )
        percentages.append(percentage)
    detailed_joint_loads.sort(key=lambda item: float(item["load_percentage"]), reverse=True)
    total_avg = float(np.mean(percentages)) if percentages else 0.0
    return joint_torques, detailed_joint_loads, total_avg


def summarize_body_part_loads(detailed_joint_loads: list[dict[str, Any]]) -> dict[str, dict[str, Any]]:
    by_joint = {str(entry["joint_id"]): entry for entry in detailed_joint_loads}
    summary: dict[str, dict[str, Any]] = {}
    for group_name, joint_names in BODY_PART_GROUPS.items():
        entries = [by_joint[name] for name in joint_names if name in by_joint]
        if not entries:
            continue
        peak = max(entries, key=lambda item: float(item["load_percentage"]))
        summary[group_name] = {
            "avg_load_percentage": float(np.mean([float(item["load_percentage"]) for item in entries])),
            "max_load_percentage": float(peak["load_percentage"]),
            "peak_joint": {
                "joint_id": str(peak["joint_id"]),
                "load_percentage": float(peak["load_percentage"]),
                "torque": float(peak["torque"]),
                "torque_limit": float(peak["torque_limit"]),
            },
            "joint_ids": [str(item["joint_id"]) for item in entries],
        }
    return summary


def compute_com_support_metrics(
    model: mujoco.MjModel,
    data: mujoco.MjData,
    mocap_positions: dict[str, np.ndarray],
) -> dict[str, Any]:
    com = compute_com(model, data)
    active_contact_points = np.array([np.asarray(pos, dtype=np.float64) for pos in mocap_positions.values()], dtype=np.float64)
    support_center = np.mean(active_contact_points, axis=0) if active_contact_points.size else np.zeros(3, dtype=np.float64)
    support_axes = (1, 2)
    stability_margin = float(np.linalg.norm(com[list(support_axes)] - support_center[list(support_axes)]))
    return {
        "com_position": [float(v) for v in com],
        "support_center_position": [float(v) for v in support_center],
        "active_contact_points": [[float(v) for v in point] for point in active_contact_points],
        "com_stability_margin_m": stability_margin,
    }


def extract_reaction_forces(
    model: mujoco.MjModel,
    data: mujoco.MjData,
    mocap_ids: dict[str, int],
) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    reaction_forces: list[dict[str, Any]] = []
    reach_errors: list[dict[str, Any]] = []
    for limb, body_name in LIMB_TO_BODY.items():
        bid = body_id(model, body_name)
        wrench = data.cfrc_ext[bid].copy()
        force_vector = np.asarray(wrench[0:3], dtype=np.float64)
        torque_vector = np.asarray(wrench[3:6], dtype=np.float64)
        force_magnitude = float(np.linalg.norm(force_vector))
        torque_magnitude = float(np.linalg.norm(torque_vector))
        reach_error = float(np.linalg.norm(data.xpos[bid] - data.mocap_pos[mocap_ids[limb]]))
        entry = {
            "limb_id": limb,
            "anchor_id": limb,
            "body_name": body_name,
            "force_vector": [float(v) for v in force_vector],
            "force_magnitude_n": force_magnitude,
            "torque_vector": [float(v) for v in torque_vector],
            "torque_magnitude_nm": torque_magnitude,
            "force": [float(v) for v in force_vector],
            "torque": [float(v) for v in torque_vector],
            "force_norm": force_magnitude,
            "torque_norm": torque_magnitude,
            "anchor_position": [float(v) for v in data.xpos[bid]],
            "mocap_target": [float(v) for v in data.mocap_pos[mocap_ids[limb]]],
            "reach_error_m": reach_error,
        }
        reaction_forces.append(entry)
        reach_errors.append(
            {
                "anchor_id": limb,
                "body_name": body_name,
                "reach_error_m": reach_error,
            }
        )
    return reaction_forces, reach_errors


def image_landmarks_to_pixels(
    image_landmarks: np.ndarray,
    frame: np.ndarray,
) -> np.ndarray:
    height, width = frame.shape[:2]
    pixels = image_landmarks.copy()
    pixels[:, 0] *= float(width)
    pixels[:, 1] *= float(height)
    return pixels


def fit_affine_projection(
    world_points: dict[str, np.ndarray],
    image_points_px: np.ndarray,
    landmark_map: dict[str, int],
) -> np.ndarray:
    rows: list[list[float]] = []
    target_u: list[float] = []
    target_v: list[float] = []
    for key, idx in landmark_map.items():
        point = np.asarray(world_points[key], dtype=np.float64)
        rows.append([float(point[0]), float(point[1]), float(point[2]), 1.0])
        target_u.append(float(image_points_px[idx, 0]))
        target_v.append(float(image_points_px[idx, 1]))
    a = np.asarray(rows, dtype=np.float64)
    coeff_u = np.linalg.lstsq(a, np.asarray(target_u, dtype=np.float64), rcond=None)[0]
    coeff_v = np.linalg.lstsq(a, np.asarray(target_v, dtype=np.float64), rcond=None)[0]
    return np.vstack([coeff_u, coeff_v])


def project_points_with_affine(
    points: dict[str, np.ndarray],
    affine_2x4: np.ndarray,
    names: list[str],
) -> dict[str, np.ndarray]:
    projected: dict[str, np.ndarray] = {}
    for name in names:
        point = np.asarray(points[name], dtype=np.float64)
        hom = np.array([float(point[0]), float(point[1]), float(point[2]), 1.0], dtype=np.float64)
        uv = affine_2x4 @ hom
        projected[name] = uv.astype(np.float64)
    return projected


def serialize_pixels(points: dict[str, np.ndarray], names: list[str]) -> dict[str, list[float]]:
    return {name: [float(v) for v in np.asarray(points[name], dtype=np.float64)] for name in names}


def compute_pixel_errors(
    mediapipe_px: np.ndarray,
    projected_points: dict[str, np.ndarray],
    landmark_map: dict[str, int],
    names: list[str],
) -> dict[str, float]:
    errors: dict[str, float] = {}
    for name in names:
        idx = landmark_map[name]
        ref = mediapipe_px[idx]
        pred = np.asarray(projected_points[name], dtype=np.float64)
        errors[name] = float(np.linalg.norm(pred - ref))
    return errors


def draw_labeled_point(
    image: np.ndarray,
    point_xy: np.ndarray,
    color_bgr: tuple[int, int, int],
    label: str,
    radius: int,
    thickness: int,
) -> None:
    x = int(round(float(point_xy[0])))
    y = int(round(float(point_xy[1])))
    cv2.circle(image, (x, y), radius, color_bgr, thickness, lineType=cv2.LINE_AA)
    cv2.putText(
        image,
        label,
        (x + 6, y - 6),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.45,
        color_bgr,
        1,
        cv2.LINE_AA,
    )


def render_2d_comparison_overlay(
    frame_bgr: np.ndarray,
    mediapipe_px: np.ndarray,
    target_projected_px: dict[str, np.ndarray],
    post_ik_projected_px: dict[str, np.ndarray],
    output_path: Path,
) -> None:
    canvas = frame_bgr.copy()
    short_labels = {
        "left_elbow": "LE",
        "right_elbow": "RE",
        "left_wrist": "LW",
        "right_wrist": "RW",
        "left_knee": "LK",
        "right_knee": "RK",
        "left_ankle": "LA",
        "right_ankle": "RA",
    }
    for name in COMPARISON_POINT_NAMES:
        label = short_labels[name]
        mp_point = mediapipe_px[PROJECTION_LANDMARKS[name]]
        target_point = target_projected_px[name]
        actual_point = post_ik_projected_px[name]
        cv2.line(
            canvas,
            (int(round(float(mp_point[0]))), int(round(float(mp_point[1])))),
            (int(round(float(actual_point[0]))), int(round(float(actual_point[1])))),
            OVERLAY_COLORS["post_ik"],
            1,
            cv2.LINE_AA,
        )
        draw_labeled_point(canvas, mp_point, OVERLAY_COLORS["mediapipe"], f"MP-{label}", 4, -1)
        draw_labeled_point(canvas, target_point, OVERLAY_COLORS["target"], f"T-{label}", 6, 2)
        draw_labeled_point(canvas, actual_point, OVERLAY_COLORS["post_ik"], f"MJ-{label}", 6, 2)

    cv2.imwrite(str(output_path), canvas)


def joint_exists(model: mujoco.MjModel, name: str) -> bool:
    return mujoco.mj_name2id(model, mujoco.mjtObj.mjOBJ_JOINT, name) >= 0


def resolve_analysis_joints(model: mujoco.MjModel) -> list[str]:
    joints: list[str] = []
    for name in DEFAULT_ANALYSIS_JOINTS:
        joints.append(name)
        if name == "shoulder2_right" and joint_exists(model, "shoulder3_right"):
            joints.append("shoulder3_right")
        if name == "shoulder2_left" and joint_exists(model, "shoulder3_left"):
            joints.append("shoulder3_left")
    return joints


def augment_joint_targets_for_model(
    model: mujoco.MjModel,
    joint_targets: dict[str, float],
) -> dict[str, float]:
    augmented = dict(joint_targets)
    for joint_name in OPTIONAL_SHOULDER3_JOINTS:
        if joint_exists(model, joint_name):
            augmented.setdefault(joint_name, 0.0)
    return augmented


def joint_vector(
    data: mujoco.MjData,
    qpos_adr: dict[str, int],
    analysis_joints: list[str],
) -> np.ndarray:
    return np.array([float(data.qpos[qpos_adr[name]]) for name in analysis_joints], dtype=np.float64)


def set_joint_vector(
    data: mujoco.MjData,
    q: np.ndarray,
    qpos_adr: dict[str, int],
    joint_limits: dict[str, tuple[float, float]],
    analysis_joints: list[str],
) -> None:
    for idx, joint_name in enumerate(analysis_joints):
        lo, hi = joint_limits[joint_name]
        data.qpos[qpos_adr[joint_name]] = float(np.clip(q[idx], lo, hi))


def refine_pose_with_ik(
    model: mujoco.MjModel,
    data: mujoco.MjData,
    analysis_joints: list[str],
    qpos_adr: dict[str, int],
    joint_ids: dict[str, int],
    joint_limits: dict[str, tuple[float, float]],
    effector_targets: dict[str, np.ndarray],
    quat_targets: dict[str, np.ndarray],
    ik_iters: int = 8,
    damping: float = 0.03,
    step_scale: float = 0.55,
    posture_weight: float = 0.02,
    use_arm_rotation_targets: bool = False,
    use_leg_rotation_targets: bool = False,
    elbow_target_weight: float = 0.8,
    knee_target_weight: float = 0.35,
    leg_rotation_weight: float = 0.18,
) -> dict[str, float]:
    dof_adr = [int(model.jnt_dofadr[joint_ids[name]]) for name in analysis_joints]
    hand_left_bid = body_id(model, "palm_contact_left")
    hand_right_bid = body_id(model, "palm_contact_right")
    foot_left_bid = body_id(model, "forefoot_contact_left")
    foot_right_bid = body_id(model, "forefoot_contact_right")
    elbow_left_bid = body_id(model, "lower_arm_left")
    elbow_right_bid = body_id(model, "lower_arm_right")
    knee_left_bid = body_id(model, "shin_left")
    knee_right_bid = body_id(model, "shin_right")
    upper_arm_left_bid = body_id(model, "upper_arm_left")
    upper_arm_right_bid = body_id(model, "upper_arm_right")
    thigh_left_bid = body_id(model, "thigh_left")
    thigh_right_bid = body_id(model, "thigh_right")

    pos_specs = [
        ("left_wrist", hand_left_bid, 1.0),
        ("right_wrist", hand_right_bid, 1.0),
        ("left_ankle", foot_left_bid, 1.0),
        ("right_ankle", foot_right_bid, 1.0),
        ("left_elbow", elbow_left_bid, elbow_target_weight),
        ("right_elbow", elbow_right_bid, elbow_target_weight),
        ("left_knee", knee_left_bid, knee_target_weight),
        ("right_knee", knee_right_bid, knee_target_weight),
    ]
    rot_specs: list[tuple[str, int, float]] = []
    if use_arm_rotation_targets:
        rot_specs = [
            ("upper_arm_left", upper_arm_left_bid, 0.45),
            ("upper_arm_right", upper_arm_right_bid, 0.45),
        ]
    if use_leg_rotation_targets:
        rot_specs.extend(
            [
                ("thigh_left", thigh_left_bid, leg_rotation_weight),
                ("thigh_right", thigh_right_bid, leg_rotation_weight),
            ]
        )

    base_q = joint_vector(data, qpos_adr, analysis_joints)
    max_pos_err = 0.0
    max_rot_err = 0.0

    for _ in range(max(int(ik_iters), 1)):
        mujoco.mj_forward(model, data)
        rows: list[np.ndarray] = []
        errs: list[np.ndarray] = []
        pos_errs: list[float] = []
        rot_errs: list[float] = []

        for key, bid, weight in pos_specs:
            jacp = np.zeros((3, model.nv), dtype=np.float64)
            jacr = np.zeros((3, model.nv), dtype=np.float64)
            mujoco.mj_jacBody(model, data, jacp, jacr, bid)
            rows.append(weight * jacp[:, dof_adr])
            err = effector_targets[key] - data.xpos[bid]
            errs.append(weight * err)
            pos_errs.append(float(np.linalg.norm(err)))

        for key, bid, weight in rot_specs:
            jacp = np.zeros((3, model.nv), dtype=np.float64)
            jacr = np.zeros((3, model.nv), dtype=np.float64)
            mujoco.mj_jacBody(model, data, jacp, jacr, bid)
            rows.append(weight * jacr[:, dof_adr])
            err = quat_error_rotvec(quat_targets[key], data.xquat[bid].copy())
            errs.append(weight * err)
            rot_errs.append(float(np.linalg.norm(err)))

        q_curr = joint_vector(data, qpos_adr, analysis_joints)
        rows.append(math.sqrt(posture_weight) * np.eye(len(analysis_joints), dtype=np.float64))
        errs.append(math.sqrt(posture_weight) * (base_q - q_curr))

        j_stack = np.vstack(rows)
        e_stack = np.concatenate(errs)
        h = j_stack.T @ j_stack + (damping * damping) * np.eye(len(analysis_joints), dtype=np.float64)
        g = j_stack.T @ e_stack
        try:
            dq = np.linalg.solve(h, g)
        except np.linalg.LinAlgError:
            dq = np.linalg.lstsq(h, g, rcond=None)[0]

        dq = np.clip(dq, -0.3, 0.3)
        set_joint_vector(data, q_curr + step_scale * dq, qpos_adr, joint_limits, analysis_joints)

        max_pos_err = max(pos_errs) if pos_errs else 0.0
        max_rot_err = max(rot_errs) if rot_errs else 0.0
        if max_pos_err < 0.015 and max_rot_err < 0.12:
            break

    mujoco.mj_forward(model, data)
    return {
        "ik_iterations": float(max(int(ik_iters), 1)),
        "ik_max_pos_err_cm": float(max_pos_err * 100.0),
        "ik_max_rot_err_deg": float(max_rot_err * (180.0 / math.pi)),
    }


def run_static_analysis(
    image_path: Path,
    config_path: Path,
    xml_path: Path,
    task_path: Path,
    output_path: Path,
    overlay_output_path: Path,
    calibration_json: Path | None = None,
    use_arm_rotation_targets: bool = False,
    use_leg_rotation_targets: bool = False,
    elbow_target_weight: float = 0.8,
    knee_target_weight: float = 0.35,
    leg_rotation_weight: float = 0.18,
) -> tuple[dict[str, Any], mujoco.MjModel, mujoco.MjData]:
    payload = json.loads(config_path.read_text(encoding="utf-8-sig"))
    biometrics = payload.get("user_biometrics", {})
    user_height = float(biometrics.get("height_m", 1.75))
    swap_lr = bool(payload.get("swap_left_right", False))
    if calibration_json is not None:
        payload["calibration_json"] = str(calibration_json.resolve())
    elif payload.get("calibration_json"):
        payload["calibration_json"] = str((config_path.parent / str(payload["calibration_json"])).resolve())
    calibration = load_calibration_json(Path(str(payload["calibration_json"]))) if payload.get("calibration_json") else None

    landmarks_mp, image_landmarks_norm, frame_bgr = detect_pose_landmarks(image_path, task_path)
    image_landmarks_px = image_landmarks_to_pixels(image_landmarks_norm, frame_bgr)
    model, data = build_analysis_model(
        xml_path,
        {
            "debug_marker_names": list(DEBUG_MARKER_SPECS.keys()),
            "calibration_json": payload.get("calibration_json"),
            "scale_model_segments": False,
        },
    )

    analysis_joints = resolve_analysis_joints(model)
    joint_ids = {name: joint_id(model, name) for name in analysis_joints}
    qpos_adr = {name: int(model.jnt_qposadr[jid]) for name, jid in joint_ids.items()}
    joint_limits: dict[str, tuple[float, float]] = {}
    for joint_name, jid in joint_ids.items():
        if bool(model.jnt_limited[jid]):
            lo, hi = model.jnt_range[jid]
            joint_limits[joint_name] = (float(lo), float(hi))
        else:
            joint_limits[joint_name] = (-1e9, 1e9)

    torso_bid = body_id(model, "torso")
    pelvis_bid = body_id(model, "pelvis")
    left_shoulder_bid = body_id(model, "upper_arm_left")
    right_shoulder_bid = body_id(model, "upper_arm_right")

    mujoco.mj_resetData(model, data)
    mujoco.mj_forward(model, data)
    torso_from_pelvis = data.xpos[torso_bid] - data.xpos[pelvis_bid]
    pelvis_anchor = data.xpos[pelvis_bid].copy()
    model_shoulder = float(np.linalg.norm(data.xpos[left_shoulder_bid] - data.xpos[right_shoulder_bid]))

    if calibration is None:
        joint_targets, points_local = extract_joint_pose_targets(landmarks_mp, swap_lr=swap_lr)
    else:
        mapped_local = np.array([mp_to_mj(point) for point in landmarks_mp], dtype=np.float64)
        shoulder_width_local = float(np.linalg.norm(mapped_local[11] - mapped_local[12]))
        segment_lengths_local = segment_lengths_local_from_calibration(calibration, shoulder_width_local)
        mapped_local = apply_inverse_depth_correction_to_mapped(
            mapped_local,
            segment_lengths_local,
            swap_lr=swap_lr,
        )
        joint_targets, points_local = _extract_joint_pose_targets_from_mapped(mapped_local, swap_lr=swap_lr)
    joint_targets = augment_joint_targets_for_model(model, joint_targets)
    shoulder_width = float(np.linalg.norm(points_local["left_shoulder"] - points_local["right_shoulder"]))
    shoulder_scale = model_shoulder / max(shoulder_width, 1e-6)
    scale = shoulder_scale if calibration is not None else shoulder_scale * (user_height / 1.75)
    offset = pelvis_anchor - points_local["hip_mid"] * scale
    mapped_points = mapped_points_from_local(points_local, scale=scale, offset=offset)
    ik_points = build_consistent_ik_points(mapped_points)

    _ = apply_pose_to_model(
        model=model,
        data=data,
        qpos_adr=qpos_adr,
        joint_limits=joint_limits,
        joint_targets=joint_targets,
        mapped_points=mapped_points,
        torso_from_pelvis=torso_from_pelvis,
    )
    analytical_positions = capture_body_positions(model, data)
    effector_targets = build_effector_targets(ik_points)
    limb_targets_world = {
        "left_wrist": np.asarray(effector_targets["left_wrist"], dtype=np.float64).copy(),
        "right_wrist": np.asarray(effector_targets["right_wrist"], dtype=np.float64).copy(),
        "left_ankle": np.asarray(effector_targets["left_ankle"], dtype=np.float64).copy(),
        "right_ankle": np.asarray(effector_targets["right_ankle"], dtype=np.float64).copy(),
    }

    ik_stats = refine_pose_with_ik(
        model=model,
        data=data,
        analysis_joints=analysis_joints,
        qpos_adr=qpos_adr,
        joint_ids=joint_ids,
        joint_limits=joint_limits,
        effector_targets=effector_targets,
        quat_targets=ik_points["quat_targets"],
        ik_iters=8,
        use_arm_rotation_targets=use_arm_rotation_targets,
        use_leg_rotation_targets=use_leg_rotation_targets,
        elbow_target_weight=elbow_target_weight,
        knee_target_weight=knee_target_weight,
        leg_rotation_weight=leg_rotation_weight,
    )
    post_ik_positions = capture_body_positions(model, data)

    projection_affine = fit_affine_projection(ik_points, image_landmarks_px, PROJECTION_LANDMARKS)
    image_debug_landmarks_px = build_debug_projection_landmarks(image_landmarks_px)
    target_projected_px = project_points_with_affine(effector_targets, projection_affine, COMPARISON_POINT_NAMES)
    analytical_projected_px = project_points_with_affine(analytical_positions, projection_affine, COMPARISON_POINT_NAMES)
    post_ik_projected_px = project_points_with_affine(post_ik_positions, projection_affine, COMPARISON_POINT_NAMES)
    render_2d_comparison_overlay(
        frame_bgr=frame_bgr,
        mediapipe_px=image_debug_landmarks_px,
        target_projected_px=target_projected_px,
        post_ik_projected_px=post_ik_projected_px,
        output_path=overlay_output_path,
    )
    debug_marker_positions = set_debug_marker_positions(model, data, ik_points)

    mocap_ids: dict[str, int] = {}
    equality_ids: dict[str, int] = {}
    for limb, mocap_body in LIMB_TO_MOCAP_BODY.items():
        bid = body_id(model, mocap_body)
        mocap_id = int(model.body_mocapid[bid])
        if mocap_id < 0:
            raise ValueError(f"Body {mocap_body} is not mocap-enabled")
        mocap_ids[limb] = mocap_id
        equality_ids[limb] = equality_id(model, LIMB_TO_EQUALITY[limb])

    for limb, target in limb_targets_world.items():
        data.mocap_pos[mocap_ids[limb]] = np.asarray(target, dtype=np.float64)
        set_weld_active(model, data, equality_ids, limb, True)

    data.qvel[:] = 0.0
    data.qacc[:] = 0.0
    mujoco.mj_forward(model, data)
    mujoco.mj_inverse(model, data)
    maybe_update_constraint_wrenches(model, data)

    torque_limits = actuator_joint_torque_limits(model)
    joint_torques, detailed_joint_loads, total_body_stress_avg = build_detailed_joint_loads(
        model=model,
        analysis_joints=analysis_joints,
        joint_ids=joint_ids,
        torque_limits=torque_limits,
        data=data,
    )
    body_part_loads = summarize_body_part_loads(detailed_joint_loads)
    mocap_positions = {limb: data.mocap_pos[mocap_id].copy() for limb, mocap_id in mocap_ids.items()}
    com_metrics = compute_com_support_metrics(model, data, mocap_positions)
    reaction_forces, reach_errors = extract_reaction_forces(model, data, mocap_ids)

    result = {
        "image_path": str(image_path),
        "summary_metrics": {
            "total_body_stress_avg": total_body_stress_avg,
            "com_stability": float(com_metrics["com_stability_margin_m"]),
        },
        "body_part_loads": body_part_loads,
        "detailed_joint_loads": detailed_joint_loads,
        "joint_torques": joint_torques,
        "load_percentage": detailed_joint_loads,
        "reaction_forces": reaction_forces,
        "anchor_forces": reaction_forces,
        "reach_errors": reach_errors,
        "com_position": com_metrics["com_position"],
        "support_center_position": com_metrics["support_center_position"],
        "active_contact_points": com_metrics["active_contact_points"],
        "com_stability_margin_m": com_metrics["com_stability_margin_m"],
        "pose_debug": {
            "mediapipe_world_points": {
                key: [float(v) for v in landmarks_mp[idx]]
                for key, idx in {
                    "left_wrist": 15,
                    "right_wrist": 16,
                    "left_knee": 25,
                    "right_knee": 26,
                    "left_ankle": 27,
                    "right_ankle": 28,
                    "left_toe": 31,
                    "right_toe": 32,
                }.items()
            },
            "mapped_world_points": serialize_points(
                mapped_points,
                [
                    "hip_mid",
                    "left_elbow",
                    "right_elbow",
                    "left_wrist",
                    "right_wrist",
                    "left_hand_tip",
                    "right_hand_tip",
                    "left_palm_contact",
                    "right_palm_contact",
                    "left_knee",
                    "right_knee",
                    "left_ankle",
                    "right_ankle",
                    "left_toe",
                    "right_toe",
                    "left_forefoot_contact",
                    "right_forefoot_contact",
                ],
            ),
            "ik_mapped_world_points": serialize_points(
                ik_points,
                [
                    "hip_mid",
                    "left_elbow",
                    "right_elbow",
                    "left_wrist",
                    "right_wrist",
                    "left_hand_tip",
                    "right_hand_tip",
                    "left_palm_contact",
                    "right_palm_contact",
                    "left_knee",
                    "right_knee",
                    "left_ankle",
                    "right_ankle",
                    "left_toe",
                    "right_toe",
                    "left_forefoot_contact",
                    "right_forefoot_contact",
                ],
            ),
            "effector_targets_world": serialize_points(
                effector_targets,
                [
                    "left_elbow",
                    "right_elbow",
                    "left_wrist",
                    "right_wrist",
                    "left_knee",
                    "right_knee",
                    "left_ankle",
                    "right_ankle",
                ],
            ),
            "analytical_body_positions": serialize_points(
                analytical_positions,
                [
                    "pelvis",
                    "left_elbow",
                    "right_elbow",
                    "left_wrist",
                    "right_wrist",
                    "left_knee",
                    "right_knee",
                    "left_ankle",
                    "right_ankle",
                ],
            ),
            "post_ik_body_positions": serialize_points(
                post_ik_positions,
                [
                    "pelvis",
                    "left_elbow",
                    "right_elbow",
                    "left_wrist",
                    "right_wrist",
                    "left_knee",
                    "right_knee",
                    "left_ankle",
                    "right_ankle",
                ],
            ),
            "analytical_position_errors_cm": compute_position_errors(effector_targets, analytical_positions),
            "post_ik_position_errors_cm": compute_position_errors(effector_targets, post_ik_positions),
            "joint_targets_analytical_rad": {name: float(val) for name, val in joint_targets.items()},
            "joint_qpos_post_ik_rad": {name: float(data.qpos[qpos_adr[name]]) for name in analysis_joints},
            "debug_marker_targets_world": debug_marker_positions,
        },
        "image_debug": {
            "overlay_path": str(overlay_output_path),
            "mediapipe_pixels": {
                name: [float(v) for v in image_debug_landmarks_px[idx]]
                for name, idx in PROJECTION_LANDMARKS.items()
            },
            "target_projected_pixels": serialize_pixels(target_projected_px, COMPARISON_POINT_NAMES),
            "analytical_projected_pixels": serialize_pixels(analytical_projected_px, COMPARISON_POINT_NAMES),
            "post_ik_projected_pixels": serialize_pixels(post_ik_projected_px, COMPARISON_POINT_NAMES),
            "target_projected_pixel_errors": compute_pixel_errors(
                image_debug_landmarks_px,
                target_projected_px,
                PROJECTION_LANDMARKS,
                COMPARISON_POINT_NAMES,
            ),
            "analytical_projected_pixel_errors": compute_pixel_errors(
                image_debug_landmarks_px,
                analytical_projected_px,
                PROJECTION_LANDMARKS,
                COMPARISON_POINT_NAMES,
            ),
            "post_ik_projected_pixel_errors": compute_pixel_errors(
                image_debug_landmarks_px,
                post_ik_projected_px,
                PROJECTION_LANDMARKS,
                COMPARISON_POINT_NAMES,
            ),
        },
        "meta": {
            "xml_path": str(xml_path),
            "calibration_json": payload.get("calibration_json"),
            "scale_model_segments": False,
            "analysis_joints": analysis_joints,
            "scale_factor": scale,
            "swap_left_right": swap_lr,
            "target_forward_axis_flipped": True,
            "arm_rotation_targets_enabled": use_arm_rotation_targets,
            "leg_rotation_targets_enabled": use_leg_rotation_targets,
            "elbow_target_weight": elbow_target_weight,
            "knee_target_weight": knee_target_weight,
            "leg_rotation_weight": leg_rotation_weight,
            "ik_stats": ik_stats,
            "weld_active_limbs": list(LIMB_TO_BODY.keys()),
            "constraint_generalized_force_norm": float(np.linalg.norm(data.qfrc_constraint)),
            "constraint_generalized_force_max": float(np.max(np.abs(data.qfrc_constraint))) if data.qfrc_constraint.size else 0.0,
            "gravity": [float(v) for v in model.opt.gravity],
        },
    }
    output_path.write_text(json.dumps(result, indent=2), encoding="utf-8")
    return result, model, data


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Static single-image posture physics validation")
    parser.add_argument("--image", default=str(Path(__file__).with_name("video") / "static.png"))
    parser.add_argument("--config", default=str(Path(__file__).with_name("sample_pose_world.json")))
    parser.add_argument("--xml", default=str(Path(__file__).with_name("humanoid_shoulder3.xml")))
    parser.add_argument("--calibration-json", help="Calibration JSON generated by calibrate_biometrics.py")
    parser.add_argument("--task-model", default=str(Path(__file__).with_name("pose_landmarker_lite.task")))
    parser.add_argument("--output", default=str(Path(__file__).with_name("static_posture_analysis.json")))
    parser.add_argument(
        "--overlay-output",
        default=str(Path(__file__).with_name("static_posture_overlay.png")),
        help="Path to save 2D MediaPipe vs MuJoCo comparison overlay",
    )
    parser.add_argument(
        "--arm-rot-targets",
        choices=("on", "off"),
        default="off",
        help="Use upper-arm orientation targets during IK refinement",
    )
    parser.add_argument(
        "--leg-rot-targets",
        choices=("on", "off"),
        default="off",
        help="Use thigh orientation targets during IK refinement",
    )
    parser.add_argument(
        "--elbow-weight",
        type=float,
        default=0.8,
        help="Position target weight for elbow IK targets",
    )
    parser.add_argument(
        "--knee-weight",
        type=float,
        default=0.35,
        help="Position target weight for knee IK targets",
    )
    parser.add_argument(
        "--leg-rot-weight",
        type=float,
        default=0.18,
        help="Orientation target weight for thigh IK targets",
    )
    parser.add_argument(
        "--no-viewer",
        action="store_true",
        help="Skip opening the MuJoCo viewer",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    result, model, data = run_static_analysis(
        image_path=Path(args.image).resolve(),
        config_path=Path(args.config).resolve(),
        xml_path=Path(args.xml).resolve(),
        task_path=Path(args.task_model).resolve(),
        output_path=Path(args.output).resolve(),
        overlay_output_path=Path(args.overlay_output).resolve(),
        calibration_json=Path(args.calibration_json).resolve() if args.calibration_json else None,
        use_arm_rotation_targets=(args.arm_rot_targets == "on"),
        use_leg_rotation_targets=(args.leg_rot_targets == "on"),
        elbow_target_weight=float(args.elbow_weight),
        knee_target_weight=float(args.knee_weight),
        leg_rotation_weight=float(args.leg_rot_weight),
    )
    print("[OK] Static posture analysis complete")
    print(
        json.dumps(
            {
                "joint_count": len(result["detailed_joint_loads"]),
                "reaction_force_count": len(result["reaction_forces"]),
                "total_body_stress_avg": float(result["summary_metrics"]["total_body_stress_avg"]),
                "com_stability_margin_m": float(result["summary_metrics"]["com_stability"]),
                "max_reach_error_cm": max(
                    [float(item["reach_error_m"]) * 100.0 for item in result["reach_errors"]],
                    default=0.0,
                ),
                "max_post_ik_pos_err_cm": max(
                    [float(v) for v in result["pose_debug"]["post_ik_position_errors_cm"].values()],
                    default=0.0,
                ),
                "output": str(Path(args.output).resolve()),
                "overlay_output": str(Path(args.overlay_output).resolve()),
            },
            indent=2,
        )
    )
    if not args.no_viewer:
        print("[INFO] Opening MuJoCo viewer. Close the viewer window to exit.")
        show_static_viewer(model, data)


if __name__ == "__main__":
    main()

from __future__ import annotations

import argparse
import sys
import time
from itertools import combinations
from pathlib import Path

import cv2
import mediapipe as mp
import mujoco
import mujoco.viewer
import numpy as np
from mediapipe.tasks import python
from mediapipe.tasks.python import vision

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT.parent / "dynamic_hold_verify"))

from physics_worker import (  # noqa: E402
    LEFT_ANKLE,
    LEFT_ELBOW,
    LEFT_FOOT_INDEX,
    LEFT_HEEL,
    LEFT_HIP,
    LEFT_INDEX,
    LEFT_KNEE,
    LEFT_PINKY,
    LEFT_SHOULDER,
    LEFT_THUMB,
    LEFT_WRIST,
    RIGHT_ANKLE,
    RIGHT_ELBOW,
    RIGHT_FOOT_INDEX,
    RIGHT_HEEL,
    RIGHT_HIP,
    RIGHT_INDEX,
    RIGHT_KNEE,
    RIGHT_PINKY,
    RIGHT_SHOULDER,
    RIGHT_THUMB,
    RIGHT_WRIST,
    apply_inverse_depth_correction_to_mapped,
    apply_two_link_pose_correction_to_mapped,
    infer_forefoot_contact,
    infer_palm_contact,
    load_calibration_json,
    mp_to_mj,
    segment_lengths_local_from_calibration,
)

NOSE = 0
LEFT_EAR = 7
RIGHT_EAR = 8

JOINT_BODY_MAP = {
    "head": "joint_head",
    "thorax": "joint_thorax",
    "pelvis": "joint_pelvis",
    "left_shoulder": "joint_left_shoulder",
    "right_shoulder": "joint_right_shoulder",
    "left_elbow": "joint_left_elbow",
    "right_elbow": "joint_right_elbow",
    "left_hand": "joint_left_hand",
    "right_hand": "joint_right_hand",
    "left_hip": "joint_left_hip",
    "right_hip": "joint_right_hip",
    "left_knee": "joint_left_knee",
    "right_knee": "joint_right_knee",
    "left_ankle": "joint_left_ankle",
    "right_ankle": "joint_right_ankle",
    "left_foot": "joint_left_foot",
    "right_foot": "joint_right_foot",
}

SEGMENTS = {
    "segment_neck": ("thorax", "head", 0.022),
    "segment_spine": ("pelvis", "thorax", 0.03),
    "segment_left_clavicle": ("thorax", "left_shoulder", 0.02),
    "segment_right_clavicle": ("thorax", "right_shoulder", 0.02),
    "segment_left_upper_arm": ("left_shoulder", "left_elbow", 0.024),
    "segment_right_upper_arm": ("right_shoulder", "right_elbow", 0.024),
    "segment_left_forearm": ("left_elbow", "left_hand", 0.022),
    "segment_right_forearm": ("right_elbow", "right_hand", 0.022),
    "segment_left_hip_link": ("pelvis", "left_hip", 0.022),
    "segment_right_hip_link": ("pelvis", "right_hip", 0.022),
    "segment_left_thigh": ("left_hip", "left_knee", 0.028),
    "segment_right_thigh": ("right_hip", "right_knee", 0.028),
    "segment_left_shin": ("left_knee", "left_ankle", 0.024),
    "segment_right_shin": ("right_knee", "right_ankle", 0.024),
    "segment_left_foot": ("left_ankle", "left_foot", 0.022),
    "segment_right_foot": ("right_ankle", "right_foot", 0.022),
}

OVERLAY_POINTS = {
    "L_SH": LEFT_SHOULDER,
    "R_SH": RIGHT_SHOULDER,
    "L_EL": LEFT_ELBOW,
    "R_EL": RIGHT_ELBOW,
    "L_WR": LEFT_WRIST,
    "R_WR": RIGHT_WRIST,
    "L_HI": LEFT_HIP,
    "R_HI": RIGHT_HIP,
    "L_KN": LEFT_KNEE,
    "R_KN": RIGHT_KNEE,
    "L_AN": LEFT_ANKLE,
    "R_AN": RIGHT_ANKLE,
}

SEGMENT_MASS_RATIOS = {
    "head_neck": 0.0694,
    "trunk": 0.4346,
    "left_upper_arm": 0.0271,
    "right_upper_arm": 0.0271,
    "left_forearm": 0.0162,
    "right_forearm": 0.0162,
    "left_hand": 0.0061,
    "right_hand": 0.0061,
    "left_thigh": 0.1416,
    "right_thigh": 0.1416,
    "left_shank": 0.0433,
    "right_shank": 0.0433,
    "left_foot": 0.0137,
    "right_foot": 0.0137,
}

SUPPORT_POINT_KEYS = ("left_hand", "right_hand", "left_foot", "right_foot")

POINT_SMOOTHING_ALPHA = {
    "pelvis": 0.60,
    "thorax": 0.55,
    "head": 0.45,
    "left_shoulder": 0.50,
    "right_shoulder": 0.50,
    "left_elbow": 0.36,
    "right_elbow": 0.36,
    "left_hand": 0.28,
    "right_hand": 0.28,
    "left_hip": 0.50,
    "right_hip": 0.50,
    "left_knee": 0.38,
    "right_knee": 0.38,
    "left_ankle": 0.32,
    "right_ankle": 0.32,
    "left_heel": 0.30,
    "right_heel": 0.30,
    "left_foot": 0.28,
    "right_foot": 0.28,
}


def normalize(v: np.ndarray, eps: float = 1e-8) -> np.ndarray:
    arr = np.asarray(v, dtype=np.float64)
    n = float(np.linalg.norm(arr))
    if n < eps:
        return np.zeros_like(arr)
    return arr / n


def make_landmarker(task_path: Path) -> vision.PoseLandmarker:
    options = vision.PoseLandmarkerOptions(
        base_options=python.BaseOptions(model_asset_path=str(task_path)),
        running_mode=vision.RunningMode.VIDEO,
        num_poses=1,
        min_pose_detection_confidence=0.5,
        min_pose_presence_confidence=0.5,
        min_tracking_confidence=0.5,
    )
    return vision.PoseLandmarker.create_from_options(options)


def quat_from_z_axis(direction: np.ndarray) -> np.ndarray:
    z_axis = np.array([0.0, 0.0, 1.0], dtype=np.float64)
    target = np.asarray(direction, dtype=np.float64)
    norm = float(np.linalg.norm(target))
    if norm < 1e-8:
        return np.array([1.0, 0.0, 0.0, 0.0], dtype=np.float64)
    target = target / norm
    dot = float(np.clip(np.dot(z_axis, target), -1.0, 1.0))
    if dot > 0.999999:
        return np.array([1.0, 0.0, 0.0, 0.0], dtype=np.float64)
    if dot < -0.999999:
        return np.array([0.0, 1.0, 0.0, 0.0], dtype=np.float64)
    axis = np.cross(z_axis, target)
    axis = axis / np.linalg.norm(axis)
    angle = np.arccos(dot)
    s = np.sin(angle / 2.0)
    return np.array([np.cos(angle / 2.0), axis[0] * s, axis[1] * s, axis[2] * s], dtype=np.float64)


def stabilize_joint_pole(
    root: np.ndarray,
    joint: np.ndarray,
    effector: np.ndarray,
    preferred_dir: np.ndarray,
    prev_root: np.ndarray | None,
    prev_joint: np.ndarray | None,
    prev_effector: np.ndarray | None,
) -> np.ndarray:
    line = effector - root
    line_norm = float(np.linalg.norm(line))
    if line_norm < 1e-8:
        return joint
    line_dir = line / line_norm

    rel = joint - root
    parallel = float(np.dot(rel, line_dir)) * line_dir
    perp = rel - parallel
    perp_norm = float(np.linalg.norm(perp))

    preferred = np.asarray(preferred_dir, dtype=np.float64)
    preferred = preferred - float(np.dot(preferred, line_dir)) * line_dir
    if float(np.linalg.norm(preferred)) < 1e-8:
        preferred = perp if perp_norm > 1e-8 else np.array([0.0, 1.0, 0.0], dtype=np.float64)
    preferred = normalize(preferred)

    reference = preferred
    if prev_root is not None and prev_joint is not None and prev_effector is not None:
        prev_line = prev_effector - prev_root
        prev_line_norm = float(np.linalg.norm(prev_line))
        if prev_line_norm >= 1e-8:
            prev_line_dir = prev_line / prev_line_norm
            prev_rel = prev_joint - prev_root
            prev_parallel = float(np.dot(prev_rel, prev_line_dir)) * prev_line_dir
            prev_perp = prev_rel - prev_parallel
            if float(np.linalg.norm(prev_perp)) >= 1e-8:
                prev_dir = normalize(prev_perp)
                if float(np.dot(prev_dir, reference)) < 0.0:
                    prev_dir = -prev_dir
                reference = normalize(0.75 * prev_dir + 0.25 * reference)

    if perp_norm < 1e-8:
        return root + parallel + reference * 0.05

    current_dir = normalize(perp)
    if float(np.dot(current_dir, reference)) < 0.0:
        current_dir = -current_dir
    blended_dir = normalize(0.55 * current_dir + 0.45 * reference)
    return root + parallel + blended_dir * perp_norm


def apply_pole_consistency(
    points: dict[str, np.ndarray],
    prev_points: dict[str, np.ndarray] | None,
) -> dict[str, np.ndarray]:
    adjusted = {key: value.copy() for key, value in points.items()}

    thorax = adjusted["thorax"]
    pelvis = adjusted["pelvis"]
    up_axis = normalize(thorax - pelvis)
    left_axis = normalize(
        (adjusted["left_shoulder"] - adjusted["right_shoulder"])
        + (adjusted["left_hip"] - adjusted["right_hip"])
    )
    if float(np.linalg.norm(left_axis)) < 1e-8:
        left_axis = np.array([0.0, 1.0, 0.0], dtype=np.float64)
    forward_axis = normalize(np.cross(left_axis, up_axis))
    if float(np.linalg.norm(forward_axis)) < 1e-8:
        forward_axis = np.array([1.0, 0.0, 0.0], dtype=np.float64)

    elbow_left_pref = normalize(0.80 * left_axis - 0.35 * up_axis)
    elbow_right_pref = normalize(-0.80 * left_axis - 0.35 * up_axis)
    knee_left_pref = normalize(0.55 * left_axis + 0.45 * forward_axis - 0.10 * up_axis)
    knee_right_pref = normalize(-0.55 * left_axis + 0.45 * forward_axis - 0.10 * up_axis)

    adjusted["left_elbow"] = stabilize_joint_pole(
        adjusted["left_shoulder"],
        adjusted["left_elbow"],
        adjusted["left_hand"],
        elbow_left_pref,
        None if prev_points is None else prev_points.get("left_shoulder"),
        None if prev_points is None else prev_points.get("left_elbow"),
        None if prev_points is None else prev_points.get("left_hand"),
    )
    adjusted["right_elbow"] = stabilize_joint_pole(
        adjusted["right_shoulder"],
        adjusted["right_elbow"],
        adjusted["right_hand"],
        elbow_right_pref,
        None if prev_points is None else prev_points.get("right_shoulder"),
        None if prev_points is None else prev_points.get("right_elbow"),
        None if prev_points is None else prev_points.get("right_hand"),
    )
    adjusted["left_knee"] = stabilize_joint_pole(
        adjusted["left_hip"],
        adjusted["left_knee"],
        adjusted["left_ankle"],
        knee_left_pref,
        None if prev_points is None else prev_points.get("left_hip"),
        None if prev_points is None else prev_points.get("left_knee"),
        None if prev_points is None else prev_points.get("left_ankle"),
    )
    adjusted["right_knee"] = stabilize_joint_pole(
        adjusted["right_hip"],
        adjusted["right_knee"],
        adjusted["right_ankle"],
        knee_right_pref,
        None if prev_points is None else prev_points.get("right_hip"),
        None if prev_points is None else prev_points.get("right_knee"),
        None if prev_points is None else prev_points.get("right_ankle"),
    )
    return adjusted


def build_pose_points(world_landmarks: list, calibration: dict[str, float] | None, prev_local: dict[str, np.ndarray] | None) -> dict[str, np.ndarray]:
    raw_mp = np.array([[float(p.x), float(p.y), float(p.z)] for p in world_landmarks], dtype=np.float64)
    mapped = np.array([mp_to_mj(point) for point in raw_mp], dtype=np.float64)

    shoulder_width_local = float(np.linalg.norm(mapped[LEFT_SHOULDER] - mapped[RIGHT_SHOULDER]))
    segment_lengths_local = segment_lengths_local_from_calibration(calibration, shoulder_width_local)
    mapped = apply_inverse_depth_correction_to_mapped(mapped, segment_lengths_local, swap_lr=False)
    mapped, _ = apply_two_link_pose_correction_to_mapped(mapped, segment_lengths_local, swap_lr=False, prev_points=prev_local)

    ls = mapped[LEFT_SHOULDER]
    rs = mapped[RIGHT_SHOULDER]
    le = mapped[LEFT_ELBOW]
    re = mapped[RIGHT_ELBOW]
    lw = mapped[LEFT_WRIST]
    rw = mapped[RIGHT_WRIST]
    lh = mapped[LEFT_HIP]
    rh = mapped[RIGHT_HIP]
    lk = mapped[LEFT_KNEE]
    rk = mapped[RIGHT_KNEE]
    la = mapped[LEFT_ANKLE]
    ra = mapped[RIGHT_ANKLE]

    shoulder_mid = 0.5 * (ls + rs)
    hip_mid = 0.5 * (lh + rh)
    axis_up = normalize(shoulder_mid - hip_mid)

    lhand = infer_palm_contact(lw, le, mapped[LEFT_INDEX], mapped[LEFT_PINKY], mapped[LEFT_THUMB])
    rhand = infer_palm_contact(rw, re, mapped[RIGHT_INDEX], mapped[RIGHT_PINKY], mapped[RIGHT_THUMB])
    lfoot = infer_forefoot_contact(mapped[LEFT_HEEL], mapped[LEFT_FOOT_INDEX])
    rfoot = infer_forefoot_contact(mapped[RIGHT_HEEL], mapped[RIGHT_FOOT_INDEX])
    lheel = mapped[LEFT_HEEL].copy()
    rheel = mapped[RIGHT_HEEL].copy()
    ear_mid = 0.5 * (mapped[LEFT_EAR] + mapped[RIGHT_EAR])
    head = 0.55 * mapped[NOSE] + 0.45 * ear_mid + 0.05 * axis_up

    points = {
        "head": head,
        "thorax": shoulder_mid,
        "pelvis": hip_mid,
        "left_shoulder": ls,
        "right_shoulder": rs,
        "left_elbow": le,
        "right_elbow": re,
        "left_hand": lhand,
        "right_hand": rhand,
        "left_hip": lh,
        "right_hip": rh,
        "left_knee": lk,
        "right_knee": rk,
        "left_ankle": la,
        "right_ankle": ra,
        "left_heel": lheel,
        "right_heel": rheel,
        "left_foot": lfoot,
        "right_foot": rfoot,
    }

    pivot_x = float(points["pelvis"][0])
    corrected: dict[str, np.ndarray] = {}
    for key, value in points.items():
        out = value.copy()
        out[0] = 2.0 * pivot_x - out[0]
        corrected[key] = out
    return apply_pole_consistency(corrected, prev_local)


def apply_fixed_torso_length(points: dict[str, np.ndarray], torso_length_local: float | None) -> dict[str, np.ndarray]:
    if torso_length_local is None:
        return points

    pelvis = np.asarray(points["pelvis"], dtype=np.float64)
    thorax = np.asarray(points["thorax"], dtype=np.float64)
    direction = thorax - pelvis
    norm = float(np.linalg.norm(direction))
    if norm < 1e-6:
        return points

    new_thorax = pelvis + direction / norm * float(torso_length_local)
    delta = new_thorax - thorax
    adjusted = {key: value.copy() for key, value in points.items()}
    for key in ("thorax", "head", "left_shoulder", "right_shoulder", "left_elbow", "right_elbow", "left_hand", "right_hand"):
        adjusted[key] = adjusted[key] + delta
    return adjusted


def compute_segment_masses_kg(total_body_mass_kg: float) -> dict[str, float]:
    return {
        segment_name: float(total_body_mass_kg) * ratio
        for segment_name, ratio in SEGMENT_MASS_RATIOS.items()
    }


def compute_com_and_support(
    points_world: dict[str, np.ndarray],
    total_body_mass_kg: float,
) -> dict[str, np.ndarray | float | dict[str, float]]:
    segment_masses = compute_segment_masses_kg(total_body_mass_kg)
    segment_positions = {
        "head_neck": 0.5 * (points_world["head"] + points_world["thorax"]),
        "trunk": 0.5 * (points_world["pelvis"] + points_world["thorax"]),
        "left_upper_arm": 0.5 * (points_world["left_shoulder"] + points_world["left_elbow"]),
        "right_upper_arm": 0.5 * (points_world["right_shoulder"] + points_world["right_elbow"]),
        "left_forearm": 0.5 * (points_world["left_elbow"] + points_world["left_hand"]),
        "right_forearm": 0.5 * (points_world["right_elbow"] + points_world["right_hand"]),
        "left_hand": points_world["left_hand"],
        "right_hand": points_world["right_hand"],
        "left_thigh": 0.5 * (points_world["left_hip"] + points_world["left_knee"]),
        "right_thigh": 0.5 * (points_world["right_hip"] + points_world["right_knee"]),
        "left_shank": 0.5 * (points_world["left_knee"] + points_world["left_ankle"]),
        "right_shank": 0.5 * (points_world["right_knee"] + points_world["right_ankle"]),
        "left_foot": 0.5 * (points_world["left_ankle"] + points_world["left_foot"]),
        "right_foot": 0.5 * (points_world["right_ankle"] + points_world["right_foot"]),
    }
    weighted_sum = np.zeros(3, dtype=np.float64)
    for segment_name, position in segment_positions.items():
        weighted_sum += segment_masses[segment_name] * position
    com_position = weighted_sum / float(total_body_mass_kg)

    support_points = np.array(
        [points_world[key] for key in SUPPORT_POINT_KEYS],
        dtype=np.float64,
    )
    support_center = support_points.mean(axis=0)
    com_support_margin_xy = float(np.linalg.norm(com_position[:2] - support_center[:2]))
    return {
        "com_position": com_position,
        "support_center": support_center,
        "support_points": support_points,
        "com_support_margin_xy": com_support_margin_xy,
        "segment_masses_kg": segment_masses,
    }


def triangle_area_xy(a: np.ndarray, b: np.ndarray, c: np.ndarray) -> float:
    return abs(
        0.5
        * (
            a[0] * (b[1] - c[1])
            + b[0] * (c[1] - a[1])
            + c[0] * (a[1] - b[1])
        )
    )


def point_in_triangle_xy(p: np.ndarray, a: np.ndarray, b: np.ndarray, c: np.ndarray) -> bool:
    area = triangle_area_xy(a, b, c)
    if area < 1e-8:
        return False
    area_sum = (
        triangle_area_xy(p, b, c)
        + triangle_area_xy(a, p, c)
        + triangle_area_xy(a, b, p)
    )
    return abs(area_sum - area) <= 1e-5


def select_tripod_support(
    points_world: dict[str, np.ndarray],
    com_position: np.ndarray,
    prev_support_points: dict[str, np.ndarray] | None,
) -> dict[str, object]:
    best_choice: dict[str, object] | None = None
    best_score = -1e18
    for tripod_keys in combinations(SUPPORT_POINT_KEYS, 3):
        tripod_points = np.array([points_world[key] for key in tripod_keys], dtype=np.float64)
        centroid = tripod_points.mean(axis=0)
        area_xy = triangle_area_xy(tripod_points[0], tripod_points[1], tripod_points[2])
        inside = point_in_triangle_xy(com_position[:2], tripod_points[0][:2], tripod_points[1][:2], tripod_points[2][:2])
        mean_speed = 0.0
        if prev_support_points is not None:
            mean_speed = float(
                np.mean(
                    [
                        np.linalg.norm(points_world[key] - prev_support_points[key])
                        for key in tripod_keys
                    ]
                )
            )
        center_error = float(np.linalg.norm(com_position[:2] - centroid[:2]))
        score = (1000.0 if inside else 0.0) + area_xy * 100.0 - mean_speed * 20.0 - center_error * 5.0
        if score > best_score:
            best_score = score
            best_choice = {
                "tripod_keys": tripod_keys,
                "tripod_points": tripod_points,
                "tripod_center": centroid,
                "tripod_area_xy": area_xy,
                "com_inside_tripod": inside,
            }
    assert best_choice is not None
    return best_choice


class MetricSkeletonMapper:
    def __init__(self, calibration: dict[str, float] | None) -> None:
        self.calibration = calibration
        self.scale_m_per_local: float | None = None
        self.offset_world: np.ndarray | None = None
        self.prev_local: dict[str, np.ndarray] | None = None
        self.prev_world: dict[str, np.ndarray] | None = None
        self.torso_length_local: float | None = None
        self.scale_lock_frames = 8
        self.scale_candidates: list[float] = []

    def smooth_world_points(self, points_world: dict[str, np.ndarray]) -> dict[str, np.ndarray]:
        if self.prev_world is None:
            self.prev_world = {key: value.copy() for key, value in points_world.items()}
            return points_world

        smoothed: dict[str, np.ndarray] = {}
        for key, value in points_world.items():
            alpha = POINT_SMOOTHING_ALPHA.get(key, 0.40)
            prev = self.prev_world[key]
            smoothed[key] = alpha * value + (1.0 - alpha) * prev
        self.prev_world = {key: value.copy() for key, value in smoothed.items()}
        return smoothed

    def snapshot_state(self) -> dict[str, object]:
        return {
            "scale_m_per_local": self.scale_m_per_local,
            "offset_world": None if self.offset_world is None else self.offset_world.copy(),
            "prev_local": None if self.prev_local is None else {key: value.copy() for key, value in self.prev_local.items()},
            "prev_world": None if self.prev_world is None else {key: value.copy() for key, value in self.prev_world.items()},
            "torso_length_local": self.torso_length_local,
            "scale_candidates": list(self.scale_candidates),
        }

    def restore_state(self, snapshot: dict[str, object]) -> None:
        self.scale_m_per_local = snapshot["scale_m_per_local"]  # type: ignore[assignment]
        offset_world = snapshot["offset_world"]
        self.offset_world = None if offset_world is None else np.asarray(offset_world, dtype=np.float64).copy()
        prev_local = snapshot["prev_local"]
        self.prev_local = None if prev_local is None else {key: np.asarray(value, dtype=np.float64).copy() for key, value in prev_local.items()}
        prev_world = snapshot["prev_world"]
        self.prev_world = None if prev_world is None else {key: np.asarray(value, dtype=np.float64).copy() for key, value in prev_world.items()}
        self.torso_length_local = snapshot["torso_length_local"]  # type: ignore[assignment]
        self.scale_candidates = list(snapshot["scale_candidates"])  # type: ignore[arg-type]

    def map_frame(self, world_landmarks: list) -> dict[str, np.ndarray]:
        points_local = build_pose_points(world_landmarks, self.calibration, self.prev_local)
        if self.torso_length_local is None:
            self.torso_length_local = float(np.linalg.norm(points_local["thorax"] - points_local["pelvis"]))
        points_local = apply_fixed_torso_length(points_local, self.torso_length_local)
        self.prev_local = {key: value.copy() for key, value in points_local.items()}

        temp_scale = self.scale_m_per_local
        if self.calibration is not None:
            shoulder_width_local = float(np.linalg.norm(points_local["left_shoulder"] - points_local["right_shoulder"]))
            if shoulder_width_local > 1e-6:
                candidate = float(self.calibration["shoulder_width_m"] / shoulder_width_local)
                candidate = float(np.clip(candidate, 0.5, 2.5))
                if self.scale_m_per_local is None:
                    self.scale_candidates.append(candidate)
                    temp_scale = float(np.median(self.scale_candidates))
                    if len(self.scale_candidates) >= self.scale_lock_frames:
                        self.scale_m_per_local = temp_scale
        if temp_scale is None:
            temp_scale = 1.0
        if self.scale_m_per_local is not None and self.offset_world is None:
            self.offset_world = np.array([0.0, 0.0, 1.05], dtype=np.float64) - points_local["pelvis"] * self.scale_m_per_local
        offset_world = self.offset_world
        if offset_world is None:
            offset_world = np.array([0.0, 0.0, 1.05], dtype=np.float64) - points_local["pelvis"] * temp_scale

        points_world = {
            key: value * temp_scale + offset_world
            for key, value in points_local.items()
        }
        return self.smooth_world_points(points_world)


class CustomSkeletonRig:
    def __init__(self, model: mujoco.MjModel, data: mujoco.MjData) -> None:
        self.model = model
        self.data = data
        self.joint_mocap_ids = {key: int(model.body_mocapid[mujoco.mj_name2id(model, mujoco.mjtObj.mjOBJ_BODY, body)]) for key, body in JOINT_BODY_MAP.items()}
        self.segment_mocap_ids = {key: int(model.body_mocapid[mujoco.mj_name2id(model, mujoco.mjtObj.mjOBJ_BODY, key)]) for key in SEGMENTS}
        self.segment_geom_ids = {key: mujoco.mj_name2id(model, mujoco.mjtObj.mjOBJ_GEOM, f"{key}_geom") for key in SEGMENTS}
        self.com_marker_mocap_id = int(
            model.body_mocapid[mujoco.mj_name2id(model, mujoco.mjtObj.mjOBJ_BODY, "marker_com")]
        )
        self.support_marker_mocap_id = int(
            model.body_mocapid[mujoco.mj_name2id(model, mujoco.mjtObj.mjOBJ_BODY, "marker_support_center")]
        )
        self.tripod_marker_mocap_id = int(
            model.body_mocapid[mujoco.mj_name2id(model, mujoco.mjtObj.mjOBJ_BODY, "marker_tripod_center")]
        )

    def update_pose(
        self,
        points_world: dict[str, np.ndarray],
        com_position: np.ndarray | None = None,
        support_center: np.ndarray | None = None,
        tripod_center: np.ndarray | None = None,
    ) -> None:
        for joint_name, mocap_id in self.joint_mocap_ids.items():
            self.data.mocap_pos[mocap_id] = points_world[joint_name]
            self.data.mocap_quat[mocap_id] = np.array([1.0, 0.0, 0.0, 0.0], dtype=np.float64)

        for seg_name, (start_key, end_key, radius) in SEGMENTS.items():
            start = points_world[start_key]
            end = points_world[end_key]
            direction = end - start
            length = float(np.linalg.norm(direction))
            gid = self.segment_geom_ids[seg_name]
            self.data.mocap_pos[self.segment_mocap_ids[seg_name]] = 0.5 * (start + end)
            self.data.mocap_quat[self.segment_mocap_ids[seg_name]] = quat_from_z_axis(direction)
            self.model.geom_size[gid, 0] = radius
            self.model.geom_size[gid, 1] = max(0.5 * length - radius, 1e-3)

        if com_position is not None:
            self.data.mocap_pos[self.com_marker_mocap_id] = np.asarray(com_position, dtype=np.float64)
            self.data.mocap_quat[self.com_marker_mocap_id] = np.array([1.0, 0.0, 0.0, 0.0], dtype=np.float64)
        if support_center is not None:
            self.data.mocap_pos[self.support_marker_mocap_id] = np.asarray(support_center, dtype=np.float64)
            self.data.mocap_quat[self.support_marker_mocap_id] = np.array([1.0, 0.0, 0.0, 0.0], dtype=np.float64)
        if tripod_center is not None:
            self.data.mocap_pos[self.tripod_marker_mocap_id] = np.asarray(tripod_center, dtype=np.float64)
            self.data.mocap_quat[self.tripod_marker_mocap_id] = np.array([1.0, 0.0, 0.0, 0.0], dtype=np.float64)

        mujoco.mj_forward(self.model, self.data)


def draw_overlay(
    frame_bgr: np.ndarray,
    pose_landmarks: list | None,
    metrics: dict[str, float] | None = None,
    tripod_label: str | None = None,
) -> np.ndarray:
    canvas = frame_bgr.copy()
    if not pose_landmarks:
        return canvas
    h, w = canvas.shape[:2]
    for label, idx in OVERLAY_POINTS.items():
        lm = pose_landmarks[idx]
        x = int(np.clip(lm.x * w, 0, w - 1))
        y = int(np.clip(lm.y * h, 0, h - 1))
        cv2.circle(canvas, (x, y), 4, (0, 255, 255), -1)
        cv2.putText(canvas, label, (x + 4, y - 4), cv2.FONT_HERSHEY_SIMPLEX, 0.4, (20, 230, 255), 1, cv2.LINE_AA)
    if metrics:
        lines = [
            f"Mass: {metrics['body_mass_kg']:.1f} kg",
            f"CoM XYZ: {metrics['com_x']:.3f}, {metrics['com_y']:.3f}, {metrics['com_z']:.3f}",
            f"Support XY margin: {metrics['com_support_margin_xy']:.3f} m",
        ]
        if tripod_label:
            lines.append(f"Tripod: {tripod_label}")
        for idx, text in enumerate(lines):
            cv2.putText(
                canvas,
                text,
                (14, 24 + idx * 22),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.58,
                (40, 240, 160),
                2,
                cv2.LINE_AA,
            )
    return canvas


def run_live(
    input_video: Path,
    xml_path: Path,
    task_model: Path,
    calibration_json: Path | None,
    body_mass_kg: float,
) -> None:
    calibration = load_calibration_json(calibration_json)
    mapper = MetricSkeletonMapper(calibration)
    model = mujoco.MjModel.from_xml_path(str(xml_path))
    data = mujoco.MjData(model)
    rig = CustomSkeletonRig(model, data)

    landmarker = make_landmarker(task_model)
    cap = cv2.VideoCapture(str(input_video))
    if not cap.isOpened():
        raise FileNotFoundError(f"Input video not found: {input_video}")

    fps = max(cap.get(cv2.CAP_PROP_FPS), 30.0)
    prev_support_points: dict[str, np.ndarray] | None = None
    with mujoco.viewer.launch_passive(model, data) as viewer:
        frame_idx = 0
        while viewer.is_running():
            ok, frame_bgr = cap.read()
            if not ok:
                break

            frame_rgb = cv2.cvtColor(frame_bgr, cv2.COLOR_BGR2RGB)
            mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=frame_rgb)
            ts_ms = int(frame_idx * 1000.0 / fps)
            result = landmarker.detect_for_video(mp_image, ts_ms)
            metrics = None
            if result.pose_world_landmarks:
                points_world = mapper.map_frame(result.pose_world_landmarks[0])
                metrics = compute_com_and_support(points_world, body_mass_kg)
                tripod = select_tripod_support(
                    points_world,
                    np.asarray(metrics["com_position"], dtype=np.float64),
                    prev_support_points,
                )
                rig.update_pose(
                    points_world,
                    com_position=np.asarray(metrics["com_position"], dtype=np.float64),
                    support_center=np.asarray(metrics["support_center"], dtype=np.float64),
                    tripod_center=np.asarray(tripod["tripod_center"], dtype=np.float64),
                )
                prev_support_points = {
                    key: np.asarray(points_world[key], dtype=np.float64).copy()
                    for key in SUPPORT_POINT_KEYS
                }
                viewer.sync()

            overlay_metrics = None
            tripod_label = None
            if metrics is not None:
                com_position = np.asarray(metrics["com_position"], dtype=np.float64)
                overlay_metrics = {
                    "body_mass_kg": float(body_mass_kg),
                    "com_x": float(com_position[0]),
                    "com_y": float(com_position[1]),
                    "com_z": float(com_position[2]),
                    "com_support_margin_xy": float(metrics["com_support_margin_xy"]),
                }
                tripod_label = "/".join(
                    key.replace("left_", "L-").replace("right_", "R-")
                    for key in tripod["tripod_keys"]
                )
            overlay = draw_overlay(
                frame_bgr,
                result.pose_landmarks[0] if result.pose_landmarks else None,
                metrics=overlay_metrics,
                tripod_label=tripod_label,
            )
            cv2.imshow("Custom Skeleton Input", overlay)
            if cv2.waitKey(1) & 0xFF == 27:
                break
            frame_idx += 1
            time.sleep(0.001)

    cap.release()
    cv2.destroyAllWindows()


def main() -> None:
    parser = argparse.ArgumentParser(description="Exact-pose custom skeleton verifier driven by corrected MediaPipe 3D joints.")
    parser.add_argument("--input-video", type=Path, default=ROOT.parent / "video" / "주황.mp4")
    parser.add_argument("--xml", type=Path, default=ROOT / "custom_skeleton_rig.xml")
    parser.add_argument("--task-model", type=Path, default=ROOT / "pose_landmarker_lite.task")
    parser.add_argument("--calibration-json", type=Path, default=ROOT / "calibration.json")
    parser.add_argument("--body-mass-kg", type=float, default=80.0)
    args = parser.parse_args()
    if not args.input_video.exists():
        fallback_video = ROOT.parent / "video" / "주황.mp4"
        if fallback_video.exists():
            args.input_video = fallback_video

    run_live(
        input_video=args.input_video.resolve(),
        xml_path=args.xml.resolve(),
        task_model=args.task_model.resolve(),
        calibration_json=args.calibration_json.resolve() if args.calibration_json else None,
        body_mass_kg=float(args.body_mass_kg),
    )


if __name__ == "__main__":
    main()

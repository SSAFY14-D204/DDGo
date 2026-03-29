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

from physics_worker import PhysicalLoadAnalyzer, build_analysis_model, load_analysis_payload

# BlazePose landmark indices
LEFT_SHOULDER = 11
RIGHT_SHOULDER = 12
LEFT_ELBOW = 13
RIGHT_ELBOW = 14
LEFT_WRIST = 15
RIGHT_WRIST = 16
LEFT_HIP = 23
RIGHT_HIP = 24
LEFT_KNEE = 25
RIGHT_KNEE = 26
LEFT_ANKLE = 27
RIGHT_ANKLE = 28
LEFT_HEEL = 29
RIGHT_HEEL = 30
LEFT_FOOT_INDEX = 31
RIGHT_FOOT_INDEX = 32

MAJOR_JOINTS = [
    "abdomen_z",
    "abdomen_y",
    "abdomen_x",
    "hip_x_right",
    "hip_z_right",
    "hip_y_right",
    "knee_right",
    "ankle_y_right",
    "ankle_x_right",
    "hip_x_left",
    "hip_z_left",
    "hip_y_left",
    "knee_left",
    "ankle_y_left",
    "ankle_x_left",
    "shoulder1_right",
    "shoulder2_right",
    "elbow_right",
    "shoulder1_left",
    "shoulder2_left",
    "elbow_left",
]

REQUIRED_BODIES = {
    "torso",
    "pelvis",
    "hand_left",
    "hand_right",
    "foot_left",
    "foot_right",
    "upper_arm_left",
    "upper_arm_right",
}

LANDMARK_TO_STRESS_JOINTS = {
    LEFT_SHOULDER: ["shoulder1_left", "shoulder2_left"],
    RIGHT_SHOULDER: ["shoulder1_right", "shoulder2_right"],
    LEFT_ELBOW: ["elbow_left"],
    RIGHT_ELBOW: ["elbow_right"],
    LEFT_HIP: ["hip_x_left", "hip_y_left", "hip_z_left"],
    RIGHT_HIP: ["hip_x_right", "hip_y_right", "hip_z_right"],
    LEFT_KNEE: ["knee_left"],
    RIGHT_KNEE: ["knee_right"],
    LEFT_ANKLE: ["ankle_x_left", "ankle_y_left"],
    RIGHT_ANKLE: ["ankle_x_right", "ankle_y_right"],
}


def body_id(model: mujoco.MjModel, name: str) -> int:
    bid = mujoco.mj_name2id(model, mujoco.mjtObj.mjOBJ_BODY, name)
    if bid < 0:
        raise ValueError(f"Body not found: {name}")
    return bid


def joint_id(model: mujoco.MjModel, name: str) -> int:
    jid = mujoco.mj_name2id(model, mujoco.mjtObj.mjOBJ_JOINT, name)
    if jid < 0:
        raise ValueError(f"Joint not found: {name}")
    return jid


def actuator_id(model: mujoco.MjModel, name: str) -> int:
    aid = mujoco.mj_name2id(model, mujoco.mjtObj.mjOBJ_ACTUATOR, name)
    if aid < 0:
        raise ValueError(f"Actuator not found: {name}")
    return aid


def normalize(v: np.ndarray, eps: float = 1e-8) -> np.ndarray:
    n = np.linalg.norm(v)
    if n < eps:
        return np.zeros_like(v)
    return v / n


def lerp_dict(prev_values: dict[str, float], next_values: dict[str, float], alpha: float) -> dict[str, float]:
    a = float(np.clip(alpha, 0.0, 1.0))
    out: dict[str, float] = {}
    for key in MAJOR_JOINTS:
        p = float(prev_values.get(key, next_values.get(key, 0.0)))
        n = float(next_values.get(key, p))
        out[key] = (1.0 - a) * p + a * n
    return out


class IdentityFilter:
    def apply(self, values: dict[str, float], _: float) -> dict[str, float]:
        return dict(values)


class EMAFilter:
    def __init__(self, alpha: float) -> None:
        self.alpha = float(np.clip(alpha, 0.0, 1.0))
        self.state: dict[str, float] = {}

    def apply(self, values: dict[str, float], _: float) -> dict[str, float]:
        out: dict[str, float] = {}
        for key, val in values.items():
            raw = float(val)
            prev = self.state.get(key, raw)
            filt = self.alpha * raw + (1.0 - self.alpha) * prev
            self.state[key] = filt
            out[key] = filt
        return out


class DoubleEMAFilter:
    def __init__(self, alpha: float) -> None:
        self.alpha = float(np.clip(alpha, 0.0, 1.0))
        self.state_1: dict[str, float] = {}
        self.state_2: dict[str, float] = {}

    def apply(self, values: dict[str, float], _: float) -> dict[str, float]:
        out: dict[str, float] = {}
        for key, val in values.items():
            raw = float(val)
            prev_1 = self.state_1.get(key, raw)
            prev_2 = self.state_2.get(key, raw)
            ema_1 = self.alpha * raw + (1.0 - self.alpha) * prev_1
            ema_2 = self.alpha * ema_1 + (1.0 - self.alpha) * prev_2
            self.state_1[key] = ema_1
            self.state_2[key] = ema_2
            out[key] = ema_2
        return out


class OneEuroFilter:
    def __init__(self, min_cutoff: float, beta: float, d_cutoff: float) -> None:
        self.min_cutoff = max(float(min_cutoff), 1e-4)
        self.beta = float(beta)
        self.d_cutoff = max(float(d_cutoff), 1e-4)
        self.prev_time_s: float | None = None
        self.prev_raw: dict[str, float] = {}
        self.prev_filtered: dict[str, float] = {}
        self.prev_d_filtered: dict[str, float] = {}

    @staticmethod
    def _alpha(cutoff_hz: float, dt: float) -> float:
        tau = 1.0 / (2.0 * math.pi * max(cutoff_hz, 1e-6))
        return float(1.0 / (1.0 + tau / max(dt, 1e-6)))

    def apply(self, values: dict[str, float], timestamp_s: float) -> dict[str, float]:
        t = float(timestamp_s)
        if self.prev_time_s is None:
            self.prev_time_s = t
            self.prev_raw = {k: float(v) for k, v in values.items()}
            self.prev_filtered = {k: float(v) for k, v in values.items()}
            self.prev_d_filtered = {k: 0.0 for k in values}
            return dict(values)

        dt = max(t - self.prev_time_s, 1e-3)
        out: dict[str, float] = {}
        alpha_d = self._alpha(self.d_cutoff, dt)

        for key, val in values.items():
            raw = float(val)
            prev_raw = self.prev_raw.get(key, raw)
            prev_f = self.prev_filtered.get(key, raw)
            prev_df = self.prev_d_filtered.get(key, 0.0)

            deriv = (raw - prev_raw) / dt
            d_filt = alpha_d * deriv + (1.0 - alpha_d) * prev_df
            cutoff = self.min_cutoff + self.beta * abs(d_filt)
            alpha = self._alpha(cutoff, dt)
            filt = alpha * raw + (1.0 - alpha) * prev_f

            out[key] = filt
            self.prev_raw[key] = raw
            self.prev_filtered[key] = filt
            self.prev_d_filtered[key] = d_filt

        self.prev_time_s = t
        return out


def build_target_filter(
    mode: str,
    ema_alpha: float,
    one_euro_min_cutoff: float,
    one_euro_beta: float,
    one_euro_d_cutoff: float,
):
    if mode == "none":
        return IdentityFilter()
    if mode == "ema":
        return EMAFilter(alpha=ema_alpha)
    if mode == "double_ema":
        return DoubleEMAFilter(alpha=ema_alpha)
    return OneEuroFilter(
        min_cutoff=one_euro_min_cutoff,
        beta=one_euro_beta,
        d_cutoff=one_euro_d_cutoff,
    )


def angle_3d(a: np.ndarray, b: np.ndarray, c: np.ndarray) -> float:
    ba = a - b
    bc = c - b
    denom = np.linalg.norm(ba) * np.linalg.norm(bc)
    if denom < 1e-8:
        return math.pi
    cosine = float(np.clip(np.dot(ba, bc) / denom, -1.0, 1.0))
    return math.acos(cosine)


def mp_to_mj(point_xyz: np.ndarray) -> np.ndarray:
    """MediaPipe world -> MuJoCo coordinates.

    MediaPipe world: x(right), y(down), z(depth)
    MuJoCo (humanoid.xml): x(forward), y(left-right), z(up)
    """
    x, y, z = point_xyz
    # Right side in this humanoid is negative y, left side is positive y.
    return np.array([-z, -x, -y], dtype=np.float64)


def lm_xyz(world_landmarks, idx: int) -> np.ndarray:
    p = world_landmarks[idx]
    return np.array([p.x, p.y, p.z], dtype=np.float64)


def stress_color_bgr(ratio: float) -> tuple[int, int, int]:
    ratio = float(max(ratio, 0.0))
    if ratio <= 0.8:
        alpha = ratio / 0.8 if ratio > 0.0 else 0.0
        red = int(round(255.0 * alpha))
        return (0, 255, red)
    alpha = min((ratio - 0.8) / 0.2, 1.0)
    green = int(round(255.0 * (1.0 - alpha)))
    return (0, green, 255)


def landmark_stress_ratios(physical_metrics: dict[str, Any] | None) -> dict[int, float]:
    if not physical_metrics:
        return {}
    ratio_map = physical_metrics.get("joint_ratio_map") or {}
    out: dict[int, float] = {}
    for landmark_idx, joint_names in LANDMARK_TO_STRESS_JOINTS.items():
        values = [float(ratio_map.get(joint_name, 0.0)) for joint_name in joint_names]
        out[landmark_idx] = max(values) if values else 0.0
    return out


def draw_pose_2d(frame: np.ndarray, normalized_landmarks, stress_ratios: dict[int, float] | None = None) -> None:
    h, w = frame.shape[:2]
    stress_ratios = stress_ratios or {}
    for idx, p in enumerate(normalized_landmarks):
        x = int(p.x * w)
        y = int(p.y * h)
        if 0 <= x < w and 0 <= y < h:
            color = stress_color_bgr(stress_ratios.get(idx, 0.0))
            radius = 4 if idx in stress_ratios else 2
            cv2.circle(frame, (x, y), radius, color, -1)


def render_stress_sidebar(frame: np.ndarray, physical_metrics: dict[str, Any] | None) -> None:
    if not physical_metrics:
        return

    overlay = frame.copy()
    h, w = frame.shape[:2]
    sidebar_w = min(260, max(180, w // 3))
    x0 = max(w - sidebar_w - 12, 0)
    y0 = 10
    y1 = min(h - 10, 172)
    cv2.rectangle(overlay, (x0, y0), (w - 10, y1), (18, 18, 18), -1)
    cv2.addWeighted(overlay, 0.55, frame, 0.45, 0.0, frame)

    cv2.putText(frame, "Joint Stress", (x0 + 12, y0 + 24), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (245, 245, 245), 2)
    top_stressed = physical_metrics.get("top_stressed_joints") or []
    for idx, joint_entry in enumerate(top_stressed[:3]):
        ratio = float(joint_entry["ratio"])
        color = stress_color_bgr(ratio)
        label = f"{joint_entry['joint_id']}: {ratio * 100.0:.0f}%"
        cv2.putText(
            frame,
            label,
            (x0 + 12, y0 + 52 + idx * 28),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.52,
            color,
            2,
        )

    contact_count = int(physical_metrics.get("contact_count", 0))
    cv2.putText(
        frame,
        f"holds={contact_count} com={float(physical_metrics.get('com_stability', 0.0)):.2f}",
        (x0 + 12, y0 + 136),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.48,
        (210, 210, 210),
        1,
    )

    failure_type = physical_metrics.get("failure_type")
    if failure_type:
        color = (0, 0, 255) if failure_type == "STRENGTH_LIMIT" else (0, 165, 255)
        cv2.putText(
            frame,
            failure_type,
            (x0 + 12, y0 + 160),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.5,
            color,
            2,
        )


def quat_from_axes(x_axis: np.ndarray, y_axis: np.ndarray, z_axis: np.ndarray) -> np.ndarray:
    """Convert orthonormal basis (world axes of body frame) to MuJoCo quaternion [w, x, y, z]."""
    r00, r01, r02 = x_axis[0], y_axis[0], z_axis[0]
    r10, r11, r12 = x_axis[1], y_axis[1], z_axis[1]
    r20, r21, r22 = x_axis[2], y_axis[2], z_axis[2]

    trace = r00 + r11 + r22
    if trace > 0.0:
        s = math.sqrt(trace + 1.0) * 2.0
        w = 0.25 * s
        x = (r21 - r12) / s
        y = (r02 - r20) / s
        z = (r10 - r01) / s
    elif (r00 > r11) and (r00 > r22):
        s = math.sqrt(1.0 + r00 - r11 - r22) * 2.0
        w = (r21 - r12) / s
        x = 0.25 * s
        y = (r01 + r10) / s
        z = (r02 + r20) / s
    elif r11 > r22:
        s = math.sqrt(1.0 + r11 - r00 - r22) * 2.0
        w = (r02 - r20) / s
        x = (r01 + r10) / s
        y = 0.25 * s
        z = (r12 + r21) / s
    else:
        s = math.sqrt(1.0 + r22 - r00 - r11) * 2.0
        w = (r10 - r01) / s
        x = (r02 + r20) / s
        y = (r12 + r21) / s
        z = 0.25 * s

    q = np.array([w, x, y, z], dtype=np.float64)
    n = np.linalg.norm(q)
    if n < 1e-8:
        return np.array([1.0, 0.0, 0.0, 0.0], dtype=np.float64)
    return q / n


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


def validate_joint_map(artifact_path: Path) -> tuple[bool, str]:
    if not artifact_path.exists():
        return False, f"[WARN] joint map not found: {artifact_path}"

    payload = json.loads(artifact_path.read_text(encoding="utf-8-sig"))
    body_map = (payload.get("index_map") or {}).get("body") or {}
    missing = sorted(list(REQUIRED_BODIES - set(body_map.keys())))
    if missing:
        return False, f"[FAIL] joint map missing bodies: {missing}"
    return True, "[OK] joint map check passed (required body names exist)"


def extract_joint_angles(world_landmarks: list, swap_lr: bool = False) -> tuple[dict[str, float], dict[str, np.ndarray]]:
    """Analytical IK angle extractor from MediaPipe world landmarks.

    Returns:
      joint_targets_rad: MuJoCo joint target angles (radians)
      points_mj: key landmark points in MuJoCo coordinates for diagnostics
    """
    l_sh_idx, r_sh_idx = (RIGHT_SHOULDER, LEFT_SHOULDER) if swap_lr else (LEFT_SHOULDER, RIGHT_SHOULDER)
    l_el_idx, r_el_idx = (RIGHT_ELBOW, LEFT_ELBOW) if swap_lr else (LEFT_ELBOW, RIGHT_ELBOW)
    l_wr_idx, r_wr_idx = (RIGHT_WRIST, LEFT_WRIST) if swap_lr else (LEFT_WRIST, RIGHT_WRIST)
    l_hi_idx, r_hi_idx = (RIGHT_HIP, LEFT_HIP) if swap_lr else (LEFT_HIP, RIGHT_HIP)
    l_kn_idx, r_kn_idx = (RIGHT_KNEE, LEFT_KNEE) if swap_lr else (LEFT_KNEE, RIGHT_KNEE)
    l_an_idx, r_an_idx = (RIGHT_ANKLE, LEFT_ANKLE) if swap_lr else (LEFT_ANKLE, RIGHT_ANKLE)
    l_he_idx, r_he_idx = (RIGHT_HEEL, LEFT_HEEL) if swap_lr else (LEFT_HEEL, RIGHT_HEEL)
    l_to_idx, r_to_idx = (RIGHT_FOOT_INDEX, LEFT_FOOT_INDEX) if swap_lr else (LEFT_FOOT_INDEX, RIGHT_FOOT_INDEX)

    # Convert relevant points into MuJoCo axes.
    # Keep torso-frame axes from anatomical (non-swapped) landmarks so that
    # swap_lr only affects limb assignment, not body-facing direction.
    ls_raw = mp_to_mj(lm_xyz(world_landmarks, LEFT_SHOULDER))
    rs_raw = mp_to_mj(lm_xyz(world_landmarks, RIGHT_SHOULDER))
    lh_raw = mp_to_mj(lm_xyz(world_landmarks, LEFT_HIP))
    rh_raw = mp_to_mj(lm_xyz(world_landmarks, RIGHT_HIP))

    ls = mp_to_mj(lm_xyz(world_landmarks, l_sh_idx))
    rs = mp_to_mj(lm_xyz(world_landmarks, r_sh_idx))
    le = mp_to_mj(lm_xyz(world_landmarks, l_el_idx))
    re = mp_to_mj(lm_xyz(world_landmarks, r_el_idx))
    lw = mp_to_mj(lm_xyz(world_landmarks, l_wr_idx))
    rw = mp_to_mj(lm_xyz(world_landmarks, r_wr_idx))

    lh = mp_to_mj(lm_xyz(world_landmarks, l_hi_idx))
    rh = mp_to_mj(lm_xyz(world_landmarks, r_hi_idx))
    lk = mp_to_mj(lm_xyz(world_landmarks, l_kn_idx))
    rk = mp_to_mj(lm_xyz(world_landmarks, r_kn_idx))
    la = mp_to_mj(lm_xyz(world_landmarks, l_an_idx))
    ra = mp_to_mj(lm_xyz(world_landmarks, r_an_idx))

    lheel = mp_to_mj(lm_xyz(world_landmarks, l_he_idx))
    rheel = mp_to_mj(lm_xyz(world_landmarks, r_he_idx))
    ltoe = mp_to_mj(lm_xyz(world_landmarks, l_to_idx))
    rtoe = mp_to_mj(lm_xyz(world_landmarks, r_to_idx))

    shoulder_mid = 0.5 * (ls_raw + rs_raw)
    hip_mid = 0.5 * (lh_raw + rh_raw)

    up = normalize(shoulder_mid - hip_mid)
    left_axis = normalize(ls_raw - rs_raw)
    forward_axis = normalize(np.cross(left_axis, up))
    if np.linalg.norm(forward_axis) < 1e-6:
        forward_axis = np.array([1.0, 0.0, 0.0], dtype=np.float64)
    left_axis = normalize(np.cross(up, forward_axis))

    # For back-view climbing footage, invert sagittal sign so "reach forward"
    # maps to forward flexion. This axis must also be used for torso root
    # orientation to keep body facing and limb angles consistent.
    sagittal_axis = -forward_axis

    def to_torso_frame(v: np.ndarray) -> np.ndarray:
        # [x_forward, y_left, z_up]
        return np.array([
            float(np.dot(v, sagittal_axis)),
            float(np.dot(v, left_axis)),
            float(np.dot(v, up)),
        ])

    # Torso orientation -> abdomen (rough)
    torso_vec = normalize(shoulder_mid - hip_mid)
    abdomen_x = math.atan2(torso_vec[1], max(torso_vec[2], 1e-6))  # roll
    abdomen_y = -math.atan2(torso_vec[0], max(torso_vec[2], 1e-6))  # pitch
    abdomen_z = 0.0

    def arm_angles(shoulder: np.ndarray, elbow: np.ndarray, wrist: np.ndarray, side_sign: float) -> tuple[float, float, float]:
        # Blend elbow and wrist directions to make shoulder targets follow hand
        # placement more robustly under elbow landmark noise.
        upper_dir = normalize(0.7 * normalize(elbow - shoulder) + 0.3 * normalize(wrist - shoulder))
        upper = to_torso_frame(upper_dir)
        # Shoulder decomposition in torso frame
        shoulder_abd = math.atan2(side_sign * upper[1], max(-upper[2], 1e-6))
        shoulder_flex = math.atan2(upper[0], max(-upper[2], 1e-6))
        elbow_flex = math.pi - angle_3d(shoulder, elbow, wrist)
        # MuJoCo elbow flexion direction in this humanoid is negative
        elbow_q = -elbow_flex
        return shoulder_abd, shoulder_flex, elbow_q

    def leg_angles(
        hip: np.ndarray,
        knee: np.ndarray,
        ankle: np.ndarray,
        heel: np.ndarray,
        toe: np.ndarray,
        side_sign: float,
    ) -> tuple[float, float, float, float, float]:
        thigh = to_torso_frame(normalize(knee - hip))
        shank = to_torso_frame(normalize(ankle - knee))
        foot = to_torso_frame(normalize(toe - heel))

        hip_abd = math.atan2(side_sign * thigh[1], max(-thigh[2], 1e-6))
        hip_flex = math.atan2(thigh[0], max(-thigh[2], 1e-6))
        hip_rot = 0.0

        knee_flex = math.pi - angle_3d(hip, knee, ankle)
        knee_q = -knee_flex

        ankle_pitch = math.atan2(foot[0], max(abs(foot[2]), 1e-6))
        ankle_roll = math.atan2(side_sign * foot[1], max(abs(foot[2]), 1e-6))
        return hip_abd, hip_rot, -hip_flex, knee_q, ankle_pitch, ankle_roll

    s1_r, s2_r, e_r = arm_angles(rs, re, rw, side_sign=1.0)
    s1_l, s2_l, e_l = arm_angles(ls, le, lw, side_sign=-1.0)

    hx_r, hz_r, hy_r, k_r, ay_r, ax_r = leg_angles(rh, rk, ra, rheel, rtoe, side_sign=1.0)
    hx_l, hz_l, hy_l, k_l, ay_l, ax_l = leg_angles(lh, lk, la, lheel, ltoe, side_sign=-1.0)

    joint_targets = {
        "abdomen_z": abdomen_z,
        "abdomen_y": abdomen_y,
        "abdomen_x": abdomen_x,
        "hip_x_right": hx_r,
        "hip_z_right": hz_r,
        "hip_y_right": hy_r,
        "knee_right": k_r,
        "ankle_y_right": ay_r,
        "ankle_x_right": ax_r,
        "hip_x_left": hx_l,
        "hip_z_left": hz_l,
        "hip_y_left": hy_l,
        "knee_left": k_l,
        "ankle_y_left": ay_l,
        "ankle_x_left": ax_l,
        "shoulder1_right": s1_r,
        "shoulder2_right": s2_r,
        "elbow_right": e_r,
        "shoulder1_left": s1_l,
        "shoulder2_left": s2_l,
        "elbow_left": e_l,
    }

    points = {
        "left_shoulder": ls,
        "right_shoulder": rs,
        "left_wrist": lw,
        "right_wrist": rw,
        "left_hip": lh,
        "right_hip": rh,
        "hip_mid": hip_mid,
        "left_ankle": la,
        "right_ankle": ra,
        "axis_forward": sagittal_axis,
        "axis_forward_raw": forward_axis,
        "axis_left": left_axis,
        "axis_up": up,
    }
    return joint_targets, points


class DirectJointController:
    def __init__(
        self,
        model: mujoco.MjModel,
        data: mujoco.MjData,
        mode: str,
        swap_lr: bool,
        target_filter_mode: str,
        filter_alpha: float,
        one_euro_min_cutoff: float,
        one_euro_beta: float,
        one_euro_d_cutoff: float,
        damping_scale: float,
    ) -> None:
        self.model = model
        self.data = data
        self.mode = mode
        self.swap_lr = swap_lr

        self.joint_ids = {name: joint_id(model, name) for name in MAJOR_JOINTS}
        self.qpos_adr = {name: int(model.jnt_qposadr[jid]) for name, jid in self.joint_ids.items()}

        self.joint_limits: dict[str, tuple[float, float]] = {}
        for name, jid in self.joint_ids.items():
            if model.jnt_limited[jid]:
                lo, hi = model.jnt_range[jid]
                self.joint_limits[name] = (float(lo), float(hi))
            else:
                self.joint_limits[name] = (-1e9, 1e9)

        # Position actuator mapping: <joint_name>_pos
        self.actuator_ids: dict[str, int] = {}
        for name in MAJOR_JOINTS:
            aname = f"{name}_pos"
            try:
                self.actuator_ids[name] = actuator_id(model, aname)
            except ValueError:
                pass

        if self.mode == "dynamic" and len(self.actuator_ids) < len(MAJOR_JOINTS):
            missing = sorted(list(set(MAJOR_JOINTS) - set(self.actuator_ids.keys())))
            raise ValueError(f"Missing position actuators for joints: {missing}")

        self.target_filter = build_target_filter(
            mode=target_filter_mode,
            ema_alpha=filter_alpha,
            one_euro_min_cutoff=one_euro_min_cutoff,
            one_euro_beta=one_euro_beta,
            one_euro_d_cutoff=one_euro_d_cutoff,
        )

        if damping_scale > 0 and abs(damping_scale - 1.0) > 1e-6:
            self.model.dof_damping[:] = self.model.dof_damping * float(damping_scale)

        self.torso_bid = body_id(model, "torso")
        self.pelvis_bid = body_id(model, "pelvis")
        self.lhand_bid = body_id(model, "hand_left")
        self.rhand_bid = body_id(model, "hand_right")
        self.lfoot_bid = body_id(model, "foot_left")
        self.rfoot_bid = body_id(model, "foot_right")
        self.lshoulder_bid = body_id(model, "upper_arm_left")
        self.rshoulder_bid = body_id(model, "upper_arm_right")

        mujoco.mj_forward(model, data)

        self.root_pos0 = data.qpos[0:3].copy()
        self.root_quat0 = data.qpos[3:7].copy()
        self.pelvis_anchor = data.xpos[self.pelvis_bid].copy()
        self.torso_from_pelvis = data.xpos[self.torso_bid] - data.xpos[self.pelvis_bid]
        self.model_shoulder = float(np.linalg.norm(data.xpos[self.lshoulder_bid] - data.xpos[self.rshoulder_bid]))

        self.scale_est: float | None = None
        self.offset_est: np.ndarray | None = None
        self.root_target_cache = self.root_pos0.copy()
        self.root_quat_cache = self.root_quat0.copy()
        self.arm_ik_iters = 2

    def _clip_joint(self, name: str, value: float) -> float:
        lo, hi = self.joint_limits[name]
        return float(np.clip(value, lo, hi))

    def _map_point(self, p_local: np.ndarray) -> np.ndarray:
        assert self.scale_est is not None
        assert self.offset_est is not None
        return p_local * self.scale_est + self.offset_est

    def _update_metric_calibration(self, points: dict[str, np.ndarray]) -> None:
        shoulder_w = float(np.linalg.norm(points["left_shoulder"] - points["right_shoulder"]))
        shoulder_w = max(shoulder_w, 1e-6)
        raw_scale = float(np.clip(self.model_shoulder / shoulder_w, 0.6, 2.2))

        if self.scale_est is None:
            self.scale_est = raw_scale
        else:
            self.scale_est = 0.9 * self.scale_est + 0.1 * raw_scale

        raw_offset = self.pelvis_anchor - points["hip_mid"] * self.scale_est
        if self.offset_est is None:
            self.offset_est = raw_offset

    def _refine_arm_targets_by_wrist_ik(
        self,
        targets: dict[str, float],
        points: dict[str, np.ndarray],
    ) -> dict[str, float]:
        if self.scale_est is None or self.offset_est is None:
            return targets

        # Run IK on a temporary pose built from current root and target joints.
        qpos_backup = self.data.qpos.copy()
        qvel_backup = self.data.qvel.copy()
        qacc_backup = self.data.qacc.copy()
        try:
            for name, val in targets.items():
                self.data.qpos[self.qpos_adr[name]] = float(val)
            self.data.qvel[:] = 0.0
            self.data.qacc[:] = 0.0

            arm_specs = [
                (
                    "left_wrist",
                    self.lhand_bid,
                    ["shoulder1_left", "shoulder2_left", "elbow_left"],
                ),
                (
                    "right_wrist",
                    self.rhand_bid,
                    ["shoulder1_right", "shoulder2_right", "elbow_right"],
                ),
            ]

            for wrist_key, hand_bid, joint_names in arm_specs:
                wrist_target_world = self._map_point(points[wrist_key])

                for _ in range(self.arm_ik_iters):
                    mujoco.mj_forward(self.model, self.data)
                    hand_pos = self.data.xpos[hand_bid].copy()
                    err = wrist_target_world - hand_pos
                    if float(np.linalg.norm(err)) < 0.015:
                        break

                    cols = []
                    for jname in joint_names:
                        adr = self.qpos_adr[jname]
                        q0 = float(self.data.qpos[adr])
                        eps = 1e-3

                        self.data.qpos[adr] = self._clip_joint(jname, q0 + eps)
                        mujoco.mj_forward(self.model, self.data)
                        p_plus = self.data.xpos[hand_bid].copy()

                        self.data.qpos[adr] = self._clip_joint(jname, q0 - eps)
                        mujoco.mj_forward(self.model, self.data)
                        p_minus = self.data.xpos[hand_bid].copy()

                        self.data.qpos[adr] = q0
                        cols.append((p_plus - p_minus) / (2.0 * eps))

                    J = np.column_stack(cols)  # (3, 3)
                    # Damped least squares for robust small-step IK.
                    damp = 1e-3
                    A = J.T @ J + damp * np.eye(J.shape[1], dtype=np.float64)
                    b = J.T @ err
                    dq = np.linalg.solve(A, b)
                    dq = np.clip(dq, -0.18, 0.18)

                    for i, jname in enumerate(joint_names):
                        adr = self.qpos_adr[jname]
                        qn = float(self.data.qpos[adr]) + 0.65 * float(dq[i])
                        self.data.qpos[adr] = self._clip_joint(jname, qn)

            refined = dict(targets)
            for jname in [
                "shoulder1_left",
                "shoulder2_left",
                "elbow_left",
                "shoulder1_right",
                "shoulder2_right",
                "elbow_right",
            ]:
                refined[jname] = float(self.data.qpos[self.qpos_adr[jname]])
            return refined
        finally:
            self.data.qpos[:] = qpos_backup
            self.data.qvel[:] = qvel_backup
            self.data.qacc[:] = qacc_backup
            mujoco.mj_forward(self.model, self.data)

    def prepare_targets(self, world_landmarks, timestamp_s: float) -> tuple[dict[str, float], dict[str, np.ndarray]]:
        raw_targets, points = extract_joint_angles(world_landmarks, swap_lr=self.swap_lr)
        clipped = {name: self._clip_joint(name, val) for name, val in raw_targets.items()}
        filtered = self.target_filter.apply(clipped, timestamp_s)
        targets = {name: self._clip_joint(name, val) for name, val in filtered.items()}
        self._update_metric_calibration(points)
        targets = self._refine_arm_targets_by_wrist_ik(targets, points)
        return targets, points

    def apply_kinematic(self, targets: dict[str, float], points: dict[str, np.ndarray]) -> None:
        mapped_pelvis = self._map_point(points["hip_mid"])
        mapped_root = mapped_pelvis + self.torso_from_pelvis

        # Root orientation from torso frame (x=forward, y=left, z=up).
        q_root = quat_from_axes(points["axis_forward"], points["axis_left"], points["axis_up"])

        self.root_target_cache = 0.6 * self.root_target_cache + 0.4 * mapped_root
        self.root_quat_cache = normalize(0.6 * self.root_quat_cache + 0.4 * q_root)
        self.data.qpos[0:3] = self.root_target_cache
        self.data.qpos[3:7] = self.root_quat_cache

        for name, target in targets.items():
            self.data.qpos[self.qpos_adr[name]] = target
        self.data.qvel[:] = 0.0
        self.data.qacc[:] = 0.0
        mujoco.mj_forward(self.model, self.data)

    def set_dynamic_ctrl(self, targets: dict[str, float]) -> None:
        for name, target in targets.items():
            aid = self.actuator_ids[name]
            ctrl = target
            if self.model.actuator_ctrllimited[aid]:
                lo, hi = self.model.actuator_ctrlrange[aid]
                ctrl = float(np.clip(ctrl, lo, hi))
            self.data.ctrl[aid] = ctrl

    def compute_world_targets(self, points: dict[str, np.ndarray]) -> dict[str, np.ndarray]:
        return {
            "pelvis": self._map_point(points["hip_mid"]),
            "left_wrist": self._map_point(points["left_wrist"]),
            "right_wrist": self._map_point(points["right_wrist"]),
            "left_ankle": self._map_point(points["left_ankle"]),
            "right_ankle": self._map_point(points["right_ankle"]),
        }

    def compute_metrics(self, targets: dict[str, float], points: dict[str, np.ndarray]) -> dict[str, object]:
        mapped_targets = self.compute_world_targets(points)
        actual_positions = {
            "pelvis": self.data.xpos[self.pelvis_bid],
            "left_wrist": self.data.xpos[self.lhand_bid],
            "right_wrist": self.data.xpos[self.rhand_bid],
            "left_ankle": self.data.xpos[self.lfoot_bid],
            "right_ankle": self.data.xpos[self.rfoot_bid],
        }
        pos_error_cm = {
            key: float(np.linalg.norm(actual_positions[key] - mapped_targets[key]) * 100.0)
            for key in mapped_targets
        }

        angle_error_deg = {}
        for name, target in targets.items():
            q = float(self.data.qpos[self.qpos_adr[name]])
            angle_error_deg[name] = abs(q - target) * (180.0 / math.pi)

        return {
            "targets": targets,
            "scale": float(self.scale_est),
            "mean_pos_error_cm": float(np.mean(list(pos_error_cm.values()))),
            "mean_angle_error_deg": float(np.mean(list(angle_error_deg.values()))),
            "pos_error_cm": pos_error_cm,
            "angle_error_deg": angle_error_deg,
        }


def build_model(
    xml_path: Path,
    analysis_payload: dict[str, Any] | None = None,
) -> tuple[mujoco.MjModel, mujoco.MjData]:
    return build_analysis_model(xml_path, analysis_payload or {})


def run_self_check(
    xml_path: Path,
    task_path: Path,
    joint_map_path: Path,
    analysis_payload: dict[str, Any],
    mode: str,
    swap_lr: bool,
    target_filter_mode: str,
    filter_alpha: float,
    one_euro_min_cutoff: float,
    one_euro_beta: float,
    one_euro_d_cutoff: float,
    damping_scale: float,
) -> None:
    ok, msg = validate_joint_map(joint_map_path)
    print(msg)

    model, data = build_model(xml_path, analysis_payload)
    with make_landmarker(task_path):
        _ = DirectJointController(
            model,
            data,
            mode=mode,
            swap_lr=swap_lr,
            target_filter_mode=target_filter_mode,
            filter_alpha=filter_alpha,
            one_euro_min_cutoff=one_euro_min_cutoff,
            one_euro_beta=one_euro_beta,
            one_euro_d_cutoff=one_euro_d_cutoff,
            damping_scale=damping_scale,
        )
        mujoco.mj_forward(model, data)

    print(f"[OK] Self-check passed: mode={mode}, gravity={model.opt.gravity.tolist()}")
    if not ok:
        print("[WARN] joint-map validation failed; mapping may not match your artifact.")


def run_live(
    xml_path: Path,
    task_path: Path,
    joint_map_path: Path,
    analysis_payload: dict[str, Any],
    cam_index: int,
    input_video: Path | None,
    mode: str,
    max_frames: int,
    mirror_view: bool,
    mirror_input: bool,
    swap_lr: bool,
    sync_fps: float,
    error_log: Path,
    target_filter_mode: str,
    filter_alpha: float,
    one_euro_min_cutoff: float,
    one_euro_beta: float,
    one_euro_d_cutoff: float,
    damping_scale: float,
) -> None:
    ok, msg = validate_joint_map(joint_map_path)
    print(msg)

    model, data = build_model(xml_path, analysis_payload)
    controller = DirectJointController(
        model,
        data,
        mode=mode,
        swap_lr=swap_lr,
        target_filter_mode=target_filter_mode,
        filter_alpha=filter_alpha,
        one_euro_min_cutoff=one_euro_min_cutoff,
        one_euro_beta=one_euro_beta,
        one_euro_d_cutoff=one_euro_d_cutoff,
        damping_scale=damping_scale,
    )
    load_analyzer = PhysicalLoadAnalyzer(model, data, analysis_payload)

    print(f"[INFO] Mode={mode}")
    print(f"[INFO] Left/Right swap={'ON' if swap_lr else 'OFF'}")
    print(f"[INFO] MuJoCo gravity is active: {model.opt.gravity}")
    print(f"[INFO] Target filter={target_filter_mode}")
    if target_filter_mode == "one_euro":
        print(
            f"[INFO] OneEuro(min_cutoff={one_euro_min_cutoff:.3f}, beta={one_euro_beta:.3f}, "
            f"d_cutoff={one_euro_d_cutoff:.3f})"
        )
    elif target_filter_mode in {"ema", "double_ema"}:
        print(f"[INFO] Filter alpha={filter_alpha:.3f}")
    print(f"[INFO] Damping scale={damping_scale:.2f}")
    print(
        f"[INFO] Physical load analysis: holds={load_analyzer.hold_points.shape[0]}, "
        f"stress_thr={load_analyzer.stress_ratio_threshold:.2f}, "
        f"strength_thr={load_analyzer.strength_ratio_threshold:.2f} x {load_analyzer.strength_consecutive_frames}f"
    )

    if input_video is not None:
        if not input_video.exists():
            raise FileNotFoundError(f"Input video not found: {input_video}")
        cap = cv2.VideoCapture(str(input_video))
        source_desc = f"video={input_video}"
    else:
        cap = cv2.VideoCapture(cam_index)
        source_desc = f"camera={cam_index}"

    if not cap.isOpened():
        raise RuntimeError(f"Could not open input source: {source_desc}")

    cap_fps = float(cap.get(cv2.CAP_PROP_FPS) or 0.0)
    if sync_fps <= 0.0:
        sync_fps = cap_fps if cap_fps > 1.0 else 30.0

    print(f"[INFO] Input source: {source_desc}")
    if cap_fps > 1.0:
        print(f"[INFO] Source FPS: {cap_fps:.2f}")
    print(f"[INFO] Frame sync target: {sync_fps:.2f} FPS")

    error_log.parent.mkdir(parents=True, exist_ok=True)
    log_fp = error_log.open("w", encoding="utf-8")

    frame_idx = 0
    step_total = 0
    t0 = time.time()
    connected = False
    pos_error_sum = 0.0
    angle_error_sum = 0.0
    peak_load_ratio = 0.0
    metric_count = 0
    prev_targets: dict[str, float] | None = None
    prev_points: dict[str, np.ndarray] | None = None

    try:
        with make_landmarker(task_path) as landmarker, mujoco.viewer.launch_passive(model, data) as viewer:
            print("[INFO] Direct-joint mapping started. Press 'q' or close viewer to exit.")

            while viewer.is_running():
                ok_frame, frame = cap.read()
                if not ok_frame:
                    print("[INFO] Input stream ended or frame read failed; stopping.")
                    break

                input_frame = cv2.flip(frame, 1) if mirror_input else frame
                rgb = cv2.cvtColor(input_frame, cv2.COLOR_BGR2RGB)

                mp_ts_ms = int(round(frame_idx * (1000.0 / sync_fps)))
                mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb)
                result = landmarker.detect_for_video(mp_image, mp_ts_ms)

                metrics = None
                physical_metrics = None
                frame_targets: dict[str, float] | None = None
                frame_points: dict[str, np.ndarray] | None = None
                if result.pose_world_landmarks:
                    frame_targets, frame_points = controller.prepare_targets(result.pose_world_landmarks[0], mp_ts_ms / 1000.0)
                    if not connected:
                        connected = True
                        print("[OK] pose_world_landmarks detected; direct joint control active")

                target_sim_time = (frame_idx + 1) / sync_fps
                step_count = 0

                if mode == "kinematic":
                    # Strict kinematic mode: bypass physics integration.
                    if frame_targets is not None and frame_points is not None:
                        controller.apply_kinematic(frame_targets, frame_points)
                        metrics = controller.compute_metrics(frame_targets, frame_points)
                        physical_metrics = load_analyzer.analyze_frame(
                            timestamp_ms=mp_ts_ms,
                            limb_targets_world={
                                key: value.copy()
                                for key, value in controller.compute_world_targets(frame_points).items()
                                if key != "pelvis"
                            },
                        )
                        prev_targets = dict(frame_targets)
                        prev_points = frame_points
                    data.time = target_sim_time
                else:
                    # Dynamic mode: interpolate filtered targets across physics sub-steps.
                    to_targets = frame_targets if frame_targets is not None else prev_targets
                    to_points = frame_points if frame_points is not None else prev_points
                    from_targets = prev_targets if prev_targets is not None else to_targets
                    start_time = float(data.time)
                    duration = max(float(target_sim_time) - start_time, model.opt.timestep)

                    while data.time + model.opt.timestep * 0.5 < target_sim_time:
                        if to_targets is not None:
                            if from_targets is None:
                                step_targets = to_targets
                            else:
                                phase = (data.time + model.opt.timestep - start_time) / duration
                                step_targets = lerp_dict(from_targets, to_targets, alpha=phase)
                            controller.set_dynamic_ctrl(step_targets)
                        mujoco.mj_step(model, data)
                        step_count += 1
                    step_total += step_count

                    if to_targets is not None and to_points is not None:
                        metrics = controller.compute_metrics(to_targets, to_points)
                        physical_metrics = load_analyzer.analyze_frame(
                            timestamp_ms=mp_ts_ms,
                            limb_targets_world={
                                key: value.copy()
                                for key, value in controller.compute_world_targets(to_points).items()
                                if key != "pelvis"
                            },
                        )
                        prev_targets = dict(to_targets)
                        prev_points = to_points

                viewer.sync()

                draw_frame = input_frame.copy()
                if result.pose_landmarks:
                    draw_pose_2d(draw_frame, result.pose_landmarks[0], stress_ratios=landmark_stress_ratios(physical_metrics))
                if mirror_view:
                    draw_frame = cv2.flip(draw_frame, 1)
                render_stress_sidebar(draw_frame, physical_metrics)

                if metrics is not None:
                    metric_count += 1
                    pos_error_sum += float(metrics["mean_pos_error_cm"])
                    angle_error_sum += float(metrics["mean_angle_error_deg"])
                    if physical_metrics is not None:
                        top_joint = (physical_metrics.get("top_stressed_joints") or [{}])[0]
                        peak_load_ratio = max(peak_load_ratio, float(top_joint.get("ratio", 0.0)))

                    record = {
                        "frame_index": frame_idx,
                        "mp_timestamp_ms": mp_ts_ms,
                        "mj_time_s": float(data.time),
                        "mode": mode,
                        "scale": float(metrics["scale"]),
                        "mean_pos_error_cm": float(metrics["mean_pos_error_cm"]),
                        "mean_angle_error_deg": float(metrics["mean_angle_error_deg"]),
                        "targets": metrics["targets"],
                        "pos_error_cm": metrics["pos_error_cm"],
                        "angle_error_deg": metrics["angle_error_deg"],
                        "mujoco_steps_this_frame": step_count,
                        "physical_load": physical_metrics,
                    }
                    log_fp.write(json.dumps(record, ensure_ascii=False) + "\n")

                    cv2.putText(
                        draw_frame,
                        f"err_pos={metrics['mean_pos_error_cm']:.1f}cm err_ang={metrics['mean_angle_error_deg']:.1f}deg",
                        (10, 48),
                        cv2.FONT_HERSHEY_SIMPLEX,
                        0.55,
                        (0, 255, 255),
                        2,
                    )
                    if physical_metrics is not None and physical_metrics.get("top_stressed_joints"):
                        top_joint = physical_metrics["top_stressed_joints"][0]
                        cv2.putText(
                            draw_frame,
                            f"peak_load={top_joint['joint_id']} {float(top_joint['ratio']) * 100.0:.0f}%",
                            (10, 72),
                            cv2.FONT_HERSHEY_SIMPLEX,
                            0.55,
                            stress_color_bgr(float(top_joint["ratio"])),
                            2,
                        )

                frame_idx += 1
                if frame_idx % 30 == 0:
                    dt = max(time.time() - t0, 1e-6)
                    fps = frame_idx / dt
                    avg_pos = pos_error_sum / max(metric_count, 1)
                    avg_ang = angle_error_sum / max(metric_count, 1)
                    print(
                        f"[INFO] fps~{fps:.1f}, mj_time={data.time:.3f}s, steps={step_total}, "
                        f"avg_pos_err={avg_pos:.1f}cm, avg_ang_err={avg_ang:.1f}deg, "
                        f"peak_load={peak_load_ratio * 100.0:.0f}%"
                    )

                cv2.putText(
                    draw_frame,
                    f"mode={mode} sync_fps={sync_fps:.1f}",
                    (10, 24),
                    cv2.FONT_HERSHEY_SIMPLEX,
                    0.6,
                    (0, 255, 0),
                    2,
                )
                cv2.imshow("MediaPipe Pose (Direct Joint Control)", draw_frame)

                if cv2.waitKey(1) & 0xFF == ord("q"):
                    break

                if max_frames > 0 and frame_idx >= max_frames:
                    print(f"[INFO] Reached max_frames={max_frames}; stopping.")
                    break
    finally:
        log_fp.close()
        cap.release()
        cv2.destroyAllWindows()

    avg_pos = pos_error_sum / max(metric_count, 1)
    avg_ang = angle_error_sum / max(metric_count, 1)
    print(f"[OK] Finished. mean_pos_error={avg_pos:.2f}cm, mean_angle_error={avg_ang:.2f}deg")
    print(
        f"[INFO] Physical load peak={peak_load_ratio * 100.0:.0f}%, "
        f"failure_type={load_analyzer.failure_type}, t_fail={load_analyzer.t_fail_timestamp}"
    )
    print(f"[INFO] Error log written: {error_log}")

    if not ok:
        print("[WARN] joint-map check failed. Please verify artifact file matches current humanoid.xml.")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Direct joint-angle mapping: MediaPipe pose_world_landmarks -> MuJoCo humanoid",
    )
    parser.add_argument("--xml", default=str(Path(__file__).with_name("humanoid.xml")), help="Path to MuJoCo XML")
    parser.add_argument(
        "--task-model",
        default=str(Path(__file__).with_name("pose_landmarker_lite.task")),
        help="Path to MediaPipe Pose Landmarker .task model",
    )
    parser.add_argument(
        "--joint-map",
        default=str(Path(__file__).with_name("artifacts") / "humanoid_joint_map.json"),
        help="Path to analyzed humanoid joint map JSON",
    )
    parser.add_argument(
        "--analysis-config",
        default="",
        help="JSON containing hold_metadata / user_biometrics for physical load analysis",
    )
    parser.add_argument("--camera", type=int, default=0, help="Camera index")
    parser.add_argument(
        "--input-video",
        default="",
        help="Path to input video file (if set, camera input is ignored)",
    )
    parser.add_argument("--mode", choices=["kinematic", "dynamic"], default="dynamic")
    parser.add_argument("--max-frames", type=int, default=0, help="Auto-stop after N frames (0 = no limit)")
    parser.add_argument(
        "--sync-fps",
        type=float,
        default=0.0,
        help="Frame sync FPS for MediaPipe <-> MuJoCo (<=0: auto from source FPS)",
    )
    parser.add_argument(
        "--target-filter",
        choices=["one_euro", "double_ema", "ema", "none"],
        default="one_euro",
        help="Temporal filter applied to joint targets before interpolation",
    )
    parser.add_argument("--filter-alpha", type=float, default=0.25, help="EMA / Double-EMA alpha")
    parser.add_argument(
        "--one-euro-min-cutoff",
        type=float,
        default=1.2,
        help="One Euro min cutoff frequency (Hz)",
    )
    parser.add_argument("--one-euro-beta", type=float, default=0.08, help="One Euro speed coefficient")
    parser.add_argument(
        "--one-euro-d-cutoff",
        type=float,
        default=1.0,
        help="One Euro derivative cutoff frequency (Hz)",
    )
    parser.add_argument(
        "--damping-scale",
        type=float,
        default=1.0,
        help="Scale factor for MuJoCo dof damping (dynamic mode stabilization)",
    )
    parser.add_argument("--error-log", default=str(Path(__file__).with_name("artifacts") / "mapping_error_log.jsonl"))
    parser.add_argument("--mirror-view", action="store_true", help="Mirror camera preview only")
    parser.add_argument("--mirror-input", action="store_true", help="Mirror input before MediaPipe inference")
    parser.add_argument(
        "--no-swap-lr",
        action="store_true",
        help="Disable left/right swap correction (default: ON)",
    )
    parser.add_argument("--self-check", action="store_true", help="Run dependency/model check only")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    xml_path = Path(args.xml).resolve()
    task_path = Path(args.task_model).resolve()
    joint_map_path = Path(args.joint_map).resolve()
    analysis_config_path = Path(args.analysis_config).resolve() if args.analysis_config else None
    error_log_path = Path(args.error_log).resolve()
    input_video_path = Path(args.input_video).resolve() if args.input_video else None

    if not xml_path.exists():
        raise FileNotFoundError(f"XML file not found: {xml_path}")
    if not task_path.exists():
        raise FileNotFoundError(f"Task model not found: {task_path}")

    swap_lr = not args.no_swap_lr
    analysis_payload = load_analysis_payload(analysis_config_path)

    if args.self_check:
        run_self_check(
            xml_path=xml_path,
            task_path=task_path,
            joint_map_path=joint_map_path,
            analysis_payload=analysis_payload,
            mode=args.mode,
            swap_lr=swap_lr,
            target_filter_mode=args.target_filter,
            filter_alpha=args.filter_alpha,
            one_euro_min_cutoff=args.one_euro_min_cutoff,
            one_euro_beta=args.one_euro_beta,
            one_euro_d_cutoff=args.one_euro_d_cutoff,
            damping_scale=args.damping_scale,
        )
    else:
        run_live(
            xml_path=xml_path,
            task_path=task_path,
            joint_map_path=joint_map_path,
            analysis_payload=analysis_payload,
            cam_index=args.camera,
            input_video=input_video_path,
            mode=args.mode,
            max_frames=args.max_frames,
            mirror_view=args.mirror_view,
            mirror_input=args.mirror_input,
            swap_lr=swap_lr,
            sync_fps=max(args.sync_fps, 0.0),
            error_log=error_log_path,
            target_filter_mode=args.target_filter,
            filter_alpha=args.filter_alpha,
            one_euro_min_cutoff=args.one_euro_min_cutoff,
            one_euro_beta=args.one_euro_beta,
            one_euro_d_cutoff=args.one_euro_d_cutoff,
            damping_scale=args.damping_scale,
        )


if __name__ == "__main__":
    main()

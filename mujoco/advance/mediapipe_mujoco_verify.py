from __future__ import annotations

import argparse
import json
import math
import time
from pathlib import Path

import cv2
import mediapipe as mp
import mujoco
import mujoco.viewer
import numpy as np
from mediapipe.tasks import python
from mediapipe.tasks.python import vision

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


def lerp_vec(prev_value: np.ndarray, next_value: np.ndarray, alpha: float) -> np.ndarray:
    a = float(np.clip(alpha, 0.0, 1.0))
    return (1.0 - a) * prev_value + a * next_value


def nlerp_quat(q0: np.ndarray, q1: np.ndarray, alpha: float) -> np.ndarray:
    a = float(np.clip(alpha, 0.0, 1.0))
    q0n = normalize(q0.astype(np.float64))
    q1n = normalize(q1.astype(np.float64))
    if float(np.dot(q0n, q1n)) < 0.0:
        q1n = -q1n
    return normalize((1.0 - a) * q0n + a * q1n)


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


def draw_pose_2d(frame: np.ndarray, normalized_landmarks) -> None:
    h, w = frame.shape[:2]
    for p in normalized_landmarks:
        x = int(p.x * w)
        y = int(p.y * h)
        if 0 <= x < w and 0 <= y < h:
            cv2.circle(frame, (x, y), 2, (0, 255, 0), -1)


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


def quat_conjugate(q: np.ndarray) -> np.ndarray:
    return np.array([q[0], -q[1], -q[2], -q[3]], dtype=np.float64)


def quat_mul(q1: np.ndarray, q2: np.ndarray) -> np.ndarray:
    w1, x1, y1, z1 = q1
    w2, x2, y2, z2 = q2
    return np.array(
        [
            w1 * w2 - x1 * x2 - y1 * y2 - z1 * z2,
            w1 * x2 + x1 * w2 + y1 * z2 - z1 * y2,
            w1 * y2 - x1 * z2 + y1 * w2 + z1 * x2,
            w1 * z2 + x1 * y2 - y1 * x2 + z1 * w2,
        ],
        dtype=np.float64,
    )


def quat_error_rotvec(q_target: np.ndarray, q_current: np.ndarray) -> np.ndarray:
    q_err = quat_mul(q_target, quat_conjugate(q_current))
    if q_err[0] < 0.0:
        q_err = -q_err
    v = q_err[1:4]
    nv = float(np.linalg.norm(v))
    w = float(np.clip(q_err[0], -1.0, 1.0))
    if nv < 1e-8:
        return 2.0 * v
    angle = 2.0 * math.atan2(nv, w)
    return (v / nv) * angle


def _frame_from_segments(
    primary: np.ndarray,
    secondary_hint: np.ndarray,
    fallback: np.ndarray,
) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    x_axis = normalize(primary)
    z_axis = normalize(np.cross(x_axis, secondary_hint))
    if np.linalg.norm(z_axis) < 1e-6:
        z_axis = normalize(np.cross(x_axis, fallback))
    if np.linalg.norm(z_axis) < 1e-6:
        z_axis = np.array([0.0, 0.0, 1.0], dtype=np.float64)
    y_axis = normalize(np.cross(z_axis, x_axis))
    z_axis = normalize(np.cross(x_axis, y_axis))
    return x_axis, y_axis, z_axis


def extract_pose_targets(world_landmarks: list, swap_lr: bool = False) -> dict[str, object]:
    """Extract position/orientation targets from MediaPipe world landmarks.

    Output:
      - points: key landmarks in MuJoCo coordinates
      - quat_targets: desired body orientations as quaternions
    """
    l_sh_idx, r_sh_idx = (RIGHT_SHOULDER, LEFT_SHOULDER) if swap_lr else (LEFT_SHOULDER, RIGHT_SHOULDER)
    l_el_idx, r_el_idx = (RIGHT_ELBOW, LEFT_ELBOW) if swap_lr else (LEFT_ELBOW, RIGHT_ELBOW)
    l_wr_idx, r_wr_idx = (RIGHT_WRIST, LEFT_WRIST) if swap_lr else (LEFT_WRIST, RIGHT_WRIST)
    l_hi_idx, r_hi_idx = (RIGHT_HIP, LEFT_HIP) if swap_lr else (LEFT_HIP, RIGHT_HIP)
    l_kn_idx, r_kn_idx = (RIGHT_KNEE, LEFT_KNEE) if swap_lr else (LEFT_KNEE, RIGHT_KNEE)
    l_an_idx, r_an_idx = (RIGHT_ANKLE, LEFT_ANKLE) if swap_lr else (LEFT_ANKLE, RIGHT_ANKLE)

    # Non-swapped landmarks are kept for torso-facing frame estimation.
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

    shoulder_mid = 0.5 * (ls_raw + rs_raw)
    hip_mid = 0.5 * (lh_raw + rh_raw)
    up_axis = normalize(shoulder_mid - hip_mid)
    left_axis = normalize(ls_raw - rs_raw)
    forward_axis = normalize(np.cross(left_axis, up_axis))
    if np.linalg.norm(forward_axis) < 1e-6:
        forward_axis = np.array([1.0, 0.0, 0.0], dtype=np.float64)
    left_axis = normalize(np.cross(up_axis, forward_axis))

    # Back-view climbing footage convention.
    sagittal_axis = -forward_axis
    torso_quat = quat_from_axes(sagittal_axis, left_axis, up_axis)

    larm_x, larm_y, larm_z = _frame_from_segments(le - ls, lw - le, up_axis)
    rarm_x, rarm_y, rarm_z = _frame_from_segments(re - rs, rw - re, up_axis)
    lthigh_x, lthigh_y, lthigh_z = _frame_from_segments(lk - lh, la - lk, sagittal_axis)
    rthigh_x, rthigh_y, rthigh_z = _frame_from_segments(rk - rh, ra - rk, sagittal_axis)

    quat_targets = {
        "torso": torso_quat,
        "upper_arm_left": quat_from_axes(larm_x, larm_y, larm_z),
        "upper_arm_right": quat_from_axes(rarm_x, rarm_y, rarm_z),
        "thigh_left": quat_from_axes(lthigh_x, lthigh_y, lthigh_z),
        "thigh_right": quat_from_axes(rthigh_x, rthigh_y, rthigh_z),
    }

    points = {
        "left_shoulder": ls,
        "right_shoulder": rs,
        "left_elbow": le,
        "right_elbow": re,
        "left_wrist": lw,
        "right_wrist": rw,
        "left_hip": lh,
        "right_hip": rh,
        "hip_mid": hip_mid,
        "left_knee": lk,
        "right_knee": rk,
        "left_ankle": la,
        "right_ankle": ra,
        "axis_forward": sagittal_axis,
        "axis_left": left_axis,
        "axis_up": up_axis,
    }
    return {
        "points": points,
        "quat_targets": quat_targets,
    }


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
        user_height_m: float,
        wall_z: float,
        wall_axis: str,
        wall_snap_distance: float,
        wall_stable_speed: float,
        ik_iters: int,
        ik_damping: float,
        ik_step_scale: float,
        ik_pos_weight: float,
        ik_aux_pos_weight: float,
        ik_torso_rot_weight: float,
        ik_limb_rot_weight: float,
        ik_posture_weight: float,
        dynamic_root_follow: bool,
        dynamic_root_gain: float,
    ) -> None:
        self.model = model
        self.data = data
        self.mode = mode
        self.swap_lr = swap_lr

        self.joint_ids = {name: joint_id(model, name) for name in MAJOR_JOINTS}
        self.qpos_adr = {name: int(model.jnt_qposadr[jid]) for name, jid in self.joint_ids.items()}
        self.dof_adr = [int(model.jnt_dofadr[self.joint_ids[name]]) for name in MAJOR_JOINTS]
        self.n_ik = len(self.dof_adr)

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
        self.head_bid = body_id(model, "head")
        self.lhand_bid = body_id(model, "hand_left")
        self.rhand_bid = body_id(model, "hand_right")
        self.lfoot_bid = body_id(model, "foot_left")
        self.rfoot_bid = body_id(model, "foot_right")
        self.lshoulder_bid = body_id(model, "upper_arm_left")
        self.rshoulder_bid = body_id(model, "upper_arm_right")
        self.lthigh_bid = body_id(model, "thigh_left")
        self.rthigh_bid = body_id(model, "thigh_right")
        self.lelbow_bid = body_id(model, "lower_arm_left")
        self.relbow_bid = body_id(model, "lower_arm_right")
        self.lknee_bid = body_id(model, "shin_left")
        self.rknee_bid = body_id(model, "shin_right")

        mujoco.mj_forward(model, data)

        self.root_pos0 = data.qpos[0:3].copy()
        self.root_quat0 = data.qpos[3:7].copy()
        self.pelvis_anchor = data.xpos[self.pelvis_bid].copy()
        self.torso_from_pelvis = data.xpos[self.torso_bid] - data.xpos[self.pelvis_bid]
        self.model_shoulder = float(np.linalg.norm(data.xpos[self.lshoulder_bid] - data.xpos[self.rshoulder_bid]))
        self.model_height = float(
            data.xpos[self.head_bid][2] - min(data.xpos[self.lfoot_bid][2], data.xpos[self.rfoot_bid][2])
        )
        self.model_height = max(self.model_height, 0.8)

        self.scale_est: float | None = None
        self.offset_est: np.ndarray | None = None
        self.height_correction = 1.0
        self.root_target_cache = self.root_pos0.copy()
        self.root_quat_cache = self.root_quat0.copy()
        self.neutral_joint = np.array([float(self.data.qpos[self.qpos_adr[name]]) for name in MAJOR_JOINTS], dtype=np.float64)
        self.last_solution = {name: float(self.neutral_joint[i]) for i, name in enumerate(MAJOR_JOINTS)}

        self.user_height_m = max(float(user_height_m), 0.8)
        axis_map = {"x": 0, "y": 1, "z": 2}
        self.wall_axis_index = axis_map[wall_axis]
        self.wall_z = float(wall_z)
        self.wall_snap_distance = max(float(wall_snap_distance), 0.0)
        self.wall_stable_speed = max(float(wall_stable_speed), 1e-5)
        self.prev_snap_points: dict[str, np.ndarray] = {}
        self.prev_snap_timestamp: float | None = None

        self.ik_iters = max(int(ik_iters), 1)
        self.ik_damping = max(float(ik_damping), 1e-6)
        self.ik_step_scale = float(np.clip(ik_step_scale, 0.05, 1.0))
        self.ik_pos_weight = max(float(ik_pos_weight), 1e-4)
        self.ik_aux_pos_weight = max(float(ik_aux_pos_weight), 1e-4)
        self.ik_torso_rot_weight = max(float(ik_torso_rot_weight), 1e-4)
        self.ik_limb_rot_weight = max(float(ik_limb_rot_weight), 1e-4)
        self.ik_posture_weight = max(float(ik_posture_weight), 0.0)
        self.dynamic_root_follow = bool(dynamic_root_follow)
        self.dynamic_root_gain = float(np.clip(dynamic_root_gain, 0.05, 1.0))

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
        shoulder_mid = 0.5 * (points["left_shoulder"] + points["right_shoulder"])
        ankle_mid = 0.5 * (points["left_ankle"] + points["right_ankle"])
        mp_height_est = float(np.linalg.norm(shoulder_mid - ankle_mid) + 0.35 * shoulder_w)
        mp_height_est = max(mp_height_est, 0.5)

        self.height_correction = float(np.clip(self.user_height_m / mp_height_est, 0.6, 1.8))
        effective_shoulder = shoulder_w * self.height_correction
        raw_scale = float(np.clip(self.model_shoulder / max(effective_shoulder, 1e-6), 0.45, 3.2))

        if self.scale_est is None:
            self.scale_est = raw_scale
        else:
            self.scale_est = 0.88 * self.scale_est + 0.12 * raw_scale

        raw_offset = self.pelvis_anchor - points["hip_mid"] * self.scale_est
        if self.offset_est is None:
            self.offset_est = raw_offset
        else:
            self.offset_est = 0.92 * self.offset_est + 0.08 * raw_offset

    def _apply_wall_plane_snap(self, world_targets: dict[str, np.ndarray], timestamp_s: float) -> dict[str, bool]:
        snap_flags = {k: False for k in ("left_wrist", "right_wrist", "left_ankle", "right_ankle")}
        dt = 0.0
        if self.prev_snap_timestamp is not None:
            dt = max(float(timestamp_s - self.prev_snap_timestamp), 1e-3)

        for key in snap_flags:
            p = world_targets[key].copy()
            prev = self.prev_snap_points.get(key)
            speed = 1e9 if prev is None or dt <= 0.0 else float(np.linalg.norm(p - prev) / dt)
            near_wall = abs(float(p[self.wall_axis_index]) - self.wall_z) <= self.wall_snap_distance
            stable = speed <= self.wall_stable_speed
            if near_wall or stable:
                p[self.wall_axis_index] = self.wall_z
                snap_flags[key] = True
            world_targets[key] = p

        self.prev_snap_points = {k: world_targets[k].copy() for k in snap_flags}
        self.prev_snap_timestamp = float(timestamp_s)
        return snap_flags

    def _joint_vector(self) -> np.ndarray:
        return np.array([float(self.data.qpos[self.qpos_adr[name]]) for name in MAJOR_JOINTS], dtype=np.float64)

    def _set_joint_vector(self, q: np.ndarray) -> None:
        for i, name in enumerate(MAJOR_JOINTS):
            self.data.qpos[self.qpos_adr[name]] = self._clip_joint(name, float(q[i]))

    def _solve_numerical_ik(
        self,
        world_targets: dict[str, np.ndarray],
        quat_targets: dict[str, np.ndarray],
        root_pos: np.ndarray,
        root_quat: np.ndarray,
    ) -> tuple[dict[str, float], dict[str, float]]:
        qpos_backup = self.data.qpos.copy()
        qvel_backup = self.data.qvel.copy()
        qacc_backup = self.data.qacc.copy()

        pos_specs = [
            ("pelvis", self.pelvis_bid, self.ik_pos_weight),
            ("left_wrist", self.lhand_bid, self.ik_pos_weight),
            ("right_wrist", self.rhand_bid, self.ik_pos_weight),
            ("left_ankle", self.lfoot_bid, self.ik_pos_weight),
            ("right_ankle", self.rfoot_bid, self.ik_pos_weight),
            ("left_elbow", self.lelbow_bid, self.ik_aux_pos_weight),
            ("right_elbow", self.relbow_bid, self.ik_aux_pos_weight),
            ("left_knee", self.lknee_bid, self.ik_aux_pos_weight),
            ("right_knee", self.rknee_bid, self.ik_aux_pos_weight),
        ]
        rot_specs = [
            ("torso", self.torso_bid, self.ik_torso_rot_weight),
            ("upper_arm_left", self.lshoulder_bid, self.ik_limb_rot_weight),
            ("upper_arm_right", self.rshoulder_bid, self.ik_limb_rot_weight),
            ("thigh_left", self.lthigh_bid, self.ik_limb_rot_weight),
            ("thigh_right", self.rthigh_bid, self.ik_limb_rot_weight),
        ]

        try:
            self.data.qpos[0:3] = root_pos
            self.data.qpos[3:7] = root_quat
            for i, name in enumerate(MAJOR_JOINTS):
                self.data.qpos[self.qpos_adr[name]] = self._clip_joint(name, self.last_solution.get(name, self.neutral_joint[i]))
            self.data.qvel[:] = 0.0
            self.data.qacc[:] = 0.0

            max_pos_err = 0.0
            max_rot_err = 0.0
            for _ in range(self.ik_iters):
                mujoco.mj_forward(self.model, self.data)
                rows: list[np.ndarray] = []
                errs: list[np.ndarray] = []
                pos_errs = []
                rot_errs = []

                for key, bid, w in pos_specs:
                    jacp = np.zeros((3, self.model.nv), dtype=np.float64)
                    jacr = np.zeros((3, self.model.nv), dtype=np.float64)
                    mujoco.mj_jacBody(self.model, self.data, jacp, jacr, bid)
                    j = jacp[:, self.dof_adr]
                    e = world_targets[key] - self.data.xpos[bid]
                    pos_errs.append(float(np.linalg.norm(e)))
                    rows.append(w * j)
                    errs.append(w * e)

                for key, bid, w in rot_specs:
                    jacp = np.zeros((3, self.model.nv), dtype=np.float64)
                    jacr = np.zeros((3, self.model.nv), dtype=np.float64)
                    mujoco.mj_jacBody(self.model, self.data, jacp, jacr, bid)
                    j = jacr[:, self.dof_adr]
                    q_cur = self.data.xquat[bid].copy()
                    e = quat_error_rotvec(quat_targets[key], q_cur)
                    rot_errs.append(float(np.linalg.norm(e)))
                    rows.append(w * j)
                    errs.append(w * e)

                q_curr = self._joint_vector()
                q_prev = np.array([self.last_solution.get(name, 0.0) for name in MAJOR_JOINTS], dtype=np.float64)
                q_ref = 0.7 * q_prev + 0.3 * self.neutral_joint
                if self.ik_posture_weight > 0.0:
                    w_reg = math.sqrt(self.ik_posture_weight)
                    rows.append(w_reg * np.eye(self.n_ik, dtype=np.float64))
                    errs.append(w_reg * (q_ref - q_curr))

                j_stack = np.vstack(rows)
                e_stack = np.concatenate(errs)
                h = j_stack.T @ j_stack + (self.ik_damping * self.ik_damping) * np.eye(self.n_ik, dtype=np.float64)
                g = j_stack.T @ e_stack
                try:
                    dq = np.linalg.solve(h, g)
                except np.linalg.LinAlgError:
                    dq = np.linalg.lstsq(h, g, rcond=None)[0]

                dq = np.clip(dq, -0.35, 0.35)
                q_next = q_curr + self.ik_step_scale * dq
                self._set_joint_vector(q_next)

                max_pos_err = max(pos_errs) if pos_errs else 0.0
                max_rot_err = max(rot_errs) if rot_errs else 0.0
                if max_pos_err < 0.01 and max_rot_err < 0.08:
                    break

            solved = {name: float(self.data.qpos[self.qpos_adr[name]]) for name in MAJOR_JOINTS}
            stats = {
                "ik_max_pos_err_cm": float(max_pos_err * 100.0),
                "ik_max_rot_err_deg": float(max_rot_err * (180.0 / math.pi)),
            }
            return solved, stats
        finally:
            self.data.qpos[:] = qpos_backup
            self.data.qvel[:] = qvel_backup
            self.data.qacc[:] = qacc_backup
            mujoco.mj_forward(self.model, self.data)

    def prepare_targets(self, world_landmarks, timestamp_s: float) -> tuple[dict[str, float], dict[str, object]]:
        payload = extract_pose_targets(world_landmarks, swap_lr=self.swap_lr)
        points = payload["points"]
        quat_targets = payload["quat_targets"]

        self._update_metric_calibration(points)

        world_targets = {
            "pelvis": self._map_point(points["hip_mid"]),
            "left_elbow": self._map_point(points["left_elbow"]),
            "right_elbow": self._map_point(points["right_elbow"]),
            "left_wrist": self._map_point(points["left_wrist"]),
            "right_wrist": self._map_point(points["right_wrist"]),
            "left_knee": self._map_point(points["left_knee"]),
            "right_knee": self._map_point(points["right_knee"]),
            "left_ankle": self._map_point(points["left_ankle"]),
            "right_ankle": self._map_point(points["right_ankle"]),
        }
        snap_flags = self._apply_wall_plane_snap(world_targets, timestamp_s)

        root_pos = world_targets["pelvis"] + self.torso_from_pelvis
        root_quat = normalize(quat_targets["torso"])

        raw_targets, ik_stats = self._solve_numerical_ik(world_targets, quat_targets, root_pos, root_quat)
        filtered = self.target_filter.apply(raw_targets, timestamp_s)
        targets = {name: self._clip_joint(name, filtered[name]) for name in MAJOR_JOINTS}
        self.last_solution = dict(targets)

        frame_state: dict[str, object] = {
            "points": points,
            "world_targets": world_targets,
            "root_pos": root_pos,
            "root_quat": root_quat,
            "snap_flags": snap_flags,
            "ik_stats": ik_stats,
            "scale": float(self.scale_est if self.scale_est is not None else 1.0),
            "height_correction": float(self.height_correction),
        }
        return targets, frame_state

    def apply_kinematic(self, targets: dict[str, float], frame_state: dict[str, object]) -> None:
        root_pos = np.asarray(frame_state["root_pos"], dtype=np.float64)
        root_quat = normalize(np.asarray(frame_state["root_quat"], dtype=np.float64))
        self.root_target_cache = root_pos.copy()
        self.root_quat_cache = root_quat.copy()
        self.data.qpos[0:3] = self.root_target_cache
        self.data.qpos[3:7] = self.root_quat_cache

        for name in MAJOR_JOINTS:
            self.data.qpos[self.qpos_adr[name]] = float(targets[name])
        self.data.qvel[:] = 0.0
        self.data.qacc[:] = 0.0
        mujoco.mj_forward(self.model, self.data)

    def apply_dynamic_root(self, root_pos_target: np.ndarray, root_quat_target: np.ndarray) -> None:
        if not self.dynamic_root_follow:
            return
        g = self.dynamic_root_gain
        self.data.qpos[0:3] = lerp_vec(self.data.qpos[0:3], root_pos_target, g)
        self.data.qpos[3:7] = nlerp_quat(self.data.qpos[3:7], root_quat_target, g)
        # Suppress freejoint velocity accumulation from target tracking.
        self.data.qvel[0:6] = 0.0

    def set_dynamic_ctrl(self, targets: dict[str, float]) -> None:
        for name, target in targets.items():
            aid = self.actuator_ids[name]
            ctrl = target
            if self.model.actuator_ctrllimited[aid]:
                lo, hi = self.model.actuator_ctrlrange[aid]
                ctrl = float(np.clip(ctrl, lo, hi))
            self.data.ctrl[aid] = ctrl

    def compute_metrics(self, targets: dict[str, float], frame_state: dict[str, object]) -> dict[str, object]:
        all_targets = frame_state["world_targets"]
        mapped_targets = {
            "pelvis": all_targets["pelvis"],
            "left_wrist": all_targets["left_wrist"],
            "right_wrist": all_targets["right_wrist"],
            "left_ankle": all_targets["left_ankle"],
            "right_ankle": all_targets["right_ankle"],
        }
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
            "scale": float(frame_state.get("scale", 1.0)),
            "height_correction": float(frame_state.get("height_correction", 1.0)),
            "mean_pos_error_cm": float(np.mean(list(pos_error_cm.values()))),
            "mean_angle_error_deg": float(np.mean(list(angle_error_deg.values()))),
            "pos_error_cm": pos_error_cm,
            "angle_error_deg": angle_error_deg,
            "snap_flags": frame_state.get("snap_flags", {}),
            "ik_stats": frame_state.get("ik_stats", {}),
        }


def build_model(xml_path: Path) -> tuple[mujoco.MjModel, mujoco.MjData]:
    model = mujoco.MjModel.from_xml_path(str(xml_path))
    data = mujoco.MjData(model)
    return model, data


def run_self_check(
    xml_path: Path,
    task_path: Path,
    joint_map_path: Path,
    mode: str,
    swap_lr: bool,
    target_filter_mode: str,
    filter_alpha: float,
    one_euro_min_cutoff: float,
    one_euro_beta: float,
    one_euro_d_cutoff: float,
    damping_scale: float,
    user_height_m: float,
    wall_z: float,
    wall_axis: str,
    wall_snap_distance: float,
    wall_stable_speed: float,
    ik_iters: int,
    ik_damping: float,
    ik_step_scale: float,
    ik_pos_weight: float,
    ik_aux_pos_weight: float,
    ik_torso_rot_weight: float,
    ik_limb_rot_weight: float,
    ik_posture_weight: float,
    dynamic_root_follow: bool,
    dynamic_root_gain: float,
) -> None:
    ok, msg = validate_joint_map(joint_map_path)
    print(msg)

    model, data = build_model(xml_path)
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
            user_height_m=user_height_m,
            wall_z=wall_z,
            wall_axis=wall_axis,
            wall_snap_distance=wall_snap_distance,
            wall_stable_speed=wall_stable_speed,
            ik_iters=ik_iters,
            ik_damping=ik_damping,
            ik_step_scale=ik_step_scale,
            ik_pos_weight=ik_pos_weight,
            ik_aux_pos_weight=ik_aux_pos_weight,
            ik_torso_rot_weight=ik_torso_rot_weight,
            ik_limb_rot_weight=ik_limb_rot_weight,
            ik_posture_weight=ik_posture_weight,
            dynamic_root_follow=dynamic_root_follow,
            dynamic_root_gain=dynamic_root_gain,
        )
        mujoco.mj_forward(model, data)

    print(f"[OK] Self-check passed: mode={mode}, gravity={model.opt.gravity.tolist()}")
    if not ok:
        print("[WARN] joint-map validation failed; mapping may not match your artifact.")


def run_live(
    xml_path: Path,
    task_path: Path,
    joint_map_path: Path,
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
    user_height_m: float,
    wall_z: float,
    wall_axis: str,
    wall_snap_distance: float,
    wall_stable_speed: float,
    ik_iters: int,
    ik_damping: float,
    ik_step_scale: float,
    ik_pos_weight: float,
    ik_aux_pos_weight: float,
    ik_torso_rot_weight: float,
    ik_limb_rot_weight: float,
    ik_posture_weight: float,
    dynamic_root_follow: bool,
    dynamic_root_gain: float,
) -> None:
    ok, msg = validate_joint_map(joint_map_path)
    print(msg)

    model, data = build_model(xml_path)
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
        user_height_m=user_height_m,
        wall_z=wall_z,
        wall_axis=wall_axis,
        wall_snap_distance=wall_snap_distance,
        wall_stable_speed=wall_stable_speed,
        ik_iters=ik_iters,
        ik_damping=ik_damping,
        ik_step_scale=ik_step_scale,
        ik_pos_weight=ik_pos_weight,
        ik_aux_pos_weight=ik_aux_pos_weight,
        ik_torso_rot_weight=ik_torso_rot_weight,
        ik_limb_rot_weight=ik_limb_rot_weight,
        ik_posture_weight=ik_posture_weight,
        dynamic_root_follow=dynamic_root_follow,
        dynamic_root_gain=dynamic_root_gain,
    )

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
        f"[INFO] IK(iters={ik_iters}, damping={ik_damping:.4f}, step_scale={ik_step_scale:.2f}, "
        f"pos_w={ik_pos_weight:.2f}, aux_pos_w={ik_aux_pos_weight:.2f}, "
        f"torso_rot_w={ik_torso_rot_weight:.2f}, limb_rot_w={ik_limb_rot_weight:.2f}, "
        f"posture_w={ik_posture_weight:.4f})"
    )
    print(
        f"[INFO] Wall snap axis={wall_axis} plane={wall_z:.3f} "
        f"(dist<={wall_snap_distance:.3f}m or speed<={wall_stable_speed:.3f}m/s)"
    )
    print(
        f"[INFO] Dynamic root follow={'ON' if dynamic_root_follow else 'OFF'} "
        f"(gain={dynamic_root_gain:.2f})"
    )
    print(f"[INFO] User height={user_height_m:.3f}m")

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
    metric_count = 0
    prev_targets: dict[str, float] | None = None
    prev_state: dict[str, object] | None = None

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
                frame_targets: dict[str, float] | None = None
                frame_state: dict[str, object] | None = None
                if result.pose_world_landmarks:
                    frame_targets, frame_state = controller.prepare_targets(result.pose_world_landmarks[0], mp_ts_ms / 1000.0)
                    if not connected:
                        connected = True
                        print("[OK] pose_world_landmarks detected; direct joint control active")

                target_sim_time = (frame_idx + 1) / sync_fps
                step_count = 0

                if mode == "kinematic":
                    # Strict kinematic mode: bypass physics integration.
                    if frame_targets is not None and frame_state is not None:
                        controller.apply_kinematic(frame_targets, frame_state)
                        metrics = controller.compute_metrics(frame_targets, frame_state)
                        prev_targets = dict(frame_targets)
                        prev_state = frame_state
                    data.time = target_sim_time
                else:
                    # Dynamic mode: interpolate filtered targets across physics sub-steps.
                    to_targets = frame_targets if frame_targets is not None else prev_targets
                    to_state = frame_state if frame_state is not None else prev_state
                    from_targets = prev_targets if prev_targets is not None else to_targets
                    from_state = prev_state if prev_state is not None else to_state
                    start_time = float(data.time)
                    duration = max(float(target_sim_time) - start_time, model.opt.timestep)

                    while data.time + model.opt.timestep * 0.5 < target_sim_time:
                        phase = (data.time + model.opt.timestep - start_time) / duration
                        phase = float(np.clip(phase, 0.0, 1.0))

                        if to_state is not None:
                            if from_state is None:
                                root_pos_target = np.asarray(to_state["root_pos"], dtype=np.float64)
                                root_quat_target = np.asarray(to_state["root_quat"], dtype=np.float64)
                            else:
                                root_pos_target = lerp_vec(
                                    np.asarray(from_state["root_pos"], dtype=np.float64),
                                    np.asarray(to_state["root_pos"], dtype=np.float64),
                                    phase,
                                )
                                root_quat_target = nlerp_quat(
                                    np.asarray(from_state["root_quat"], dtype=np.float64),
                                    np.asarray(to_state["root_quat"], dtype=np.float64),
                                    phase,
                                )
                            controller.apply_dynamic_root(root_pos_target, root_quat_target)

                        if to_targets is not None:
                            if from_targets is None:
                                step_targets = to_targets
                            else:
                                step_targets = lerp_dict(from_targets, to_targets, alpha=phase)
                            controller.set_dynamic_ctrl(step_targets)
                        mujoco.mj_step(model, data)
                        step_count += 1
                    step_total += step_count

                    if to_targets is not None and to_state is not None:
                        metrics = controller.compute_metrics(to_targets, to_state)
                        prev_targets = dict(to_targets)
                        prev_state = to_state

                viewer.sync()

                draw_frame = input_frame.copy()
                if result.pose_landmarks:
                    draw_pose_2d(draw_frame, result.pose_landmarks[0])
                if mirror_view:
                    draw_frame = cv2.flip(draw_frame, 1)

                if metrics is not None:
                    metric_count += 1
                    pos_error_sum += float(metrics["mean_pos_error_cm"])
                    angle_error_sum += float(metrics["mean_angle_error_deg"])

                    record = {
                        "frame_index": frame_idx,
                        "mp_timestamp_ms": mp_ts_ms,
                        "mj_time_s": float(data.time),
                        "mode": mode,
                        "scale": float(metrics["scale"]),
                        "height_correction": float(metrics.get("height_correction", 1.0)),
                        "mean_pos_error_cm": float(metrics["mean_pos_error_cm"]),
                        "mean_angle_error_deg": float(metrics["mean_angle_error_deg"]),
                        "targets": metrics["targets"],
                        "pos_error_cm": metrics["pos_error_cm"],
                        "angle_error_deg": metrics["angle_error_deg"],
                        "snap_flags": metrics.get("snap_flags", {}),
                        "ik_stats": metrics.get("ik_stats", {}),
                        "mujoco_steps_this_frame": step_count,
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

                frame_idx += 1
                if frame_idx % 30 == 0:
                    dt = max(time.time() - t0, 1e-6)
                    fps = frame_idx / dt
                    avg_pos = pos_error_sum / max(metric_count, 1)
                    avg_ang = angle_error_sum / max(metric_count, 1)
                    print(
                        f"[INFO] fps~{fps:.1f}, mj_time={data.time:.3f}s, steps={step_total}, "
                        f"avg_pos_err={avg_pos:.1f}cm, avg_ang_err={avg_ang:.1f}deg"
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
        default=2.0,
        help="Scale factor for MuJoCo dof damping (dynamic mode stabilization)",
    )
    parser.add_argument(
        "--user-height-m",
        type=float,
        default=1.70,
        help="User height in meters for proportional target scaling",
    )
    parser.add_argument("--wall-z", type=float, default=0.9, help="Wall plane value on selected axis")
    parser.add_argument(
        "--wall-axis",
        choices=["x", "y", "z"],
        default="x",
        help="Axis used by wall snap plane",
    )
    parser.add_argument(
        "--wall-snap-distance",
        type=float,
        default=0.06,
        help="Snap when limb is within this distance from wall plane (m)",
    )
    parser.add_argument(
        "--wall-stable-speed",
        type=float,
        default=0.10,
        help="Snap when limb speed is below this threshold (m/s)",
    )
    parser.add_argument("--ik-iters", type=int, default=16, help="Numerical IK solver iterations per frame")
    parser.add_argument("--ik-damping", type=float, default=0.02, help="Damped least-squares lambda")
    parser.add_argument("--ik-step-scale", type=float, default=0.55, help="IK update step scale [0,1]")
    parser.add_argument("--ik-pos-weight", type=float, default=1.0, help="Position residual weight in IK")
    parser.add_argument(
        "--ik-aux-pos-weight",
        type=float,
        default=0.20,
        help="Elbow/knee auxiliary position residual weight in IK",
    )
    parser.add_argument(
        "--ik-torso-rot-weight",
        type=float,
        default=0.55,
        help="Torso quaternion residual weight in IK",
    )
    parser.add_argument(
        "--ik-limb-rot-weight",
        type=float,
        default=0.35,
        help="Shoulder/hip quaternion residual weight in IK",
    )
    parser.add_argument(
        "--ik-posture-weight",
        type=float,
        default=0.02,
        help="Posture regularization weight in IK",
    )
    parser.add_argument("--error-log", default=str(Path(__file__).with_name("artifacts") / "mapping_error_log.jsonl"))
    parser.add_argument("--mirror-view", action="store_true", help="Mirror camera preview only")
    parser.add_argument("--mirror-input", action="store_true", help="Mirror input before MediaPipe inference")
    parser.add_argument(
        "--swap-lr",
        action="store_true",
        help="Enable left/right swap correction (default: OFF)",
    )
    parser.add_argument(
        "--no-swap-lr",
        action="store_true",
        help="Deprecated compatibility flag (forces swap OFF)",
    )
    parser.add_argument(
        "--no-dynamic-root-follow",
        action="store_true",
        help="Disable dynamic root tracking (not recommended)",
    )
    parser.add_argument(
        "--dynamic-root-gain",
        type=float,
        default=0.40,
        help="Root follow gain in dynamic mode [0..1]",
    )
    parser.add_argument("--self-check", action="store_true", help="Run dependency/model check only")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    xml_path = Path(args.xml).resolve()
    task_path = Path(args.task_model).resolve()
    joint_map_path = Path(args.joint_map).resolve()
    error_log_path = Path(args.error_log).resolve()
    input_video_path = Path(args.input_video).resolve() if args.input_video else None

    if not xml_path.exists():
        raise FileNotFoundError(f"XML file not found: {xml_path}")
    if not task_path.exists():
        raise FileNotFoundError(f"Task model not found: {task_path}")

    swap_lr = bool(args.swap_lr)
    if args.no_swap_lr:
        swap_lr = False
    dynamic_root_follow = not args.no_dynamic_root_follow

    if args.self_check:
        run_self_check(
            xml_path=xml_path,
            task_path=task_path,
            joint_map_path=joint_map_path,
            mode=args.mode,
            swap_lr=swap_lr,
            target_filter_mode=args.target_filter,
            filter_alpha=args.filter_alpha,
            one_euro_min_cutoff=args.one_euro_min_cutoff,
            one_euro_beta=args.one_euro_beta,
            one_euro_d_cutoff=args.one_euro_d_cutoff,
            damping_scale=args.damping_scale,
            user_height_m=args.user_height_m,
            wall_z=args.wall_z,
            wall_axis=args.wall_axis,
            wall_snap_distance=args.wall_snap_distance,
            wall_stable_speed=args.wall_stable_speed,
            ik_iters=args.ik_iters,
            ik_damping=args.ik_damping,
            ik_step_scale=args.ik_step_scale,
            ik_pos_weight=args.ik_pos_weight,
            ik_aux_pos_weight=args.ik_aux_pos_weight,
            ik_torso_rot_weight=args.ik_torso_rot_weight,
            ik_limb_rot_weight=args.ik_limb_rot_weight,
            ik_posture_weight=args.ik_posture_weight,
            dynamic_root_follow=dynamic_root_follow,
            dynamic_root_gain=args.dynamic_root_gain,
        )
    else:
        run_live(
            xml_path=xml_path,
            task_path=task_path,
            joint_map_path=joint_map_path,
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
            user_height_m=args.user_height_m,
            wall_z=args.wall_z,
            wall_axis=args.wall_axis,
            wall_snap_distance=args.wall_snap_distance,
            wall_stable_speed=args.wall_stable_speed,
            ik_iters=args.ik_iters,
            ik_damping=args.ik_damping,
            ik_step_scale=args.ik_step_scale,
            ik_pos_weight=args.ik_pos_weight,
            ik_aux_pos_weight=args.ik_aux_pos_weight,
            ik_torso_rot_weight=args.ik_torso_rot_weight,
            ik_limb_rot_weight=args.ik_limb_rot_weight,
            ik_posture_weight=args.ik_posture_weight,
            dynamic_root_follow=dynamic_root_follow,
            dynamic_root_gain=args.dynamic_root_gain,
        )


if __name__ == "__main__":
    main()

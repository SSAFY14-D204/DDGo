from __future__ import annotations

import argparse
import json
import math
import re
from pathlib import Path
from typing import Any

import mujoco
import numpy as np

# MediaPipe world landmark indices (BlazePose 33)
LEFT_SHOULDER = 11
RIGHT_SHOULDER = 12
LEFT_ELBOW = 13
RIGHT_ELBOW = 14
LEFT_WRIST = 15
RIGHT_WRIST = 16
LEFT_PINKY = 17
RIGHT_PINKY = 18
LEFT_INDEX = 19
RIGHT_INDEX = 20
LEFT_THUMB = 21
RIGHT_THUMB = 22
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

LIMB_TO_LM = {
    "left_wrist": LEFT_WRIST,
    "right_wrist": RIGHT_WRIST,
    "left_ankle": LEFT_ANKLE,
    "right_ankle": RIGHT_ANKLE,
}

LIMB_TO_MOCAP_BODY = {
    "left_wrist": "mocap_wrist_left",
    "right_wrist": "mocap_wrist_right",
    "left_ankle": "mocap_ankle_left",
    "right_ankle": "mocap_ankle_right",
}

LIMB_TO_BODY = {
    "left_wrist": "palm_contact_left",
    "right_wrist": "palm_contact_right",
    "left_ankle": "forefoot_contact_left",
    "right_ankle": "forefoot_contact_right",
}

LIMB_TO_EQUALITY = {
    "left_wrist": "weld_hand_left",
    "right_wrist": "weld_hand_right",
    "left_ankle": "weld_foot_left",
    "right_ankle": "weld_foot_right",
}

DEBUG_MARKER_SPECS = {
    "debug_marker_shoulder_left": {"rgba": "0.15 0.95 0.95 0.7", "size": 0.018},
    "debug_marker_shoulder_right": {"rgba": "0.95 0.35 0.95 0.7", "size": 0.018},
    "debug_marker_elbow_left": {"rgba": "0.15 1.0 0.55 0.7", "size": 0.017},
    "debug_marker_elbow_right": {"rgba": "1.0 0.45 0.15 0.7", "size": 0.017},
    "debug_marker_knee_left": {"rgba": "1.0 0.9 0.15 0.7", "size": 0.017},
    "debug_marker_knee_right": {"rgba": "0.55 0.55 1.0 0.7", "size": 0.017},
}

DEFAULT_ANALYSIS_JOINTS = [
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
    "shoulder_shrug_right",
    "shoulder1_right",
    "shoulder2_right",
    "elbow_right",
    "shoulder_shrug_left",
    "shoulder1_left",
    "shoulder2_left",
    "elbow_left",
]

AXIS_INDEX = {"x": 0, "y": 1, "z": 2}

BASE_SHOULDER_WIDTH_M = 0.34
BASE_UPPER_ARM_VEC = np.array([0.18, -0.18, -0.18], dtype=np.float64)
BASE_FOREARM_VEC = np.array([0.18, 0.18, 0.18], dtype=np.float64)
BASE_THIGH_VEC = np.array([0.0, 0.01, -0.4], dtype=np.float64)
BASE_SHIN_VEC = np.array([0.0, 0.0, -0.39], dtype=np.float64)
BASE_FOOT_LENGTH_SCALE = 1.0
FOREFOOT_CONTACT_RATIO = 0.80
PALM_CONTACT_FOREARM_RATIO = 0.28


def normalize(v: np.ndarray, eps: float = 1e-8) -> np.ndarray:
    n = float(np.linalg.norm(v))
    if n < eps:
        return np.zeros_like(v)
    return v / n


def angle_3d(a: np.ndarray, b: np.ndarray, c: np.ndarray) -> float:
    ba = a - b
    bc = c - b
    denom = float(np.linalg.norm(ba) * np.linalg.norm(bc))
    if denom < 1e-8:
        return math.pi
    cosine = float(np.clip(np.dot(ba, bc) / denom, -1.0, 1.0))
    return math.acos(cosine)


def quat_from_axes(x_axis: np.ndarray, y_axis: np.ndarray, z_axis: np.ndarray) -> np.ndarray:
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
    n = float(np.linalg.norm(q))
    if n < 1e-8:
        return np.array([1.0, 0.0, 0.0, 0.0], dtype=np.float64)
    return q / n


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


def frame_from_segments(
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


def signed_angle_about_axis(v_ref: np.ndarray, v_cur: np.ndarray, axis: np.ndarray) -> float:
    a = normalize(axis)
    ref = normalize(v_ref - a * float(np.dot(v_ref, a)))
    cur = normalize(v_cur - a * float(np.dot(v_cur, a)))
    if np.linalg.norm(ref) < 1e-6 or np.linalg.norm(cur) < 1e-6:
        return 0.0
    cross = np.cross(ref, cur)
    return math.atan2(float(np.dot(cross, a)), float(np.dot(ref, cur)))


def unwrap_half_turn(angle_rad: float) -> float:
    angle = math.atan2(math.sin(angle_rad), math.cos(angle_rad))
    if angle > 0.5 * math.pi:
        angle -= math.pi
    elif angle < -0.5 * math.pi:
        angle += math.pi
    return angle


def mp_to_mj(point_xyz: np.ndarray) -> np.ndarray:
    """MediaPipe world -> MuJoCo coordinates used by pysical_verify."""
    x, y, z = point_xyz
    return np.array([-z, -x, -y], dtype=np.float64)


def infer_palm_contact(
    wrist: np.ndarray,
    elbow: np.ndarray,
    index_tip: np.ndarray,
    pinky_tip: np.ndarray,
    thumb_tip: np.ndarray,
) -> np.ndarray:
    fingertip_centroid = (index_tip + pinky_tip + thumb_tip) / 3.0
    fingertip_dir = normalize(fingertip_centroid - wrist)
    forearm_dir = normalize(wrist - elbow)
    blended_dir = normalize(0.55 * fingertip_dir + 0.45 * forearm_dir)
    if float(np.linalg.norm(blended_dir)) < 1e-6:
        blended_dir = forearm_dir if float(np.linalg.norm(forearm_dir)) >= 1e-6 else fingertip_dir
    forearm_len = float(np.linalg.norm(wrist - elbow))
    fingertip_span = float(np.linalg.norm(fingertip_centroid - wrist))
    offset = np.clip(
        max(PALM_CONTACT_FOREARM_RATIO * forearm_len, 0.65 * fingertip_span),
        0.035,
        0.08,
    )
    return wrist + blended_dir * offset


def infer_forefoot_contact(heel: np.ndarray, toe: np.ndarray) -> np.ndarray:
    return heel + FOREFOOT_CONTACT_RATIO * (toe - heel)


def parse_landmarks(frame: dict[str, Any]) -> np.ndarray:
    raw = frame.get("pose_world_landmarks") or frame.get("landmarks")
    if raw is None:
        raise ValueError("Frame is missing pose_world_landmarks")
    if len(raw) < 33:
        raise ValueError("pose_world_landmarks must contain 33 points")

    pts = np.zeros((len(raw), 3), dtype=np.float64)
    for i, point in enumerate(raw):
        if isinstance(point, dict):
            pts[i] = [float(point["x"]), float(point["y"]), float(point["z"])]
        else:
            pts[i] = [float(point[0]), float(point[1]), float(point[2])]
    return pts


def build_hold_points(hold_meta: dict[str, Any]) -> np.ndarray:
    holds = hold_meta.get("holds", [])
    points = []
    for hold in holds:
        points.append([float(hold["x"]), float(hold["y"]), float(hold["z"])])
    if not points:
        return np.zeros((0, 3), dtype=np.float64)
    return np.array(points, dtype=np.float64)


def infer_wall_axis(hold_points: np.ndarray) -> str:
    if hold_points.shape[0] < 2:
        return "x"
    variances = np.var(hold_points, axis=0)
    axis_idx = int(np.argmin(variances))
    return ("x", "y", "z")[axis_idx]


def resolve_wall_plane(hold_meta: dict[str, Any], hold_points: np.ndarray) -> tuple[str, float]:
    explicit_axis = str(hold_meta.get("wall_axis", "")).strip().lower()
    legacy_axis = ""
    if not explicit_axis:
        for axis_name in ("x", "y", "z"):
            if f"wall_plane_{axis_name}" in hold_meta:
                legacy_axis = axis_name
                break
    wall_axis = explicit_axis or legacy_axis or infer_wall_axis(hold_points)
    plane_key = f"wall_plane_{wall_axis}"
    if plane_key in hold_meta:
        return wall_axis, float(hold_meta[plane_key])
    if hold_points.shape[0] > 0:
        return wall_axis, float(np.mean(hold_points[:, AXIS_INDEX[wall_axis]]))
    return wall_axis, 0.0


def load_calibration_json(calibration_json: Path | None) -> dict[str, float] | None:
    if calibration_json is None:
        return None
    payload = json.loads(calibration_json.read_text(encoding="utf-8-sig"))
    required = (
        "upper_arm_m",
        "forearm_m",
        "thigh_m",
        "shin_m",
        "shoulder_width_m",
        "wingspan_m",
    )
    missing = [key for key in required if key not in payload]
    if missing:
        raise ValueError(f"Calibration JSON missing required fields: {missing}")
    return {key: float(payload[key]) for key in payload.keys() if isinstance(payload[key], (int, float))}


def _format_float(value: float) -> str:
    text = f"{float(value):.6f}"
    text = text.rstrip("0").rstrip(".")
    if text == "-0":
        return "0"
    return text


def _format_vec(values: np.ndarray) -> str:
    return " ".join(_format_float(float(v)) for v in np.asarray(values, dtype=np.float64))


def _replace_body_pos(xml_text: str, body_name: str, values: np.ndarray) -> str:
    pattern = rf'(<body name="{re.escape(body_name)}" pos=")([^"]+)(")'
    return re.sub(pattern, rf'\g<1>{_format_vec(values)}\g<3>', xml_text, count=1)


def _replace_geom_fromto(xml_text: str, geom_name: str, values: np.ndarray) -> str:
    pattern = rf'(<geom name="{re.escape(geom_name)}"[^>]*fromto=")([^"]+)(")'
    return re.sub(pattern, rf'\g<1>{_format_vec(values)}\g<3>', xml_text, count=1)


def _replace_default_geom_size(xml_text: str, class_name: str, size_value: float) -> str:
    pattern = rf'(<default class="{re.escape(class_name)}">\s*<geom size=")([^"]+)(")'
    return re.sub(pattern, rf'\g<1>{_format_float(size_value)}\g<3>', xml_text, count=1)


def _replace_default_geom_fromto(xml_text: str, class_name: str, values: np.ndarray) -> str:
    pattern = rf'(<default class="{re.escape(class_name)}">\s*<geom fromto=")([^"]+)(")'
    return re.sub(pattern, rf'\g<1>{_format_vec(values)}\g<3>', xml_text, count=1)


def _replace_default_joint_pos(xml_text: str, class_name: str, values: np.ndarray) -> str:
    pattern = rf'(<default class="{re.escape(class_name)}">\s*<joint[^>]*pos=")([^"]+)(")'
    return re.sub(pattern, rf'\g<1>{_format_vec(values)}\g<3>', xml_text, count=1)


def apply_segment_scaling_template(xml_text: str, calibration: dict[str, float] | None) -> str:
    if calibration is None:
        return xml_text

    upper_arm_scale = calibration["upper_arm_m"] / float(np.linalg.norm(BASE_UPPER_ARM_VEC))
    forearm_scale = calibration["forearm_m"] / float(np.linalg.norm(BASE_FOREARM_VEC))
    thigh_scale = calibration["thigh_m"] / float(np.linalg.norm(BASE_THIGH_VEC))
    shin_scale = calibration["shin_m"] / float(np.linalg.norm(BASE_SHIN_VEC))
    shoulder_scale = calibration["shoulder_width_m"] / BASE_SHOULDER_WIDTH_M
    hand_scale = forearm_scale
    foot_scale = shin_scale * BASE_FOOT_LENGTH_SCALE

    global_height_scale = float(
        np.mean([upper_arm_scale, forearm_scale, thigh_scale, shin_scale, shoulder_scale])
    )

    xml_text = _replace_body_pos(xml_text, "upper_arm_right", np.array([0.0, -0.17 * shoulder_scale, 0.06 * global_height_scale]))
    xml_text = _replace_body_pos(xml_text, "upper_arm_left", np.array([0.0, 0.17 * shoulder_scale, 0.06 * global_height_scale]))
    xml_text = _replace_body_pos(xml_text, "lower_arm_right", BASE_UPPER_ARM_VEC * upper_arm_scale)
    xml_text = _replace_body_pos(xml_text, "lower_arm_left", np.array([0.18, 0.18, -0.18], dtype=np.float64) * upper_arm_scale)
    xml_text = _replace_body_pos(xml_text, "hand_right", BASE_FOREARM_VEC * forearm_scale)
    xml_text = _replace_body_pos(xml_text, "hand_left", np.array([0.18, -0.18, 0.18], dtype=np.float64) * forearm_scale)
    xml_text = _replace_body_pos(xml_text, "shin_right", BASE_THIGH_VEC * thigh_scale)
    xml_text = _replace_body_pos(xml_text, "shin_left", np.array([0.0, -0.01, -0.4], dtype=np.float64) * thigh_scale)
    xml_text = _replace_body_pos(xml_text, "foot_right", BASE_SHIN_VEC * shin_scale)
    xml_text = _replace_body_pos(xml_text, "foot_left", np.array([0.0, 0.0, -0.39], dtype=np.float64) * shin_scale)

    xml_text = _replace_geom_fromto(xml_text, "upper_arm_right", np.array([0.0, 0.0, 0.0, 0.16, -0.16, -0.16], dtype=np.float64) * upper_arm_scale)
    xml_text = _replace_geom_fromto(xml_text, "upper_arm_left", np.array([0.0, 0.0, 0.0, 0.16, 0.16, -0.16], dtype=np.float64) * upper_arm_scale)
    xml_text = _replace_geom_fromto(xml_text, "lower_arm_right", np.array([0.01, 0.01, 0.01, 0.17, 0.17, 0.17], dtype=np.float64) * forearm_scale)
    xml_text = _replace_geom_fromto(xml_text, "lower_arm_left", np.array([0.01, -0.01, 0.01, 0.17, -0.17, 0.17], dtype=np.float64) * forearm_scale)
    xml_text = _replace_geom_fromto(xml_text, "thigh_right", np.array([0.0, 0.0, 0.0, 0.0, 0.01, -0.34], dtype=np.float64) * thigh_scale)
    xml_text = _replace_geom_fromto(xml_text, "thigh_left", np.array([0.0, 0.0, 0.0, 0.0, -0.01, -0.34], dtype=np.float64) * thigh_scale)
    xml_text = _replace_geom_fromto(xml_text, "shin_left", np.array([0.0, 0.0, 0.0, 0.0, 0.0, -0.3], dtype=np.float64) * shin_scale)
    xml_text = _replace_default_geom_fromto(xml_text, "shin", np.array([0.0, 0.0, 0.0, 0.0, 0.0, -0.3], dtype=np.float64) * shin_scale)
    xml_text = _replace_default_geom_fromto(xml_text, "foot1", np.array([-0.07, -0.01, 0.0, 0.14, -0.03, 0.0], dtype=np.float64) * foot_scale)
    xml_text = _replace_default_geom_fromto(xml_text, "foot2", np.array([-0.07, 0.01, 0.0, 0.14, 0.03, 0.0], dtype=np.float64) * foot_scale)

    xml_text = _replace_default_geom_size(xml_text, "arm_upper", 0.04 * upper_arm_scale)
    xml_text = _replace_default_geom_size(xml_text, "arm_lower", 0.031 * forearm_scale)
    xml_text = _replace_default_geom_size(xml_text, "thigh", 0.06 * thigh_scale)
    xml_text = _replace_default_geom_size(xml_text, "shin", 0.049 * shin_scale)
    xml_text = _replace_default_geom_size(xml_text, "hand", 0.04 * hand_scale)
    xml_text = _replace_default_geom_size(xml_text, "foot", 0.027 * foot_scale)
    xml_text = _replace_default_joint_pos(xml_text, "knee", np.array([0.0, 0.0, 0.02], dtype=np.float64) * thigh_scale)
    xml_text = _replace_default_joint_pos(xml_text, "ankle_y", np.array([0.0, 0.0, 0.08], dtype=np.float64) * shin_scale)
    xml_text = _replace_default_joint_pos(xml_text, "ankle_x", np.array([0.0, 0.0, 0.04], dtype=np.float64) * shin_scale)
    return xml_text


def segment_lengths_local_from_calibration(
    calibration: dict[str, float] | None,
    shoulder_width_local: float,
) -> dict[str, float] | None:
    if calibration is None or shoulder_width_local <= 1e-6:
        return None
    local_to_m = calibration["shoulder_width_m"] / shoulder_width_local
    if local_to_m <= 1e-9:
        return None
    return {
        "upper_arm": calibration["upper_arm_m"] / local_to_m,
        "forearm": calibration["forearm_m"] / local_to_m,
        "thigh": calibration["thigh_m"] / local_to_m,
        "shin": calibration["shin_m"] / local_to_m,
    }


def _resolve_depth_coordinate(
    parent: np.ndarray,
    joint: np.ndarray,
    child: np.ndarray,
    parent_length: float,
    child_length: float,
    depth_axis: int = 0,
    planar_axes: tuple[int, int] = (1, 2),
) -> float:
    planar_parent = float(np.linalg.norm(joint[list(planar_axes)] - parent[list(planar_axes)]))
    planar_child = float(np.linalg.norm(joint[list(planar_axes)] - child[list(planar_axes)]))

    delta_parent = math.sqrt(max(parent_length * parent_length - planar_parent * planar_parent, 0.0))
    delta_child = math.sqrt(max(child_length * child_length - planar_child * planar_child, 0.0))

    sign_parent = 1.0 if float(joint[depth_axis] - parent[depth_axis]) >= 0.0 else -1.0
    sign_child = 1.0 if float(joint[depth_axis] - child[depth_axis]) >= 0.0 else -1.0

    candidate_parent = float(parent[depth_axis] + sign_parent * delta_parent)
    candidate_child = float(child[depth_axis] + sign_child * delta_child)
    return 0.5 * (candidate_parent + candidate_child)


def apply_inverse_depth_correction_to_mapped(
    mapped: np.ndarray,
    segment_lengths_local: dict[str, float] | None,
    swap_lr: bool = False,
) -> np.ndarray:
    if segment_lengths_local is None:
        return mapped

    corrected = mapped.copy()
    l_sh_idx, r_sh_idx = (RIGHT_SHOULDER, LEFT_SHOULDER) if swap_lr else (LEFT_SHOULDER, RIGHT_SHOULDER)
    l_el_idx, r_el_idx = (RIGHT_ELBOW, LEFT_ELBOW) if swap_lr else (LEFT_ELBOW, RIGHT_ELBOW)
    l_wr_idx, r_wr_idx = (RIGHT_WRIST, LEFT_WRIST) if swap_lr else (LEFT_WRIST, RIGHT_WRIST)
    l_hi_idx, r_hi_idx = (RIGHT_HIP, LEFT_HIP) if swap_lr else (LEFT_HIP, RIGHT_HIP)
    l_kn_idx, r_kn_idx = (RIGHT_KNEE, LEFT_KNEE) if swap_lr else (LEFT_KNEE, RIGHT_KNEE)
    l_an_idx, r_an_idx = (RIGHT_ANKLE, LEFT_ANKLE) if swap_lr else (LEFT_ANKLE, RIGHT_ANKLE)

    for sh_idx, el_idx, wr_idx in ((l_sh_idx, l_el_idx, l_wr_idx), (r_sh_idx, r_el_idx, r_wr_idx)):
        corrected[el_idx, 0] = _resolve_depth_coordinate(
            parent=corrected[sh_idx],
            joint=corrected[el_idx],
            child=corrected[wr_idx],
            parent_length=segment_lengths_local["upper_arm"],
            child_length=segment_lengths_local["forearm"],
        )

    for hi_idx, kn_idx, an_idx in ((l_hi_idx, l_kn_idx, l_an_idx), (r_hi_idx, r_kn_idx, r_an_idx)):
        corrected[kn_idx, 0] = _resolve_depth_coordinate(
            parent=corrected[hi_idx],
            joint=corrected[kn_idx],
            child=corrected[an_idx],
            parent_length=segment_lengths_local["thigh"],
            child_length=segment_lengths_local["shin"],
        )
    return corrected


def _orthogonal_component(vec: np.ndarray, axis: np.ndarray) -> np.ndarray:
    return np.asarray(vec, dtype=np.float64) - np.asarray(axis, dtype=np.float64) * float(
        np.dot(np.asarray(vec, dtype=np.float64), np.asarray(axis, dtype=np.float64))
    )


def _choose_fallback_pole(axis: np.ndarray, preferred: np.ndarray) -> np.ndarray:
    pole = _orthogonal_component(preferred, axis)
    if float(np.linalg.norm(pole)) >= 1e-6:
        return normalize(pole)
    trial = np.cross(axis, np.array([0.0, 0.0, 1.0], dtype=np.float64))
    if float(np.linalg.norm(trial)) < 1e-6:
        trial = np.cross(axis, np.array([0.0, 1.0, 0.0], dtype=np.float64))
    return normalize(trial)


def solve_two_link_chain(
    root: np.ndarray,
    joint_hint: np.ndarray,
    end_hint: np.ndarray,
    len1: float,
    len2: float,
    pole_hint: np.ndarray,
    fallback_pole: np.ndarray,
    prev_joint: np.ndarray | None = None,
) -> tuple[np.ndarray, np.ndarray, dict[str, float]]:
    root = np.asarray(root, dtype=np.float64)
    joint_hint = np.asarray(joint_hint, dtype=np.float64)
    end_hint = np.asarray(end_hint, dtype=np.float64)
    pole_hint = np.asarray(pole_hint, dtype=np.float64)
    fallback_pole = np.asarray(fallback_pole, dtype=np.float64)

    direction = end_hint - root
    distance_raw = float(np.linalg.norm(direction))
    if distance_raw < 1e-6:
        direction = joint_hint - root
        distance_raw = float(np.linalg.norm(direction))
    if distance_raw < 1e-6:
        direction = fallback_pole
        distance_raw = float(np.linalg.norm(direction))
    direction = normalize(direction)

    min_reach = max(abs(float(len1) - float(len2)) + 1e-4, 1e-4)
    max_reach = max(float(len1) + float(len2) - 1e-4, min_reach + 1e-4)
    distance = float(np.clip(distance_raw, min_reach, max_reach))
    end = root + direction * distance

    x = (float(len1) ** 2 - float(len2) ** 2 + distance * distance) / (2.0 * max(distance, 1e-6))
    h_sq = max(float(len1) ** 2 - x * x, 0.0)
    h = math.sqrt(h_sq)
    mid = root + direction * x

    pole = _orthogonal_component(pole_hint, direction)
    if prev_joint is not None:
        pole = 0.7 * pole + 0.3 * _orthogonal_component(np.asarray(prev_joint, dtype=np.float64) - root, direction)
    if float(np.linalg.norm(pole)) < 1e-6:
        pole = _choose_fallback_pole(direction, fallback_pole)
    else:
        pole = normalize(pole)

    joint = mid + pole * h
    diagnostics = {
        "raw_distance": distance_raw,
        "corrected_distance": distance,
        "was_clamped": 1.0 if abs(distance - distance_raw) > 1e-5 else 0.0,
    }
    return joint, end, diagnostics


def apply_two_link_pose_correction_to_mapped(
    mapped: np.ndarray,
    segment_lengths_local: dict[str, float] | None,
    swap_lr: bool = False,
    prev_points: dict[str, np.ndarray] | None = None,
) -> tuple[np.ndarray, dict[str, dict[str, float]]]:
    if segment_lengths_local is None:
        return mapped, {}

    corrected = mapped.copy()
    diagnostics: dict[str, dict[str, float]] = {}

    l_sh_idx, r_sh_idx = (RIGHT_SHOULDER, LEFT_SHOULDER) if swap_lr else (LEFT_SHOULDER, RIGHT_SHOULDER)
    l_el_idx, r_el_idx = (RIGHT_ELBOW, LEFT_ELBOW) if swap_lr else (LEFT_ELBOW, RIGHT_ELBOW)
    l_wr_idx, r_wr_idx = (RIGHT_WRIST, LEFT_WRIST) if swap_lr else (LEFT_WRIST, RIGHT_WRIST)
    l_hi_idx, r_hi_idx = (RIGHT_HIP, LEFT_HIP) if swap_lr else (LEFT_HIP, RIGHT_HIP)
    l_kn_idx, r_kn_idx = (RIGHT_KNEE, LEFT_KNEE) if swap_lr else (LEFT_KNEE, RIGHT_KNEE)
    l_an_idx, r_an_idx = (RIGHT_ANKLE, LEFT_ANKLE) if swap_lr else (LEFT_ANKLE, RIGHT_ANKLE)
    l_he_idx, r_he_idx = (RIGHT_HEEL, LEFT_HEEL) if swap_lr else (LEFT_HEEL, RIGHT_HEEL)
    l_to_idx, r_to_idx = (RIGHT_FOOT_INDEX, LEFT_FOOT_INDEX) if swap_lr else (LEFT_FOOT_INDEX, RIGHT_FOOT_INDEX)
    lpi_idx, rpi_idx = (RIGHT_PINKY, LEFT_PINKY) if swap_lr else (LEFT_PINKY, RIGHT_PINKY)
    lin_idx, rin_idx = (RIGHT_INDEX, LEFT_INDEX) if swap_lr else (LEFT_INDEX, RIGHT_INDEX)
    lth_idx, rth_idx = (RIGHT_THUMB, LEFT_THUMB) if swap_lr else (LEFT_THUMB, RIGHT_THUMB)

    shoulder_mid = 0.5 * (corrected[LEFT_SHOULDER] + corrected[RIGHT_SHOULDER])
    hip_mid = 0.5 * (corrected[LEFT_HIP] + corrected[RIGHT_HIP])
    axis_up = normalize(shoulder_mid - hip_mid)
    axis_left = normalize(corrected[LEFT_SHOULDER] - corrected[RIGHT_SHOULDER])
    if float(np.linalg.norm(axis_left)) < 1e-6:
        axis_left = np.array([0.0, 1.0, 0.0], dtype=np.float64)

    arm_specs = [
        ("left", l_sh_idx, l_el_idx, l_wr_idx, (lin_idx, lpi_idx, lth_idx), -1.0),
        ("right", r_sh_idx, r_el_idx, r_wr_idx, (rin_idx, rpi_idx, rth_idx), 1.0),
    ]
    for side, sh_idx, el_idx, wr_idx, finger_indices, side_sign in arm_specs:
        root = corrected[sh_idx].copy()
        joint_hint = corrected[el_idx].copy()
        end_hint = corrected[wr_idx].copy()
        pole_hint = joint_hint - root
        prev_joint = prev_points.get(f"{side}_elbow") if prev_points is not None else None
        fallback_pole = side_sign * axis_left + 0.25 * axis_up
        joint, end, diag = solve_two_link_chain(
            root=root,
            joint_hint=joint_hint,
            end_hint=end_hint,
            len1=segment_lengths_local["upper_arm"],
            len2=segment_lengths_local["forearm"],
            pole_hint=pole_hint,
            fallback_pole=fallback_pole,
            prev_joint=prev_joint,
        )
        wrist_delta = end - corrected[wr_idx]
        corrected[el_idx] = joint
        for idx in (wr_idx, *finger_indices):
            corrected[idx] = corrected[idx] + wrist_delta
        diagnostics[f"{side}_arm"] = diag

    leg_specs = [
        ("left", l_hi_idx, l_kn_idx, l_an_idx, (l_he_idx, l_to_idx), -1.0),
        ("right", r_hi_idx, r_kn_idx, r_an_idx, (r_he_idx, r_to_idx), 1.0),
    ]
    for side, hi_idx, kn_idx, an_idx, foot_indices, side_sign in leg_specs:
        root = corrected[hi_idx].copy()
        joint_hint = corrected[kn_idx].copy()
        end_hint = corrected[an_idx].copy()
        pole_hint = joint_hint - root
        prev_joint = prev_points.get(f"{side}_knee") if prev_points is not None else None
        fallback_pole = side_sign * axis_left - 0.15 * axis_up
        joint, end, diag = solve_two_link_chain(
            root=root,
            joint_hint=joint_hint,
            end_hint=end_hint,
            len1=segment_lengths_local["thigh"],
            len2=segment_lengths_local["shin"],
            pole_hint=pole_hint,
            fallback_pole=fallback_pole,
            prev_joint=prev_joint,
        )
        ankle_delta = end - corrected[an_idx]
        corrected[kn_idx] = joint
        for idx in (an_idx, *foot_indices):
            corrected[idx] = corrected[idx] + ankle_delta
        diagnostics[f"{side}_leg"] = diag

    return corrected, diagnostics


def _extract_joint_pose_targets_from_mapped(
    mapped: np.ndarray,
    swap_lr: bool = False,
) -> tuple[dict[str, float], dict[str, np.ndarray]]:
    l_sh_idx, r_sh_idx = (RIGHT_SHOULDER, LEFT_SHOULDER) if swap_lr else (LEFT_SHOULDER, RIGHT_SHOULDER)
    l_el_idx, r_el_idx = (RIGHT_ELBOW, LEFT_ELBOW) if swap_lr else (LEFT_ELBOW, RIGHT_ELBOW)
    l_wr_idx, r_wr_idx = (RIGHT_WRIST, LEFT_WRIST) if swap_lr else (LEFT_WRIST, RIGHT_WRIST)
    l_hi_idx, r_hi_idx = (RIGHT_HIP, LEFT_HIP) if swap_lr else (LEFT_HIP, RIGHT_HIP)
    l_kn_idx, r_kn_idx = (RIGHT_KNEE, LEFT_KNEE) if swap_lr else (LEFT_KNEE, RIGHT_KNEE)
    l_an_idx, r_an_idx = (RIGHT_ANKLE, LEFT_ANKLE) if swap_lr else (LEFT_ANKLE, RIGHT_ANKLE)
    l_he_idx, r_he_idx = (RIGHT_HEEL, LEFT_HEEL) if swap_lr else (LEFT_HEEL, RIGHT_HEEL)
    l_to_idx, r_to_idx = (RIGHT_FOOT_INDEX, LEFT_FOOT_INDEX) if swap_lr else (LEFT_FOOT_INDEX, RIGHT_FOOT_INDEX)

    ls_raw = mapped[LEFT_SHOULDER]
    rs_raw = mapped[RIGHT_SHOULDER]
    lh_raw = mapped[LEFT_HIP]
    rh_raw = mapped[RIGHT_HIP]

    ls = mapped[l_sh_idx]
    rs = mapped[r_sh_idx]
    le = mapped[l_el_idx]
    re = mapped[r_el_idx]
    lw = mapped[l_wr_idx]
    rw = mapped[r_wr_idx]
    lpi = mapped[RIGHT_PINKY if swap_lr else LEFT_PINKY]
    rpi = mapped[LEFT_PINKY if swap_lr else RIGHT_PINKY]
    lin = mapped[RIGHT_INDEX if swap_lr else LEFT_INDEX]
    rin = mapped[LEFT_INDEX if swap_lr else RIGHT_INDEX]
    lth = mapped[RIGHT_THUMB if swap_lr else LEFT_THUMB]
    rth = mapped[LEFT_THUMB if swap_lr else RIGHT_THUMB]

    lh = mapped[l_hi_idx]
    rh = mapped[r_hi_idx]
    lk = mapped[l_kn_idx]
    rk = mapped[r_kn_idx]
    la = mapped[l_an_idx]
    ra = mapped[r_an_idx]

    lheel = mapped[l_he_idx]
    rheel = mapped[r_he_idx]
    ltoe = mapped[l_to_idx]
    rtoe = mapped[r_to_idx]
    lhand_tip = (lin + lpi + lth) / 3.0
    rhand_tip = (rin + rpi + rth) / 3.0
    lpalm_contact = infer_palm_contact(lw, le, lin, lpi, lth)
    rpalm_contact = infer_palm_contact(rw, re, rin, rpi, rth)
    lforefoot_contact = infer_forefoot_contact(lheel, ltoe)
    rforefoot_contact = infer_forefoot_contact(rheel, rtoe)

    shoulder_mid = 0.5 * (ls_raw + rs_raw)
    hip_mid = 0.5 * (lh_raw + rh_raw)

    up = normalize(shoulder_mid - hip_mid)
    left_axis = normalize(ls_raw - rs_raw)
    forward_axis = normalize(np.cross(left_axis, up))
    if float(np.linalg.norm(forward_axis)) < 1e-6:
        forward_axis = np.array([1.0, 0.0, 0.0], dtype=np.float64)
    left_axis = normalize(np.cross(up, forward_axis))
    sagittal_axis = -forward_axis

    def to_torso_frame(v: np.ndarray) -> np.ndarray:
        return np.array(
            [
                float(np.dot(v, sagittal_axis)),
                float(np.dot(v, left_axis)),
                float(np.dot(v, up)),
            ],
            dtype=np.float64,
        )

    torso_vec = normalize(shoulder_mid - hip_mid)
    abdomen_x = math.atan2(float(torso_vec[1]), max(float(torso_vec[2]), 1e-6))
    abdomen_y = -math.atan2(float(torso_vec[0]), max(float(torso_vec[2]), 1e-6))
    abdomen_z = 0.0

    def arm_angles(
        shoulder: np.ndarray,
        elbow: np.ndarray,
        wrist: np.ndarray,
        hand_contact: np.ndarray,
        side_sign: float,
    ) -> tuple[float, float, float]:
        upper_x, _, _ = frame_from_segments(elbow - shoulder, hand_contact - elbow, up)
        upper_dir = normalize(0.8 * upper_x + 0.2 * normalize(wrist - shoulder))
        upper = to_torso_frame(upper_dir)
        shoulder_abd = math.atan2(side_sign * float(upper[1]), max(float(-upper[2]), 1e-6))
        shoulder_flex = math.atan2(float(upper[0]), max(float(-upper[2]), 1e-6))
        elbow_flex = math.pi - angle_3d(shoulder, elbow, hand_contact)
        return shoulder_abd, shoulder_flex, -elbow_flex

    def leg_angles(
        hip: np.ndarray,
        knee: np.ndarray,
        ankle: np.ndarray,
        heel: np.ndarray,
        toe: np.ndarray,
        side_sign: float,
    ) -> tuple[float, float, float, float, float, float]:
        shank_dir = normalize(ankle - knee)
        foot_dir_world = normalize(toe - heel)
        twist_hint = normalize(shank_dir + 0.7 * foot_dir_world)
        if float(np.linalg.norm(np.cross(knee - hip, twist_hint))) < 1e-6:
            twist_hint = foot_dir_world
        thigh_axis, thigh_y, _ = frame_from_segments(knee - hip, twist_hint, sagittal_axis)
        thigh = to_torso_frame(thigh_axis)
        foot = to_torso_frame(foot_dir_world)

        hip_abd = math.atan2(side_sign * float(thigh[1]), max(float(-thigh[2]), 1e-6))
        hip_flex = math.atan2(float(thigh[0]), max(float(-thigh[2]), 1e-6))
        lateral_ref = normalize((-side_sign * left_axis) - thigh_axis * float(np.dot(-side_sign * left_axis, thigh_axis)))
        foot_twist = normalize(foot_dir_world - thigh_axis * float(np.dot(foot_dir_world, thigh_axis)))
        rot_ref = foot_twist if float(np.linalg.norm(foot_twist)) >= 1e-6 else thigh_y
        hip_rot = unwrap_half_turn(signed_angle_about_axis(lateral_ref, rot_ref, thigh_axis))

        knee_flex = math.pi - angle_3d(hip, knee, ankle)
        ankle_pitch = math.atan2(float(foot[0]), max(abs(float(foot[2])), 1e-6))
        ankle_roll = math.atan2(side_sign * float(foot[1]), max(abs(float(foot[2])), 1e-6))
        return hip_abd, hip_rot, -hip_flex, -knee_flex, ankle_pitch, ankle_roll

    s1_r, s2_r, e_r = arm_angles(rs, re, rw, rpalm_contact, side_sign=1.0)
    s1_l, s2_l, e_l = arm_angles(ls, le, lw, lpalm_contact, side_sign=-1.0)
    hx_r, hz_r, hy_r, k_r, ay_r, ax_r = leg_angles(rh, rk, ra, rheel, rtoe, side_sign=1.0)
    hx_l, hz_l, hy_l, k_l, ay_l, ax_l = leg_angles(lh, lk, la, lheel, ltoe, side_sign=-1.0)

    larm_x, larm_y, larm_z = frame_from_segments(le - ls, lpalm_contact - le, up)
    rarm_x, rarm_y, rarm_z = frame_from_segments(re - rs, rpalm_contact - re, up)
    lthigh_x, lthigh_y, lthigh_z = frame_from_segments(lk - lh, la - lk, sagittal_axis)
    rthigh_x, rthigh_y, rthigh_z = frame_from_segments(rk - rh, ra - rk, sagittal_axis)
    quat_targets = {
        "torso": quat_from_axes(sagittal_axis, left_axis, up),
        "upper_arm_left": quat_from_axes(larm_x, larm_y, larm_z),
        "upper_arm_right": quat_from_axes(rarm_x, rarm_y, rarm_z),
        "thigh_left": quat_from_axes(lthigh_x, lthigh_y, lthigh_z),
        "thigh_right": quat_from_axes(rthigh_x, rthigh_y, rthigh_z),
    }

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
        "left_hand_tip": lhand_tip,
        "right_hand_tip": rhand_tip,
        "left_palm_contact": lpalm_contact,
        "right_palm_contact": rpalm_contact,
        "left_hip": lh,
        "right_hip": rh,
        "hip_mid": hip_mid,
        "left_knee": lk,
        "right_knee": rk,
        "left_ankle": la,
        "right_ankle": ra,
        "left_heel": lheel,
        "right_heel": rheel,
        "left_toe": ltoe,
        "right_toe": rtoe,
        "left_forefoot_contact": lforefoot_contact,
        "right_forefoot_contact": rforefoot_contact,
        "left_elbow": le,
        "right_elbow": re,
        "axis_forward": sagittal_axis,
        "axis_left": left_axis,
        "axis_up": up,
        "quat_targets": quat_targets,
    }
    return joint_targets, points


def extract_joint_pose_targets(
    landmarks_mp: np.ndarray,
    swap_lr: bool = False,
) -> tuple[dict[str, float], dict[str, np.ndarray]]:
    mapped = np.array([mp_to_mj(p) for p in landmarks_mp], dtype=np.float64)
    return _extract_joint_pose_targets_from_mapped(mapped, swap_lr=swap_lr)


def point_in_polygon_2d(point_xy: np.ndarray, polygon: np.ndarray) -> bool:
    x, y = float(point_xy[0]), float(point_xy[1])
    n = polygon.shape[0]
    inside = False
    j = n - 1
    for i in range(n):
        xi, yi = float(polygon[i][0]), float(polygon[i][1])
        xj, yj = float(polygon[j][0]), float(polygon[j][1])
        intersects = ((yi > y) != (yj > y)) and (
            x < (xj - xi) * (y - yi) / (yj - yi + 1e-12) + xi
        )
        if intersects:
            inside = not inside
        j = i
    return inside


def convex_hull_2d(points: np.ndarray) -> np.ndarray:
    if len(points) <= 2:
        return points

    pts = points[np.lexsort((points[:, 1], points[:, 0]))]

    def cross(o: np.ndarray, a: np.ndarray, b: np.ndarray) -> float:
        return float((a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0]))

    lower: list[np.ndarray] = []
    for point in pts:
        while len(lower) >= 2 and cross(lower[-2], lower[-1], point) <= 0.0:
            lower.pop()
        lower.append(point)

    upper: list[np.ndarray] = []
    for point in reversed(pts):
        while len(upper) >= 2 and cross(upper[-2], upper[-1], point) <= 0.0:
            upper.pop()
        upper.append(point)

    return np.array(lower[:-1] + upper[:-1], dtype=np.float64)


def distance_point_to_segment(p: np.ndarray, a: np.ndarray, b: np.ndarray) -> float:
    ab = b - a
    t = np.dot(p - a, ab) / (np.dot(ab, ab) + 1e-12)
    t = float(np.clip(t, 0.0, 1.0))
    proj = a + t * ab
    return float(np.linalg.norm(p - proj))


def support_stability_score(com_xy: np.ndarray, contacts_xy: np.ndarray, margin: float = 0.15) -> float:
    if contacts_xy.shape[0] == 0:
        return 0.0
    if contacts_xy.shape[0] == 1:
        d = float(np.linalg.norm(com_xy - contacts_xy[0]))
        return float(np.clip(1.0 - d / margin, 0.0, 1.0))
    if contacts_xy.shape[0] == 2:
        d = distance_point_to_segment(com_xy, contacts_xy[0], contacts_xy[1])
        return float(np.clip(1.0 - d / margin, 0.0, 1.0))

    hull = convex_hull_2d(contacts_xy)
    if hull.shape[0] < 3:
        return 0.0

    inside = point_in_polygon_2d(com_xy, hull)
    dmin = 1e9
    for i in range(hull.shape[0]):
        a = hull[i]
        b = hull[(i + 1) % hull.shape[0]]
        dmin = min(dmin, distance_point_to_segment(com_xy, a, b))

    if inside:
        return float(np.clip(0.5 + dmin / margin, 0.0, 1.0))
    return float(np.clip(0.5 - dmin / margin, 0.0, 1.0))


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


def equality_id(model: mujoco.MjModel, name: str) -> int:
    eqid = mujoco.mj_name2id(model, mujoco.mjtObj.mjOBJ_EQUALITY, name)
    if eqid < 0:
        raise ValueError(f"Equality constraint not found: {name}")
    return eqid


def actuator_joint_torque_limits(model: mujoco.MjModel) -> dict[str, float]:
    limits: dict[str, float] = {}
    for aid in range(model.nu):
        trnid = int(model.actuator_trnid[aid][0])
        if trnid < 0:
            continue
        jname = mujoco.mj_id2name(model, mujoco.mjtObj.mjOBJ_JOINT, trnid)
        if not jname:
            continue

        gear = float(abs(model.actuator_gear[aid][0]))
        gear = max(gear, 1.0)

        if bool(model.actuator_forcelimited[aid]):
            fmin, fmax = model.actuator_forcerange[aid]
            torque_limit = gear * max(abs(float(fmin)), abs(float(fmax)))
        elif bool(model.actuator_ctrllimited[aid]):
            cmin, cmax = model.actuator_ctrlrange[aid]
            gain = float(abs(model.actuator_gainprm[aid][0]))
            gain = max(gain, 1.0)
            torque_limit = gear * gain * max(abs(float(cmin)), abs(float(cmax)))
        else:
            torque_limit = gear

        limits[jname] = max(limits.get(jname, 0.0), torque_limit)
    return limits


def compute_com(model: mujoco.MjModel, data: mujoco.MjData) -> np.ndarray:
    masses = model.body_mass[:, None]
    total_mass = float(np.sum(masses))
    return np.sum(data.xipos * masses, axis=0) / max(total_mass, 1e-9)


def hold_bodies_xml(hold_points: np.ndarray, hold_radius: float) -> str:
    if hold_points.shape[0] == 0:
        return ""
    geom_radius = float(np.clip(hold_radius * 0.35, 0.025, 0.065))
    lines = []
    for idx, point in enumerate(hold_points):
        x, y, z = [float(v) for v in point]
        lines.append(
            "    "
            + (
                f'<body name="hold_anchor_{idx}" pos="{x:.6f} {y:.6f} {z:.6f}">\n'
                f'      <geom name="hold_geom_{idx}" type="sphere" size="{geom_radius:.6f}" '
                'rgba="0.96 0.54 0.22 0.95" contype="1" conaffinity="1" friction="1.3 0.12 0.02"/>\n'
                "    </body>"
            )
        )
    return "\n".join(lines) + "\n"


def debug_marker_bodies_xml(marker_names: list[str] | None = None) -> str:
    marker_names = marker_names or []
    lines = []
    for name in marker_names:
        spec = DEBUG_MARKER_SPECS.get(name)
        if spec is None:
            continue
        lines.append(
            "    "
            + (
                f'<body name="{name}" mocap="true" pos="0 0 0">\n'
                f'      <geom type="sphere" size="{float(spec["size"]):.6f}" '
                f'rgba="{spec["rgba"]}" contype="0" conaffinity="0"/>\n'
                "    </body>"
            )
        )
    if not lines:
        return ""
    return "\n".join(lines) + "\n"


def add_mocap_and_equality(
    xml_text: str,
    hold_points: np.ndarray | None = None,
    hold_radius: float = 0.08,
    debug_marker_names: list[str] | None = None,
) -> str:
    hold_points = hold_points if hold_points is not None else np.zeros((0, 3), dtype=np.float64)
    debug_marker_names = debug_marker_names or []

    mocap_block = """
    <body name="mocap_wrist_right" mocap="true" pos="0 -0.2 1.2">
      <geom type="sphere" size="0.02" rgba="1 0 0 0.5" contype="0" conaffinity="0"/>
    </body>
    <body name="mocap_wrist_left" mocap="true" pos="0 0.2 1.2">
      <geom type="sphere" size="0.02" rgba="0 1 0 0.5" contype="0" conaffinity="0"/>
    </body>
    <body name="mocap_ankle_right" mocap="true" pos="0 -0.1 0.3">
      <geom type="sphere" size="0.02" rgba="0 0 1 0.5" contype="0" conaffinity="0"/>
    </body>
    <body name="mocap_ankle_left" mocap="true" pos="0 0.1 0.3">
      <geom type="sphere" size="0.02" rgba="1 1 0 0.5" contype="0" conaffinity="0"/>
    </body>
"""

    equality_block = """
  <equality>
    <weld name="weld_hand_right" body1="palm_contact_right" body2="mocap_wrist_right" active="false" solref="0.02 1" solimp="0.9 0.95 0.01"/>
    <weld name="weld_hand_left" body1="palm_contact_left" body2="mocap_wrist_left" active="false" solref="0.02 1" solimp="0.9 0.95 0.01"/>
    <weld name="weld_foot_right" body1="forefoot_contact_right" body2="mocap_ankle_right" active="false" solref="0.02 1" solimp="0.9 0.95 0.01"/>
    <weld name="weld_foot_left" body1="forefoot_contact_left" body2="mocap_ankle_left" active="false" solref="0.02 1" solimp="0.9 0.95 0.01"/>
  </equality>
"""

    if "mocap_wrist_right" not in xml_text:
        extra_worldbody = mocap_block + debug_marker_bodies_xml(debug_marker_names) + hold_bodies_xml(hold_points, hold_radius)
        xml_text = xml_text.replace("</worldbody>", f"{extra_worldbody}  </worldbody>")
    else:
        extra_worldbody = ""
        if debug_marker_names:
            for marker_name in debug_marker_names:
                if marker_name not in xml_text:
                    extra_worldbody += debug_marker_bodies_xml([marker_name])
        if "hold_anchor_0" not in xml_text and hold_points.shape[0] > 0:
            extra_worldbody += hold_bodies_xml(hold_points, hold_radius)
        if extra_worldbody:
            xml_text = xml_text.replace("</worldbody>", f"{extra_worldbody}  </worldbody>")

    if "<equality>" not in xml_text:
        xml_text = xml_text.replace("</mujoco>", f"{equality_block}\n</mujoco>")
    return xml_text


def load_analysis_payload(config_path: Path | None) -> dict[str, Any]:
    if config_path is None:
        return {}
    if not config_path.exists():
        raise FileNotFoundError(f"Analysis config not found: {config_path}")
    return json.loads(config_path.read_text(encoding="utf-8-sig"))


def build_analysis_model(
    xml_path: Path,
    payload: dict[str, Any] | None = None,
) -> tuple[mujoco.MjModel, mujoco.MjData]:
    payload = payload or {}
    hold_meta = payload.get("hold_metadata", {})
    hold_points = build_hold_points(hold_meta)
    hold_radius = float(hold_meta.get("hold_radius", 0.08))
    debug_marker_names = [str(name) for name in payload.get("debug_marker_names", [])]
    calibration = None
    calibration_path_value = payload.get("calibration_json")
    scale_model_segments = bool(payload.get("scale_model_segments", False))
    if calibration_path_value:
        calibration = load_calibration_json(Path(str(calibration_path_value)))

    xml_text = xml_path.read_text(encoding="utf-8")
    if scale_model_segments:
        xml_text = apply_segment_scaling_template(xml_text, calibration)
    xml_text = add_mocap_and_equality(
        xml_text,
        hold_points=hold_points,
        hold_radius=hold_radius,
        debug_marker_names=debug_marker_names,
    )
    model = mujoco.MjModel.from_xml_string(xml_text)
    data = mujoco.MjData(model)
    return model, data


class PhysicalLoadAnalyzer:
    def __init__(
        self,
        model: mujoco.MjModel,
        data: mujoco.MjData,
        payload: dict[str, Any] | None = None,
    ) -> None:
        payload = payload or {}
        hold_meta = payload.get("hold_metadata", {})

        self.model = model
        self.data = data
        self.hold_meta = hold_meta
        self.hold_points = build_hold_points(hold_meta)
        self.hold_radius = float(hold_meta.get("hold_radius", 0.08))
        self.stress_ratio_threshold = float(payload.get("stress_ratio_threshold", 0.8))
        self.strength_ratio_threshold = float(payload.get("strength_ratio_threshold", 1.0))
        self.strength_consecutive_frames = int(payload.get("strength_consecutive_frames", 5))
        self.support_margin = float(payload.get("support_margin_m", 0.15))
        self.hold_lock_tolerance = float(payload.get("hold_lock_tolerance_m", 0.06))
        self.balance_failure_threshold = float(payload.get("balance_failure_stability_threshold", 0.08))

        self.wall_axis, self.wall_plane_value = resolve_wall_plane(hold_meta, self.hold_points)
        self.wall_axis_index = AXIS_INDEX[self.wall_axis]
        self.support_axes = [idx for idx in range(3) if idx != self.wall_axis_index]

        self.torque_limits = actuator_joint_torque_limits(model)
        self.analysis_joints = [name for name in DEFAULT_ANALYSIS_JOINTS if name in self.torque_limits]
        self.joint_ids = {name: joint_id(model, name) for name in self.analysis_joints}
        self.limb_body_ids = {limb: body_id(model, body_name) for limb, body_name in LIMB_TO_BODY.items()}

        self.mocap_ids: dict[str, int] = {}
        self.eq_ids: dict[str, int] = {}
        for limb, mocap_body in LIMB_TO_MOCAP_BODY.items():
            bid = body_id(model, mocap_body)
            mocap_id = int(model.body_mocapid[bid])
            if mocap_id < 0:
                raise ValueError(f"Body {mocap_body} is not mocap-enabled")
            self.mocap_ids[limb] = mocap_id
            self.eq_ids[limb] = equality_id(model, LIMB_TO_EQUALITY[limb])

        self.eq_active_runtime = hasattr(data, "eq_active")
        self.strength_counts = {name: 0 for name in self.analysis_joints}
        self.peak_joint_metrics = {
            name: {
                "joint_id": name,
                "ratio": 0.0,
                "torque": 0.0,
                "torque_limit": float(self.torque_limits[name]),
                "timestamp_ms": None,
            }
            for name in self.analysis_joints
        }
        self.stress_events: list[dict[str, Any]] = []
        self.failure_type: str | None = None
        self.t_fail_timestamp: int | None = None

        mujoco.mj_forward(model, data)

    def _set_weld_active(self, limb: str, active: bool) -> None:
        eq_id = self.eq_ids[limb]
        if self.eq_active_runtime:
            self.data.eq_active[eq_id] = 1 if active else 0
        if hasattr(self.model, "eq_active0"):
            self.model.eq_active0[eq_id] = 1 if active else 0

    def assign_holds(self, limb_targets_world: dict[str, np.ndarray]) -> dict[str, dict[str, Any]]:
        assignments: dict[str, dict[str, Any]] = {}
        for limb, target in limb_targets_world.items():
            assignment = {
                "active": False,
                "hold_index": None,
                "hold_center": None,
                "target_distance_m": None,
            }
            if self.hold_points.shape[0] == 0:
                assignments[limb] = assignment
                continue

            distances = np.linalg.norm(self.hold_points - target[None, :], axis=1)
            nearest_idx = int(np.argmin(distances))
            nearest_distance = float(distances[nearest_idx])
            if nearest_distance <= self.hold_radius:
                assignment["active"] = True
                assignment["hold_index"] = nearest_idx
                assignment["hold_center"] = self.hold_points[nearest_idx].copy()
                assignment["target_distance_m"] = nearest_distance
            assignments[limb] = assignment
        return assignments

    def _apply_contact_targets(
        self,
        assignments: dict[str, dict[str, Any]],
        limb_targets_world: dict[str, np.ndarray],
    ) -> None:
        for limb, target in limb_targets_world.items():
            assignment = assignments[limb]
            mocap_target = assignment["hold_center"] if assignment["active"] else target
            self.data.mocap_pos[self.mocap_ids[limb]] = np.asarray(mocap_target, dtype=np.float64)
            self._set_weld_active(limb, bool(assignment["active"]))

    def _extract_joint_loads(self, timestamp_ms: int) -> tuple[list[dict[str, Any]], dict[str, float]]:
        joint_loads: list[dict[str, Any]] = []
        ratio_map: dict[str, float] = {}
        for joint_name in self.analysis_joints:
            jid = self.joint_ids[joint_name]
            dofadr = int(self.model.jnt_dofadr[jid])
            torque = float(abs(self.data.qfrc_inverse[dofadr]))
            limit = float(max(self.torque_limits.get(joint_name, 1.0), 1e-6))
            ratio = torque / limit
            ratio_map[joint_name] = ratio

            entry = {
                "joint_id": joint_name,
                "timestamp_ms": timestamp_ms,
                "torque": torque,
                "torque_limit": limit,
                "ratio": ratio,
                "stress_level": "red" if ratio >= 1.0 else "yellow" if ratio >= self.stress_ratio_threshold else "green",
            }
            joint_loads.append(entry)

            if ratio >= self.stress_ratio_threshold:
                self.stress_events.append(dict(entry))

            peak = self.peak_joint_metrics[joint_name]
            if ratio > float(peak["ratio"]):
                peak["ratio"] = ratio
                peak["torque"] = torque
                peak["timestamp_ms"] = timestamp_ms

            if ratio >= self.strength_ratio_threshold:
                self.strength_counts[joint_name] += 1
            else:
                self.strength_counts[joint_name] = 0

        joint_loads.sort(key=lambda item: float(item["ratio"]), reverse=True)
        return joint_loads, ratio_map

    def _effective_contacts(
        self,
        assignments: dict[str, dict[str, Any]],
    ) -> tuple[list[dict[str, Any]], bool]:
        active_contacts: list[dict[str, Any]] = []
        effective = True
        for limb, assignment in assignments.items():
            if not assignment["active"]:
                continue
            hold_center = np.asarray(assignment["hold_center"], dtype=np.float64)
            body_pos = self.data.xpos[self.limb_body_ids[limb]].copy()
            body_error = float(np.linalg.norm(body_pos - hold_center))
            active_contacts.append(
                {
                    "limb": limb,
                    "hold_index": int(assignment["hold_index"]),
                    "hold_center": hold_center.tolist(),
                    "target_distance_m": float(assignment["target_distance_m"]),
                    "body_error_m": body_error,
                    "weld_active": True,
                }
            )
            effective = effective and body_error <= self.hold_lock_tolerance
        return active_contacts, bool(active_contacts) and effective

    def analyze_frame(
        self,
        timestamp_ms: int,
        limb_targets_world: dict[str, np.ndarray],
    ) -> dict[str, Any]:
        assignments = self.assign_holds(limb_targets_world)
        self._apply_contact_targets(assignments, limb_targets_world)

        qvel_backup = self.data.qvel.copy()
        qacc_backup = self.data.qacc.copy()
        try:
            self.data.qvel[:] = 0.0
            self.data.qacc[:] = 0.0
            mujoco.mj_forward(self.model, self.data)
            mujoco.mj_inverse(self.model, self.data)

            joint_loads, ratio_map = self._extract_joint_loads(timestamp_ms)
            top_stressed = joint_loads[:3]
            joints_over_80 = [item for item in joint_loads if float(item["ratio"]) >= self.stress_ratio_threshold]

            active_contacts, effective_contact = self._effective_contacts(assignments)
            contact_points = (
                np.array([contact["hold_center"] for contact in active_contacts], dtype=np.float64)
                if active_contacts
                else np.zeros((0, 3), dtype=np.float64)
            )

            com = compute_com(self.model, self.data)
            com_support = com[self.support_axes]
            contact_support = contact_points[:, self.support_axes]
            com_stability = support_stability_score(com_support, contact_support, margin=self.support_margin)
            wall_distance = float(abs(com[self.wall_axis_index] - self.wall_plane_value))
            limb_reach_error_m = {
                limb: float(np.linalg.norm(self.data.xpos[self.limb_body_ids[limb]] - np.asarray(target, dtype=np.float64)))
                for limb, target in limb_targets_world.items()
            }

            strength_failure_joint = None
            for joint_name, count in self.strength_counts.items():
                if count >= self.strength_consecutive_frames:
                    strength_failure_joint = joint_name
                    break

            frame_failure_type = None
            if strength_failure_joint is not None:
                frame_failure_type = "STRENGTH_LIMIT"
                if self.failure_type is None:
                    self.failure_type = frame_failure_type
                    self.t_fail_timestamp = timestamp_ms
            elif active_contacts and com_stability < self.balance_failure_threshold:
                frame_failure_type = "BALANCE_DISRUPTION"
                if self.failure_type is None:
                    self.failure_type = frame_failure_type
                    self.t_fail_timestamp = timestamp_ms

            return {
                "timestamp_ms": timestamp_ms,
                "active_hold_contacts": active_contacts,
                "effective_contact": effective_contact,
                "contact_count": len(active_contacts),
                "com_position": com.tolist(),
                "com_wall_distance_m": wall_distance,
                "com_stability": com_stability,
                "joint_loads": joint_loads,
                "joint_ratio_map": ratio_map,
                "joints_over_threshold": joints_over_80,
                "top_stressed_joints": top_stressed,
                "limb_reach_error_m": limb_reach_error_m,
                "strength_failure_active": strength_failure_joint is not None,
                "strength_failure_joint": strength_failure_joint,
                "failure_type": frame_failure_type,
            }
        finally:
            self.data.qvel[:] = qvel_backup
            self.data.qacc[:] = qacc_backup
            mujoco.mj_forward(self.model, self.data)

    def peak_torque_joints(self, top_k: int = 8) -> list[dict[str, Any]]:
        peaks = [value for value in self.peak_joint_metrics.values() if value["timestamp_ms"] is not None]
        peaks.sort(key=lambda item: float(item["ratio"]), reverse=True)
        return peaks[:top_k]


def apply_pose_to_model(
    model: mujoco.MjModel,
    data: mujoco.MjData,
    qpos_adr: dict[str, int],
    joint_limits: dict[str, tuple[float, float]],
    joint_targets: dict[str, float],
    mapped_points: dict[str, np.ndarray],
    torso_from_pelvis: np.ndarray,
) -> dict[str, np.ndarray]:
    root_pos = mapped_points["hip_mid"] + torso_from_pelvis
    root_quat = quat_from_axes(mapped_points["axis_forward"], mapped_points["axis_left"], mapped_points["axis_up"])

    data.qpos[0:3] = root_pos
    data.qpos[3:7] = root_quat
    for joint_name, value in joint_targets.items():
        lo, hi = joint_limits[joint_name]
        data.qpos[qpos_adr[joint_name]] = float(np.clip(value, lo, hi))
    data.qvel[:] = 0.0
    data.qacc[:] = 0.0
    mujoco.mj_forward(model, data)

    return {
        "left_wrist": mapped_points.get("left_palm_contact", mapped_points.get("left_hand_tip", mapped_points["left_wrist"])),
        "right_wrist": mapped_points.get("right_palm_contact", mapped_points.get("right_hand_tip", mapped_points["right_wrist"])),
        "left_ankle": mapped_points.get("left_forefoot_contact", mapped_points.get("left_toe", mapped_points["left_ankle"])),
        "right_ankle": mapped_points.get("right_forefoot_contact", mapped_points.get("right_toe", mapped_points["right_ankle"])),
    }


def mapped_points_from_local(points: dict[str, np.ndarray], scale: float, offset: np.ndarray) -> dict[str, np.ndarray]:
    return {
        key: value.copy() if key.startswith("axis_") or key == "quat_targets" else value * scale + offset
        for key, value in points.items()
    }


def run_worker(
    input_json: Path,
    xml_path: Path,
    output_json: Path,
    calibration_json: Path | None = None,
) -> dict[str, Any]:
    payload = json.loads(input_json.read_text(encoding="utf-8-sig"))
    frames = payload.get("frames", [])
    if not frames:
        raise ValueError("Input JSON must include non-empty frames")

    if calibration_json is not None:
        payload["calibration_json"] = str(calibration_json.resolve())
    elif payload.get("calibration_json"):
        calibration_path = Path(str(payload["calibration_json"]))
        if not calibration_path.is_absolute():
            calibration_path = (input_json.parent / calibration_path).resolve()
        payload["calibration_json"] = str(calibration_path)

    swap_lr = bool(payload.get("swap_left_right", False))
    biometrics = payload.get("user_biometrics", {})
    user_height = float(biometrics.get("height_m", 1.75))
    calibration = load_calibration_json(Path(str(payload["calibration_json"]))) if payload.get("calibration_json") else None

    model, data = build_analysis_model(xml_path, payload)
    analyzer = PhysicalLoadAnalyzer(model, data, payload)

    joint_ids = {name: joint_id(model, name) for name in DEFAULT_ANALYSIS_JOINTS}
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

    first_landmarks = parse_landmarks(frames[0])
    first_mapped_local = np.array([mp_to_mj(p) for p in first_landmarks], dtype=np.float64)
    first_shoulder_width_local = float(np.linalg.norm(first_mapped_local[LEFT_SHOULDER] - first_mapped_local[RIGHT_SHOULDER]))
    first_segment_lengths_local = segment_lengths_local_from_calibration(calibration, first_shoulder_width_local)
    first_mapped_local = apply_inverse_depth_correction_to_mapped(
        first_mapped_local,
        first_segment_lengths_local,
        swap_lr=swap_lr,
    )
    _, first_points_local = _extract_joint_pose_targets_from_mapped(first_mapped_local, swap_lr=swap_lr)
    shoulder_width = float(np.linalg.norm(first_points_local["left_shoulder"] - first_points_local["right_shoulder"]))
    shoulder_scale = model_shoulder / max(shoulder_width, 1e-6)
    anthropometric_scale = user_height / 1.75
    scale = shoulder_scale if calibration is not None else shoulder_scale * anthropometric_scale
    offset = pelvis_anchor - first_points_local["hip_mid"] * scale

    frame_metrics: list[dict[str, Any]] = []
    for index, frame in enumerate(frames):
        timestamp_ms = int(frame.get("timestamp_ms", index * 33))
        landmarks_mp = parse_landmarks(frame)
        mapped_local = np.array([mp_to_mj(p) for p in landmarks_mp], dtype=np.float64)
        shoulder_width_local = float(np.linalg.norm(mapped_local[LEFT_SHOULDER] - mapped_local[RIGHT_SHOULDER]))
        segment_lengths_local = segment_lengths_local_from_calibration(calibration, shoulder_width_local)
        mapped_local = apply_inverse_depth_correction_to_mapped(
            mapped_local,
            segment_lengths_local,
            swap_lr=swap_lr,
        )
        joint_targets, points_local = _extract_joint_pose_targets_from_mapped(mapped_local, swap_lr=swap_lr)
        mapped_points = mapped_points_from_local(points_local, scale=scale, offset=offset)
        limb_targets_world = apply_pose_to_model(
            model=model,
            data=data,
            qpos_adr=qpos_adr,
            joint_limits=joint_limits,
            joint_targets=joint_targets,
            mapped_points=mapped_points,
            torso_from_pelvis=torso_from_pelvis,
        )
        physical_metrics = analyzer.analyze_frame(timestamp_ms=timestamp_ms, limb_targets_world=limb_targets_world)
        physical_metrics["frame_index"] = index
        frame_metrics.append(physical_metrics)

    stability_score = float(np.mean([frame["com_stability"] for frame in frame_metrics])) if frame_metrics else 0.0
    contact_efficiency = float(
        np.mean([1.0 if frame["effective_contact"] else 0.0 for frame in frame_metrics])
    ) if frame_metrics else 0.0
    reach_values = [
        float(value)
        for frame in frame_metrics
        for value in frame.get("limb_reach_error_m", {}).values()
    ]
    reach_error_summary = {
        "mean_reach_error_m": float(np.mean(reach_values)) if reach_values else 0.0,
        "max_reach_error_m": float(np.max(reach_values)) if reach_values else 0.0,
    }

    output = {
        "stability_score": stability_score,
        "contact_efficiency": contact_efficiency,
        "reach_error_summary": reach_error_summary,
        "joint_stress_log": analyzer.stress_events,
        "peak_torque_joints": analyzer.peak_torque_joints(),
        "frame_metrics": frame_metrics,
        "t_fail_timestamp": analyzer.t_fail_timestamp,
        "failure_type": analyzer.failure_type,
        "meta": {
            "frames": len(frame_metrics),
            "scale_factor": scale,
            "mocap_weld_enabled": True,
            "stress_ratio_threshold": analyzer.stress_ratio_threshold,
            "strength_ratio_threshold": analyzer.strength_ratio_threshold,
            "strength_consecutive_frames": analyzer.strength_consecutive_frames,
            "hold_radius_m": analyzer.hold_radius,
            "wall_axis": analyzer.wall_axis,
            "wall_plane_value": analyzer.wall_plane_value,
            "support_axes": analyzer.support_axes,
            "hold_lock_tolerance_m": analyzer.hold_lock_tolerance,
            "calibration_json": payload.get("calibration_json"),
            "scale_model_segments": bool(payload.get("scale_model_segments", False)),
        },
    }

    output_json.write_text(json.dumps(output, indent=2), encoding="utf-8")
    return output


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Physical load analysis worker with hold weld constraints")
    parser.add_argument("--input", required=True, help="Input JSON path")
    parser.add_argument("--xml", default=str(Path(__file__).with_name("humanoid.xml")), help="Base humanoid XML path")
    parser.add_argument("--output", default=str(Path(__file__).with_name("analysis_output.json")), help="Output JSON path")
    parser.add_argument("--calibration-json", help="Calibration JSON generated from T-pose image")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    result = run_worker(
        Path(args.input),
        Path(args.xml),
        Path(args.output),
        calibration_json=Path(args.calibration_json).resolve() if args.calibration_json else None,
    )
    summary = {
        "stability_score": result["stability_score"],
        "contact_efficiency": result["contact_efficiency"],
        "joint_stress_events": len(result["joint_stress_log"]),
        "peak_joint": result["peak_torque_joints"][0]["joint_id"] if result["peak_torque_joints"] else None,
        "failure_type": result["failure_type"],
        "t_fail_timestamp": result["t_fail_timestamp"],
    }
    print("[OK] Physical analysis complete")
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()

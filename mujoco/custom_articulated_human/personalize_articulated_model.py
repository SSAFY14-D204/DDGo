from __future__ import annotations

import argparse
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

import cv2
import mediapipe as mp
import numpy as np

ROOT = Path(__file__).resolve().parent
CUSTOM_SKELETON_ROOT = ROOT.parent / "custom_skeleton_verify"
sys.path.insert(0, str(CUSTOM_SKELETON_ROOT))

from mediapipe_custom_skeleton_verify import MetricSkeletonMapper, make_landmarker  # noqa: E402
from physics_worker import load_calibration_json  # noqa: E402

DEFAULT_INPUT_VIDEO = ROOT.parent / "video" / "주황.mp4"


BODY_MASS_FRACTIONS = {
    "pelvis": 0.0800,
    "torso_base": 0.1275,
    "thorax": 0.2221,
    "head": 0.0694,
    "left_shoulder_mount": 0.00125,
    "right_shoulder_mount": 0.00125,
    "left_upper_arm": 0.0271,
    "right_upper_arm": 0.0271,
    "left_elbow": 0.0162,
    "right_elbow": 0.0162,
    "left_hand": 0.0061,
    "right_hand": 0.0061,
    "left_hip_mount": 0.00125,
    "right_hip_mount": 0.00125,
    "left_thigh": 0.1416,
    "right_thigh": 0.1416,
    "left_knee": 0.0433,
    "right_knee": 0.0433,
    "left_foot": 0.0137,
    "right_foot": 0.0137,
}


def normalize(v: np.ndarray, eps: float = 1e-8) -> np.ndarray:
    arr = np.asarray(v, dtype=np.float64)
    norm = float(np.linalg.norm(arr))
    if norm < eps:
        return np.zeros_like(arr)
    return arr / norm


def root_rotation_from_targets(points: dict[str, np.ndarray]) -> np.ndarray:
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
    return rot


def sample_frame_indices(frame_count: int, sample_count: int) -> list[int]:
    if frame_count <= 0:
        return []
    if sample_count <= 1:
        return [0]
    return sorted({int(round(i * (frame_count - 1) / (sample_count - 1))) for i in range(sample_count)})


def body_local(rot: np.ndarray, origin: np.ndarray, point: np.ndarray) -> np.ndarray:
    return rot.T @ (np.asarray(point, dtype=np.float64) - np.asarray(origin, dtype=np.float64))


def robust_scalar(calibration: dict[str, object], left_key: str, right_key: str, fallback_key: str) -> float:
    values: list[float] = []
    if left_key in calibration:
        values.append(float(calibration[left_key]))
    if right_key in calibration:
        values.append(float(calibration[right_key]))
    if values:
        return float(np.mean(values))
    if fallback_key in calibration:
        return float(calibration[fallback_key])
    raise KeyError(f"Missing calibration keys: {left_key}, {right_key}, {fallback_key}")


def find_elem(root: ET.Element, tag: str, name: str) -> ET.Element:
    for elem in root.iter(tag):
        if elem.get("name") == name:
            return elem
    raise KeyError(f"Unable to find <{tag} name=\"{name}\"> in XML")


def parse_vec(text: str) -> np.ndarray:
    return np.asarray([float(token) for token in text.split()], dtype=np.float64)


def fmt_vec(vec: np.ndarray) -> str:
    return " ".join(f"{float(value):.6f}" for value in np.asarray(vec, dtype=np.float64))


def box_diaginertia(mass: float, half_sizes: np.ndarray) -> np.ndarray:
    hx, hy, hz = np.asarray(half_sizes, dtype=np.float64)
    diag = (float(mass) / 3.0) * np.array(
        [
            hy * hy + hz * hz,
            hx * hx + hz * hz,
            hx * hx + hy * hy,
        ],
        dtype=np.float64,
    )
    return np.maximum(diag, 1e-6)


def ensure_inertial(body: ET.Element) -> ET.Element:
    inertial = body.find("inertial")
    if inertial is None:
        inertial = ET.Element("inertial")
        body.insert(0, inertial)
    return inertial


def set_body_inertial(body: ET.Element, pos: np.ndarray, mass: float, half_sizes: np.ndarray) -> None:
    inertial = ensure_inertial(body)
    inertial.set("pos", fmt_vec(pos))
    inertial.set("mass", f"{float(mass):.6f}")
    inertial.set("diaginertia", fmt_vec(box_diaginertia(float(mass), np.asarray(half_sizes, dtype=np.float64))))


def compute_body_masses_kg(total_mass_kg: float) -> dict[str, float]:
    return {name: float(total_mass_kg) * fraction for name, fraction in BODY_MASS_FRACTIONS.items()}


def collect_target_metrics(
    video_path: Path,
    task_path: Path,
    calibration: dict[str, object],
    sample_count: int,
) -> dict[str, object]:
    cap = cv2.VideoCapture(str(video_path))
    if not cap.isOpened():
        raise FileNotFoundError(f"Unable to open video: {video_path}")

    frame_count = int(cap.get(cv2.CAP_PROP_FRAME_COUNT) or 0)
    frame_indices = sample_frame_indices(frame_count, sample_count)
    mapper = MetricSkeletonMapper(calibration)
    landmarker = make_landmarker(task_path)

    torso_lengths: list[float] = []
    shoulder_widths: list[float] = []
    hip_widths: list[float] = []
    head_offsets_local: list[np.ndarray] = []
    left_shoulder_offsets_local: list[np.ndarray] = []
    right_shoulder_offsets_local: list[np.ndarray] = []
    left_hip_offsets_local: list[np.ndarray] = []
    right_hip_offsets_local: list[np.ndarray] = []
    hand_reaches: list[float] = []
    foot_reaches: list[float] = []
    left_foot_vectors_local: list[np.ndarray] = []
    right_foot_vectors_local: list[np.ndarray] = []
    left_heel_vectors_local: list[np.ndarray] = []
    right_heel_vectors_local: list[np.ndarray] = []

    detected_frames = 0
    for frame_idx in frame_indices:
        cap.set(cv2.CAP_PROP_POS_FRAMES, int(frame_idx))
        ok, frame_bgr = cap.read()
        if not ok:
            continue
        timestamp_ms = int(round((frame_idx / max(cap.get(cv2.CAP_PROP_FPS), 1.0)) * 1000.0))
        frame_rgb = cv2.cvtColor(frame_bgr, cv2.COLOR_BGR2RGB)
        mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=frame_rgb)
        result = landmarker.detect_for_video(mp_image, timestamp_ms)
        if not result.pose_world_landmarks:
            continue

        points = mapper.map_frame(result.pose_world_landmarks[0])
        rot = root_rotation_from_targets(points)
        pelvis = np.asarray(points["pelvis"], dtype=np.float64)
        thorax = np.asarray(points["thorax"], dtype=np.float64)

        torso_lengths.append(float(np.linalg.norm(thorax - pelvis)))
        shoulder_widths.append(float(np.linalg.norm(points["left_shoulder"] - points["right_shoulder"])))
        hip_widths.append(float(np.linalg.norm(points["left_hip"] - points["right_hip"])))
        head_offsets_local.append(body_local(rot, thorax, points["head"]))
        left_shoulder_offsets_local.append(body_local(rot, thorax, points["left_shoulder"]))
        right_shoulder_offsets_local.append(body_local(rot, thorax, points["right_shoulder"]))
        left_hip_offsets_local.append(body_local(rot, pelvis, points["left_hip"]))
        right_hip_offsets_local.append(body_local(rot, pelvis, points["right_hip"]))
        left_foot_vectors_local.append(body_local(rot, points["left_ankle"], points["left_foot"]))
        right_foot_vectors_local.append(body_local(rot, points["right_ankle"], points["right_foot"]))
        left_heel_vectors_local.append(body_local(rot, points["left_ankle"], points["left_heel"]))
        right_heel_vectors_local.append(body_local(rot, points["right_ankle"], points["right_heel"]))
        hand_reaches.append(float(np.linalg.norm(points["left_hand"] - points["left_elbow"])))
        hand_reaches.append(float(np.linalg.norm(points["right_hand"] - points["right_elbow"])))
        foot_reaches.append(float(np.linalg.norm(points["left_foot"] - points["left_ankle"])))
        foot_reaches.append(float(np.linalg.norm(points["right_foot"] - points["right_ankle"])))
        detected_frames += 1

    cap.release()
    landmarker.close()

    if detected_frames == 0:
        raise RuntimeError("Failed to detect any target skeleton frames for personalization")

    metrics = {
        "detected_frames": detected_frames,
        "torso_length_target_m": float(np.median(torso_lengths)),
        "shoulder_width_target_m": float(np.median(shoulder_widths)),
        "hip_width_target_m": float(np.median(hip_widths)),
        "head_offset_local_m": np.median(np.asarray(head_offsets_local, dtype=np.float64), axis=0).tolist(),
        "left_shoulder_offset_local_m": np.median(np.asarray(left_shoulder_offsets_local, dtype=np.float64), axis=0).tolist(),
        "right_shoulder_offset_local_m": np.median(np.asarray(right_shoulder_offsets_local, dtype=np.float64), axis=0).tolist(),
        "left_hip_offset_local_m": np.median(np.asarray(left_hip_offsets_local, dtype=np.float64), axis=0).tolist(),
        "right_hip_offset_local_m": np.median(np.asarray(right_hip_offsets_local, dtype=np.float64), axis=0).tolist(),
        "left_foot_vector_local_m": np.median(np.asarray(left_foot_vectors_local, dtype=np.float64), axis=0).tolist(),
        "right_foot_vector_local_m": np.median(np.asarray(right_foot_vectors_local, dtype=np.float64), axis=0).tolist(),
        "left_heel_vector_local_m": np.median(np.asarray(left_heel_vectors_local, dtype=np.float64), axis=0).tolist(),
        "right_heel_vector_local_m": np.median(np.asarray(right_heel_vectors_local, dtype=np.float64), axis=0).tolist(),
        "hand_reach_target_m": float(np.median(hand_reaches)),
        "foot_reach_target_m": float(np.median(foot_reaches)),
    }
    return metrics


def build_personalization_metrics(calibration: dict[str, object], target_metrics: dict[str, object]) -> dict[str, float]:
    upper_arm = robust_scalar(calibration, "left_upper_arm_m", "right_upper_arm_m", "upper_arm_m")
    forearm = robust_scalar(calibration, "left_forearm_m", "right_forearm_m", "forearm_m")
    thigh = robust_scalar(calibration, "left_thigh_m", "right_thigh_m", "thigh_m")
    shin = robust_scalar(calibration, "left_shin_m", "right_shin_m", "shin_m")
    shoulder_width = float(calibration.get("shoulder_width_m", target_metrics["shoulder_width_target_m"]))
    torso_length = float(calibration.get("torso_length_m", target_metrics["torso_length_target_m"]))
    hip_width = float(calibration.get("hip_width_m", target_metrics["hip_width_target_m"]))

    hand_extension = max(
        0.05,
        min(0.18, float(target_metrics["hand_reach_target_m"]) - forearm),
    )
    return {
        "body_mass_kg": float(calibration.get("body_mass_kg", 80.0)),
        "upper_arm_m": upper_arm,
        "forearm_m": forearm,
        "thigh_m": thigh,
        "shin_m": shin,
        "shoulder_width_m": shoulder_width,
        "torso_length_m": torso_length,
        "hip_width_m": hip_width,
        "hand_extension_m": hand_extension,
    }


def apply_personalization(template_xml: Path, output_xml: Path, metrics: dict[str, float], target_metrics: dict[str, object]) -> None:
    tree = ET.parse(template_xml)
    root = tree.getroot()

    pelvis = find_elem(root, "body", "pelvis")
    torso_base = find_elem(root, "body", "torso_base")
    thorax = find_elem(root, "body", "thorax")
    head = find_elem(root, "body", "head")
    left_shoulder_mount = find_elem(root, "body", "left_shoulder_mount")
    right_shoulder_mount = find_elem(root, "body", "right_shoulder_mount")
    left_upper_arm = find_elem(root, "body", "left_upper_arm")
    right_upper_arm = find_elem(root, "body", "right_upper_arm")
    left_elbow = find_elem(root, "body", "left_elbow")
    right_elbow = find_elem(root, "body", "right_elbow")
    left_hand = find_elem(root, "body", "left_hand")
    right_hand = find_elem(root, "body", "right_hand")
    left_hip_mount = find_elem(root, "body", "left_hip_mount")
    right_hip_mount = find_elem(root, "body", "right_hip_mount")
    left_thigh = find_elem(root, "body", "left_thigh")
    right_thigh = find_elem(root, "body", "right_thigh")
    left_knee = find_elem(root, "body", "left_knee")
    right_knee = find_elem(root, "body", "right_knee")
    left_ankle = find_elem(root, "body", "left_ankle")
    right_ankle = find_elem(root, "body", "right_ankle")
    left_foot = find_elem(root, "body", "left_foot")
    right_foot = find_elem(root, "body", "right_foot")

    lower_torso_geom = find_elem(root, "geom", "lower_torso_geom")
    thorax_geom = find_elem(root, "geom", "thorax_geom")
    neck_geom = find_elem(root, "geom", "neck_geom")
    left_upper_arm_geom = find_elem(root, "geom", "left_upper_arm_geom")
    right_upper_arm_geom = find_elem(root, "geom", "right_upper_arm_geom")
    left_forearm_geom = find_elem(root, "geom", "left_forearm_geom")
    right_forearm_geom = find_elem(root, "geom", "right_forearm_geom")
    left_hand_geom = find_elem(root, "geom", "left_hand_geom")
    right_hand_geom = find_elem(root, "geom", "right_hand_geom")
    left_thigh_geom = find_elem(root, "geom", "left_thigh_geom")
    right_thigh_geom = find_elem(root, "geom", "right_thigh_geom")
    left_shin_geom = find_elem(root, "geom", "left_shin_geom")
    right_shin_geom = find_elem(root, "geom", "right_shin_geom")
    left_foot_geom = find_elem(root, "geom", "left_foot_geom")
    right_foot_geom = find_elem(root, "geom", "right_foot_geom")

    left_hand_site = find_elem(root, "site", "left_hand_site")
    right_hand_site = find_elem(root, "site", "right_hand_site")
    left_foot_site = find_elem(root, "site", "left_foot_site")
    right_foot_site = find_elem(root, "site", "right_foot_site")
    left_heel_site = find_elem(root, "site", "left_heel_site")
    right_heel_site = find_elem(root, "site", "right_heel_site")
    left_foot_dorsal_site = find_elem(root, "site", "left_foot_dorsal_site")
    right_foot_dorsal_site = find_elem(root, "site", "right_foot_dorsal_site")
    left_thigh_lateral_site = find_elem(root, "site", "left_thigh_lateral_site")
    right_thigh_lateral_site = find_elem(root, "site", "right_thigh_lateral_site")
    left_elbow_pole = find_elem(root, "site", "left_elbow_pole_site")
    right_elbow_pole = find_elem(root, "site", "right_elbow_pole_site")
    left_knee_pole = find_elem(root, "site", "left_knee_pole_site")
    right_knee_pole = find_elem(root, "site", "right_knee_pole_site")

    baseline_torso_total = parse_vec(torso_base.get("pos"))[2] + parse_vec(thorax.get("pos"))[2]
    torso_base_ratio = parse_vec(torso_base.get("pos"))[2] / baseline_torso_total
    torso_total = metrics["torso_length_m"]
    torso_base_z = torso_total * torso_base_ratio
    thorax_z = torso_total - torso_base_z
    torso_base.set("pos", fmt_vec(np.array([0.0, 0.0, torso_base_z])))
    thorax.set("pos", fmt_vec(np.array([0.0, 0.0, thorax_z])))

    lower_torso_fromto = parse_vec(lower_torso_geom.get("fromto"))
    lower_torso_geom.set(
        "fromto",
        fmt_vec(np.array([
            0.0,
            0.0,
            lower_torso_fromto[2] / baseline_torso_total * torso_total,
            0.0,
            0.0,
            lower_torso_fromto[5] / baseline_torso_total * torso_total,
        ])),
    )
    thorax_fromto = parse_vec(thorax_geom.get("fromto"))
    thorax_geom.set(
        "fromto",
        fmt_vec(np.array([
            0.0,
            0.0,
            thorax_fromto[2] / baseline_torso_total * torso_total,
            0.0,
            0.0,
            thorax_fromto[5] / baseline_torso_total * torso_total,
        ])),
    )
    neck_fromto = parse_vec(neck_geom.get("fromto"))
    neck_geom.set(
        "fromto",
        fmt_vec(np.array([
            0.0,
            0.0,
            neck_fromto[2] / baseline_torso_total * torso_total,
            0.0,
            0.0,
            neck_fromto[5] / baseline_torso_total * torso_total,
        ])),
    )
    lower_torso_geom.set("size", f"{max(0.082, min(0.110, 0.22 * metrics['shoulder_width_m'])):.6f}")
    thorax_geom.set("size", f"{max(0.078, min(0.105, 0.24 * metrics['shoulder_width_m'])):.6f}")
    neck_geom.set("size", f"{max(0.035, min(0.055, 0.11 * metrics['shoulder_width_m'])):.6f}")

    head_offset = np.asarray(target_metrics["head_offset_local_m"], dtype=np.float64)
    head.set("pos", fmt_vec(np.array([float(head_offset[0]), 0.0, max(0.14, float(head_offset[2]))])))

    shoulder_half = metrics["shoulder_width_m"] * 0.5
    left_shoulder_offset = np.asarray(target_metrics["left_shoulder_offset_local_m"], dtype=np.float64)
    right_shoulder_offset = np.asarray(target_metrics["right_shoulder_offset_local_m"], dtype=np.float64)
    left_shoulder_mount.set(
        "pos",
        fmt_vec(np.array([float(left_shoulder_offset[0]), shoulder_half, max(0.04, float(left_shoulder_offset[2]))])),
    )
    right_shoulder_mount.set(
        "pos",
        fmt_vec(np.array([float(right_shoulder_offset[0]), -shoulder_half, max(0.04, float(right_shoulder_offset[2]))])),
    )

    upper_arm = metrics["upper_arm_m"]
    forearm = metrics["forearm_m"]
    hand_extension = metrics["hand_extension_m"]
    hand_scale = hand_extension / 0.10

    left_upper_arm_geom.set("fromto", fmt_vec(np.array([0.0, 0.0, 0.0, upper_arm, 0.0, 0.0])))
    right_upper_arm_geom.set("fromto", fmt_vec(np.array([0.0, 0.0, 0.0, upper_arm, 0.0, 0.0])))
    left_elbow.set("pos", fmt_vec(np.array([upper_arm, 0.0, 0.0])))
    right_elbow.set("pos", fmt_vec(np.array([upper_arm, 0.0, 0.0])))

    left_forearm_geom.set("fromto", fmt_vec(np.array([0.0, 0.0, 0.0, forearm, 0.0, 0.0])))
    right_forearm_geom.set("fromto", fmt_vec(np.array([0.0, 0.0, 0.0, forearm, 0.0, 0.0])))
    left_hand.set("pos", fmt_vec(np.array([forearm, 0.0, 0.0])))
    right_hand.set("pos", fmt_vec(np.array([forearm, 0.0, 0.0])))
    left_hand_geom.set("fromto", fmt_vec(np.array([0.0, 0.0, 0.0, hand_extension, 0.0, 0.0])))
    right_hand_geom.set("fromto", fmt_vec(np.array([0.0, 0.0, 0.0, hand_extension, 0.0, 0.0])))
    left_hand_site.set("pos", fmt_vec(np.array([hand_extension, 0.0, 0.0])))
    right_hand_site.set("pos", fmt_vec(np.array([hand_extension, 0.0, 0.0])))
    elbow_pole_y = max(0.045, min(0.085, 0.18 * (upper_arm + forearm)))
    left_elbow_pole.set("pos", fmt_vec(np.array([0.0, elbow_pole_y, 0.0])))
    right_elbow_pole.set("pos", fmt_vec(np.array([0.0, elbow_pole_y, 0.0])))

    hip_half = metrics["hip_width_m"] * 0.5
    left_hip_offset = np.asarray(target_metrics["left_hip_offset_local_m"], dtype=np.float64)
    right_hip_offset = np.asarray(target_metrics["right_hip_offset_local_m"], dtype=np.float64)
    hip_center_x = float(0.5 * (left_hip_offset[0] + right_hip_offset[0]))
    hip_center_z = float(0.5 * (left_hip_offset[2] + right_hip_offset[2]))
    left_hip_mount.set("pos", fmt_vec(np.array([hip_center_x, hip_half, hip_center_z])))
    right_hip_mount.set("pos", fmt_vec(np.array([hip_center_x, -hip_half, hip_center_z])))

    thigh = metrics["thigh_m"]
    shin = metrics["shin_m"]
    left_thigh_geom.set("fromto", fmt_vec(np.array([0.0, 0.0, 0.0, 0.0, 0.0, -thigh])))
    right_thigh_geom.set("fromto", fmt_vec(np.array([0.0, 0.0, 0.0, 0.0, 0.0, -thigh])))
    left_thigh_lateral_site.set("pos", fmt_vec(np.array([0.0, 0.055, -0.46 * thigh])))
    right_thigh_lateral_site.set("pos", fmt_vec(np.array([0.0, -0.055, -0.46 * thigh])))
    left_knee.set("pos", fmt_vec(np.array([0.0, 0.0, -thigh])))
    right_knee.set("pos", fmt_vec(np.array([0.0, 0.0, -thigh])))
    left_shin_geom.set("fromto", fmt_vec(np.array([0.0, 0.0, 0.0, 0.0, 0.0, -shin])))
    right_shin_geom.set("fromto", fmt_vec(np.array([0.0, 0.0, 0.0, 0.0, 0.0, -shin])))
    left_ankle.set("pos", fmt_vec(np.array([0.0, 0.0, -shin])))
    right_ankle.set("pos", fmt_vec(np.array([0.0, 0.0, -shin])))
    knee_pole_x = max(0.045, min(0.090, 0.16 * (thigh + shin)))
    left_knee_pole.set("pos", fmt_vec(np.array([knee_pole_x, 0.0, 0.0])))
    right_knee_pole.set("pos", fmt_vec(np.array([knee_pole_x, 0.0, 0.0])))

    def apply_foot_layout(
        foot_body: ET.Element,
        foot_geom: ET.Element,
        heel_site: ET.Element,
        foot_site: ET.Element,
        foot_dorsal_site: ET.Element,
        heel_vec_local: np.ndarray,
        foot_vec_local: np.ndarray,
    ) -> None:
        toe_vec = np.asarray(foot_vec_local, dtype=np.float64)
        heel_vec = np.asarray(heel_vec_local, dtype=np.float64)
        if float(np.linalg.norm(toe_vec)) < 1e-6:
            toe_vec = np.array([0.16, 0.0, -0.03], dtype=np.float64)
        if float(np.linalg.norm(heel_vec)) < 1e-6:
            heel_vec = np.array([-0.05, 0.0, 0.015], dtype=np.float64)

        foot_axis = toe_vec - heel_vec
        foot_len = float(np.linalg.norm(foot_axis))
        if foot_len < 1e-6:
            heel_vec = np.array([-0.05, 0.0, 0.015], dtype=np.float64)
            toe_vec = np.array([0.16, 0.0, -0.03], dtype=np.float64)
            foot_axis = toe_vec - heel_vec
            foot_len = float(np.linalg.norm(foot_axis))
        foot_len = float(np.clip(foot_len, 0.18, 0.28))
        horizontal = np.array([foot_axis[0], foot_axis[1], 0.0], dtype=np.float64)
        if float(np.linalg.norm(horizontal)) < 1e-6:
            direction_xy = np.array([1.0, 0.0, 0.0], dtype=np.float64)
        else:
            forward_x = max(abs(float(horizontal[0])), 0.35 * foot_len)
            direction_xy = normalize(np.array([forward_x, float(horizontal[1]), 0.0], dtype=np.float64))
        heel_abs = direction_xy * (-0.28 * foot_len) + np.array([0.0, 0.0, float(heel_vec[2])], dtype=np.float64)
        toe_abs = direction_xy * (0.72 * foot_len) + np.array([0.0, 0.0, float(toe_vec[2])], dtype=np.float64)
        lateral_center = 0.5 * (float(heel_vec[1]) + float(toe_vec[1]))
        heel_abs[1] += lateral_center
        toe_abs[1] += lateral_center

        direction = normalize(toe_abs - heel_abs)
        dorsal = np.array([0.0, 0.0, 1.0], dtype=np.float64)
        dorsal = dorsal - float(np.dot(dorsal, direction)) * direction
        if float(np.linalg.norm(dorsal)) < 1e-6:
            dorsal = np.array([0.0, 1.0, 0.0], dtype=np.float64)
            dorsal = dorsal - float(np.dot(dorsal, direction)) * direction
        dorsal = normalize(dorsal)
        if float(np.dot(dorsal, np.array([0.0, 0.0, 1.0], dtype=np.float64))) < 0.0:
            dorsal = -dorsal

        body_pos = 0.55 * heel_abs + 0.18 * toe_abs
        heel_local = heel_abs - body_pos
        toe_local = toe_abs - body_pos
        foot_mid = 0.5 * (heel_local + toe_local)
        dorsal_pos = foot_mid + dorsal * 0.04

        foot_body.set("pos", fmt_vec(body_pos))
        foot_geom.set("fromto", fmt_vec(np.concatenate([heel_local, toe_local])))
        heel_site.set("pos", fmt_vec(heel_local))
        foot_site.set("pos", fmt_vec(toe_local))
        foot_dorsal_site.set("pos", fmt_vec(dorsal_pos))

    apply_foot_layout(
        left_foot,
        left_foot_geom,
        left_heel_site,
        left_foot_site,
        left_foot_dorsal_site,
        np.asarray(target_metrics["left_heel_vector_local_m"], dtype=np.float64),
        np.asarray(target_metrics["left_foot_vector_local_m"], dtype=np.float64),
    )
    apply_foot_layout(
        right_foot,
        right_foot_geom,
        right_heel_site,
        right_foot_site,
        right_foot_dorsal_site,
        np.asarray(target_metrics["right_heel_vector_local_m"], dtype=np.float64),
        np.asarray(target_metrics["right_foot_vector_local_m"], dtype=np.float64),
    )

    masses = compute_body_masses_kg(metrics["body_mass_kg"])
    lower_torso_fromto_updated = parse_vec(lower_torso_geom.get("fromto"))
    left_hand_geom_fromto = parse_vec(left_hand_geom.get("fromto"))
    right_hand_geom_fromto = parse_vec(right_hand_geom.get("fromto"))
    left_foot_geom_fromto = parse_vec(left_foot_geom.get("fromto"))
    right_foot_geom_fromto = parse_vec(right_foot_geom.get("fromto"))

    set_body_inertial(
        pelvis,
        np.array([0.0, 0.0, 0.0], dtype=np.float64),
        masses["pelvis"],
        np.array([0.07, max(0.5 * metrics["hip_width_m"], 0.06), 0.06], dtype=np.float64),
    )
    set_body_inertial(
        torso_base,
        np.array([0.0, 0.0, 0.5 * (lower_torso_fromto_updated[2] + lower_torso_fromto_updated[5])], dtype=np.float64),
        masses["torso_base"],
        np.array([0.11, max(0.36 * metrics["shoulder_width_m"], 0.08), 0.5 * (lower_torso_fromto_updated[5] - lower_torso_fromto_updated[2])], dtype=np.float64),
    )
    set_body_inertial(
        thorax,
        np.array([0.0, 0.0, 0.05 * torso_total], dtype=np.float64),
        masses["thorax"],
        np.array([0.13, max(0.48 * metrics["shoulder_width_m"], 0.10), max(0.18 * torso_total, 0.12)], dtype=np.float64),
    )
    set_body_inertial(head, np.zeros(3, dtype=np.float64), masses["head"], np.array([0.10, 0.10, 0.10], dtype=np.float64))
    set_body_inertial(left_shoulder_mount, np.array([0.0, 0.0, 0.03], dtype=np.float64), masses["left_shoulder_mount"], np.array([0.03, 0.03, 0.04], dtype=np.float64))
    set_body_inertial(right_shoulder_mount, np.array([0.0, 0.0, 0.03], dtype=np.float64), masses["right_shoulder_mount"], np.array([0.03, 0.03, 0.04], dtype=np.float64))
    set_body_inertial(left_upper_arm, np.array([0.5 * upper_arm, 0.0, 0.0], dtype=np.float64), masses["left_upper_arm"], np.array([0.5 * upper_arm, 0.04, 0.04], dtype=np.float64))
    set_body_inertial(right_upper_arm, np.array([0.5 * upper_arm, 0.0, 0.0], dtype=np.float64), masses["right_upper_arm"], np.array([0.5 * upper_arm, 0.04, 0.04], dtype=np.float64))
    set_body_inertial(left_elbow, np.array([0.5 * forearm, 0.0, 0.0], dtype=np.float64), masses["left_elbow"], np.array([0.5 * forearm, 0.034, 0.034], dtype=np.float64))
    set_body_inertial(right_elbow, np.array([0.5 * forearm, 0.0, 0.0], dtype=np.float64), masses["right_elbow"], np.array([0.5 * forearm, 0.034, 0.034], dtype=np.float64))
    set_body_inertial(left_hand, 0.5 * left_hand_geom_fromto[3:6], masses["left_hand"], np.array([0.5 * np.linalg.norm(left_hand_geom_fromto[3:6]), 0.03, 0.03], dtype=np.float64))
    set_body_inertial(right_hand, 0.5 * right_hand_geom_fromto[3:6], masses["right_hand"], np.array([0.5 * np.linalg.norm(right_hand_geom_fromto[3:6]), 0.03, 0.03], dtype=np.float64))
    set_body_inertial(left_hip_mount, np.zeros(3, dtype=np.float64), masses["left_hip_mount"], np.array([0.03, 0.03, 0.04], dtype=np.float64))
    set_body_inertial(right_hip_mount, np.zeros(3, dtype=np.float64), masses["right_hip_mount"], np.array([0.03, 0.03, 0.04], dtype=np.float64))
    set_body_inertial(left_thigh, np.array([0.0, 0.0, -0.5 * thigh], dtype=np.float64), masses["left_thigh"], np.array([0.05, 0.05, 0.5 * thigh], dtype=np.float64))
    set_body_inertial(right_thigh, np.array([0.0, 0.0, -0.5 * thigh], dtype=np.float64), masses["right_thigh"], np.array([0.05, 0.05, 0.5 * thigh], dtype=np.float64))
    set_body_inertial(left_knee, np.array([0.0, 0.0, -0.5 * shin], dtype=np.float64), masses["left_knee"], np.array([0.04, 0.04, 0.5 * shin], dtype=np.float64))
    set_body_inertial(right_knee, np.array([0.0, 0.0, -0.5 * shin], dtype=np.float64), masses["right_knee"], np.array([0.04, 0.04, 0.5 * shin], dtype=np.float64))
    set_body_inertial(left_ankle, np.zeros(3, dtype=np.float64), 1e-6, np.array([0.01, 0.01, 0.01], dtype=np.float64))
    set_body_inertial(right_ankle, np.zeros(3, dtype=np.float64), 1e-6, np.array([0.01, 0.01, 0.01], dtype=np.float64))
    set_body_inertial(
        left_foot,
        0.5 * (left_foot_geom_fromto[0:3] + left_foot_geom_fromto[3:6]),
        masses["left_foot"],
        np.array([0.5 * np.linalg.norm(left_foot_geom_fromto[3:6] - left_foot_geom_fromto[0:3]), 0.035, 0.045], dtype=np.float64),
    )
    set_body_inertial(
        right_foot,
        0.5 * (right_foot_geom_fromto[0:3] + right_foot_geom_fromto[3:6]),
        masses["right_foot"],
        np.array([0.5 * np.linalg.norm(right_foot_geom_fromto[3:6] - right_foot_geom_fromto[0:3]), 0.035, 0.045], dtype=np.float64),
    )

    ET.indent(tree, space="  ")
    tree.write(output_xml, encoding="utf-8", xml_declaration=False)


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate a user-personalized articulated human XML from calibration and target skeleton samples.")
    parser.add_argument("--base-xml", type=Path, default=ROOT / "custom_articulated_human.xml")
    parser.add_argument("--output-xml", type=Path, default=ROOT / "custom_articulated_human_personalized.xml")
    parser.add_argument("--output-report", type=Path, default=ROOT / "custom_articulated_human_personalized_report.json")
    parser.add_argument("--input-video", type=Path, default=DEFAULT_INPUT_VIDEO)
    parser.add_argument("--task-model", type=Path, default=CUSTOM_SKELETON_ROOT / "pose_landmarker_lite.task")
    parser.add_argument("--calibration-json", type=Path, default=CUSTOM_SKELETON_ROOT / "calibration.json")
    parser.add_argument("--sample-count", type=int, default=24)
    parser.add_argument("--body-mass-kg", type=float, default=80.0)
    args = parser.parse_args()

    calibration = load_calibration_json(args.calibration_json)
    calibration["body_mass_kg"] = float(args.body_mass_kg)
    target_metrics = collect_target_metrics(
        video_path=args.input_video,
        task_path=args.task_model,
        calibration=calibration,
        sample_count=args.sample_count,
    )
    applied_metrics = build_personalization_metrics(calibration, target_metrics)
    apply_personalization(
        template_xml=args.base_xml,
        output_xml=args.output_xml,
        metrics=applied_metrics,
        target_metrics=target_metrics,
    )

    report = {
        "base_xml": str(args.base_xml.resolve()),
        "output_xml": str(args.output_xml.resolve()),
        "input_video": str(args.input_video.resolve()),
        "calibration_json": str(args.calibration_json.resolve()),
        "applied_metrics_m": applied_metrics,
        "target_metrics_m": target_metrics,
    }
    args.output_report.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()

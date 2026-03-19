from __future__ import annotations

import argparse
import json
import sys
import time
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

import cv2
import mediapipe as mp
import mujoco
import numpy as np

ROOT = Path(__file__).resolve().parent
PROJECT_ROOT = ROOT.parent
ARTIC_ROOT = PROJECT_ROOT / "custom_articulated_human"
CUSTOM_SKELETON_ROOT = PROJECT_ROOT / "custom_skeleton_verify"
DEFAULT_PERSONALIZED_XML = ARTIC_ROOT / "custom_articulated_human_personalized.xml"
DEFAULT_XML = DEFAULT_PERSONALIZED_XML if DEFAULT_PERSONALIZED_XML.exists() else ARTIC_ROOT / "custom_articulated_human.xml"

sys.path.insert(0, str(ARTIC_ROOT))
sys.path.insert(0, str(CUSTOM_SKELETON_ROOT))

from evaluate_static_fit import AUX_SITE_TARGETS, POLE_TARGETS, SITE_TARGETS, fit_static_pose  # noqa: E402
from hold_contact_state import HoldContactTracker, compute_contact_points_px, load_hold_detections  # noqa: E402
from support_stability import analyze_support_stability  # noqa: E402
from contact_force_distribution import estimate_contact_forces, summarize_contact_force_history  # noqa: E402
from mediapipe_custom_skeleton_verify import MetricSkeletonMapper, make_landmarker  # noqa: E402
from physics_worker import load_calibration_json  # noqa: E402

JUMP_KEYS = (
    "pelvis",
    "thorax",
    "left_shoulder",
    "right_shoulder",
    "left_hip",
    "right_hip",
    "left_knee",
    "right_knee",
    "left_ankle",
    "right_ankle",
    "left_foot",
    "right_foot",
    "left_hand",
    "right_hand",
)
MAX_TARGET_JUMP_M = 0.45
MEAN_TARGET_JUMP_M = 0.16
BAD_FIT_MEAN_ERROR_M = 0.28
BAD_FIT_MAX_ERROR_M = 0.80
BAD_FOOT_FORWARD_DOT = -0.05
BAD_KNEE_FOOT_ALIGNMENT_DOT = -0.20

SUPPORT_SITE_BY_LIMB = {
    "left_hand": "left_hand_site",
    "right_hand": "right_hand_site",
    "left_foot": "left_foot_site",
    "right_foot": "right_foot_site",
}

BODY_GROUPS = {
    "core": ("abdomen_", "neck_"),
    "left_arm": ("shoulder_shrug_left", "shoulder1_left", "shoulder2_left", "shoulder3_left", "elbow_left"),
    "right_arm": ("shoulder_shrug_right", "shoulder1_right", "shoulder2_right", "shoulder3_right", "elbow_right"),
    "left_leg": ("hip_x_left", "hip_z_left", "hip_y_left", "knee_left", "ankle_y_left", "ankle_x_left"),
    "right_leg": ("hip_x_right", "hip_z_right", "hip_y_right", "knee_right", "ankle_y_right", "ankle_x_right"),
}


def target_jump_stats(current: dict[str, np.ndarray], previous: dict[str, np.ndarray] | None) -> tuple[float, float]:
    if previous is None:
        return 0.0, 0.0
    diffs = [
        float(np.linalg.norm(np.asarray(current[key], dtype=np.float64) - np.asarray(previous[key], dtype=np.float64)))
        for key in JUMP_KEYS
        if key in current and key in previous
    ]
    if not diffs:
        return 0.0, 0.0
    return float(np.mean(diffs)), float(np.max(diffs))


def has_bad_lower_limb_consistency(fit: dict[str, object]) -> bool:
    consistency = fit.get("lower_limb_consistency")
    if not isinstance(consistency, dict):
        return False
    for side in ("left", "right"):
        payload = consistency.get(side)
        if not isinstance(payload, dict):
            continue
        bend_dot = float(payload.get("bend_alignment_dot", 1.0))
        foot_dot = float(payload.get("foot_vs_pelvis_forward_dot", 1.0))
        bend_norm = float(payload.get("bend_norm", 0.0))
        if foot_dot < BAD_FOOT_FORWARD_DOT:
            return True
        if bend_norm > 0.05 and bend_dot < BAD_KNEE_FOOT_ALIGNMENT_DOT:
            return True
    return False


def clone_pose_bundle(bundle: dict[str, object]) -> dict[str, object]:
    fit_payload = dict(bundle["fit"])
    fit_summary = {
        "mean_error_m": float(fit_payload["mean_error_m"]),
        "max_error_m": float(fit_payload["max_error_m"]),
        "final_error_norm": float(fit_payload["final_error_norm"]),
        "lower_limb_consistency": fit_payload.get("lower_limb_consistency", {}),
    }
    return {
        "qpos": np.asarray(bundle["qpos"], dtype=np.float64).copy(),
        "fit": fit_summary,
    }


def compute_model_com(model: mujoco.MjModel, data: mujoco.MjData) -> np.ndarray:
    masses = np.asarray(model.body_mass[1:], dtype=np.float64)
    if masses.size == 0:
        return np.zeros(3, dtype=np.float64)
    positions = np.asarray(data.xipos[1:], dtype=np.float64)
    total_mass = float(np.sum(masses))
    if total_mass <= 1e-8:
        return np.zeros(3, dtype=np.float64)
    return np.sum(positions * masses[:, None], axis=0) / total_mass


def collect_joint_inverse_forces(model: mujoco.MjModel, data: mujoco.MjData) -> dict[str, dict[str, float]]:
    joint_forces: dict[str, dict[str, float]] = {}
    for jnt_id in range(model.njnt):
        jnt_type = int(model.jnt_type[jnt_id])
        if jnt_type not in (mujoco.mjtJoint.mjJNT_HINGE, mujoco.mjtJoint.mjJNT_SLIDE):
            continue
        joint_name = mujoco.mj_id2name(model, mujoco.mjtObj.mjOBJ_JOINT, jnt_id) or f"joint_{jnt_id}"
        dof_adr = int(model.jnt_dofadr[jnt_id])
        qpos_adr = int(model.jnt_qposadr[jnt_id])
        joint_forces[joint_name] = {
            "qfrc_inverse": float(data.qfrc_inverse[dof_adr]),
            "qpos": float(data.qpos[qpos_adr]),
            "range_low": float(model.jnt_range[jnt_id, 0]),
            "range_high": float(model.jnt_range[jnt_id, 1]),
        }
    return joint_forces


def compute_support_center(
    data: mujoco.MjData,
    site_ids: dict[str, int],
    active_limbs: list[str],
) -> tuple[np.ndarray, list[str], str]:
    used_support_limbs = [limb for limb in active_limbs if limb in SUPPORT_SITE_BY_LIMB]
    support_mode = "active_contacts"
    if not used_support_limbs:
        used_support_limbs = list(SUPPORT_SITE_BY_LIMB.keys())
        support_mode = "fallback_all_limbs"
    positions = [
        np.asarray(data.site_xpos[site_ids[SUPPORT_SITE_BY_LIMB[limb]]], dtype=np.float64)
        for limb in used_support_limbs
    ]
    support_center = np.mean(np.asarray(positions, dtype=np.float64), axis=0)
    return support_center, used_support_limbs, support_mode


def moving_average_2d(arr: np.ndarray, window: int) -> np.ndarray:
    if window <= 1 or arr.shape[0] <= 1:
        return arr.copy()
    half = max(0, window // 2)
    out = np.zeros_like(arr)
    for idx in range(arr.shape[0]):
        start = max(0, idx - half)
        end = min(arr.shape[0], idx + half + 1)
        out[idx] = np.mean(arr[start:end], axis=0)
    return out


def compute_qvel_sequence(model: mujoco.MjModel, qpos_seq: np.ndarray, timestamps_ms: np.ndarray) -> np.ndarray:
    frame_count = qpos_seq.shape[0]
    qvel_seq = np.zeros((frame_count, model.nv), dtype=np.float64)
    scratch = np.zeros(model.nv, dtype=np.float64)
    if frame_count <= 1:
        return qvel_seq
    for idx in range(frame_count):
        if idx == 0:
            qpos_a = qpos_seq[0]
            qpos_b = qpos_seq[1]
            diff_dt = max((float(timestamps_ms[1]) - float(timestamps_ms[0])) / 1000.0, 1e-6)
        elif idx == frame_count - 1:
            qpos_a = qpos_seq[-2]
            qpos_b = qpos_seq[-1]
            diff_dt = max((float(timestamps_ms[-1]) - float(timestamps_ms[-2])) / 1000.0, 1e-6)
        else:
            qpos_a = qpos_seq[idx - 1]
            qpos_b = qpos_seq[idx + 1]
            diff_dt = max((float(timestamps_ms[idx + 1]) - float(timestamps_ms[idx - 1])) / 1000.0, 1e-6)
        mujoco.mj_differentiatePos(model, scratch, diff_dt, qpos_a, qpos_b)
        qvel_seq[idx] = scratch
    return qvel_seq


def compute_qacc_sequence(qvel_seq: np.ndarray, timestamps_ms: np.ndarray) -> np.ndarray:
    frame_count, dof_count = qvel_seq.shape
    qacc_seq = np.zeros((frame_count, dof_count), dtype=np.float64)
    if frame_count <= 1:
        return qacc_seq
    for idx in range(frame_count):
        if idx == 0:
            dt = max((float(timestamps_ms[1]) - float(timestamps_ms[0])) / 1000.0, 1e-6)
            qacc_seq[idx] = (qvel_seq[1] - qvel_seq[0]) / dt
        elif idx == frame_count - 1:
            dt = max((float(timestamps_ms[-1]) - float(timestamps_ms[-2])) / 1000.0, 1e-6)
            qacc_seq[idx] = (qvel_seq[-1] - qvel_seq[-2]) / dt
        else:
            dt = max((float(timestamps_ms[idx + 1]) - float(timestamps_ms[idx - 1])) / 1000.0, 1e-6)
            qacc_seq[idx] = (qvel_seq[idx + 1] - qvel_seq[idx - 1]) / dt
    return qacc_seq


def fill_missing_qpos(records: list[dict[str, Any]]) -> None:
    valid_indices = [idx for idx, record in enumerate(records) if record.get("qpos") is not None]
    if not valid_indices:
        return
    first_valid = valid_indices[0]
    first_qpos = np.asarray(records[first_valid]["qpos"], dtype=np.float64).copy()
    for idx in range(0, first_valid):
        records[idx]["qpos"] = first_qpos.copy()
        records[idx]["pose_mode"] = "backfilled_initial"
        records[idx]["frozen"] = True
        records[idx]["fit_mean_error_m"] = None
        records[idx]["fit_max_error_m"] = None
        records[idx]["fit_final_error_norm"] = None
        records[idx]["lower_limb_consistency"] = {}
    for idx in range(first_valid + 1, len(records)):
        if records[idx].get("qpos") is None:
            records[idx]["qpos"] = np.asarray(records[idx - 1]["qpos"], dtype=np.float64).copy()
            records[idx]["pose_mode"] = "filled_gap"
            records[idx]["frozen"] = True
            records[idx]["fit_mean_error_m"] = None
            records[idx]["fit_max_error_m"] = None
            records[idx]["fit_final_error_norm"] = None
            records[idx]["lower_limb_consistency"] = {}


def extract_active_contacts(limb_states: dict[str, dict[str, Any]] | None) -> tuple[list[str], dict[str, int]]:
    active_limbs: list[str] = []
    active_hold_ids: dict[str, int] = {}
    if limb_states is None:
        return active_limbs, active_hold_ids
    for limb_name, payload in limb_states.items():
        hold_id = payload.get("active_hold_id")
        if str(payload.get("state")) in ("GRIP", "STEP") and hold_id is not None:
            active_limbs.append(limb_name)
            active_hold_ids[limb_name] = int(hold_id)
    return active_limbs, active_hold_ids


def classify_phase(
    pose_mode: str,
    frozen: bool,
    limb_states: dict[str, dict[str, Any]] | None,
    active_contact_limbs: list[str],
    support_type: str,
    root_speed_m_s: float,
) -> str:
    if frozen or pose_mode in ("frozen_missing", "frozen_glitch", "backfilled_initial", "filled_gap"):
        return "recovery"
    states = [str(payload.get("state")) for payload in (limb_states or {}).values()]
    has_transition = any(state in ("REACH", "RELEASE") for state in states)
    if len(active_contact_limbs) >= 3 and support_type in ("tri_support", "quad_support") and not has_transition and root_speed_m_s < 0.35:
        return "static_support"
    if len(active_contact_limbs) >= 2:
        return "loaded_transition"
    return "dynamic_transition"


def classify_confidence(pose_mode: str, support_mode: str, support_type: str) -> str:
    if pose_mode in ("frozen_missing", "frozen_glitch", "backfilled_initial", "filled_gap"):
        return "low"
    if support_mode == "active_contacts" and support_type in ("tri_support", "quad_support"):
        return "high"
    if support_type in ("line_support", "point_support") or support_mode == "fallback_all_limbs":
        return "low"
    return "medium"


def top_joint_loads(joint_forces: dict[str, dict[str, float]], top_k: int) -> list[dict[str, float | str]]:
    ordered = sorted(
        (
            {
                "joint": joint_name,
                "abs_qfrc_inverse": abs(float(payload["qfrc_inverse"])),
                "signed_qfrc_inverse": float(payload["qfrc_inverse"]),
            }
            for joint_name, payload in joint_forces.items()
        ),
        key=lambda item: float(item["abs_qfrc_inverse"]),
        reverse=True,
    )
    return ordered[:top_k]


def summarize_body_loads(joint_forces: dict[str, dict[str, float]]) -> dict[str, float]:
    loads = {group_name: 0.0 for group_name in BODY_GROUPS}
    for joint_name, payload in joint_forces.items():
        value = abs(float(payload["qfrc_inverse"]))
        for group_name, prefixes in BODY_GROUPS.items():
            if any(joint_name.startswith(prefix) or joint_name == prefix for prefix in prefixes):
                loads[group_name] += value
                break
    return loads


def summarize_joint_load_history(history: list[dict[str, dict[str, float]]]) -> dict[str, dict[str, float]]:
    bucket: dict[str, list[float]] = defaultdict(list)
    for frame_payload in history:
        for joint_name, payload in frame_payload.items():
            bucket[joint_name].append(abs(float(payload["qfrc_inverse"])))
    summary: dict[str, dict[str, float]] = {}
    for joint_name, values in bucket.items():
        arr = np.asarray(values, dtype=np.float64)
        summary[joint_name] = {
            "mean_abs_qfrc_inverse": float(np.mean(arr)),
            "max_abs_qfrc_inverse": float(np.max(arr)),
            "p95_abs_qfrc_inverse": float(np.percentile(arr, 95)),
        }
    return summary


def summarize_body_load_history(history: list[dict[str, float]]) -> dict[str, dict[str, float]]:
    bucket: dict[str, list[float]] = defaultdict(list)
    for frame_payload in history:
        for group_name, value in frame_payload.items():
            bucket[group_name].append(float(value))
    summary: dict[str, dict[str, float]] = {}
    for group_name, values in bucket.items():
        arr = np.asarray(values, dtype=np.float64)
        summary[group_name] = {
            "mean_abs_load_proxy": float(np.mean(arr)),
            "max_abs_load_proxy": float(np.max(arr)),
            "p95_abs_load_proxy": float(np.percentile(arr, 95)),
        }
    return summary


def summarize_support_stability(frames: list[dict[str, Any]]) -> dict[str, Any]:
    support_type_counts: dict[str, int] = defaultdict(int)
    inside_count = 0
    outside_count = 0
    margins: list[float] = []
    for frame in frames:
        stability = frame.get("support_stability")
        if not stability:
            continue
        support_type = str(stability.get("support_type"))
        support_type_counts[support_type] += 1
        inside = stability.get("inside_support")
        if inside is True:
            inside_count += 1
        elif inside is False:
            outside_count += 1
        margin = stability.get("stability_margin_m")
        if margin is not None and np.isfinite(float(margin)):
            margins.append(float(margin))
    margin_summary = None
    if margins:
        arr = np.asarray(margins, dtype=np.float64)
        margin_summary = {
            "mean_m": float(np.mean(arr)),
            "median_m": float(np.median(arr)),
            "min_m": float(np.min(arr)),
            "max_m": float(np.max(arr)),
        }
    return {
        "support_type_counts": dict(support_type_counts),
        "inside_support_count": inside_count,
        "outside_support_count": outside_count,
        "stability_margin_summary_m": margin_summary,
    }


def summarize_hold_states(frames: list[dict[str, Any]]) -> dict[str, Any]:
    state_counts: dict[str, Counter[str]] = defaultdict(Counter)
    transitions: dict[str, Counter[str]] = defaultdict(Counter)
    for frame in frames:
        limb_states = frame.get("limb_states") or {}
        for limb_name, payload in limb_states.items():
            state_counts[limb_name][str(payload.get("state"))] += 1
            transition = payload.get("transition")
            if transition:
                transitions[limb_name][str(transition)] += 1
    return {
        limb_name: {
            "state_counts": dict(counter),
            "transition_counts": dict(transitions.get(limb_name, Counter())),
        }
        for limb_name, counter in state_counts.items()
    }


def sequence_gate_decision(frames: list[dict[str, Any]]) -> dict[str, Any]:
    fitted = [frame for frame in frames if frame.get("fit_mean_error_m") is not None]
    if not fitted:
        return {
            "passed": False,
            "failures": ["no_fitted_frames"],
        }
    failures: list[str] = []
    mean_fit = float(np.mean([float(frame["fit_mean_error_m"]) for frame in fitted]))
    recovery_ratio = float(np.mean([1.0 if frame.get("phase") == "recovery" else 0.0 for frame in frames]))
    if mean_fit > 0.14:
        failures.append("fit_mean_error_above_14cm")
    if recovery_ratio > 0.12:
        failures.append("recovery_ratio_above_12pct")
    return {
        "passed": len(failures) == 0,
        "failures": failures,
        "fit_mean_error_m": mean_fit,
        "recovery_ratio": recovery_ratio,
    }


def evaluate_video(
    xml_path: Path,
    video_path: Path,
    task_path: Path,
    calibration: dict[str, float] | None,
    detections_json: Path | None,
    ik_iterations: int,
    damping: float,
    frame_step: int,
    smoothing_window: int,
    top_k_joints: int,
    store_state_vectors: bool,
) -> dict[str, Any]:
    started = time.perf_counter()
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
    frame_width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH) or 0)
    frame_height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT) or 0)
    fps = float(cap.get(cv2.CAP_PROP_FPS) or 30.0)
    frame_count = int(cap.get(cv2.CAP_PROP_FRAME_COUNT) or 0)
    landmarker = make_landmarker(task_path)
    mapper = MetricSkeletonMapper(calibration)
    hold_payload = None
    tracker = None
    if detections_json is not None:
        hold_payload = load_hold_detections(detections_json)
        tracker = HoldContactTracker(hold_payload["holds"])

    frames: list[dict[str, Any]] = []
    prev_qpos: np.ndarray | None = None
    prev_target_points: dict[str, np.ndarray] | None = None
    last_good_bundle: dict[str, object] | None = None

    frame_idx = 0
    while True:
        ok, frame_bgr = cap.read()
        if not ok:
            break
        if frame_step > 1 and frame_idx % frame_step != 0:
            frame_idx += 1
            continue

        timestamp_ms = int(round((frame_idx / max(fps, 1.0)) * 1000.0))
        frame_rgb = cv2.cvtColor(frame_bgr, cv2.COLOR_BGR2RGB)
        mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=frame_rgb)
        result = landmarker.detect_for_video(mp_image, timestamp_ms)
        pose_landmarks_2d = result.pose_landmarks[0] if result.pose_landmarks else None

        limb_states = None
        if tracker is not None:
            contact_points = compute_contact_points_px(pose_landmarks_2d, frame_width, frame_height)
            limb_states = tracker.update_frame(contact_points, timestamp_ms)

        base_record: dict[str, Any] = {
            "frame_index": frame_idx,
            "timestamp_ms": timestamp_ms,
            "detected": bool(result.pose_world_landmarks),
            "limb_states": limb_states or {},
            "qpos": None,
            "pose_mode": "missing",
            "frozen": False,
            "fit_mean_error_m": None,
            "fit_max_error_m": None,
            "fit_final_error_norm": None,
            "lower_limb_consistency": {},
        }

        if not result.pose_world_landmarks:
            if last_good_bundle is not None:
                frozen = clone_pose_bundle(last_good_bundle)
                base_record.update(
                    {
                        "qpos": np.asarray(frozen["qpos"], dtype=np.float64).copy(),
                        "pose_mode": "frozen_missing",
                        "frozen": True,
                        "fit_mean_error_m": frozen["fit"]["mean_error_m"],
                        "fit_max_error_m": frozen["fit"]["max_error_m"],
                        "fit_final_error_norm": frozen["fit"]["final_error_norm"],
                        "lower_limb_consistency": frozen["fit"].get("lower_limb_consistency", {}),
                    }
                )
            frames.append(base_record)
            frame_idx += 1
            continue

        mapper_snapshot = mapper.snapshot_state()
        target_points = mapper.map_frame(result.pose_world_landmarks[0])
        mean_jump, max_jump = target_jump_stats(target_points, prev_target_points)
        if prev_target_points is not None and (max_jump > MAX_TARGET_JUMP_M or mean_jump > MEAN_TARGET_JUMP_M):
            mapper.restore_state(mapper_snapshot)
            if last_good_bundle is not None:
                frozen = clone_pose_bundle(last_good_bundle)
                base_record.update(
                    {
                        "qpos": np.asarray(frozen["qpos"], dtype=np.float64).copy(),
                        "pose_mode": "frozen_glitch",
                        "frozen": True,
                        "fit_mean_error_m": frozen["fit"]["mean_error_m"],
                        "fit_max_error_m": frozen["fit"]["max_error_m"],
                        "fit_final_error_norm": frozen["fit"]["final_error_norm"],
                        "lower_limb_consistency": frozen["fit"].get("lower_limb_consistency", {}),
                    }
                )
            frames.append(base_record)
            frame_idx += 1
            continue

        fit = fit_static_pose(
            model=model,
            data=data,
            site_ids=site_ids,
            target_points=target_points,
            seed_qpos=prev_qpos,
            iterations=ik_iterations,
            damping=damping,
        )
        if prev_qpos is not None and (
            float(fit["mean_error_m"]) > BAD_FIT_MEAN_ERROR_M or float(fit["max_error_m"]) > BAD_FIT_MAX_ERROR_M
        ):
            retry = fit_static_pose(
                model=model,
                data=data,
                site_ids=site_ids,
                target_points=target_points,
                seed_qpos=None,
                iterations=ik_iterations,
                damping=damping,
            )
            if float(retry["mean_error_m"]) < float(fit["mean_error_m"]):
                fit = retry
        if has_bad_lower_limb_consistency(fit):
            retry = fit_static_pose(
                model=model,
                data=data,
                site_ids=site_ids,
                target_points=target_points,
                seed_qpos=None,
                iterations=ik_iterations,
                damping=damping,
            )
            if not has_bad_lower_limb_consistency(retry) or float(retry["mean_error_m"]) < float(fit["mean_error_m"]):
                fit = retry

        if (
            float(fit["mean_error_m"]) > BAD_FIT_MEAN_ERROR_M
            or float(fit["max_error_m"]) > BAD_FIT_MAX_ERROR_M
            or has_bad_lower_limb_consistency(fit)
        ):
            mapper.restore_state(mapper_snapshot)
            if last_good_bundle is not None:
                frozen = clone_pose_bundle(last_good_bundle)
                base_record.update(
                    {
                        "qpos": np.asarray(frozen["qpos"], dtype=np.float64).copy(),
                        "pose_mode": "frozen_glitch",
                        "frozen": True,
                        "fit_mean_error_m": frozen["fit"]["mean_error_m"],
                        "fit_max_error_m": frozen["fit"]["max_error_m"],
                        "fit_final_error_norm": frozen["fit"]["final_error_norm"],
                        "lower_limb_consistency": frozen["fit"].get("lower_limb_consistency", {}),
                    }
                )
            frames.append(base_record)
            frame_idx += 1
            continue

        prev_qpos = fit["qpos"].copy()
        prev_target_points = {key: value.copy() for key, value in target_points.items()}
        last_good_bundle = {
            "qpos": fit["qpos"].copy(),
            "fit": fit,
        }
        base_record.update(
            {
                "qpos": fit["qpos"].copy(),
                "pose_mode": "fitted",
                "frozen": False,
                "fit_mean_error_m": float(fit["mean_error_m"]),
                "fit_max_error_m": float(fit["max_error_m"]),
                "fit_final_error_norm": float(fit["final_error_norm"]),
                "lower_limb_consistency": fit.get("lower_limb_consistency", {}),
            }
        )
        frames.append(base_record)
        frame_idx += 1

    cap.release()
    landmarker.close()

    fill_missing_qpos(frames)
    qpos_seq = np.asarray([np.asarray(frame["qpos"], dtype=np.float64) for frame in frames], dtype=np.float64)
    timestamps_ms = np.asarray([float(frame["timestamp_ms"]) for frame in frames], dtype=np.float64)
    qvel_seq = compute_qvel_sequence(model, qpos_seq, timestamps_ms)
    qvel_seq = moving_average_2d(qvel_seq, smoothing_window)
    qacc_seq = compute_qacc_sequence(qvel_seq, timestamps_ms)
    qacc_seq = moving_average_2d(qacc_seq, smoothing_window)

    joint_force_history: list[dict[str, dict[str, float]]] = []
    body_load_history: list[dict[str, float]] = []
    pose_mode_counts: Counter[str] = Counter()
    phase_counts: Counter[str] = Counter()
    support_mode_counts: Counter[str] = Counter()

    for idx, frame in enumerate(frames):
        qpos = qpos_seq[idx]
        qvel = qvel_seq[idx]
        qacc = qacc_seq[idx]

        data.qpos[:] = qpos
        data.qvel[:] = qvel
        data.qacc[:] = qacc
        data.qfrc_applied[:] = 0.0
        data.xfrc_applied[:] = 0.0
        mujoco.mj_forward(model, data)
        data.qvel[:] = qvel
        data.qacc[:] = qacc
        mujoco.mj_inverse(model, data)

        com = compute_model_com(model, data)
        active_contact_limbs, active_hold_ids = extract_active_contacts(frame.get("limb_states"))
        support_center, used_support_limbs, support_mode = compute_support_center(data, site_ids, active_contact_limbs)
        support_points_xyz = {
            limb_name: np.asarray(data.site_xpos[site_ids[SUPPORT_SITE_BY_LIMB[limb_name]]], dtype=np.float64)
            for limb_name in used_support_limbs
        }
        support_stability = analyze_support_stability(com, support_points_xyz)
        joint_inverse_forces = collect_joint_inverse_forces(model, data)
        body_loads = summarize_body_loads(joint_inverse_forces)
        root_inverse_force = np.asarray(data.qfrc_inverse[:6], dtype=np.float64)
        contact_modes = {
            limb_name: str(frame["limb_states"].get(limb_name, {}).get("state", "MOVE"))
            for limb_name in active_contact_limbs
        }
        active_contact_positions = {
            limb_name: np.asarray(data.site_xpos[site_ids[SUPPORT_SITE_BY_LIMB[limb_name]]], dtype=np.float64)
            for limb_name in active_contact_limbs
        }
        contact_force_distribution = estimate_contact_forces(
            root_position_xyz=np.asarray(data.qpos[0:3], dtype=np.float64),
            required_wrench=root_inverse_force,
            contact_positions_xyz=active_contact_positions,
            contact_modes=contact_modes,
        )

        root_linear_speed = float(np.linalg.norm(qvel[0:3]))
        root_linear_accel = float(np.linalg.norm(qacc[0:3]))
        phase = classify_phase(
            pose_mode=str(frame["pose_mode"]),
            frozen=bool(frame["frozen"]),
            limb_states=frame.get("limb_states"),
            active_contact_limbs=active_contact_limbs,
            support_type=str(support_stability.get("support_type")),
            root_speed_m_s=root_linear_speed,
        )
        confidence = classify_confidence(
            pose_mode=str(frame["pose_mode"]),
            support_mode=support_mode,
            support_type=str(support_stability.get("support_type")),
        )

        frame["active_contact_limbs"] = active_contact_limbs
        frame["active_hold_ids"] = active_hold_ids
        frame["root_position_m"] = np.asarray(data.qpos[0:3], dtype=np.float64).tolist()
        frame["support_mode"] = support_mode
        frame["used_support_limbs"] = used_support_limbs
        frame["support_center_m"] = support_center.tolist()
        frame["com_position_m"] = com.tolist()
        frame["com_support_offset_m"] = (com - support_center).tolist()
        frame["support_stability"] = support_stability
        frame["root_inverse_force"] = root_inverse_force.tolist()
        frame["root_linear_speed_m_s"] = root_linear_speed
        frame["root_linear_accel_m_s2"] = root_linear_accel
        frame["qvel_norm"] = float(np.linalg.norm(qvel))
        frame["qacc_norm"] = float(np.linalg.norm(qacc))
        frame["phase"] = phase
        frame["analysis_confidence"] = confidence
        frame["contact_force_distribution"] = contact_force_distribution
        frame["top_joint_loads"] = top_joint_loads(joint_inverse_forces, top_k_joints)
        frame["body_loads"] = body_loads
        if store_state_vectors:
            frame["qpos"] = qpos.tolist()
            frame["qvel"] = qvel.tolist()
            frame["qacc"] = qacc.tolist()
        else:
            frame.pop("qpos", None)

        joint_force_history.append(joint_inverse_forces)
        body_load_history.append(body_loads)
        pose_mode_counts[str(frame["pose_mode"])] += 1
        phase_counts[phase] += 1
        support_mode_counts[support_mode] += 1

    detection_video_scale = None
    hold_source_file = None
    if hold_payload is not None:
        bbox_extent = hold_payload["bbox_extent_px"]
        hold_source_file = hold_payload["source_file"]
        detection_video_scale = {
            "video_width_px": frame_width,
            "video_height_px": frame_height,
            "detection_extent_x_px": bbox_extent[0],
            "detection_extent_y_px": bbox_extent[1],
            "coverage_ratio_x": float(bbox_extent[0] / max(frame_width, 1)),
            "coverage_ratio_y": float(bbox_extent[1] / max(frame_height, 1)),
        }

    runtime_s = float(time.perf_counter() - started)
    support_stability_summary = summarize_support_stability(frames)
    hold_state_summary = summarize_hold_states(frames)
    dynamic_gate = sequence_gate_decision(frames)

    return {
        "xml": str(xml_path.resolve()),
        "video": str(video_path.resolve()),
        "task_model": str(task_path.resolve()),
        "detections_json": None if detections_json is None else str(detections_json.resolve()),
        "hold_source_file": hold_source_file,
        "detection_video_scale_check": detection_video_scale,
        "frame_width": frame_width,
        "frame_height": frame_height,
        "fps": fps,
        "frame_count": frame_count,
        "processed_frame_count": len(frames),
        "frame_step": frame_step,
        "smoothing_window": smoothing_window,
        "runtime_s": runtime_s,
        "pose_mode_counts": dict(pose_mode_counts),
        "phase_counts": dict(phase_counts),
        "support_mode_counts": dict(support_mode_counts),
        "hold_state_summary": hold_state_summary,
        "joint_load_summary": summarize_joint_load_history(joint_force_history),
        "body_load_summary": summarize_body_load_history(body_load_history),
        "contact_force_distribution_summary": summarize_contact_force_history(frames),
        "support_stability_summary": support_stability_summary,
        "dynamic_sequence_gate": dynamic_gate,
        "frames": frames,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Run full-sequence dynamic articulated fitting and inverse dynamics.")
    parser.add_argument("--xml", type=Path, default=DEFAULT_XML)
    parser.add_argument("--input-video", type=Path, default=PROJECT_ROOT / "video" / "주황.mp4")
    parser.add_argument("--task-model", type=Path, default=CUSTOM_SKELETON_ROOT / "pose_landmarker_lite.task")
    parser.add_argument("--calibration-json", type=Path, default=CUSTOM_SKELETON_ROOT / "calibration.json")
    parser.add_argument("--detections-json", type=Path, default=PROJECT_ROOT / "detections.json")
    parser.add_argument("--ik-iters", type=int, default=45)
    parser.add_argument("--ik-damping", type=float, default=1e-3)
    parser.add_argument("--frame-step", type=int, default=1)
    parser.add_argument("--smoothing-window", type=int, default=5)
    parser.add_argument("--top-k-joints", type=int, default=5)
    parser.add_argument("--store-state-vectors", action="store_true")
    parser.add_argument("--output", type=Path, default=ROOT / "dynamic_sequence_report.json")
    args = parser.parse_args()

    calibration = load_calibration_json(args.calibration_json)
    report = evaluate_video(
        xml_path=args.xml,
        video_path=args.input_video,
        task_path=args.task_model,
        calibration=calibration,
        detections_json=args.detections_json,
        ik_iterations=args.ik_iters,
        damping=args.ik_damping,
        frame_step=max(1, int(args.frame_step)),
        smoothing_window=max(1, int(args.smoothing_window)),
        top_k_joints=max(1, int(args.top_k_joints)),
        store_state_vectors=bool(args.store_state_vectors),
    )
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    summary = {
        "processed_frame_count": report["processed_frame_count"],
        "runtime_s": report["runtime_s"],
        "pose_mode_counts": report["pose_mode_counts"],
        "phase_counts": report["phase_counts"],
        "support_mode_counts": report["support_mode_counts"],
        "dynamic_sequence_gate": report["dynamic_sequence_gate"],
    }
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    print(f"[OK] Wrote {args.output.resolve()}")


if __name__ == "__main__":
    main()

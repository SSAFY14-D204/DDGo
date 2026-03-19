from __future__ import annotations

import argparse
import hashlib
import json
import sys
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import mujoco
import numpy as np

ROOT = Path(__file__).resolve().parent
PROJECT_ROOT = ROOT.parent
ARTIC_ROOT = PROJECT_ROOT / "custom_articulated_human"
DYNAMIC_ROOT = PROJECT_ROOT / "dynamic_sequence_pipeline"
CUSTOM_SKELETON_ROOT = PROJECT_ROOT / "custom_skeleton_verify"

sys.path.insert(0, str(ARTIC_ROOT))
sys.path.insert(0, str(DYNAMIC_ROOT))
sys.path.insert(0, str(CUSTOM_SKELETON_ROOT))

import run_dynamic_sequence_analysis as dyn  # noqa: E402
from contact_force_distribution import estimate_contact_forces, summarize_contact_force_history  # noqa: E402
from evaluate_static_fit import AUX_SITE_TARGETS, POLE_TARGETS, SITE_TARGETS, fit_static_pose  # noqa: E402
from hold_contact_state import HoldContactTracker, HoldDetection, RIGHT_FOOT_INDEX, compute_contact_points_px  # noqa: E402
from mediapipe_custom_skeleton_verify import MetricSkeletonMapper  # noqa: E402
from personalize_articulated_model import (  # noqa: E402
    apply_personalization,
    body_local,
    build_personalization_metrics,
    root_rotation_from_targets,
)
from support_stability import analyze_support_stability  # noqa: E402


DEFAULT_HOLDS_JSON = ROOT / "benchmark_inputs" / "holds.json"
DEFAULT_POSE_JSON = ROOT / "benchmark_inputs" / "pose3d_sequence.json"
DEFAULT_USER_BODY_JSON = ROOT / "benchmark_inputs" / "user_body.json"
DEFAULT_XML = ARTIC_ROOT / "custom_articulated_human.xml"
DEFAULT_CACHE_DIR = ROOT / "cache"
DEFAULT_OUTPUT = ROOT / "json_service_benchmark_report.json"
MIN_POSE_LANDMARK_COUNT = RIGHT_FOOT_INDEX + 1


@dataclass(slots=True)
class JsonLandmark:
    x: float
    y: float
    z: float


def landmark_payload_to_objects(payload: list[dict[str, Any]] | None) -> list[JsonLandmark] | None:
    if payload is None or len(payload) < MIN_POSE_LANDMARK_COUNT:
        return None
    return [
        JsonLandmark(
            x=float(item["x"]),
            y=float(item["y"]),
            z=float(item.get("z", 0.0)),
        )
        for item in payload
    ]


def calibration_from_user_body(user_body: dict[str, Any]) -> dict[str, float]:
    calibration = user_body.get("calibration_compat")
    if not isinstance(calibration, dict):
        raise KeyError("user_body.json is missing calibration_compat")
    return {str(key): float(value) for key, value in calibration.items()}


def load_service_holds(holds_json: Path) -> dict[str, Any]:
    payload = json.loads(holds_json.read_text(encoding="utf-8"))
    if "detections" in payload:
        return dyn.load_hold_detections(holds_json)

    holds = payload.get("holds", [])
    hold_objects: list[HoldDetection] = []
    max_x = 0.0
    max_y = 0.0
    for hold in holds:
        bbox = hold["bbox_px"]
        x1 = float(bbox["x1"])
        y1 = float(bbox["y1"])
        x2 = float(bbox["x2"])
        y2 = float(bbox["y2"])
        radius_px = float(hold.get("radius_px", 0.45 * min(max(1.0, x2 - x1), max(1.0, y2 - y1))))
        hold_objects.append(
            HoldDetection(
                hold_id=int(hold["hold_id"]),
                cx_px=float(hold["center_px"]["x"]),
                cy_px=float(hold["center_px"]["y"]),
                radius_px=radius_px,
                x1=x1,
                y1=y1,
                x2=x2,
                y2=y2,
                confidence=float(hold.get("confidence", 0.0)),
            )
        )
        max_x = max(max_x, x2)
        max_y = max(max_y, y2)

    return {
        "source_file": payload.get("source", {}).get("legacy_source_file") or payload.get("source", {}).get("path"),
        "detection_count": len(hold_objects),
        "bbox_extent_px": [max_x, max_y],
        "holds": hold_objects,
    }


def sample_detected_frames(frames: list[dict[str, Any]], sample_count: int) -> list[dict[str, Any]]:
    detected = [frame for frame in frames if frame.get("pose_detected") and frame.get("pose_world_landmarks")]
    if not detected:
        return []
    if len(detected) <= sample_count:
        return detected
    indices = np.linspace(0, len(detected) - 1, sample_count, dtype=int)
    return [detected[int(idx)] for idx in indices]


def collect_target_metrics_from_pose_frames(
    pose_payload: dict[str, Any],
    calibration: dict[str, float],
    sample_count: int,
) -> dict[str, Any]:
    mapper = MetricSkeletonMapper(calibration)
    sample_frames = sample_detected_frames(list(pose_payload["frames"]), sample_count)

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
    for frame in sample_frames:
        world_landmarks = landmark_payload_to_objects(frame.get("pose_world_landmarks"))
        if not world_landmarks:
            continue
        points = mapper.map_frame(world_landmarks)
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

    if detected_frames == 0:
        raise RuntimeError("No detected pose frames available to collect target metrics")

    return {
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


def ensure_personalized_model(
    base_xml: Path,
    cache_dir: Path,
    applied_metrics: dict[str, Any],
    target_metrics: dict[str, Any],
) -> tuple[Path, bool]:
    cache_dir.mkdir(parents=True, exist_ok=True)
    fingerprint_payload = {
        "base_xml": str(base_xml.resolve()),
        "base_xml_mtime_ns": base_xml.stat().st_mtime_ns,
        "applied_metrics_m": applied_metrics,
        "target_metrics_m": target_metrics,
    }
    fingerprint = hashlib.sha256(
        json.dumps(fingerprint_payload, ensure_ascii=False, sort_keys=True).encode("utf-8")
    ).hexdigest()[:16]
    xml_path = cache_dir / f"personalized_{fingerprint}.xml"
    meta_path = cache_dir / f"personalized_{fingerprint}.json"
    if xml_path.exists() and meta_path.exists():
        return xml_path, True

    apply_personalization(
        template_xml=base_xml,
        output_xml=xml_path,
        metrics={str(key): float(value) for key, value in applied_metrics.items()},
        target_metrics=target_metrics,
    )
    meta_path.write_text(json.dumps(fingerprint_payload, ensure_ascii=False, indent=2), encoding="utf-8")
    return xml_path, False


def should_retry_high_confidence(limb_states: dict[str, dict[str, Any]] | None) -> bool:
    if limb_states is None:
        return False
    states = [str(payload.get("state", "FREE")) for payload in limb_states.values()]
    engaged = sum(1 for state in states if state in ("GRIP", "STEP"))
    has_transition = any(state in ("REACH", "RELEASE") for state in states)
    return engaged >= 2 and not has_transition


def interpolate_qpos_pair(model: mujoco.MjModel, qpos_a: np.ndarray, qpos_b: np.ndarray, alpha: float) -> np.ndarray:
    alpha = float(np.clip(alpha, 0.0, 1.0))
    out = (1.0 - alpha) * np.asarray(qpos_a, dtype=np.float64) + alpha * np.asarray(qpos_b, dtype=np.float64)
    if model.nq >= 7:
        quat = np.asarray(out[3:7], dtype=np.float64)
        quat_norm = float(np.linalg.norm(quat))
        if quat_norm > 1e-8:
            out[3:7] = quat / quat_norm
    return out


def interpolate_missing_qpos(records: list[dict[str, Any]], model: mujoco.MjModel) -> int:
    valid_indices = [idx for idx, record in enumerate(records) if record.get("qpos") is not None]
    if not valid_indices:
        return 0

    interpolated_count = 0
    first_valid = valid_indices[0]
    first_qpos = np.asarray(records[first_valid]["qpos"], dtype=np.float64).copy()
    for idx in range(0, first_valid):
        if records[idx].get("qpos") is None:
            records[idx]["qpos"] = first_qpos.copy()
            records[idx]["pose_mode"] = "backfilled_initial"
            records[idx]["frozen"] = True
            records[idx]["fit_mean_error_m"] = None
            records[idx]["fit_max_error_m"] = None
            records[idx]["fit_final_error_norm"] = None
            records[idx]["lower_limb_consistency"] = {}

    last_valid = valid_indices[-1]
    last_qpos = np.asarray(records[last_valid]["qpos"], dtype=np.float64).copy()
    for idx in range(last_valid + 1, len(records)):
        if records[idx].get("qpos") is None:
            records[idx]["qpos"] = last_qpos.copy()
            records[idx]["pose_mode"] = "filled_gap"
            records[idx]["frozen"] = True
            records[idx]["fit_mean_error_m"] = None
            records[idx]["fit_max_error_m"] = None
            records[idx]["fit_final_error_norm"] = None
            records[idx]["lower_limb_consistency"] = {}

    cursor = first_valid
    while cursor < last_valid:
        next_valid = cursor + 1
        while next_valid <= last_valid and records[next_valid].get("qpos") is None:
            next_valid += 1
        if next_valid > last_valid:
            break
        if next_valid == cursor + 1:
            cursor = next_valid
            continue
        qpos_a = np.asarray(records[cursor]["qpos"], dtype=np.float64)
        qpos_b = np.asarray(records[next_valid]["qpos"], dtype=np.float64)
        t0 = float(records[cursor]["timestamp_ms"])
        t1 = float(records[next_valid]["timestamp_ms"])
        span = max(t1 - t0, 1.0)
        for idx in range(cursor + 1, next_valid):
            if records[idx].get("qpos") is not None:
                continue
            alpha = (float(records[idx]["timestamp_ms"]) - t0) / span
            records[idx]["qpos"] = interpolate_qpos_pair(model, qpos_a, qpos_b, alpha)
            records[idx]["pose_mode"] = "interpolated"
            records[idx]["frozen"] = False
            records[idx]["fit_mean_error_m"] = None
            records[idx]["fit_max_error_m"] = None
            records[idx]["fit_final_error_norm"] = None
            records[idx]["lower_limb_consistency"] = {}
            interpolated_count += 1
        cursor = next_valid

    return interpolated_count


def evaluate_from_json_inputs(
    xml_path: Path,
    holds_json: Path,
    pose_json: Path,
    user_body_json: Path,
    frame_step: int,
    sample_count: int,
    ik_iterations: int,
    damping: float,
    smoothing_window: int,
    top_k_joints: int,
    cache_dir: Path,
    fit_frame_step: int = 1,
    retry_high_confidence_only: bool = False,
    keep_qpos: bool = False,
    holds_payload_override: dict[str, Any] | None = None,
    pose_payload_override: dict[str, Any] | None = None,
    user_body_payload_override: dict[str, Any] | None = None,
) -> dict[str, Any]:
    started = time.perf_counter()

    load_inputs_started = time.perf_counter()
    holds_payload = holds_payload_override if holds_payload_override is not None else load_service_holds(holds_json)
    pose_payload = pose_payload_override if pose_payload_override is not None else json.loads(pose_json.read_text(encoding="utf-8"))
    user_body_payload = (
        user_body_payload_override
        if user_body_payload_override is not None
        else json.loads(user_body_json.read_text(encoding="utf-8"))
    )
    calibration = calibration_from_user_body(user_body_payload)
    video_metadata = pose_payload.get("video_metadata", {})
    frame_width = int(video_metadata.get("frame_width", 0))
    frame_height = int(video_metadata.get("frame_height", 0))
    fps = float(video_metadata.get("fps", 30.0))
    total_frames = int(video_metadata.get("total_frames", len(pose_payload.get("frames", []))))
    frames_input = list(pose_payload.get("frames", []))
    tracker = HoldContactTracker(holds_payload["holds"])
    load_inputs_s = float(time.perf_counter() - load_inputs_started)

    prepare_model_started = time.perf_counter()
    target_metrics = collect_target_metrics_from_pose_frames(pose_payload, calibration, sample_count)
    applied_metrics = build_personalization_metrics(calibration, target_metrics)
    applied_metrics["body_mass_kg"] = float(user_body_payload["user_profile"]["weight_kg"])
    personalized_xml, cache_hit = ensure_personalized_model(
        base_xml=xml_path,
        cache_dir=cache_dir,
        applied_metrics=applied_metrics,
        target_metrics=target_metrics,
    )
    model = mujoco.MjModel.from_xml_path(str(personalized_xml.resolve()))
    data = mujoco.MjData(model)
    required_sites = tuple(SITE_TARGETS.keys()) + tuple(POLE_TARGETS.keys()) + tuple(AUX_SITE_TARGETS.keys())
    site_ids = {
        site_name: mujoco.mj_name2id(model, mujoco.mjtObj.mjOBJ_SITE, site_name)
        for site_name in required_sites
    }
    prepare_model_s = float(time.perf_counter() - prepare_model_started)

    fit_started = time.perf_counter()
    mapper = MetricSkeletonMapper(calibration)
    frames: list[dict[str, Any]] = []
    prev_qpos: np.ndarray | None = None
    prev_target_points: dict[str, np.ndarray] | None = None
    last_good_bundle: dict[str, object] | None = None
    fitted_frame_count = 0
    interpolated_frame_count = 0
    retry_attempt_count = 0
    retry_applied_count = 0

    for raw_frame in frames_input:
        frame_index = int(raw_frame["frame_index"])
        if frame_step > 1 and frame_index % frame_step != 0:
            continue

        timestamp_ms = int(raw_frame["timestamp_ms"])
        pose_landmarks_2d = landmark_payload_to_objects(raw_frame.get("pose_landmarks"))
        contact_points = compute_contact_points_px(pose_landmarks_2d, frame_width, frame_height)
        limb_states = tracker.update_frame(contact_points, timestamp_ms)

        base_record: dict[str, Any] = {
            "frame_index": frame_index,
            "timestamp_ms": timestamp_ms,
            "detected": bool(raw_frame.get("pose_detected")),
            "limb_states": limb_states or {},
            "qpos": None,
            "pose_mode": "missing",
            "frozen": False,
            "fit_mean_error_m": None,
            "fit_max_error_m": None,
            "fit_final_error_norm": None,
            "lower_limb_consistency": {},
        }
        should_fit_frame = fit_frame_step <= 1 or (frame_index % fit_frame_step == 0)

        world_landmarks = landmark_payload_to_objects(raw_frame.get("pose_world_landmarks"))
        if not world_landmarks:
            if last_good_bundle is not None:
                frozen = dyn.clone_pose_bundle(last_good_bundle)
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
            continue

        mapper_snapshot = mapper.snapshot_state()
        target_points = mapper.map_frame(world_landmarks)
        mean_jump, max_jump = dyn.target_jump_stats(target_points, prev_target_points)
        if prev_target_points is not None and (max_jump > dyn.MAX_TARGET_JUMP_M or mean_jump > dyn.MEAN_TARGET_JUMP_M):
            mapper.restore_state(mapper_snapshot)
            if last_good_bundle is not None:
                frozen = dyn.clone_pose_bundle(last_good_bundle)
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
            continue

        if not should_fit_frame:
            base_record.update(
                {
                    "pose_mode": "interpolated_pending",
                    "frozen": False,
                }
            )
            frames.append(base_record)
            prev_target_points = {key: value.copy() for key, value in target_points.items()}
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
        allow_retry = True
        if retry_high_confidence_only:
            allow_retry = should_retry_high_confidence(limb_states)
        if allow_retry and prev_qpos is not None and (
            float(fit["mean_error_m"]) > dyn.BAD_FIT_MEAN_ERROR_M
            or float(fit["max_error_m"]) > dyn.BAD_FIT_MAX_ERROR_M
        ):
            retry_attempt_count += 1
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
                retry_applied_count += 1
        if allow_retry and dyn.has_bad_lower_limb_consistency(fit):
            retry_attempt_count += 1
            retry = fit_static_pose(
                model=model,
                data=data,
                site_ids=site_ids,
                target_points=target_points,
                seed_qpos=None,
                iterations=ik_iterations,
                damping=damping,
            )
            if not dyn.has_bad_lower_limb_consistency(retry) or float(retry["mean_error_m"]) < float(fit["mean_error_m"]):
                fit = retry
                retry_applied_count += 1

        if (
            float(fit["mean_error_m"]) > dyn.BAD_FIT_MEAN_ERROR_M
            or float(fit["max_error_m"]) > dyn.BAD_FIT_MAX_ERROR_M
            or dyn.has_bad_lower_limb_consistency(fit)
        ):
            mapper.restore_state(mapper_snapshot)
            if last_good_bundle is not None:
                frozen = dyn.clone_pose_bundle(last_good_bundle)
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
        fitted_frame_count += 1

    interpolated_frame_count = interpolate_missing_qpos(frames, model)
    dyn.fill_missing_qpos(frames)
    qpos_seq = np.asarray([np.asarray(frame["qpos"], dtype=np.float64) for frame in frames], dtype=np.float64)
    timestamps_ms = np.asarray([float(frame["timestamp_ms"]) for frame in frames], dtype=np.float64)
    qvel_seq = dyn.compute_qvel_sequence(model, qpos_seq, timestamps_ms)
    qvel_seq = dyn.moving_average_2d(qvel_seq, smoothing_window)
    qacc_seq = dyn.compute_qacc_sequence(qvel_seq, timestamps_ms)
    qacc_seq = dyn.moving_average_2d(qacc_seq, smoothing_window)
    fit_sequence_s = float(time.perf_counter() - fit_started)

    inverse_started = time.perf_counter()
    joint_force_history: list[dict[str, dict[str, float]]] = []
    body_load_history: list[dict[str, float]] = []
    pose_mode_counts: dict[str, int] = {}
    phase_counts: dict[str, int] = {}
    support_mode_counts: dict[str, int] = {}

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

        com = dyn.compute_model_com(model, data)
        active_contact_limbs, active_hold_ids = dyn.extract_active_contacts(frame.get("limb_states"))
        support_center, used_support_limbs, support_mode = dyn.compute_support_center(data, site_ids, active_contact_limbs)
        support_points_xyz = {
            limb_name: np.asarray(data.site_xpos[site_ids[dyn.SUPPORT_SITE_BY_LIMB[limb_name]]], dtype=np.float64)
            for limb_name in used_support_limbs
        }
        support_stability = analyze_support_stability(com, support_points_xyz)
        joint_inverse_forces = dyn.collect_joint_inverse_forces(model, data)
        body_loads = dyn.summarize_body_loads(joint_inverse_forces)
        root_inverse_force = np.asarray(data.qfrc_inverse[:6], dtype=np.float64)
        contact_modes = {
            limb_name: str(frame["limb_states"].get(limb_name, {}).get("state", "MOVE"))
            for limb_name in active_contact_limbs
        }
        active_contact_positions = {
            limb_name: np.asarray(data.site_xpos[site_ids[dyn.SUPPORT_SITE_BY_LIMB[limb_name]]], dtype=np.float64)
            for limb_name in active_contact_limbs
        }
        contact_force_distribution = estimate_contact_forces(
            root_position_xyz=np.asarray(data.qpos[0:3], dtype=np.float64),
            required_wrench=root_inverse_force,
            contact_positions_xyz=active_contact_positions,
            contact_modes=contact_modes,
        )

        root_linear_speed = float(np.linalg.norm(qvel[0:3]))
        phase = dyn.classify_phase(
            pose_mode=str(frame["pose_mode"]),
            frozen=bool(frame["frozen"]),
            limb_states=frame.get("limb_states"),
            active_contact_limbs=active_contact_limbs,
            support_type=str(support_stability.get("support_type")),
            root_speed_m_s=root_linear_speed,
        )
        confidence = dyn.classify_confidence(
            pose_mode=str(frame["pose_mode"]),
            support_mode=support_mode,
            support_type=str(support_stability.get("support_type")),
        )

        pose_mode_counts[str(frame["pose_mode"])] = pose_mode_counts.get(str(frame["pose_mode"]), 0) + 1
        phase_counts[phase] = phase_counts.get(phase, 0) + 1
        support_mode_counts[support_mode] = support_mode_counts.get(support_mode, 0) + 1
        joint_force_history.append(joint_inverse_forces)
        body_load_history.append(body_loads)

        frame["active_contact_limbs"] = active_contact_limbs
        frame["active_hold_ids"] = active_hold_ids
        frame["support_mode"] = support_mode
        frame["used_support_limbs"] = used_support_limbs
        frame["support_stability"] = support_stability
        frame["phase"] = phase
        frame["analysis_confidence"] = confidence
        frame["root_inverse_force"] = root_inverse_force.tolist()
        frame["body_loads"] = body_loads
        frame["contact_force_distribution"] = contact_force_distribution
        frame["top_joint_loads"] = dyn.top_joint_loads(joint_inverse_forces, top_k_joints)
        frame["joint_loads"] = {
            joint_name: float(payload["qfrc_inverse"])
            for joint_name, payload in joint_inverse_forces.items()
        }
        frame["com_position_m"] = com.tolist()
        frame["estimated_contact_forces_n"] = contact_force_distribution.get("contact_forces", {})
        if not keep_qpos:
            frame.pop("qpos", None)

    inverse_dynamics_s = float(time.perf_counter() - inverse_started)

    report = {
        "schema_version": "1.0.0",
        "mode": "json_only_service_benchmark",
        "inputs": {
            "holds_json": str(holds_json.resolve()),
            "pose3d_sequence_json": str(pose_json.resolve()),
            "user_body_json": str(user_body_json.resolve()),
            "base_xml": str(xml_path.resolve()),
            "personalized_xml": str(personalized_xml.resolve()),
        },
        "benchmark_timings_s": {
            "load_inputs_s": load_inputs_s,
            "prepare_model_s": prepare_model_s,
            "fit_sequence_s": fit_sequence_s,
            "inverse_dynamics_s": inverse_dynamics_s,
            "serialize_s": None,
            "total_s": None,
        },
        "model_cache": {
            "cache_dir": str(cache_dir.resolve()),
            "personalized_xml_cache_hit": cache_hit,
        },
        "personalization": {
            "target_metrics_m": target_metrics,
            "applied_metrics_m": applied_metrics,
        },
        "video_metadata": {
            "frame_width": frame_width,
            "frame_height": frame_height,
            "fps": fps,
            "total_frames": total_frames,
            "processed_frames": len(frames),
            "frame_step": frame_step,
            "fit_frame_step": fit_frame_step,
        },
        "fit_optimization": {
            "ik_iterations": ik_iterations,
            "retry_high_confidence_only": bool(retry_high_confidence_only),
            "fitted_frame_count": fitted_frame_count,
            "interpolated_frame_count": interpolated_frame_count,
            "retry_attempt_count": retry_attempt_count,
            "retry_applied_count": retry_applied_count,
        },
        "pose_mode_counts": pose_mode_counts,
        "phase_counts": phase_counts,
        "support_mode_counts": support_mode_counts,
        "hold_state_summary": dyn.summarize_hold_states(frames),
        "joint_load_summary": dyn.summarize_joint_load_history(joint_force_history),
        "body_load_summary": dyn.summarize_body_load_history(body_load_history),
        "contact_force_distribution_summary": summarize_contact_force_history(frames),
        "support_stability_summary": dyn.summarize_support_stability(frames),
        "dynamic_sequence_gate": dyn.sequence_gate_decision(frames),
        "frames": [
            {
                "frame_index": int(frame["frame_index"]),
                "timestamp_ms": int(frame["timestamp_ms"]),
                "pose_mode": frame["pose_mode"],
                "phase": frame["phase"],
                "analysis_confidence": frame["analysis_confidence"],
                "active_contact_limbs": frame["active_contact_limbs"],
                "active_hold_ids": frame["active_hold_ids"],
                "support_mode": frame["support_mode"],
                "support_stability": frame["support_stability"],
                "joint_loads": frame["joint_loads"],
                "top_joint_loads": frame["top_joint_loads"],
                "body_loads": frame["body_loads"],
                "com_position_m": frame["com_position_m"],
                "estimated_contact_forces_n": frame["estimated_contact_forces_n"],
                "contact_force_status": frame["contact_force_distribution"].get("status"),
                "contact_force_relative_residual": frame["contact_force_distribution"].get("relative_residual"),
                **(
                    {
                        "qpos": np.asarray(frame["qpos"], dtype=np.float64).tolist(),
                    }
                    if keep_qpos and frame.get("qpos") is not None
                    else {}
                ),
            }
            for frame in frames
        ],
    }

    serialize_started = time.perf_counter()
    _ = json.dumps(report, ensure_ascii=False, indent=2)
    serialize_s = float(time.perf_counter() - serialize_started)
    report["benchmark_timings_s"]["serialize_s"] = serialize_s
    report["benchmark_timings_s"]["total_s"] = float(time.perf_counter() - started)
    return report


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run the MuJoCo physics pipeline from 3 JSON inputs only.")
    parser.add_argument("--holds-json", type=Path, default=DEFAULT_HOLDS_JSON)
    parser.add_argument("--pose-json", type=Path, default=DEFAULT_POSE_JSON)
    parser.add_argument("--user-body-json", type=Path, default=DEFAULT_USER_BODY_JSON)
    parser.add_argument("--xml", type=Path, default=DEFAULT_XML)
    parser.add_argument("--cache-dir", type=Path, default=DEFAULT_CACHE_DIR)
    parser.add_argument("--frame-step", type=int, default=1)
    parser.add_argument("--sample-count", type=int, default=24)
    parser.add_argument("--ik-iters", type=int, default=45)
    parser.add_argument("--ik-damping", type=float, default=1e-3)
    parser.add_argument("--smoothing-window", type=int, default=5)
    parser.add_argument("--top-k-joints", type=int, default=5)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    return parser.parse_args()


def main() -> None:
    args = parse_args()

    report = evaluate_from_json_inputs(
        xml_path=args.xml.resolve(),
        holds_json=args.holds_json.resolve(),
        pose_json=args.pose_json.resolve(),
        user_body_json=args.user_body_json.resolve(),
        frame_step=max(1, int(args.frame_step)),
        sample_count=max(4, int(args.sample_count)),
        ik_iterations=max(1, int(args.ik_iters)),
        damping=float(args.ik_damping),
        smoothing_window=max(1, int(args.smoothing_window)),
        top_k_joints=max(1, int(args.top_k_joints)),
        cache_dir=args.cache_dir.resolve(),
    )

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(
        json.dumps(
            {
                "output": str(args.output.resolve()),
                "benchmark_timings_s": report["benchmark_timings_s"],
                "processed_frames": report["video_metadata"]["processed_frames"],
                "cache_hit": report["model_cache"]["personalized_xml_cache_hit"],
                "dynamic_sequence_gate": report["dynamic_sequence_gate"],
            },
            ensure_ascii=False,
            indent=2,
        )
    )


if __name__ == "__main__":
    main()

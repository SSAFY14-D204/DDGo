from __future__ import annotations

import argparse
import json
from pathlib import Path

import cv2
import mediapipe as mp
import mujoco
import numpy as np

from evaluate_static_fit import (
    AUX_SITE_TARGETS,
    DEFAULT_XML,
    POLE_TARGETS,
    ROOT,
    SITE_TARGETS,
    fit_static_pose,
    sample_frame_indices,
)

CUSTOM_SKELETON_ROOT = ROOT.parent / "custom_skeleton_verify"

from mediapipe_custom_skeleton_verify import MetricSkeletonMapper, make_landmarker  # noqa: E402
from physics_worker import load_calibration_json  # noqa: E402
from hold_contact_state import (  # noqa: E402
    HoldContactTracker,
    compute_contact_points_px,
    load_hold_detections,
)
from support_stability import analyze_support_stability  # noqa: E402


def compute_model_com(model: mujoco.MjModel, data: mujoco.MjData) -> np.ndarray:
    masses = np.asarray(model.body_mass[1:], dtype=np.float64)
    if masses.size == 0:
        return np.zeros(3, dtype=np.float64)
    positions = np.asarray(data.xipos[1:], dtype=np.float64)
    total_mass = float(np.sum(masses))
    if total_mass <= 1e-8:
        return np.zeros(3, dtype=np.float64)
    return np.sum(positions * masses[:, None], axis=0) / total_mass


SUPPORT_SITE_BY_LIMB = {
    "left_hand": "left_hand_site",
    "right_hand": "right_hand_site",
    "left_foot": "left_foot_site",
    "right_foot": "right_foot_site",
}


def compute_support_center(
    data: mujoco.MjData,
    site_ids: dict[str, int],
    active_limbs: list[str] | None = None,
) -> tuple[np.ndarray, list[str], str]:
    used_support_limbs = [limb for limb in (active_limbs or []) if limb in SUPPORT_SITE_BY_LIMB]
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


def summarize_joint_loads(sample_reports: list[dict[str, object]]) -> dict[str, dict[str, float]]:
    bucket: dict[str, list[float]] = {}
    for report in sample_reports:
        joint_forces = report.get("joint_inverse_forces", {})
        for joint_name, payload in joint_forces.items():
            bucket.setdefault(joint_name, []).append(abs(float(payload["qfrc_inverse"])))

    summary: dict[str, dict[str, float]] = {}
    for joint_name, values in bucket.items():
        arr = np.asarray(values, dtype=np.float64)
        summary[joint_name] = {
            "mean_abs_qfrc_inverse": float(np.mean(arr)),
            "max_abs_qfrc_inverse": float(np.max(arr)),
            "p95_abs_qfrc_inverse": float(np.percentile(arr, 95)),
        }
    return summary


def summarize_support_stability(sample_reports: list[dict[str, object]]) -> dict[str, object]:
    support_type_counts: dict[str, int] = {}
    inside_count = 0
    outside_count = 0
    margins: list[float] = []
    for report in sample_reports:
        stability = report.get("support_stability")
        if not stability:
            continue
        support_type = str(stability.get("support_type"))
        support_type_counts[support_type] = support_type_counts.get(support_type, 0) + 1
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
        "support_type_counts": support_type_counts,
        "inside_support_count": inside_count,
        "outside_support_count": outside_count,
        "stability_margin_summary_m": margin_summary,
    }


def gate2_decision(valid_reports: list[dict[str, object]]) -> dict[str, object]:
    if not valid_reports:
        return {
            "passed": False,
            "failures": ["no_detected_sample_frames"],
        }

    failures: list[str] = []
    fit_means = np.asarray([float(report["fit_mean_error_m"]) for report in valid_reports], dtype=np.float64)
    if float(np.mean(fit_means)) > 0.12:
        failures.append("fit_mean_error_above_12cm")

    finite_ok = all(
        np.isfinite(report["com_position_m"]).all() and np.isfinite(report["support_center_m"]).all()
        for report in valid_reports
    )
    if not finite_ok:
        failures.append("non_finite_com_or_support")

    max_joint_force = 0.0
    for report in valid_reports:
        for payload in report.get("joint_inverse_forces", {}).values():
            max_joint_force = max(max_joint_force, abs(float(payload["qfrc_inverse"])))
    if not np.isfinite(max_joint_force):
        failures.append("non_finite_joint_inverse_force")

    return {
        "passed": len(failures) == 0,
        "failures": failures,
        "fit_mean_error_m": float(np.mean(fit_means)),
        "max_abs_joint_inverse_force": float(max_joint_force),
    }


def evaluate_video(
    xml_path: Path,
    video_path: Path,
    task_path: Path,
    calibration: dict[str, float] | None,
    detections_json: Path | None,
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
    frame_width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH) or 0)
    frame_height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT) or 0)
    fps = float(cap.get(cv2.CAP_PROP_FPS) or 30.0)
    frame_count = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    sample_indices = sample_frame_indices(frame_count, sample_count)
    sample_set = set(sample_indices)

    landmarker = make_landmarker(task_path)
    mapper = MetricSkeletonMapper(calibration)
    hold_payload = None
    tracker = None
    if detections_json is not None:
        hold_payload = load_hold_detections(detections_json)
        tracker = HoldContactTracker(hold_payload["holds"])
    sample_reports: list[dict[str, object]] = []
    prev_qpos: np.ndarray | None = None
    frame_idx = 0

    while True:
        ok, frame_bgr = cap.read()
        if not ok:
            break

        timestamp_ms = int(round((frame_idx / max(fps, 1.0)) * 1000.0))
        frame_rgb = cv2.cvtColor(frame_bgr, cv2.COLOR_BGR2RGB)
        mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=frame_rgb)
        result = landmarker.detect_for_video(mp_image, timestamp_ms)
        pose_landmarks_2d = result.pose_landmarks[0] if result.pose_landmarks else None
        limb_states = None
        if tracker is not None:
            contact_points = compute_contact_points_px(pose_landmarks_2d, frame_width, frame_height)
            limb_states = tracker.update_frame(contact_points, timestamp_ms)

        if frame_idx not in sample_set:
            frame_idx += 1
            continue

        if not result.pose_world_landmarks:
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
        fit = fit_static_pose(model, data, site_ids, target_points, prev_qpos, ik_iterations, damping)
        prev_qpos = fit["qpos"].copy()

        data.qpos[:] = fit["qpos"]
        data.qvel[:] = 0.0
        data.qacc[:] = 0.0
        data.qfrc_applied[:] = 0.0
        data.xfrc_applied[:] = 0.0
        mujoco.mj_forward(model, data)
        # Keep the fitted pose as a static configuration. mj_forward updates qacc
        # to the free-dynamics solution, so zero it again before inverse dynamics.
        data.qvel[:] = 0.0
        data.qacc[:] = 0.0
        mujoco.mj_inverse(model, data)

        com = compute_model_com(model, data)
        active_contact_limbs: list[str] = []
        active_hold_ids: dict[str, int] = {}
        if limb_states is not None:
            for limb_name, payload in limb_states.items():
                hold_id = payload.get("active_hold_id")
                if str(payload["state"]) in ("GRIP", "STEP") and hold_id is not None:
                    active_contact_limbs.append(limb_name)
                    active_hold_ids[limb_name] = int(hold_id)
        support_center, used_support_limbs, support_mode = compute_support_center(data, site_ids, active_contact_limbs)
        support_points_xyz = {
            limb_name: np.asarray(data.site_xpos[site_ids[SUPPORT_SITE_BY_LIMB[limb_name]]], dtype=np.float64)
            for limb_name in used_support_limbs
        }
        support_stability = analyze_support_stability(com, support_points_xyz)
        joint_inverse_forces = collect_joint_inverse_forces(model, data)

        sample_reports.append(
            {
                "frame_index": frame_idx,
                "timestamp_ms": timestamp_ms,
                "detected": True,
                "fit_mean_error_m": float(fit["mean_error_m"]),
                "fit_max_error_m": float(fit["max_error_m"]),
                "fit_final_error_norm": float(fit["final_error_norm"]),
                "com_position_m": com.tolist(),
                "support_center_m": support_center.tolist(),
                "com_support_offset_m": (com - support_center).tolist(),
                "support_mode": support_mode,
                "active_contact_limbs": active_contact_limbs,
                "used_support_limbs": used_support_limbs,
                "active_hold_ids": active_hold_ids,
                "support_stability": support_stability,
                "root_inverse_force": [float(v) for v in data.qfrc_inverse[:6]],
                "joint_inverse_forces": joint_inverse_forces,
            }
        )
        frame_idx += 1

    cap.release()
    landmarker.close()

    valid_reports = [report for report in sample_reports if report.get("detected")]
    joint_load_summary = summarize_joint_loads(valid_reports)
    support_stability_summary = summarize_support_stability(valid_reports)
    gate2 = gate2_decision(valid_reports)
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

    return {
        "xml": str(xml_path.resolve()),
        "video": str(video_path.resolve()),
        "detections_json": None if detections_json is None else str(detections_json.resolve()),
        "hold_source_file": hold_source_file,
        "detection_video_scale_check": detection_video_scale,
        "sample_frame_indices": sample_indices,
        "sample_reports": sample_reports,
        "joint_load_summary": joint_load_summary,
        "support_stability_summary": support_stability_summary,
        "gate2_static_inverse_dynamics": gate2,
        "processed_sample_frames": len(valid_reports),
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Evaluate static inverse dynamics on sampled articulated fit frames.")
    parser.add_argument("--xml", type=Path, default=DEFAULT_XML)
    parser.add_argument("--input-video", type=Path, default=ROOT.parent / "video" / "주황.mp4")
    parser.add_argument("--task-model", type=Path, default=CUSTOM_SKELETON_ROOT / "pose_landmarker_lite.task")
    parser.add_argument("--calibration-json", type=Path, default=CUSTOM_SKELETON_ROOT / "calibration.json")
    parser.add_argument("--detections-json", type=Path, default=ROOT.parent / "detections.json")
    parser.add_argument("--sample-count", type=int, default=8)
    parser.add_argument("--ik-iters", type=int, default=60)
    parser.add_argument("--ik-damping", type=float, default=1e-3)
    parser.add_argument("--output", type=Path, default=ROOT / "gate2_static_inverse_dynamics_report.json")
    args = parser.parse_args()

    calibration = load_calibration_json(args.calibration_json)
    report = evaluate_video(
        xml_path=args.xml,
        video_path=args.input_video,
        task_path=args.task_model,
        calibration=calibration,
        detections_json=args.detections_json,
        sample_count=args.sample_count,
        ik_iterations=args.ik_iters,
        damping=args.ik_damping,
    )
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(report["gate2_static_inverse_dynamics"], ensure_ascii=False, indent=2))
    print(f"[OK] Wrote {args.output.resolve()}")


if __name__ == "__main__":
    main()

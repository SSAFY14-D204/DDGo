from __future__ import annotations

import argparse
import json
from pathlib import Path

import cv2
import mediapipe as mp
import numpy as np

from mediapipe_custom_skeleton_verify import (
    LEFT_ANKLE,
    LEFT_ELBOW,
    LEFT_HIP,
    LEFT_KNEE,
    LEFT_SHOULDER,
    MetricSkeletonMapper,
    RIGHT_ANKLE,
    RIGHT_ELBOW,
    RIGHT_HIP,
    RIGHT_KNEE,
    RIGHT_SHOULDER,
    make_landmarker,
)
from physics_worker import load_calibration_json


WORLD_TO_2D_KEYS = {
    "left_shoulder": LEFT_SHOULDER,
    "right_shoulder": RIGHT_SHOULDER,
    "left_elbow": LEFT_ELBOW,
    "right_elbow": RIGHT_ELBOW,
    "left_hip": LEFT_HIP,
    "right_hip": RIGHT_HIP,
    "left_knee": LEFT_KNEE,
    "right_knee": RIGHT_KNEE,
    "left_ankle": LEFT_ANKLE,
    "right_ankle": RIGHT_ANKLE,
}

SEGMENT_SPECS = {
    "left_upper_arm": ("left_shoulder", "left_elbow", "upper_arm_m"),
    "right_upper_arm": ("right_shoulder", "right_elbow", "upper_arm_m"),
    "left_forearm": ("left_elbow", "left_hand", "forearm_m"),
    "right_forearm": ("right_elbow", "right_hand", "forearm_m"),
    "left_thigh": ("left_hip", "left_knee", "thigh_m"),
    "right_thigh": ("right_hip", "right_knee", "thigh_m"),
    "left_shin": ("left_knee", "left_ankle", "shin_m"),
    "right_shin": ("right_knee", "right_ankle", "shin_m"),
    "shoulder_width": ("left_shoulder", "right_shoulder", "shoulder_width_m"),
    "torso_length": ("pelvis", "thorax", "torso_length_m"),
}


def fit_affine_world_to_image(
    points_world: dict[str, np.ndarray],
    pose_landmarks: list,
    frame_width: int,
    frame_height: int,
) -> float | None:
    src_xy: list[list[float]] = []
    dst_xy: list[list[float]] = []
    for point_name, landmark_idx in WORLD_TO_2D_KEYS.items():
        world = np.asarray(points_world[point_name], dtype=np.float64)
        lm = pose_landmarks[landmark_idx]
        src_xy.append([float(world[0]), float(world[1]), 1.0])
        dst_xy.append([float(lm.x) * frame_width, float(lm.y) * frame_height])
    if len(src_xy) < 3:
        return None
    src = np.asarray(src_xy, dtype=np.float64)
    dst = np.asarray(dst_xy, dtype=np.float64)
    coeff_x, *_ = np.linalg.lstsq(src, dst[:, 0], rcond=None)
    coeff_y, *_ = np.linalg.lstsq(src, dst[:, 1], rcond=None)
    pred_x = src @ coeff_x
    pred_y = src @ coeff_y
    pred = np.stack([pred_x, pred_y], axis=1)
    errors = np.linalg.norm(pred - dst, axis=1)
    return float(np.mean(errors))


def summarize_values(values: list[float], target_value: float | None = None) -> dict[str, float | None]:
    arr = np.asarray(values, dtype=np.float64)
    summary: dict[str, float | None] = {
        "mean": float(np.mean(arr)),
        "std": float(np.std(arr)),
        "min": float(np.min(arr)),
        "max": float(np.max(arr)),
        "median": float(np.median(arr)),
    }
    if target_value is not None:
        target = float(target_value)
        abs_errors = np.abs(arr - target)
        summary["target_m"] = target
        summary["mean_abs_error_m"] = float(np.mean(abs_errors))
        summary["max_abs_error_m"] = float(np.max(abs_errors))
    return summary


def evaluate_video(
    input_video: Path,
    task_model: Path,
    calibration_json: Path | None,
) -> dict[str, object]:
    calibration = load_calibration_json(calibration_json)
    mapper = MetricSkeletonMapper(calibration)
    landmarker = make_landmarker(task_model)

    cap = cv2.VideoCapture(str(input_video))
    if not cap.isOpened():
        raise FileNotFoundError(f"Input video not found: {input_video}")

    fps = max(cap.get(cv2.CAP_PROP_FPS), 30.0)
    total_frames = 0
    frames_with_pose = 0
    reprojection_errors_px: list[float] = []
    segment_length_samples: dict[str, list[float]] = {key: [] for key in SEGMENT_SPECS}

    while True:
        ok, frame_bgr = cap.read()
        if not ok:
            break
        total_frames += 1
        frame_rgb = cv2.cvtColor(frame_bgr, cv2.COLOR_BGR2RGB)
        mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=frame_rgb)
        ts_ms = int((total_frames - 1) * 1000.0 / fps)
        result = landmarker.detect_for_video(mp_image, ts_ms)
        if not result.pose_world_landmarks:
            continue

        frames_with_pose += 1
        points_world = mapper.map_frame(result.pose_world_landmarks[0])
        for segment_name, (start_key, end_key, _) in SEGMENT_SPECS.items():
            length = float(np.linalg.norm(points_world[end_key] - points_world[start_key]))
            segment_length_samples[segment_name].append(length)

        reproj = fit_affine_world_to_image(
            points_world,
            result.pose_landmarks[0],
            frame_bgr.shape[1],
            frame_bgr.shape[0],
        )
        if reproj is not None:
            reprojection_errors_px.append(reproj)

    cap.release()

    pose_detect_rate = float(frames_with_pose / total_frames) if total_frames else 0.0
    segment_summaries: dict[str, dict[str, float | None]] = {}
    for segment_name, values in segment_length_samples.items():
        if not values:
            continue
        _, _, calibration_key = SEGMENT_SPECS[segment_name]
        target_value = None
        if calibration is not None and calibration_key in calibration:
            target_value = float(calibration[calibration_key])
        segment_summaries[segment_name] = summarize_values(values, target_value=target_value)

    reprojection_summary: dict[str, float | None] = {"mean": None, "median": None, "p95": None, "max": None}
    if reprojection_errors_px:
        reproj = np.asarray(reprojection_errors_px, dtype=np.float64)
        reprojection_summary = {
            "mean": float(np.mean(reproj)),
            "median": float(np.median(reproj)),
            "p95": float(np.percentile(reproj, 95)),
            "max": float(np.max(reproj)),
        }

    failures: list[str] = []
    if pose_detect_rate < 0.95:
        failures.append("pose_detect_rate_below_0.95")
    for key in ("left_upper_arm", "right_upper_arm", "left_forearm", "right_forearm", "left_thigh", "right_thigh", "left_shin", "right_shin"):
        summary = segment_summaries.get(key)
        if summary and summary.get("mean_abs_error_m") is not None and float(summary["mean_abs_error_m"]) > 0.05:
            failures.append(f"{key}_mean_abs_error_above_5cm")
    torso = segment_summaries.get("torso_length")
    if torso and float(torso["std"]) > 0.03:
        failures.append("torso_length_std_above_3cm")
    if reprojection_summary["mean"] is not None and float(reprojection_summary["mean"]) > 25.0:
        failures.append("reprojection_mean_above_25px")

    return {
        "input_video": str(input_video),
        "calibration_json": str(calibration_json) if calibration_json else None,
        "total_frames": total_frames,
        "frames_with_pose": frames_with_pose,
        "pose_detect_rate": pose_detect_rate,
        "segment_lengths_m": segment_summaries,
        "reprojection_error_px": reprojection_summary,
        "gate1_prereq": {
            "passed": len(failures) == 0,
            "failures": failures,
            "criteria": {
                "pose_detect_rate_min": 0.95,
                "mean_segment_abs_error_m_max": 0.05,
                "torso_length_std_m_max": 0.03,
                "reprojection_mean_px_max": 25.0,
            },
        },
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Evaluate corrected target skeleton stability before building articulated human model.")
    parser.add_argument("--input-video", type=Path, default=Path(__file__).resolve().parent.parent / "video" / "주황.mp4")
    parser.add_argument("--task-model", type=Path, default=Path(__file__).resolve().parent / "pose_landmarker_lite.task")
    parser.add_argument("--calibration-json", type=Path, default=Path(__file__).resolve().parent / "calibration.json")
    parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parent / "target_skeleton_gate1_report.json")
    args = parser.parse_args()

    report = evaluate_video(
        input_video=args.input_video.resolve(),
        task_model=args.task_model.resolve(),
        calibration_json=args.calibration_json.resolve() if args.calibration_json else None,
    )
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({
        "output": str(args.output.resolve()),
        "passed": report["gate1_prereq"]["passed"],
        "pose_detect_rate": report["pose_detect_rate"],
        "reprojection_mean_px": report["reprojection_error_px"]["mean"],
    }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any

import cv2
import mediapipe as mp
import numpy as np

ROOT = Path(__file__).resolve().parent
PROJECT_ROOT = ROOT.parent
CUSTOM_SKELETON_ROOT = PROJECT_ROOT / "custom_skeleton_verify"
PHYSICAL_ROOT = PROJECT_ROOT / "pysical_verify"

sys.path.insert(0, str(CUSTOM_SKELETON_ROOT))
sys.path.insert(0, str(PHYSICAL_ROOT))

from mediapipe_custom_skeleton_verify import make_landmarker  # noqa: E402
from calibrate_biometrics import (  # noqa: E402
    LEFT_ANKLE,
    LEFT_ELBOW,
    LEFT_FOOT_INDEX,
    LEFT_HEEL,
    LEFT_HIP,
    LEFT_KNEE,
    LEFT_SHOULDER,
    LEFT_WRIST,
    RIGHT_ANKLE,
    RIGHT_ELBOW,
    RIGHT_FOOT_INDEX,
    RIGHT_HEEL,
    RIGHT_HIP,
    RIGHT_KNEE,
    RIGHT_SHOULDER,
    RIGHT_WRIST,
    detect_pose_landmarks,
    dist2,
    full_body_height_px,
    hand_tip_px,
    landmarks_to_pixels,
    serialize_point,
)


DEFAULT_VIDEO = PROJECT_ROOT / "video" / "주황.mp4"
DEFAULT_DETECTIONS = PROJECT_ROOT / "detections.json"
DEFAULT_TPOSE_IMAGE = PROJECT_ROOT / "video" / "fullbody_dg.png"
DEFAULT_TASK_MODEL = CUSTOM_SKELETON_ROOT / "pose_landmarker_lite.task"
DEFAULT_OUTPUT_DIR = ROOT / "benchmark_inputs"


def serialize_landmarks(landmarks: list[Any] | None) -> list[dict[str, float]] | None:
    if not landmarks:
        return None
    serialized: list[dict[str, float]] = []
    for landmark in landmarks:
        item = {
            "x": float(landmark.x),
            "y": float(landmark.y),
            "z": float(landmark.z),
        }
        if hasattr(landmark, "visibility"):
            item["visibility"] = float(landmark.visibility)
        if hasattr(landmark, "presence"):
            item["presence"] = float(landmark.presence)
        serialized.append(item)
    return serialized


def midpoint(point_a: np.ndarray, point_b: np.ndarray) -> np.ndarray:
    return 0.5 * (np.asarray(point_a, dtype=np.float64) + np.asarray(point_b, dtype=np.float64))


def build_holds_payload(detections_json: Path, video_path: Path) -> dict[str, Any]:
    payload = json.loads(detections_json.read_text(encoding="utf-8"))
    detections = payload.get("detections", [])

    cap = cv2.VideoCapture(str(video_path))
    if not cap.isOpened():
        raise FileNotFoundError(f"Unable to open video: {video_path}")
    frame_width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH) or 0)
    frame_height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT) or 0)
    cap.release()

    holds: list[dict[str, Any]] = []
    for det in detections:
        x1 = float(det["x1"])
        y1 = float(det["y1"])
        x2 = float(det["x2"])
        y2 = float(det["y2"])
        width = max(1.0, x2 - x1)
        height = max(1.0, y2 - y1)
        center_x = 0.5 * (x1 + x2)
        center_y = 0.5 * (y1 + y2)
        holds.append(
            {
                "hold_id": int(det["hold_id"]),
                "bbox_px": {
                    "x1": x1,
                    "y1": y1,
                    "x2": x2,
                    "y2": y2,
                },
                "center_px": {
                    "x": center_x,
                    "y": center_y,
                },
                "radius_px": 0.45 * min(width, height),
                "confidence": float(det.get("confidence", 0.0)),
            }
        )

    return {
        "schema_version": "1.0.0",
        "source": {
            "type": "detections_json",
            "path": str(detections_json.resolve()),
            "legacy_source_file": payload.get("image", {}).get("source_file"),
        },
        "video_metadata": {
            "video_path": str(video_path.resolve()),
            "frame_width": frame_width,
            "frame_height": frame_height,
        },
        "holds": holds,
    }


def build_pose_sequence_payload(
    video_path: Path,
    task_model: Path,
    frame_step: int,
    max_frames: int | None,
) -> dict[str, Any]:
    cap = cv2.VideoCapture(str(video_path))
    if not cap.isOpened():
        raise FileNotFoundError(f"Unable to open video: {video_path}")

    fps = float(cap.get(cv2.CAP_PROP_FPS) or 30.0)
    frame_width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH) or 0)
    frame_height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT) or 0)
    total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT) or 0)
    duration_ms = int(round((total_frames / max(fps, 1.0)) * 1000.0))

    frames: list[dict[str, Any]] = []
    processed_count = 0

    with make_landmarker(task_model) as landmarker:
        frame_idx = 0
        while True:
            ok, frame_bgr = cap.read()
            if not ok:
                break
            if frame_step > 1 and frame_idx % frame_step != 0:
                frame_idx += 1
                continue
            if max_frames is not None and processed_count >= max_frames:
                break

            timestamp_ms = int(round((frame_idx / max(fps, 1.0)) * 1000.0))
            frame_rgb = cv2.cvtColor(frame_bgr, cv2.COLOR_BGR2RGB)
            mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=frame_rgb)
            result = landmarker.detect_for_video(mp_image, timestamp_ms)

            frames.append(
                {
                    "frame_index": frame_idx,
                    "timestamp_ms": timestamp_ms,
                    "pose_detected": bool(result.pose_world_landmarks),
                    "pose_world_landmarks": serialize_landmarks(result.pose_world_landmarks[0]) if result.pose_world_landmarks else None,
                    "pose_landmarks": serialize_landmarks(result.pose_landmarks[0]) if result.pose_landmarks else None,
                }
            )
            processed_count += 1
            frame_idx += 1

    cap.release()

    return {
        "schema_version": "1.0.0",
        "source": {
            "type": "video_mediapipe_pose_landmarker_lite",
            "video_path": str(video_path.resolve()),
            "task_model_path": str(task_model.resolve()),
        },
        "video_metadata": {
            "frame_width": frame_width,
            "frame_height": frame_height,
            "fps": fps,
            "total_frames": total_frames,
            "duration_ms": duration_ms,
            "frame_step": frame_step,
            "processed_frames": len(frames),
        },
        "frames": frames,
    }


def build_user_body_payload(
    image_path: Path,
    task_model: Path,
    height_m: float,
    weight_kg: float,
) -> dict[str, Any]:
    image_rgb, landmarks_2d, landmarks_world = detect_pose_landmarks(image_path, task_model)
    points_px = landmarks_to_pixels(landmarks_2d, image_rgb)

    upper_arm_left_px = dist2(points_px[LEFT_SHOULDER], points_px[LEFT_ELBOW])
    upper_arm_right_px = dist2(points_px[RIGHT_SHOULDER], points_px[RIGHT_ELBOW])
    forearm_left_px = dist2(points_px[LEFT_ELBOW], points_px[LEFT_WRIST])
    forearm_right_px = dist2(points_px[RIGHT_ELBOW], points_px[RIGHT_WRIST])
    thigh_left_px = dist2(points_px[LEFT_HIP], points_px[LEFT_KNEE])
    thigh_right_px = dist2(points_px[RIGHT_HIP], points_px[RIGHT_KNEE])
    shin_left_px = dist2(points_px[LEFT_KNEE], points_px[LEFT_ANKLE])
    shin_right_px = dist2(points_px[RIGHT_KNEE], points_px[RIGHT_ANKLE])
    shoulder_width_px = dist2(points_px[LEFT_SHOULDER], points_px[RIGHT_SHOULDER])
    hip_width_px = dist2(points_px[LEFT_HIP], points_px[RIGHT_HIP])
    wingspan_px = dist2(hand_tip_px(points_px, left=True), hand_tip_px(points_px, left=False))
    body_height_px = full_body_height_px(points_px)
    torso_length_px = dist2(
        midpoint(points_px[LEFT_SHOULDER], points_px[RIGHT_SHOULDER]),
        midpoint(points_px[LEFT_HIP], points_px[RIGHT_HIP]),
    )

    meters_per_px = float(height_m) / max(body_height_px, 1e-6)
    wingspan_extra_m = 0.20

    left_upper_arm_m = upper_arm_left_px * meters_per_px
    right_upper_arm_m = upper_arm_right_px * meters_per_px
    left_forearm_m = forearm_left_px * meters_per_px
    right_forearm_m = forearm_right_px * meters_per_px
    left_thigh_m = thigh_left_px * meters_per_px
    right_thigh_m = thigh_right_px * meters_per_px
    left_shin_m = shin_left_px * meters_per_px
    right_shin_m = shin_right_px * meters_per_px
    shoulder_width_m = shoulder_width_px * meters_per_px
    hip_width_m = hip_width_px * meters_per_px
    torso_length_m = torso_length_px * meters_per_px
    wingspan_raw_m = wingspan_px * meters_per_px
    wingspan_m = wingspan_raw_m + wingspan_extra_m

    calibration_compat = {
        "body_mass_kg": float(weight_kg),
        "left_upper_arm_m": left_upper_arm_m,
        "right_upper_arm_m": right_upper_arm_m,
        "upper_arm_m": 0.5 * (left_upper_arm_m + right_upper_arm_m),
        "left_forearm_m": left_forearm_m,
        "right_forearm_m": right_forearm_m,
        "forearm_m": 0.5 * (left_forearm_m + right_forearm_m),
        "left_thigh_m": left_thigh_m,
        "right_thigh_m": right_thigh_m,
        "thigh_m": 0.5 * (left_thigh_m + right_thigh_m),
        "left_shin_m": left_shin_m,
        "right_shin_m": right_shin_m,
        "shin_m": 0.5 * (left_shin_m + right_shin_m),
        "shoulder_width_m": shoulder_width_m,
        "hip_width_m": hip_width_m,
        "torso_length_m": torso_length_m,
        "wingspan_m": wingspan_m,
    }

    return {
        "schema_version": "1.0.0",
        "source": {
            "type": "t_pose_image_estimation",
            "image_path": str(image_path.resolve()),
            "task_model_path": str(task_model.resolve()),
        },
        "user_profile": {
            "height_m": float(height_m),
            "height_cm": float(height_m) * 100.0,
            "weight_kg": float(weight_kg),
        },
        "static_biometrics": {
            "left_upper_arm_m": left_upper_arm_m,
            "right_upper_arm_m": right_upper_arm_m,
            "left_forearm_m": left_forearm_m,
            "right_forearm_m": right_forearm_m,
            "left_thigh_m": left_thigh_m,
            "right_thigh_m": right_thigh_m,
            "left_shin_m": left_shin_m,
            "right_shin_m": right_shin_m,
            "shoulder_width_m": shoulder_width_m,
            "hip_width_m": hip_width_m,
            "torso_length_m": torso_length_m,
            "wingspan_raw_m": wingspan_raw_m,
            "wingspan_extra_m": wingspan_extra_m,
            "wingspan_m": wingspan_m,
            "scale_m_per_px": meters_per_px,
        },
        "calibration_compat": calibration_compat,
        "pixel_lengths": {
            "upper_arm_left_px": upper_arm_left_px,
            "upper_arm_right_px": upper_arm_right_px,
            "forearm_left_px": forearm_left_px,
            "forearm_right_px": forearm_right_px,
            "thigh_left_px": thigh_left_px,
            "thigh_right_px": thigh_right_px,
            "shin_left_px": shin_left_px,
            "shin_right_px": shin_right_px,
            "shoulder_width_px": shoulder_width_px,
            "hip_width_px": hip_width_px,
            "torso_length_px": torso_length_px,
            "wingspan_px": wingspan_px,
            "body_height_px": body_height_px,
        },
        "landmarks_px": {
            "left_shoulder": serialize_point(points_px[LEFT_SHOULDER]),
            "right_shoulder": serialize_point(points_px[RIGHT_SHOULDER]),
            "left_elbow": serialize_point(points_px[LEFT_ELBOW]),
            "right_elbow": serialize_point(points_px[RIGHT_ELBOW]),
            "left_wrist": serialize_point(points_px[LEFT_WRIST]),
            "right_wrist": serialize_point(points_px[RIGHT_WRIST]),
            "left_hip": serialize_point(points_px[LEFT_HIP]),
            "right_hip": serialize_point(points_px[RIGHT_HIP]),
            "left_knee": serialize_point(points_px[LEFT_KNEE]),
            "right_knee": serialize_point(points_px[RIGHT_KNEE]),
            "left_ankle": serialize_point(points_px[LEFT_ANKLE]),
            "right_ankle": serialize_point(points_px[RIGHT_ANKLE]),
            "left_heel": serialize_point(points_px[LEFT_HEEL]),
            "right_heel": serialize_point(points_px[RIGHT_HEEL]),
            "left_foot_index": serialize_point(points_px[LEFT_FOOT_INDEX]),
            "right_foot_index": serialize_point(points_px[RIGHT_FOOT_INDEX]),
            "left_hand_tip": serialize_point(hand_tip_px(points_px, left=True)),
            "right_hand_tip": serialize_point(hand_tip_px(points_px, left=False)),
        },
        "world_landmarks_sample": {
            "left_shoulder": [float(v) for v in landmarks_world[LEFT_SHOULDER]],
            "right_shoulder": [float(v) for v in landmarks_world[RIGHT_SHOULDER]],
            "left_hip": [float(v) for v in landmarks_world[LEFT_HIP]],
            "right_hip": [float(v) for v in landmarks_world[RIGHT_HIP]],
        },
    }


def build_user_body_payload_from_profile(
    height_m: float,
    weight_kg: float,
    wingspan_m: float,
) -> dict[str, Any]:
    # Lightweight anthropometric estimate for service-like inputs without a T-pose image.
    shoulder_width_m = 0.228 * float(height_m)
    hip_width_m = 0.191 * float(height_m)
    torso_length_m = 0.300 * float(height_m)
    thigh_m = 0.245 * float(height_m)
    shin_m = 0.246 * float(height_m)
    hand_length_m = 0.108 * float(height_m)

    half_arm_reach_m = max((float(wingspan_m) - shoulder_width_m) * 0.5, 0.34 * float(height_m))
    arm_bone_reach_m = max(half_arm_reach_m - hand_length_m, 0.26 * float(height_m))
    upper_arm_m = 0.53 * arm_bone_reach_m
    forearm_m = 0.47 * arm_bone_reach_m

    calibration_compat = {
        "body_mass_kg": float(weight_kg),
        "left_upper_arm_m": float(upper_arm_m),
        "right_upper_arm_m": float(upper_arm_m),
        "upper_arm_m": float(upper_arm_m),
        "left_forearm_m": float(forearm_m),
        "right_forearm_m": float(forearm_m),
        "forearm_m": float(forearm_m),
        "left_thigh_m": float(thigh_m),
        "right_thigh_m": float(thigh_m),
        "thigh_m": float(thigh_m),
        "left_shin_m": float(shin_m),
        "right_shin_m": float(shin_m),
        "shin_m": float(shin_m),
        "shoulder_width_m": float(shoulder_width_m),
        "hip_width_m": float(hip_width_m),
        "torso_length_m": float(torso_length_m),
        "wingspan_m": float(wingspan_m),
    }

    return {
        "schema_version": "1.1.0",
        "source": {
            "type": "anthropometric_profile_estimation",
            "note": "height/weight/wingspan only; no T-pose image used",
        },
        "user_profile": {
            "height_m": float(height_m),
            "height_cm": float(height_m) * 100.0,
            "weight_kg": float(weight_kg),
            "wingspan_m": float(wingspan_m),
            "wingspan_cm": float(wingspan_m) * 100.0,
        },
        "static_biometrics": {
            "left_upper_arm_m": float(upper_arm_m),
            "right_upper_arm_m": float(upper_arm_m),
            "left_forearm_m": float(forearm_m),
            "right_forearm_m": float(forearm_m),
            "left_thigh_m": float(thigh_m),
            "right_thigh_m": float(thigh_m),
            "left_shin_m": float(shin_m),
            "right_shin_m": float(shin_m),
            "shoulder_width_m": float(shoulder_width_m),
            "hip_width_m": float(hip_width_m),
            "torso_length_m": float(torso_length_m),
            "wingspan_raw_m": float(wingspan_m),
            "wingspan_extra_m": 0.0,
            "wingspan_m": float(wingspan_m),
            "hand_length_m": float(hand_length_m),
            "estimation_mode": "profile_only",
        },
        "calibration_compat": calibration_compat,
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Prepare JSON-only benchmark inputs for the MuJoCo service pipeline.")
    parser.add_argument("--input-video", type=Path, default=DEFAULT_VIDEO)
    parser.add_argument("--detections-json", type=Path, default=DEFAULT_DETECTIONS)
    parser.add_argument("--tpose-image", type=Path, default=DEFAULT_TPOSE_IMAGE)
    parser.add_argument("--task-model", type=Path, default=DEFAULT_TASK_MODEL)
    parser.add_argument("--height-m", type=float, default=1.75)
    parser.add_argument("--weight-kg", type=float, default=80.0)
    parser.add_argument("--frame-step", type=int, default=1)
    parser.add_argument("--max-frames", type=int, default=None)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    output_dir = args.output_dir.resolve()
    output_dir.mkdir(parents=True, exist_ok=True)

    holds_payload = build_holds_payload(args.detections_json.resolve(), args.input_video.resolve())
    pose_payload = build_pose_sequence_payload(
        video_path=args.input_video.resolve(),
        task_model=args.task_model.resolve(),
        frame_step=max(1, int(args.frame_step)),
        max_frames=args.max_frames,
    )
    user_body_payload = build_user_body_payload(
        image_path=args.tpose_image.resolve(),
        task_model=args.task_model.resolve(),
        height_m=float(args.height_m),
        weight_kg=float(args.weight_kg),
    )

    holds_path = output_dir / "holds.json"
    pose_path = output_dir / "pose3d_sequence.json"
    user_body_path = output_dir / "user_body.json"
    manifest_path = output_dir / "benchmark_input_manifest.json"

    holds_path.write_text(json.dumps(holds_payload, ensure_ascii=False, indent=2), encoding="utf-8")
    pose_path.write_text(json.dumps(pose_payload, ensure_ascii=False, indent=2), encoding="utf-8")
    user_body_path.write_text(json.dumps(user_body_payload, ensure_ascii=False, indent=2), encoding="utf-8")

    manifest = {
        "schema_version": "1.0.0",
        "holds_json": str(holds_path),
        "pose3d_sequence_json": str(pose_path),
        "user_body_json": str(user_body_path),
        "frame_count": len(pose_payload["frames"]),
        "pose_detected_frames": int(sum(1 for frame in pose_payload["frames"] if frame["pose_detected"])),
        "video_metadata": pose_payload["video_metadata"],
        "user_profile": user_body_payload["user_profile"],
    }
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")

    print(
        json.dumps(
            {
                "output_dir": str(output_dir),
                "holds_json": str(holds_path),
                "pose3d_sequence_json": str(pose_path),
                "user_body_json": str(user_body_path),
                "frame_count": manifest["frame_count"],
                "pose_detected_frames": manifest["pose_detected_frames"],
            },
            ensure_ascii=False,
            indent=2,
        )
    )


if __name__ == "__main__":
    main()

from __future__ import annotations

import argparse
import json
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import numpy as np

ROOT = Path(__file__).resolve().parent
PROJECT_ROOT = ROOT.parent
DYNAMIC_HOLD_ROOT = PROJECT_ROOT / "dynamic_hold_verify"
sys.path.insert(0, str(DYNAMIC_HOLD_ROOT))

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
)


DEFAULT_POSE_JSON = ROOT / "benchmark_inputs" / "pose3d_sequence.json"
DEFAULT_USER_BODY_JSON = ROOT / "benchmark_inputs" / "user_body.json"
DEFAULT_OUTPUT = ROOT / "benchmark_inputs" / "pose3d_sequence_corrected.json"

ALPHA_MIN = 0.05
ALPHA_TORSO = 0.55
ALPHA_MAJOR = 0.40
ALPHA_DISTAL = 0.25
VISIBILITY_LOW = 0.35
VISIBILITY_MISSING = 0.15
FREEZE_FRAMES = 2
SEGMENT_LENGTH_TOLERANCE = 0.12

TORSO_INDICES = {0, LEFT_SHOULDER, RIGHT_SHOULDER, LEFT_HIP, RIGHT_HIP}
MAJOR_INDICES = {LEFT_ELBOW, RIGHT_ELBOW, LEFT_KNEE, RIGHT_KNEE}
DISTAL_INDICES = {
    LEFT_WRIST,
    RIGHT_WRIST,
    LEFT_ANKLE,
    RIGHT_ANKLE,
    LEFT_HEEL,
    RIGHT_HEEL,
    LEFT_FOOT_INDEX,
    RIGHT_FOOT_INDEX,
    LEFT_INDEX,
    RIGHT_INDEX,
    LEFT_PINKY,
    RIGHT_PINKY,
    LEFT_THUMB,
    RIGHT_THUMB,
}

WORLD_LENGTH_KEYS = {
    LEFT_ELBOW: "left_upper_arm_m",
    RIGHT_ELBOW: "right_upper_arm_m",
    LEFT_WRIST: "left_forearm_m",
    RIGHT_WRIST: "right_forearm_m",
    LEFT_KNEE: "left_thigh_m",
    RIGHT_KNEE: "right_thigh_m",
    LEFT_ANKLE: "left_shin_m",
    RIGHT_ANKLE: "right_shin_m",
}

WORLD_PARENT_INDEX = {
    LEFT_ELBOW: LEFT_SHOULDER,
    RIGHT_ELBOW: RIGHT_SHOULDER,
    LEFT_WRIST: LEFT_ELBOW,
    RIGHT_WRIST: RIGHT_ELBOW,
    LEFT_KNEE: LEFT_HIP,
    RIGHT_KNEE: RIGHT_HIP,
    LEFT_ANKLE: LEFT_KNEE,
    RIGHT_ANKLE: RIGHT_KNEE,
}

RELATIVE_PARENT_INDEX = {
    LEFT_HEEL: LEFT_ANKLE,
    RIGHT_HEEL: RIGHT_ANKLE,
    LEFT_FOOT_INDEX: LEFT_ANKLE,
    RIGHT_FOOT_INDEX: RIGHT_ANKLE,
    LEFT_INDEX: LEFT_WRIST,
    RIGHT_INDEX: RIGHT_WRIST,
    LEFT_PINKY: LEFT_WRIST,
    RIGHT_PINKY: RIGHT_WRIST,
    LEFT_THUMB: LEFT_WRIST,
    RIGHT_THUMB: RIGHT_WRIST,
}


@dataclass
class CorrectionConfig:
    visibility_low: float = VISIBILITY_LOW
    visibility_missing: float = VISIBILITY_MISSING
    freeze_frames: int = FREEZE_FRAMES
    alpha_torso: float = ALPHA_TORSO
    alpha_major: float = ALPHA_MAJOR
    alpha_distal: float = ALPHA_DISTAL
    alpha_min: float = ALPHA_MIN
    segment_length_tolerance: float = SEGMENT_LENGTH_TOLERANCE


@dataclass
class DomainState:
    prev_coords: np.ndarray | None = None
    prev_reliability: np.ndarray | None = None
    low_streak: np.ndarray | None = None


def normalize(v: np.ndarray, eps: float = 1e-8) -> np.ndarray:
    norm = float(np.linalg.norm(v))
    if norm < eps:
        return np.zeros_like(v)
    return v / norm


def calibration_from_user_body(user_body: dict[str, Any]) -> dict[str, float]:
    calibration = user_body.get("calibration_compat")
    if not isinstance(calibration, dict):
        raise KeyError("user_body.json is missing calibration_compat")
    return {str(key): float(value) for key, value in calibration.items()}


def base_alpha_for_index(index: int, config: CorrectionConfig) -> float:
    if index in TORSO_INDICES:
        return config.alpha_torso
    if index in MAJOR_INDICES:
        return config.alpha_major
    if index in DISTAL_INDICES:
        return config.alpha_distal
    return config.alpha_major


def reliability_from_landmark(item: dict[str, Any]) -> float:
    vis = float(item.get("visibility", 1.0))
    presence = float(item.get("presence", 1.0))
    return float(np.clip(0.5 * (vis + presence), 0.0, 1.0))


def landmarks_to_arrays(landmarks: list[dict[str, Any]]) -> tuple[np.ndarray, np.ndarray, list[dict[str, float]]]:
    coords = np.asarray(
        [[float(item["x"]), float(item["y"]), float(item.get("z", 0.0))] for item in landmarks],
        dtype=np.float64,
    )
    reliability = np.asarray([reliability_from_landmark(item) for item in landmarks], dtype=np.float64)
    extras = [
        {
            "visibility": float(item.get("visibility", 1.0)),
            "presence": float(item.get("presence", 1.0)),
        }
        for item in landmarks
    ]
    return coords, reliability, extras


def arrays_to_landmarks(coords: np.ndarray, extras: list[dict[str, float]]) -> list[dict[str, float]]:
    output: list[dict[str, float]] = []
    for idx in range(coords.shape[0]):
        item = {
            "x": float(coords[idx, 0]),
            "y": float(coords[idx, 1]),
            "z": float(coords[idx, 2]),
            "visibility": float(extras[idx]["visibility"]),
            "presence": float(extras[idx]["presence"]),
        }
        output.append(item)
    return output


def _reconstruct_world_segments(
    corrected: np.ndarray,
    prev_coords: np.ndarray | None,
    reliability: np.ndarray,
    calibration: dict[str, float],
    config: CorrectionConfig,
) -> int:
    reconstructed = 0
    for child_idx, key in WORLD_LENGTH_KEYS.items():
        if reliability[child_idx] >= config.visibility_low:
            continue
        parent_idx = WORLD_PARENT_INDEX[child_idx]
        expected = float(calibration.get(key, calibration.get(key.replace("left_", "").replace("right_", ""), 0.0)))
        if expected <= 1e-6:
            continue
        base = corrected[parent_idx]
        direction = None
        if prev_coords is not None:
            prev_vec = prev_coords[child_idx] - prev_coords[parent_idx]
            if float(np.linalg.norm(prev_vec)) > 1e-6:
                direction = normalize(prev_vec)
        if direction is None:
            current_vec = corrected[child_idx] - corrected[parent_idx]
            if float(np.linalg.norm(current_vec)) > 1e-6:
                direction = normalize(current_vec)
        if direction is None:
            continue
        current_len = float(np.linalg.norm(corrected[child_idx] - corrected[parent_idx]))
        if current_len <= 1e-6 or abs(current_len - expected) / expected > config.segment_length_tolerance or reliability[child_idx] < config.visibility_missing:
            corrected[child_idx] = base + direction * expected
            reconstructed += 1
    return reconstructed


def _reconstruct_relative_points(
    corrected: np.ndarray,
    prev_coords: np.ndarray | None,
    reliability: np.ndarray,
    config: CorrectionConfig,
) -> int:
    if prev_coords is None:
        return 0
    reconstructed = 0
    for child_idx, parent_idx in RELATIVE_PARENT_INDEX.items():
        if reliability[child_idx] >= config.visibility_low:
            continue
        prev_vec = prev_coords[child_idx] - prev_coords[parent_idx]
        if float(np.linalg.norm(prev_vec)) <= 1e-6:
            continue
        corrected[child_idx] = corrected[parent_idx] + prev_vec
        reconstructed += 1
    return reconstructed


def _correct_domain_frame(
    landmarks: list[dict[str, Any]] | None,
    state: DomainState,
    calibration: dict[str, float] | None,
    config: CorrectionConfig,
) -> tuple[list[dict[str, float]] | None, dict[str, Any]]:
    if landmarks is None:
        if state.prev_coords is None:
            return None, {
                "filled_from_previous": False,
                "low_visibility_joint_count": 0,
                "frozen_joint_count": 0,
                "reconstructed_joint_count": 0,
            }
        extras = [
            {"visibility": 0.0, "presence": 0.0}
            for _ in range(state.prev_coords.shape[0])
        ]
        return arrays_to_landmarks(state.prev_coords.copy(), extras), {
            "filled_from_previous": True,
            "low_visibility_joint_count": int(state.prev_coords.shape[0]),
            "frozen_joint_count": int(state.prev_coords.shape[0]),
            "reconstructed_joint_count": 0,
        }

    coords, reliability, extras = landmarks_to_arrays(landmarks)
    if state.low_streak is None or state.low_streak.shape[0] != coords.shape[0]:
        state.low_streak = np.zeros(coords.shape[0], dtype=np.int32)

    if state.prev_coords is None:
        corrected = coords.copy()
        state.prev_coords = corrected.copy()
        state.prev_reliability = reliability.copy()
        return arrays_to_landmarks(corrected, extras), {
            "filled_from_previous": False,
            "low_visibility_joint_count": int(np.sum(reliability < config.visibility_low)),
            "frozen_joint_count": 0,
            "reconstructed_joint_count": 0,
        }

    corrected = coords.copy()
    low_visibility_mask = reliability < config.visibility_low
    missing_mask = reliability < config.visibility_missing
    state.low_streak = np.where(low_visibility_mask, state.low_streak + 1, 0)

    frozen_count = 0
    for idx in range(coords.shape[0]):
        base_alpha = base_alpha_for_index(idx, config)
        alpha = config.alpha_min + (base_alpha - config.alpha_min) * reliability[idx]
        corrected[idx] = alpha * coords[idx] + (1.0 - alpha) * state.prev_coords[idx]
        if missing_mask[idx] and state.low_streak[idx] <= config.freeze_frames:
            corrected[idx] = state.prev_coords[idx].copy()
            frozen_count += 1

    reconstructed_count = 0
    if calibration is not None:
        reconstructed_count += _reconstruct_world_segments(
            corrected=corrected,
            prev_coords=state.prev_coords,
            reliability=reliability,
            calibration=calibration,
            config=config,
        )
    else:
        reconstructed_count += _reconstruct_world_segments(
            corrected=corrected,
            prev_coords=state.prev_coords,
            reliability=reliability,
            calibration={},
            config=config,
        )
    reconstructed_count += _reconstruct_relative_points(
        corrected=corrected,
        prev_coords=state.prev_coords,
        reliability=reliability,
        config=config,
    )

    state.prev_coords = corrected.copy()
    state.prev_reliability = reliability.copy()
    return arrays_to_landmarks(corrected, extras), {
        "filled_from_previous": False,
        "low_visibility_joint_count": int(np.sum(low_visibility_mask)),
        "frozen_joint_count": int(frozen_count),
        "reconstructed_joint_count": int(reconstructed_count),
    }


def correct_pose_sequence_payload(
    pose_payload: dict[str, Any],
    user_body_payload: dict[str, Any],
    config: CorrectionConfig | None = None,
    preserve_raw_copy: bool = True,
) -> dict[str, Any]:
    cfg = config or CorrectionConfig()
    calibration = calibration_from_user_body(user_body_payload)

    corrected_payload = json.loads(json.dumps(pose_payload))
    corrected_payload["schema_version"] = "1.1.0"
    corrected_payload["source"]["correction_mode"] = "server_pose_sequence_correction"

    world_state = DomainState()
    image_state = DomainState()

    summary = {
        "frame_count": 0,
        "filled_from_previous_frame_count": 0,
        "total_low_visibility_joint_count": 0,
        "total_frozen_joint_count": 0,
        "total_reconstructed_joint_count": 0,
    }

    for frame in corrected_payload.get("frames", []):
        raw_world = frame.get("pose_world_landmarks")
        raw_image = frame.get("pose_landmarks")
        if preserve_raw_copy:
            frame["pose_world_landmarks_raw"] = raw_world
            frame["pose_landmarks_raw"] = raw_image
            frame["pose_detected_raw"] = bool(frame.get("pose_detected"))

        corrected_world, world_flags = _correct_domain_frame(
            landmarks=raw_world,
            state=world_state,
            calibration=calibration,
            config=cfg,
        )
        corrected_image, image_flags = _correct_domain_frame(
            landmarks=raw_image,
            state=image_state,
            calibration=None,
            config=cfg,
        )

        frame["pose_world_landmarks"] = corrected_world
        frame["pose_landmarks"] = corrected_image
        frame["pose_detected"] = corrected_world is not None and corrected_image is not None
        frame["correction_flags"] = {
            "world": world_flags,
            "image": image_flags,
        }

        summary["frame_count"] += 1
        if world_flags["filled_from_previous"] or image_flags["filled_from_previous"]:
            summary["filled_from_previous_frame_count"] += 1
        summary["total_low_visibility_joint_count"] += (
            int(world_flags["low_visibility_joint_count"]) + int(image_flags["low_visibility_joint_count"])
        )
        summary["total_frozen_joint_count"] += (
            int(world_flags["frozen_joint_count"]) + int(image_flags["frozen_joint_count"])
        )
        summary["total_reconstructed_joint_count"] += (
            int(world_flags["reconstructed_joint_count"]) + int(image_flags["reconstructed_joint_count"])
        )

    corrected_payload["correction_summary"] = {
        "config": {
            "visibility_low": cfg.visibility_low,
            "visibility_missing": cfg.visibility_missing,
            "freeze_frames": cfg.freeze_frames,
            "alpha_torso": cfg.alpha_torso,
            "alpha_major": cfg.alpha_major,
            "alpha_distal": cfg.alpha_distal,
            "segment_length_tolerance": cfg.segment_length_tolerance,
        },
        **summary,
    }
    return corrected_payload


def main() -> None:
    parser = argparse.ArgumentParser(description="Create a corrected pose3d sequence JSON from raw MediaPipe pose JSON.")
    parser.add_argument("--pose-json", type=Path, default=DEFAULT_POSE_JSON)
    parser.add_argument("--user-body-json", type=Path, default=DEFAULT_USER_BODY_JSON)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--no-raw-copy", action="store_true")
    args = parser.parse_args()

    pose_payload = json.loads(args.pose_json.read_text(encoding="utf-8"))
    user_body_payload = json.loads(args.user_body_json.read_text(encoding="utf-8"))
    corrected = correct_pose_sequence_payload(
        pose_payload=pose_payload,
        user_body_payload=user_body_payload,
        preserve_raw_copy=not args.no_raw_copy,
    )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(corrected, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(corrected["correction_summary"], ensure_ascii=False, indent=2))
    print(f"[OK] Wrote {args.output.resolve()}")


if __name__ == "__main__":
    main()

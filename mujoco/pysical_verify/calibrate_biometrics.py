from __future__ import annotations

import argparse
import json
from pathlib import Path

import mediapipe as mp
import numpy as np
from mediapipe.tasks import python
from mediapipe.tasks.python import vision

NOSE = 0
LEFT_EYE_INNER = 1
LEFT_EYE = 2
LEFT_EYE_OUTER = 3
RIGHT_EYE_INNER = 4
RIGHT_EYE = 5
RIGHT_EYE_OUTER = 6
LEFT_EAR = 7
RIGHT_EAR = 8
MOUTH_LEFT = 9
MOUTH_RIGHT = 10
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

FACE_INDICES = [
    NOSE,
    LEFT_EYE_INNER,
    LEFT_EYE,
    LEFT_EYE_OUTER,
    RIGHT_EYE_INNER,
    RIGHT_EYE,
    RIGHT_EYE_OUTER,
    LEFT_EAR,
    RIGHT_EAR,
    MOUTH_LEFT,
    MOUTH_RIGHT,
]


def make_landmarker(task_path: Path) -> vision.PoseLandmarker:
    options = vision.PoseLandmarkerOptions(
        base_options=python.BaseOptions(model_asset_path=str(task_path)),
        running_mode=vision.RunningMode.IMAGE,
        num_poses=1,
        min_pose_detection_confidence=0.5,
        min_pose_presence_confidence=0.5,
        min_tracking_confidence=0.5,
    )
    return vision.PoseLandmarker.create_from_options(options)


def detect_pose_landmarks(image_path: Path, task_path: Path) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    if not image_path.exists():
        raise FileNotFoundError(f"Could not read image: {image_path}")

    mp_image = mp.Image.create_from_file(str(image_path))
    image_rgb = np.asarray(mp_image.numpy_view(), dtype=np.uint8)
    with make_landmarker(task_path) as landmarker:
        result = landmarker.detect(mp_image)

    if not result.pose_landmarks or not result.pose_world_landmarks:
        raise RuntimeError("Pose detection did not return both 2D and world landmarks")

    landmarks_2d = np.array([[float(p.x), float(p.y)] for p in result.pose_landmarks[0]], dtype=np.float64)
    landmarks_world = np.array([[float(p.x), float(p.y), float(p.z)] for p in result.pose_world_landmarks[0]], dtype=np.float64)
    return image_rgb, landmarks_2d, landmarks_world


def landmarks_to_pixels(landmarks_2d: np.ndarray, frame: np.ndarray) -> np.ndarray:
    height, width = frame.shape[:2]
    pixels = landmarks_2d.copy()
    pixels[:, 0] *= float(width)
    pixels[:, 1] *= float(height)
    return pixels


def dist2(a: np.ndarray, b: np.ndarray) -> float:
    return float(np.linalg.norm(np.asarray(a, dtype=np.float64) - np.asarray(b, dtype=np.float64)))


def hand_tip_px(points_px: np.ndarray, left: bool) -> np.ndarray:
    if left:
        indices = [LEFT_INDEX, LEFT_PINKY, LEFT_THUMB]
    else:
        indices = [RIGHT_INDEX, RIGHT_PINKY, RIGHT_THUMB]
    return np.mean(points_px[indices], axis=0)


def estimate_head_top_y(points_px: np.ndarray) -> float:
    face_points = points_px[FACE_INDICES]
    top_y = float(np.min(face_points[:, 1]))
    if dist2(points_px[LEFT_EAR], points_px[RIGHT_EAR]) > 1e-6:
        face_width = dist2(points_px[LEFT_EAR], points_px[RIGHT_EAR])
    else:
        face_width = dist2(points_px[LEFT_EYE_OUTER], points_px[RIGHT_EYE_OUTER])
    return top_y - 0.35 * face_width


def full_body_height_px(points_px: np.ndarray) -> float:
    head_top_y = estimate_head_top_y(points_px)
    heel_y = float(max(points_px[LEFT_HEEL, 1], points_px[RIGHT_HEEL, 1], points_px[LEFT_FOOT_INDEX, 1], points_px[RIGHT_FOOT_INDEX, 1]))
    return max(heel_y - head_top_y, 1e-6)


def serialize_point(point_xy: np.ndarray) -> list[float]:
    return [float(point_xy[0]), float(point_xy[1])]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Extract personalized limb calibration from a T-pose image")
    parser.add_argument("--image", default=str(Path(__file__).with_name("video") / "fullbody_dg.png"))
    parser.add_argument("--task-model", default=str(Path(__file__).with_name("pose_landmarker_lite.task")))
    parser.add_argument("--height-m", type=float, required=True, help="User height in meters")
    parser.add_argument("--output", default=str(Path(__file__).with_name("calibration.json")))
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    image_path = Path(args.image).resolve()
    task_path = Path(args.task_model).resolve()
    output_path = Path(args.output).resolve()

    frame, landmarks_2d, landmarks_world = detect_pose_landmarks(image_path, task_path)
    points_px = landmarks_to_pixels(landmarks_2d, frame)

    upper_arm_left_px = dist2(points_px[LEFT_SHOULDER], points_px[LEFT_ELBOW])
    upper_arm_right_px = dist2(points_px[RIGHT_SHOULDER], points_px[RIGHT_ELBOW])
    forearm_left_px = dist2(points_px[LEFT_ELBOW], points_px[LEFT_WRIST])
    forearm_right_px = dist2(points_px[RIGHT_ELBOW], points_px[RIGHT_WRIST])
    thigh_left_px = dist2(points_px[LEFT_HIP], points_px[LEFT_KNEE])
    thigh_right_px = dist2(points_px[RIGHT_HIP], points_px[RIGHT_KNEE])
    shin_left_px = dist2(points_px[LEFT_KNEE], points_px[LEFT_ANKLE])
    shin_right_px = dist2(points_px[RIGHT_KNEE], points_px[RIGHT_ANKLE])

    shoulder_width_px = dist2(points_px[LEFT_SHOULDER], points_px[RIGHT_SHOULDER])
    wingspan_px = dist2(hand_tip_px(points_px, left=True), hand_tip_px(points_px, left=False))
    body_height_px = full_body_height_px(points_px)
    meters_per_px = float(args.height_m) / body_height_px

    upper_arm_m = 0.5 * (upper_arm_left_px + upper_arm_right_px) * meters_per_px
    forearm_m = 0.5 * (forearm_left_px + forearm_right_px) * meters_per_px
    thigh_m = 0.5 * (thigh_left_px + thigh_right_px) * meters_per_px
    shin_m = 0.5 * (shin_left_px + shin_right_px) * meters_per_px
    shoulder_width_m = shoulder_width_px * meters_per_px
    wingspan_m = wingspan_px * meters_per_px

    result = {
        "image_path": str(image_path),
        "height_m": float(args.height_m),
        "scale_m_per_px": meters_per_px,
        "upper_arm_m": upper_arm_m,
        "forearm_m": forearm_m,
        "thigh_m": thigh_m,
        "shin_m": shin_m,
        "shoulder_width_m": shoulder_width_m,
        "wingspan_m": wingspan_m,
        "ratios": {
            "upper_arm": upper_arm_m / float(args.height_m),
            "forearm": forearm_m / float(args.height_m),
            "thigh": thigh_m / float(args.height_m),
            "shin": shin_m / float(args.height_m),
            "shoulder_width": shoulder_width_m / float(args.height_m),
            "wingspan": wingspan_m / float(args.height_m),
        },
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
            "left_hand_tip": serialize_point(hand_tip_px(points_px, left=True)),
            "right_hand_tip": serialize_point(hand_tip_px(points_px, left=False)),
        },
        "world_landmarks_sample": {
            "left_shoulder": [float(v) for v in landmarks_world[LEFT_SHOULDER]],
            "right_shoulder": [float(v) for v in landmarks_world[RIGHT_SHOULDER]],
            "left_elbow": [float(v) for v in landmarks_world[LEFT_ELBOW]],
            "right_elbow": [float(v) for v in landmarks_world[RIGHT_ELBOW]],
        },
    }

    output_path.write_text(json.dumps(result, indent=2), encoding="utf-8")
    print("[OK] Biometrics calibration complete")
    print(json.dumps({"output": str(output_path), "upper_arm_m": upper_arm_m, "forearm_m": forearm_m, "thigh_m": thigh_m, "shin_m": shin_m}, indent=2))


if __name__ == "__main__":
    main()

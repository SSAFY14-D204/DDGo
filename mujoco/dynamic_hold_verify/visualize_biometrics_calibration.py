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

BONES = [
    ("L-UpperArm", LEFT_SHOULDER, LEFT_ELBOW, (255, 120, 80)),
    ("R-UpperArm", RIGHT_SHOULDER, RIGHT_ELBOW, (80, 180, 255)),
    ("L-Forearm", LEFT_ELBOW, LEFT_WRIST, (255, 170, 100)),
    ("R-Forearm", RIGHT_ELBOW, RIGHT_WRIST, (120, 210, 255)),
    ("L-Thigh", LEFT_HIP, LEFT_KNEE, (120, 255, 120)),
    ("R-Thigh", RIGHT_HIP, RIGHT_KNEE, (150, 255, 150)),
    ("L-Shin", LEFT_KNEE, LEFT_ANKLE, (80, 220, 80)),
    ("R-Shin", RIGHT_KNEE, RIGHT_ANKLE, (120, 220, 120)),
    ("ShoulderWidth", LEFT_SHOULDER, RIGHT_SHOULDER, (255, 255, 80)),
]

KEYPOINT_NAMES = {
    LEFT_SHOULDER: "LS",
    RIGHT_SHOULDER: "RS",
    LEFT_ELBOW: "LE",
    RIGHT_ELBOW: "RE",
    LEFT_WRIST: "LW",
    RIGHT_WRIST: "RW",
    LEFT_HIP: "LH",
    RIGHT_HIP: "RH",
    LEFT_KNEE: "LK",
    RIGHT_KNEE: "RK",
    LEFT_ANKLE: "LA",
    RIGHT_ANKLE: "RA",
}


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


def detect_pose_landmarks(image_path: Path, task_path: Path) -> tuple[np.ndarray, np.ndarray]:
    if not image_path.exists():
        raise FileNotFoundError(f"Could not read image: {image_path}")

    mp_image = mp.Image.create_from_file(str(image_path))
    image_rgb = np.asarray(mp_image.numpy_view(), dtype=np.uint8)
    if image_rgb.ndim == 2:
        image_rgb = np.repeat(image_rgb[:, :, None], 3, axis=2)
    elif image_rgb.ndim == 3 and image_rgb.shape[2] >= 3:
        image_rgb = image_rgb[:, :, :3]
    else:
        raise RuntimeError(f"Unexpected image shape from MediaPipe: {image_rgb.shape}")
    image_rgb = image_rgb.copy()
    with make_landmarker(task_path) as landmarker:
        result = landmarker.detect(mp_image)

    if not result.pose_landmarks:
        raise RuntimeError("Pose detection did not return 2D landmarks")

    landmarks_2d = np.array([[float(p.x), float(p.y)] for p in result.pose_landmarks[0]], dtype=np.float64)
    return image_rgb, landmarks_2d


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


def estimate_head_top(points_px: np.ndarray) -> np.ndarray:
    face_points = points_px[FACE_INDICES]
    x_mid = float(np.mean(face_points[:, 0]))
    top_y = float(np.min(face_points[:, 1]))
    if dist2(points_px[LEFT_EAR], points_px[RIGHT_EAR]) > 1e-6:
        face_width = dist2(points_px[LEFT_EAR], points_px[RIGHT_EAR])
    else:
        face_width = dist2(points_px[LEFT_EYE_OUTER], points_px[RIGHT_EYE_OUTER])
    return np.array([x_mid, top_y - 0.35 * face_width], dtype=np.float64)


def estimate_heel_center(points_px: np.ndarray) -> np.ndarray:
    candidates = np.vstack(
        [
            points_px[LEFT_HEEL],
            points_px[RIGHT_HEEL],
            points_px[LEFT_FOOT_INDEX],
            points_px[RIGHT_FOOT_INDEX],
        ]
    )
    return np.array([float(np.mean(candidates[:, 0])), float(np.max(candidates[:, 1]))], dtype=np.float64)


def draw_disk(image: np.ndarray, center: np.ndarray, radius: int, color: tuple[int, int, int]) -> None:
    cx = int(round(float(center[0])))
    cy = int(round(float(center[1])))
    h, w = image.shape[:2]
    x0 = max(cx - radius, 0)
    x1 = min(cx + radius + 1, w)
    y0 = max(cy - radius, 0)
    y1 = min(cy + radius + 1, h)
    yy, xx = np.ogrid[y0:y1, x0:x1]
    mask = (xx - cx) * (xx - cx) + (yy - cy) * (yy - cy) <= radius * radius
    image[y0:y1, x0:x1][mask] = np.asarray(color, dtype=np.uint8)


def draw_line(image: np.ndarray, p0: np.ndarray, p1: np.ndarray, color: tuple[int, int, int], thickness: int = 2) -> None:
    p0 = np.asarray(p0, dtype=np.float64)
    p1 = np.asarray(p1, dtype=np.float64)
    length = max(int(np.linalg.norm(p1 - p0)), 1)
    for t in np.linspace(0.0, 1.0, num=length * 2 + 1):
        point = (1.0 - t) * p0 + t * p1
        draw_disk(image, point, radius=thickness, color=color)


def draw_char(image: np.ndarray, x: int, y: int, char: str, color: tuple[int, int, int]) -> None:
    patterns = {
        "A": ["010", "101", "111", "101", "101"],
        "E": ["111", "100", "110", "100", "111"],
        "H": ["101", "101", "111", "101", "101"],
        "K": ["101", "110", "100", "110", "101"],
        "L": ["100", "100", "100", "100", "111"],
        "R": ["110", "101", "110", "101", "101"],
        "S": ["111", "100", "111", "001", "111"],
        "W": ["101", "101", "101", "111", "101"],
        "-": ["000", "000", "111", "000", "000"],
    }
    pattern = patterns.get(char.upper())
    if pattern is None:
        return
    for row_idx, row in enumerate(pattern):
        for col_idx, value in enumerate(row):
            if value == "1":
                draw_disk(image, np.array([x + col_idx * 2, y + row_idx * 2], dtype=np.float64), 1, color)


def draw_label(image: np.ndarray, point: np.ndarray, text: str, color: tuple[int, int, int]) -> None:
    x = int(round(float(point[0]))) + 6
    y = int(round(float(point[1]))) - 6
    for idx, char in enumerate(text):
        draw_char(image, x + idx * 8, y, char, color)


def save_ppm(image: np.ndarray, output_path: Path) -> None:
    image = np.asarray(image, dtype=np.uint8)
    header = f"P6\n{image.shape[1]} {image.shape[0]}\n255\n".encode("ascii")
    output_path.write_bytes(header + image.tobytes())


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Visualize T-pose biometric calibration skeleton")
    parser.add_argument("--image", default=str(Path(__file__).parents[1] / "video" / "fullbody_dg.png"))
    parser.add_argument("--task-model", default=str(Path(__file__).with_name("pose_landmarker_lite.task")))
    parser.add_argument("--output", default=str(Path(__file__).with_name("fullbody_dg_biometrics_overlay.ppm")))
    parser.add_argument("--json-output", default=str(Path(__file__).with_name("fullbody_dg_biometrics_overlay.json")))
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    image_path = Path(args.image).resolve()
    task_path = Path(args.task_model).resolve()
    output_path = Path(args.output).resolve()
    json_output_path = Path(args.json_output).resolve()

    frame, landmarks_2d = detect_pose_landmarks(image_path, task_path)
    points_px = landmarks_to_pixels(landmarks_2d, frame)
    overlay = frame.copy()

    for _, start_idx, end_idx, color in BONES:
        draw_line(overlay, points_px[start_idx], points_px[end_idx], color, thickness=2)

    left_tip = hand_tip_px(points_px, left=True)
    right_tip = hand_tip_px(points_px, left=False)
    head_top = estimate_head_top(points_px)
    heel_center = estimate_heel_center(points_px)
    draw_line(overlay, left_tip, right_tip, (255, 255, 255), thickness=2)
    draw_line(overlay, head_top, heel_center, (255, 200, 0), thickness=2)

    keypoints = {
        "left_shoulder": points_px[LEFT_SHOULDER],
        "right_shoulder": points_px[RIGHT_SHOULDER],
        "left_elbow": points_px[LEFT_ELBOW],
        "right_elbow": points_px[RIGHT_ELBOW],
        "left_wrist": points_px[LEFT_WRIST],
        "right_wrist": points_px[RIGHT_WRIST],
        "left_hip": points_px[LEFT_HIP],
        "right_hip": points_px[RIGHT_HIP],
        "left_knee": points_px[LEFT_KNEE],
        "right_knee": points_px[RIGHT_KNEE],
        "left_ankle": points_px[LEFT_ANKLE],
        "right_ankle": points_px[RIGHT_ANKLE],
        "left_hand_tip": left_tip,
        "right_hand_tip": right_tip,
        "head_top": head_top,
        "heel_center": heel_center,
    }

    for idx, label in KEYPOINT_NAMES.items():
        color = (255, 80, 80) if idx % 2 else (80, 255, 255)
        draw_disk(overlay, points_px[idx], radius=4, color=color)
        draw_label(overlay, points_px[idx], label, color)

    for name, point in {"L-TIP": left_tip, "R-TIP": right_tip, "HEAD": head_top, "HEEL": heel_center}.items():
        draw_disk(overlay, point, radius=4, color=(255, 255, 255))
        draw_label(overlay, point, name, (255, 255, 255))

    measurements = {
        "upper_arm_left_px": dist2(points_px[LEFT_SHOULDER], points_px[LEFT_ELBOW]),
        "upper_arm_right_px": dist2(points_px[RIGHT_SHOULDER], points_px[RIGHT_ELBOW]),
        "forearm_left_px": dist2(points_px[LEFT_ELBOW], points_px[LEFT_WRIST]),
        "forearm_right_px": dist2(points_px[RIGHT_ELBOW], points_px[RIGHT_WRIST]),
        "thigh_left_px": dist2(points_px[LEFT_HIP], points_px[LEFT_KNEE]),
        "thigh_right_px": dist2(points_px[RIGHT_HIP], points_px[RIGHT_KNEE]),
        "shin_left_px": dist2(points_px[LEFT_KNEE], points_px[LEFT_ANKLE]),
        "shin_right_px": dist2(points_px[RIGHT_KNEE], points_px[RIGHT_ANKLE]),
        "shoulder_width_px": dist2(points_px[LEFT_SHOULDER], points_px[RIGHT_SHOULDER]),
        "wingspan_px": dist2(left_tip, right_tip),
        "body_height_px": dist2(head_top, heel_center),
    }

    payload = {
        "image_path": str(image_path),
        "overlay_path": str(output_path),
        "keypoints_px": {name: [float(v) for v in point] for name, point in keypoints.items()},
        "measurements_px": measurements,
    }

    save_ppm(overlay, output_path)
    json_output_path.write_text(json.dumps(payload, indent=2), encoding="utf-8")

    print("[OK] Biometrics overlay saved")
    print(json.dumps({"overlay": str(output_path), "json": str(json_output_path)}, indent=2))


if __name__ == "__main__":
    main()

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

import cv2
import mediapipe as mp
import mujoco
import mujoco.viewer
import numpy as np

ROOT = Path(__file__).resolve().parent
CUSTOM_SKELETON_ROOT = ROOT.parent / "custom_skeleton_verify"
DEFAULT_PERSONALIZED_XML = ROOT / "custom_articulated_human_personalized.xml"
DEFAULT_XML = DEFAULT_PERSONALIZED_XML if DEFAULT_PERSONALIZED_XML.exists() else ROOT / "custom_articulated_human.xml"
sys.path.insert(0, str(CUSTOM_SKELETON_ROOT))

from evaluate_static_fit import AUX_SITE_TARGETS, POLE_TARGETS, SITE_TARGETS, fit_static_pose
from mediapipe_custom_skeleton_verify import MetricSkeletonMapper, make_landmarker
from physics_worker import load_calibration_json

OVERLAY_ORDER = (
    "left_shoulder",
    "right_shoulder",
    "left_elbow",
    "right_elbow",
    "left_hand",
    "right_hand",
    "left_hip",
    "right_hip",
    "left_knee",
    "right_knee",
    "left_ankle",
    "right_ankle",
    "left_foot",
    "right_foot",
)

LABEL_TO_MP_ID = {
    "left_shoulder": 11,
    "right_shoulder": 12,
    "left_elbow": 13,
    "right_elbow": 14,
    "left_hand": 15,
    "right_hand": 16,
    "left_hip": 23,
    "right_hip": 24,
    "left_knee": 25,
    "right_knee": 26,
    "left_ankle": 27,
    "right_ankle": 28,
}


def fit_affine_world_to_image(
    points_world: dict[str, np.ndarray],
    pose_landmarks: list,
    frame_width: int,
    frame_height: int,
) -> tuple[np.ndarray, np.ndarray]:
    src_rows: list[list[float]] = []
    dst_x: list[float] = []
    dst_y: list[float] = []
    for point_name, mp_id in LABEL_TO_MP_ID.items():
        world = np.asarray(points_world[point_name], dtype=np.float64)
        src_rows.append([float(world[0]), float(world[1]), 1.0])
        lm = pose_landmarks[mp_id]
        if hasattr(lm, "x"):
            lm_x = float(lm.x)
            lm_y = float(lm.y)
        else:
            lm_x = float(lm[0])
            lm_y = float(lm[1])
        dst_x.append(lm_x * frame_width)
        dst_y.append(lm_y * frame_height)
    src = np.asarray(src_rows, dtype=np.float64)
    coeff_x, *_ = np.linalg.lstsq(src, np.asarray(dst_x, dtype=np.float64), rcond=None)
    coeff_y, *_ = np.linalg.lstsq(src, np.asarray(dst_y, dtype=np.float64), rcond=None)
    return coeff_x, coeff_y


def project_world_xy(point: np.ndarray, coeff_x: np.ndarray, coeff_y: np.ndarray) -> tuple[int, int]:
    row = np.array([float(point[0]), float(point[1]), 1.0], dtype=np.float64)
    px = int(round(float(row @ coeff_x)))
    py = int(round(float(row @ coeff_y)))
    return px, py


def draw_cross(image: np.ndarray, center: tuple[int, int], color: tuple[int, int, int], size: int = 8, thickness: int = 2) -> None:
    x, y = center
    cv2.line(image, (x - size, y), (x + size, y), color, thickness)
    cv2.line(image, (x, y - size), (x, y + size), color, thickness)


def draw_overlay(
    frame_bgr: np.ndarray,
    mp_landmarks: list,
    target_points: dict[str, np.ndarray],
    fitted_sites: dict[str, np.ndarray],
    show_target: bool = True,
    show_fit: bool = True,
) -> np.ndarray:
    overlay = frame_bgr.copy()
    coeff_x, coeff_y = fit_affine_world_to_image(target_points, mp_landmarks, frame_bgr.shape[1], frame_bgr.shape[0])

    for label in OVERLAY_ORDER:
        if label in LABEL_TO_MP_ID:
            lm = mp_landmarks[LABEL_TO_MP_ID[label]]
            if hasattr(lm, "x"):
                lm_x = float(lm.x)
                lm_y = float(lm.y)
            else:
                lm_x = float(lm[0])
                lm_y = float(lm[1])
            mp_px = (int(round(lm_x * frame_bgr.shape[1])), int(round(lm_y * frame_bgr.shape[0])))
            draw_cross(overlay, mp_px, (0, 255, 0), size=7, thickness=2)
            cv2.putText(overlay, f"MP-{label}", (mp_px[0] + 5, mp_px[1] - 5), cv2.FONT_HERSHEY_SIMPLEX, 0.35, (0, 255, 0), 1, cv2.LINE_AA)

        if show_target:
            tgt_px = project_world_xy(target_points[label], coeff_x, coeff_y)
            cv2.circle(overlay, tgt_px, 5, (0, 220, 255), -1, cv2.LINE_AA)
            cv2.putText(overlay, f"T-{label}", (tgt_px[0] + 5, tgt_px[1] + 12), cv2.FONT_HERSHEY_SIMPLEX, 0.35, (0, 220, 255), 1, cv2.LINE_AA)

        if show_fit:
            fit_px = project_world_xy(fitted_sites[label], coeff_x, coeff_y)
            cv2.circle(overlay, fit_px, 5, (0, 0, 255), 1, cv2.LINE_AA)
            cv2.putText(overlay, f"MJ-{label}", (fit_px[0] + 5, fit_px[1] - 10), cv2.FONT_HERSHEY_SIMPLEX, 0.35, (0, 0, 255), 1, cv2.LINE_AA)

    legend_parts = ["Green: MediaPipe 2D"]
    if show_target:
        legend_parts.append("Yellow: target skeleton")
    if show_fit:
        legend_parts.append("Red: articulated fit")
    cv2.putText(overlay, "  ".join(legend_parts), (24, 32), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (240, 240, 240), 2, cv2.LINE_AA)
    return overlay


def extract_frame_fit(
    xml_path: Path,
    video_path: Path,
    task_path: Path,
    calibration: dict[str, float] | None,
    frame_index: int,
    ik_iterations: int,
    damping: float,
) -> tuple[mujoco.MjModel, mujoco.MjData, dict[str, np.ndarray], list, np.ndarray, dict[str, float]]:
    model = mujoco.MjModel.from_xml_path(str(xml_path.resolve()))
    data = mujoco.MjData(model)
    required_sites = tuple(SITE_TARGETS.keys()) + tuple(POLE_TARGETS.keys()) + tuple(AUX_SITE_TARGETS.keys())
    site_ids = {
        site_name: mujoco.mj_name2id(model, mujoco.mjtObj.mjOBJ_SITE, site_name)
        for site_name in required_sites
    }

    cap = cv2.VideoCapture(str(video_path))
    if not cap.isOpened():
        raise FileNotFoundError(f"Input video not found: {video_path}")
    landmarker = make_landmarker(task_path)
    mapper = MetricSkeletonMapper(calibration)

    chosen_frame_bgr: np.ndarray | None = None
    chosen_pose_landmarks: list | None = None
    target_points: dict[str, np.ndarray] | None = None
    current_index = 0
    while True:
        ok, frame_bgr = cap.read()
        if not ok:
            break
        timestamp_ms = int(round((current_index / max(cap.get(cv2.CAP_PROP_FPS), 1.0)) * 1000.0))
        frame_rgb = cv2.cvtColor(frame_bgr, cv2.COLOR_BGR2RGB)
        mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=frame_rgb)
        result = landmarker.detect_for_video(mp_image, timestamp_ms)
        if result.pose_world_landmarks:
            mapped = mapper.map_frame(result.pose_world_landmarks[0])
            if current_index == frame_index:
                chosen_frame_bgr = frame_bgr.copy()
                chosen_pose_landmarks = result.pose_landmarks[0]
                target_points = mapped
                break
        if current_index == frame_index:
            break
        current_index += 1

    cap.release()
    landmarker.close()

    if chosen_frame_bgr is None or chosen_pose_landmarks is None or target_points is None:
        raise RuntimeError(f"Failed to extract pose at frame {frame_index}")

    fit = fit_static_pose(model, data, site_ids, target_points, seed_qpos=None, iterations=ik_iterations, damping=damping)
    fitted_sites = {
        target_key: data.site_xpos[site_ids[site_name]].copy()
        for site_name, (target_key, _) in SITE_TARGETS.items()
    }
    return model, data, target_points, chosen_pose_landmarks, chosen_frame_bgr, fit["per_target_errors_m"], fitted_sites


def main() -> None:
    parser = argparse.ArgumentParser(description="Visualize one static fitting frame for the custom articulated human model.")
    parser.add_argument("--xml", type=Path, default=DEFAULT_XML)
    parser.add_argument("--input-video", type=Path, default=ROOT.parent / "video" / "주황.mp4")
    parser.add_argument("--task-model", type=Path, default=CUSTOM_SKELETON_ROOT / "pose_landmarker_lite.task")
    parser.add_argument("--calibration-json", type=Path, default=CUSTOM_SKELETON_ROOT / "calibration.json")
    parser.add_argument("--frame-index", type=int, default=413)
    parser.add_argument("--ik-iters", type=int, default=60)
    parser.add_argument("--ik-damping", type=float, default=1e-3)
    parser.add_argument("--overlay-output", type=Path, default=ROOT / "static_fit_overlay.png")
    parser.add_argument("--no-viewer", action="store_true")
    args = parser.parse_args()

    calibration = load_calibration_json(args.calibration_json)
    model, data, target_points, pose_landmarks, frame_bgr, per_target_errors, fitted_sites = extract_frame_fit(
        xml_path=args.xml,
        video_path=args.input_video,
        task_path=args.task_model,
        calibration=calibration,
        frame_index=args.frame_index,
        ik_iterations=args.ik_iters,
        damping=args.ik_damping,
    )

    overlay = draw_overlay(frame_bgr, pose_landmarks, target_points, fitted_sites)
    args.overlay_output.write_bytes(cv2.imencode(".png", overlay)[1].tobytes())
    print(json.dumps(
        {
            "frame_index": args.frame_index,
            "overlay_output": str(args.overlay_output.resolve()),
            "per_target_errors_m": per_target_errors,
        },
        ensure_ascii=False,
        indent=2,
    ))

    if args.no_viewer:
        return

    with mujoco.viewer.launch_passive(model, data) as viewer:
        while viewer.is_running():
            viewer.sync()


if __name__ == "__main__":
    main()

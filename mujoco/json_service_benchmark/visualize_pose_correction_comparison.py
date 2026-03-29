from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

import cv2
import numpy as np


ROOT = Path(__file__).resolve().parent
DEFAULT_RAW_POSE_JSON = ROOT / "benchmark_inputs" / "polygon" / "pose3d_sequence.json"
DEFAULT_CORRECTED_POSE_JSON = ROOT / "benchmark_inputs" / "polygon" / "pose3d_sequence_corrected.json"
DEFAULT_OUTPUT_VIDEO = ROOT / "pose_correction_comparison_overlay.mp4"
DEFAULT_PANEL_HEIGHT = 960

POSE_CONNECTIONS = [
    (0, 1), (1, 2), (2, 3), (3, 7),
    (0, 4), (4, 5), (5, 6), (6, 8),
    (9, 10),
    (11, 12),
    (11, 13), (13, 15), (15, 17), (15, 19), (15, 21),
    (12, 14), (14, 16), (16, 18), (16, 20), (16, 22),
    (11, 23), (12, 24), (23, 24),
    (23, 25), (25, 27), (27, 29), (29, 31),
    (24, 26), (26, 28), (28, 30), (30, 32),
]


def load_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def resize_panel(frame_bgr: np.ndarray, panel_height: int) -> np.ndarray:
    h, w = frame_bgr.shape[:2]
    scale = panel_height / max(h, 1)
    new_w = max(1, int(round(w * scale)))
    return cv2.resize(frame_bgr, (new_w, panel_height), interpolation=cv2.INTER_AREA)


def draw_pose(frame_bgr: np.ndarray, landmarks: list[dict[str, Any]] | None, title: str, footer_lines: list[str]) -> np.ndarray:
    canvas = frame_bgr.copy()
    height, width = canvas.shape[:2]

    if landmarks:
        for start_idx, end_idx in POSE_CONNECTIONS:
            if start_idx >= len(landmarks) or end_idx >= len(landmarks):
                continue
            start = landmarks[start_idx]
            end = landmarks[end_idx]
            sx = int(round(float(start["x"]) * width))
            sy = int(round(float(start["y"]) * height))
            ex = int(round(float(end["x"]) * width))
            ey = int(round(float(end["y"]) * height))
            cv2.line(canvas, (sx, sy), (ex, ey), (70, 220, 255), 2, cv2.LINE_AA)

        for idx, lm in enumerate(landmarks):
            x = int(round(float(lm["x"]) * width))
            y = int(round(float(lm["y"]) * height))
            visibility = float(lm.get("visibility", 1.0))
            if visibility >= 0.75:
                color = (0, 255, 120)
            elif visibility >= 0.35:
                color = (0, 220, 255)
            else:
                color = (0, 120, 255)
            cv2.circle(canvas, (x, y), 4, color, -1, cv2.LINE_AA)

    cv2.putText(canvas, title, (18, 34), cv2.FONT_HERSHEY_SIMPLEX, 0.95, (255, 255, 255), 2, cv2.LINE_AA)
    for idx, line in enumerate(footer_lines):
        y = 66 + idx * 24
        cv2.putText(canvas, line, (18, y), cv2.FONT_HERSHEY_SIMPLEX, 0.62, (20, 20, 20), 3, cv2.LINE_AA)
        cv2.putText(canvas, line, (18, y), cv2.FONT_HERSHEY_SIMPLEX, 0.62, (255, 255, 255), 1, cv2.LINE_AA)

    return canvas


def correction_footer(frame_payload: dict[str, Any]) -> list[str]:
    flags = frame_payload.get("correction_flags", {})
    world = flags.get("world", {}) if isinstance(flags, dict) else {}
    image = flags.get("image", {}) if isinstance(flags, dict) else {}
    return [
        f"pose_detected={bool(frame_payload.get('pose_detected'))}",
        f"world low_vis={int(world.get('low_visibility_joint_count', 0))}  frozen={int(world.get('frozen_joint_count', 0))}  recon={int(world.get('reconstructed_joint_count', 0))}",
        f"image low_vis={int(image.get('low_visibility_joint_count', 0))}  frozen={int(image.get('frozen_joint_count', 0))}  recon={int(image.get('reconstructed_joint_count', 0))}",
    ]


def raw_footer(frame_payload: dict[str, Any]) -> list[str]:
    landmarks = frame_payload.get("pose_landmarks")
    if not landmarks:
        return [f"pose_detected={bool(frame_payload.get('pose_detected'))}", "no landmarks"]
    low_vis = sum(1 for lm in landmarks if float(lm.get("visibility", 1.0)) < 0.35)
    missing = sum(1 for lm in landmarks if float(lm.get("presence", 1.0)) < 0.35)
    return [
        f"pose_detected={bool(frame_payload.get('pose_detected'))}",
        f"low_visibility_joints={low_vis}",
        f"low_presence_joints={missing}",
    ]


def main() -> None:
    parser = argparse.ArgumentParser(description="Render raw vs corrected MediaPipe skeleton comparison video.")
    parser.add_argument("--raw-pose-json", type=Path, default=DEFAULT_RAW_POSE_JSON)
    parser.add_argument("--corrected-pose-json", type=Path, default=DEFAULT_CORRECTED_POSE_JSON)
    parser.add_argument("--output-video", type=Path, default=DEFAULT_OUTPUT_VIDEO)
    parser.add_argument("--panel-height", type=int, default=DEFAULT_PANEL_HEIGHT)
    parser.add_argument("--max-frames", type=int, default=0)
    args = parser.parse_args()

    raw_payload = load_json(args.raw_pose_json)
    corrected_payload = load_json(args.corrected_pose_json)

    raw_frames = {int(frame["frame_index"]): frame for frame in raw_payload.get("frames", [])}
    corrected_frames = {int(frame["frame_index"]): frame for frame in corrected_payload.get("frames", [])}
    frame_indices = sorted(set(raw_frames.keys()) & set(corrected_frames.keys()))
    if args.max_frames > 0:
        frame_indices = frame_indices[: args.max_frames]

    video_path = Path(
        corrected_payload.get("video_metadata", {}).get("video_path")
        or raw_payload.get("video_metadata", {}).get("video_path")
        or ROOT.parent / "video" / "주황.mp4"
    )

    cap = cv2.VideoCapture(str(video_path))
    if not cap.isOpened():
        raise FileNotFoundError(f"Unable to open video: {video_path}")

    fps = float(cap.get(cv2.CAP_PROP_FPS) or 30.0)

    ok, first_frame = cap.read()
    if not ok:
        raise RuntimeError("Unable to read first video frame")
    first_panel = resize_panel(first_frame, args.panel_height)
    panel_h, panel_w = first_panel.shape[:2]
    output_size = (panel_w * 2, panel_h)

    args.output_video.parent.mkdir(parents=True, exist_ok=True)
    writer = cv2.VideoWriter(
        str(args.output_video),
        cv2.VideoWriter_fourcc(*"mp4v"),
        fps,
        output_size,
    )

    try:
        for frame_index in frame_indices:
            cap.set(cv2.CAP_PROP_POS_FRAMES, frame_index)
            ok, frame_bgr = cap.read()
            if not ok:
                break

            raw_frame = raw_frames[frame_index]
            corrected_frame = corrected_frames[frame_index]

            raw_panel = resize_panel(frame_bgr, args.panel_height)
            corrected_panel = raw_panel.copy()

            raw_panel = draw_pose(
                raw_panel,
                raw_frame.get("pose_landmarks"),
                "RAW MEDIAPIPE POSE",
                [
                    f"frame={frame_index}  ts={int(raw_frame['timestamp_ms'])}ms",
                    *raw_footer(raw_frame),
                ],
            )
            corrected_panel = draw_pose(
                corrected_panel,
                corrected_frame.get("pose_landmarks"),
                "CORRECTED POSE",
                [
                    f"frame={frame_index}  ts={int(corrected_frame['timestamp_ms'])}ms",
                    *correction_footer(corrected_frame),
                ],
            )

            canvas = np.zeros((panel_h, panel_w * 2, 3), dtype=np.uint8)
            canvas[:, :panel_w] = raw_panel
            canvas[:, panel_w:] = corrected_panel
            cv2.line(canvas, (panel_w, 0), (panel_w, panel_h), (255, 255, 255), 2)
            writer.write(canvas)
    finally:
        writer.release()
        cap.release()


if __name__ == "__main__":
    main()

from __future__ import annotations

import argparse
from pathlib import Path
import sys

import cv2
import mediapipe as mp
import numpy as np

ROOT = Path(__file__).resolve().parent
CUSTOM_SKELETON_ROOT = ROOT.parent / "custom_skeleton_verify"
sys.path.insert(0, str(CUSTOM_SKELETON_ROOT))

from mediapipe_custom_skeleton_verify import make_landmarker  # noqa: E402

from hold_contact_state import (  # noqa: E402
    HoldContactTracker,
    compute_contact_points_px,
    load_hold_detections,
)


STATE_COLORS = {
    "FREE": (130, 130, 130),
    "REACH": (0, 215, 255),
    "GRIP": (0, 200, 0),
    "STEP": (255, 180, 0),
    "RELEASE": (0, 0, 255),
}

LIMB_DRAW_ORDER = [
    "left_hand",
    "right_hand",
    "left_foot",
    "right_foot",
]


def resize_for_preview(
    frame_bgr: np.ndarray,
    max_width: int,
    max_height: int,
) -> np.ndarray:
    height, width = frame_bgr.shape[:2]
    if width <= 0 or height <= 0:
        return frame_bgr
    scale = min(max_width / width, max_height / height, 1.0)
    if scale >= 0.999:
        return frame_bgr
    new_size = (max(1, int(round(width * scale))), max(1, int(round(height * scale))))
    return cv2.resize(frame_bgr, new_size, interpolation=cv2.INTER_AREA)


def draw_hold_boxes(
    frame_bgr: np.ndarray,
    holds: list,
    limb_states: dict[str, dict[str, object]],
) -> None:
    active_hold_ids = {
        int(payload["active_hold_id"])
        for payload in limb_states.values()
        if payload.get("active_hold_id") is not None
    }
    candidate_hold_ids = {
        int(payload["candidate_hold_id"])
        for payload in limb_states.values()
        if payload.get("candidate_hold_id") is not None
    }

    for hold in holds:
        color = (90, 90, 90)
        thickness = 1
        if hold.hold_id in candidate_hold_ids:
            color = (0, 215, 255)
            thickness = 2
        if hold.hold_id in active_hold_ids:
            color = (0, 220, 0)
            thickness = 3
        pt1 = (int(round(hold.x1)), int(round(hold.y1)))
        pt2 = (int(round(hold.x2)), int(round(hold.y2)))
        cv2.rectangle(frame_bgr, pt1, pt2, color, thickness, cv2.LINE_AA)
        cv2.putText(
            frame_bgr,
            str(hold.hold_id),
            (pt1[0], max(18, pt1[1] - 6)),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.45,
            color,
            1,
            cv2.LINE_AA,
        )


def draw_limb_states(
    frame_bgr: np.ndarray,
    limb_points_px: dict[str, np.ndarray | None],
    limb_states: dict[str, dict[str, object]],
) -> None:
    panel_y = 28
    for limb_name in LIMB_DRAW_ORDER:
        payload = limb_states[limb_name]
        point_px = limb_points_px.get(limb_name)
        state = str(payload["state"])
        color = STATE_COLORS.get(state, (255, 255, 255))
        hold_id = payload.get("active_hold_id")
        if point_px is not None:
            center = tuple(int(round(v)) for v in point_px)
            cv2.circle(frame_bgr, center, 6, color, -1, cv2.LINE_AA)
            cv2.circle(frame_bgr, center, 9, color, 2, cv2.LINE_AA)
            label = f"{limb_name}: {state}"
            if hold_id is not None:
                label += f" #{int(hold_id)}"
            cv2.putText(
                frame_bgr,
                label,
                (center[0] + 10, center[1] - 8),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.45,
                color,
                1,
                cv2.LINE_AA,
            )

        summary = f"{limb_name}: {state}"
        if hold_id is not None:
            summary += f" #{int(hold_id)}"
        transition = payload.get("transition")
        if transition:
            summary += f" ({transition})"
        cv2.putText(
            frame_bgr,
            summary,
            (18, panel_y),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.55,
            color,
            2,
            cv2.LINE_AA,
        )
        panel_y += 24


def draw_header(
    frame_bgr: np.ndarray,
    frame_idx: int,
    timestamp_ms: int,
    source_file: str | None,
) -> None:
    line1 = f"Frame {frame_idx}  Time {timestamp_ms} ms"
    line2 = f"Hold source: {source_file or 'unknown'}"
    cv2.putText(
        frame_bgr,
        line1,
        (18, frame_bgr.shape[0] - 38),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.6,
        (255, 255, 255),
        2,
        cv2.LINE_AA,
    )
    cv2.putText(
        frame_bgr,
        line2,
        (18, frame_bgr.shape[0] - 14),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.5,
        (200, 200, 200),
        1,
        cv2.LINE_AA,
    )


def evaluate_and_visualize(
    input_video: Path,
    task_model: Path,
    detections_json: Path,
    output_video: Path | None,
    no_window: bool,
    preview_max_width: int,
    preview_max_height: int,
) -> None:
    hold_payload = load_hold_detections(detections_json)
    tracker = HoldContactTracker(hold_payload["holds"])

    cap = cv2.VideoCapture(str(input_video))
    if not cap.isOpened():
        raise FileNotFoundError(f"Unable to open video: {input_video}")

    frame_width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH) or 0)
    frame_height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT) or 0)
    fps = float(cap.get(cv2.CAP_PROP_FPS) or 30.0)

    writer = None
    if output_video is not None:
        fourcc = cv2.VideoWriter_fourcc(*"mp4v")
        writer = cv2.VideoWriter(str(output_video), fourcc, fps, (frame_width, frame_height))

    landmarker = make_landmarker(task_model)

    window_name = "Hold Contact States"
    frame_idx = 0
    while True:
        ok, frame_bgr = cap.read()
        if not ok:
            break

        timestamp_ms = int(round(frame_idx * 1000.0 / max(fps, 1.0)))
        frame_rgb = cv2.cvtColor(frame_bgr, cv2.COLOR_BGR2RGB)
        mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=frame_rgb)
        result = landmarker.detect_for_video(mp_image, timestamp_ms)

        pose_landmarks = result.pose_landmarks[0] if result.pose_landmarks else None
        limb_points = compute_contact_points_px(pose_landmarks, frame_width, frame_height)
        limb_states = tracker.update_frame(limb_points, timestamp_ms)

        overlay = frame_bgr.copy()
        draw_hold_boxes(overlay, hold_payload["holds"], limb_states)
        draw_limb_states(overlay, limb_points, limb_states)
        draw_header(overlay, frame_idx, timestamp_ms, hold_payload["source_file"])

        if writer is not None:
            writer.write(overlay)

        if not no_window:
            preview = resize_for_preview(overlay, preview_max_width, preview_max_height)
            cv2.imshow(window_name, preview)
            key = cv2.waitKey(1) & 0xFF
            if key in (27, ord("q"), ord("Q")):
                break

        frame_idx += 1

    cap.release()
    landmarker.close()
    if writer is not None:
        writer.release()
    if not no_window:
        cv2.destroyAllWindows()


def main() -> None:
    parser = argparse.ArgumentParser(description="Visualize 2D hold contact / grip states on top of the source video.")
    parser.add_argument("--input-video", type=Path, default=ROOT.parent / "video" / "주황.mp4")
    parser.add_argument("--task-model", type=Path, default=CUSTOM_SKELETON_ROOT / "pose_landmarker_lite.task")
    parser.add_argument("--detections-json", type=Path, default=ROOT.parent / "detections.json")
    parser.add_argument("--output-video", type=Path, default=ROOT / "gate2_hold_contact_overlay.mp4")
    parser.add_argument("--no-window", action="store_true")
    parser.add_argument("--preview-max-width", type=int, default=1400)
    parser.add_argument("--preview-max-height", type=int, default=900)
    args = parser.parse_args()

    evaluate_and_visualize(
        input_video=args.input_video,
        task_model=args.task_model,
        detections_json=args.detections_json,
        output_video=args.output_video,
        no_window=args.no_window,
        preview_max_width=args.preview_max_width,
        preview_max_height=args.preview_max_height,
    )
    print(f"[OK] Wrote {args.output_video.resolve()}")


if __name__ == "__main__":
    main()

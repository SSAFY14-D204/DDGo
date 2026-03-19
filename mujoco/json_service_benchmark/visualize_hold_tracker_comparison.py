from __future__ import annotations

import argparse
import json
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

import cv2
import numpy as np

import run_json_service_benchmark as bench
from polygon_hold_contact_state import PolygonHoldContactTracker, load_polygon_service_holds


ROOT = Path(__file__).resolve().parent
DEFAULT_VIDEO = ROOT.parent / "video" / "주황.mp4"
DEFAULT_BBOX_HOLDS = ROOT / "benchmark_inputs" / "holds.json"
DEFAULT_POLYGON_HOLDS = ROOT / "benchmark_inputs" / "polygon" / "holds_polygon.json"
DEFAULT_POSE_JSON = ROOT / "benchmark_inputs" / "polygon" / "pose3d_sequence.json"
DEFAULT_OUTPUT_VIDEO = ROOT / "hold_tracker_comparison_overlay.mp4"
DEFAULT_OUTPUT_SUMMARY = ROOT / "hold_tracker_comparison_summary.json"


STATE_COLORS = {
    "FREE": (130, 130, 130),
    "REACH": (0, 215, 255),
    "GRIP": (0, 200, 0),
    "STEP": (255, 180, 0),
    "RELEASE": (0, 0, 255),
}

LIMB_ORDER = ("left_hand", "right_hand", "left_foot", "right_foot")


def resize_for_preview(frame_bgr: np.ndarray, max_width: int, max_height: int) -> np.ndarray:
    height, width = frame_bgr.shape[:2]
    if width <= 0 or height <= 0:
        return frame_bgr
    scale = min(max_width / width, max_height / height, 1.0)
    if scale >= 0.999:
        return frame_bgr
    new_size = (max(1, int(round(width * scale))), max(1, int(round(height * scale))))
    return cv2.resize(frame_bgr, new_size, interpolation=cv2.INTER_AREA)


def draw_text_block(frame_bgr: np.ndarray, lines: list[str], origin: tuple[int, int], color: tuple[int, int, int]) -> None:
    x, y = origin
    for idx, line in enumerate(lines):
        cv2.putText(
            frame_bgr,
            line,
            (x, y + idx * 22),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.56,
            color,
            2,
            cv2.LINE_AA,
        )


def draw_bbox_holds(frame_bgr: np.ndarray, holds: list[Any], limb_states: dict[str, dict[str, Any]]) -> None:
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
        cv2.putText(frame_bgr, str(hold.hold_id), (pt1[0], max(20, pt1[1] - 6)), cv2.FONT_HERSHEY_SIMPLEX, 0.45, color, 1, cv2.LINE_AA)


def draw_polygon_holds(frame_bgr: np.ndarray, holds: list[Any], limb_states: dict[str, dict[str, Any]]) -> None:
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
        pts = np.round(np.asarray(hold.polygon_px, dtype=np.float32)).astype(np.int32).reshape(-1, 1, 2)
        cv2.polylines(frame_bgr, [pts], isClosed=True, color=color, thickness=thickness, lineType=cv2.LINE_AA)
        label_pos = tuple(np.round(np.asarray([hold.cx_px, hold.cy_px], dtype=np.float64)).astype(np.int32))
        cv2.putText(frame_bgr, str(hold.hold_id), label_pos, cv2.FONT_HERSHEY_SIMPLEX, 0.45, color, 1, cv2.LINE_AA)


def draw_limb_overlay(frame_bgr: np.ndarray, limb_points: dict[str, np.ndarray | None], limb_states: dict[str, dict[str, Any]], title: str) -> None:
    draw_text_block(frame_bgr, [title], (18, 28), (255, 255, 255))
    panel_y = 56
    for limb_name in LIMB_ORDER:
        payload = limb_states[limb_name]
        state = str(payload.get("state", "FREE"))
        point = limb_points.get(limb_name)
        color = STATE_COLORS.get(state, (255, 255, 255))
        label = f"{limb_name}: {state}"
        if payload.get("active_hold_id") is not None:
            label += f" #{int(payload['active_hold_id'])}"
        if point is not None:
            center = tuple(int(round(v)) for v in point)
            cv2.circle(frame_bgr, center, 6, color, -1, cv2.LINE_AA)
            cv2.circle(frame_bgr, center, 10, color, 2, cv2.LINE_AA)
            cv2.putText(frame_bgr, label, (center[0] + 10, center[1] - 8), cv2.FONT_HERSHEY_SIMPLEX, 0.45, color, 1, cv2.LINE_AA)
        draw_text_block(frame_bgr, [label], (18, panel_y), color)
        panel_y += 24


def load_pose_frames(pose_json: Path) -> tuple[dict[int, dict[str, Any]], dict[str, Any]]:
    payload = json.loads(pose_json.read_text(encoding="utf-8"))
    frames = {int(frame["frame_index"]): frame for frame in payload.get("frames", [])}
    return frames, payload.get("video_metadata", {})


def compare_trackers(
    input_video: Path,
    bbox_holds_json: Path,
    polygon_holds_json: Path,
    pose_json: Path,
    output_video: Path,
    output_summary: Path,
    no_window: bool,
    preview_max_width: int,
    preview_max_height: int,
) -> None:
    bbox_payload = bench.load_service_holds(bbox_holds_json)
    polygon_payload = load_polygon_service_holds(polygon_holds_json)
    bbox_tracker = bench.HoldContactTracker(bbox_payload["holds"])
    polygon_tracker = PolygonHoldContactTracker(polygon_payload["holds"])
    frames_by_index, pose_meta = load_pose_frames(pose_json)

    cap = cv2.VideoCapture(str(input_video))
    if not cap.isOpened():
        raise FileNotFoundError(f"Unable to open video: {input_video}")

    frame_width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH) or pose_meta.get("frame_width") or 0)
    frame_height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT) or pose_meta.get("frame_height") or 0)
    fps = float(cap.get(cv2.CAP_PROP_FPS) or pose_meta.get("fps") or 30.0)

    out_size = (frame_width * 2, frame_height)
    writer = cv2.VideoWriter(str(output_video), cv2.VideoWriter_fourcc(*"mp4v"), fps, out_size)

    state_counts = {
        "bbox": defaultdict(Counter),
        "polygon": defaultdict(Counter),
    }
    difference_counts = {limb: Counter() for limb in LIMB_ORDER}

    window_name = "Hold Tracker Comparison"
    frame_idx = 0
    while True:
        ok, frame_bgr = cap.read()
        if not ok:
            break

        frame_payload = frames_by_index.get(frame_idx)
        timestamp_ms = int(round(frame_idx * 1000.0 / max(fps, 1.0)))
        pose_landmarks = bench.landmark_payload_to_objects(frame_payload.get("pose_landmarks")) if frame_payload else None
        limb_points = bench.compute_contact_points_px(pose_landmarks, frame_width, frame_height)
        bbox_states = bbox_tracker.update_frame(limb_points, timestamp_ms)
        polygon_states = polygon_tracker.update_frame(limb_points, timestamp_ms)

        for limb_name in LIMB_ORDER:
            bbox_state = str(bbox_states[limb_name]["state"])
            polygon_state = str(polygon_states[limb_name]["state"])
            state_counts["bbox"][limb_name][bbox_state] += 1
            state_counts["polygon"][limb_name][polygon_state] += 1
            if bbox_state != polygon_state or bbox_states[limb_name].get("active_hold_id") != polygon_states[limb_name].get("active_hold_id"):
                difference_counts[limb_name]["different"] += 1
            else:
                difference_counts[limb_name]["same"] += 1

        left_panel = frame_bgr.copy()
        right_panel = frame_bgr.copy()
        draw_bbox_holds(left_panel, bbox_payload["holds"], bbox_states)
        draw_polygon_holds(right_panel, polygon_payload["holds"], polygon_states)
        draw_limb_overlay(left_panel, limb_points, bbox_states, "BBox Hold Tracker")
        draw_limb_overlay(right_panel, limb_points, polygon_states, "Polygon Hold Tracker")
        draw_text_block(left_panel, [f"Frame {frame_idx}", f"Time {timestamp_ms} ms"], (18, frame_height - 56), (255, 255, 255))
        draw_text_block(right_panel, [f"Frame {frame_idx}", f"Time {timestamp_ms} ms"], (18, frame_height - 56), (255, 255, 255))

        combined = np.hstack([left_panel, right_panel])
        writer.write(combined)

        if not no_window:
            preview = resize_for_preview(combined, preview_max_width, preview_max_height)
            cv2.imshow(window_name, preview)
            key = cv2.waitKey(1) & 0xFF
            if key in (27, ord("q"), ord("Q")):
                break

        frame_idx += 1

    cap.release()
    writer.release()
    if not no_window:
        cv2.destroyAllWindows()

    summary = {
        "video": str(input_video.resolve()),
        "bbox_holds_json": str(bbox_holds_json.resolve()),
        "polygon_holds_json": str(polygon_holds_json.resolve()),
        "pose_json": str(pose_json.resolve()),
        "state_counts": {
            tracker_name: {
                limb_name: dict(counter)
                for limb_name, counter in tracker_counts.items()
            }
            for tracker_name, tracker_counts in state_counts.items()
        },
        "difference_counts": {
            limb_name: dict(counter)
            for limb_name, counter in difference_counts.items()
        },
        "output_video": str(output_video.resolve()),
    }
    output_summary.write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description="Visualize bbox vs polygon hold tracking side-by-side.")
    parser.add_argument("--input-video", type=Path, default=DEFAULT_VIDEO)
    parser.add_argument("--bbox-holds-json", type=Path, default=DEFAULT_BBOX_HOLDS)
    parser.add_argument("--polygon-holds-json", type=Path, default=DEFAULT_POLYGON_HOLDS)
    parser.add_argument("--pose-json", type=Path, default=DEFAULT_POSE_JSON)
    parser.add_argument("--output-video", type=Path, default=DEFAULT_OUTPUT_VIDEO)
    parser.add_argument("--output-summary", type=Path, default=DEFAULT_OUTPUT_SUMMARY)
    parser.add_argument("--no-window", action="store_true")
    parser.add_argument("--preview-max-width", type=int, default=1600)
    parser.add_argument("--preview-max-height", type=int, default=900)
    args = parser.parse_args()

    compare_trackers(
        input_video=args.input_video,
        bbox_holds_json=args.bbox_holds_json,
        polygon_holds_json=args.polygon_holds_json,
        pose_json=args.pose_json,
        output_video=args.output_video,
        output_summary=args.output_summary,
        no_window=args.no_window,
        preview_max_width=args.preview_max_width,
        preview_max_height=args.preview_max_height,
    )
    print(f"[OK] Wrote {args.output_video.resolve()}")
    print(f"[OK] Wrote {args.output_summary.resolve()}")


if __name__ == "__main__":
    main()

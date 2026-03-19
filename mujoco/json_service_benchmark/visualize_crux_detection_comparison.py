from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

import cv2
import numpy as np

from polygon_hold_contact_state import load_polygon_service_holds


ROOT = Path(__file__).resolve().parent
DEFAULT_FAST_REPORT = ROOT / "crux_detection_fast_report.json"
DEFAULT_PHYSICS_REPORT = ROOT / "crux_detection_physics_report.json"
DEFAULT_HOLDS_JSON = ROOT / "benchmark_inputs" / "polygon" / "holds_polygon.json"
DEFAULT_VIDEO = ROOT.parent / "video" / "주황.mp4"
DEFAULT_OUTPUT = ROOT / "crux_detection_comparison_overlay.mp4"

RANK_COLORS = [
    (80, 255, 80),
    (0, 215, 255),
    (255, 180, 80),
]


def draw_text_block(
    image: np.ndarray,
    lines: list[str],
    origin: tuple[int, int],
    color: tuple[int, int, int] = (255, 255, 255),
    line_height: int = 26,
) -> None:
    x, y = origin
    for idx, line in enumerate(lines):
        yy = y + idx * line_height
        cv2.putText(image, line, (x + 1, yy + 1), cv2.FONT_HERSHEY_SIMPLEX, 0.63, (0, 0, 0), 2, cv2.LINE_AA)
        cv2.putText(image, line, (x, yy), cv2.FONT_HERSHEY_SIMPLEX, 0.63, color, 1, cv2.LINE_AA)


def load_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def _candidate_activity(candidate: dict[str, Any], frame_index: int) -> bool:
    segment = candidate.get("best_segment", {})
    return int(segment.get("start_frame", -1)) <= frame_index <= int(segment.get("end_frame", -2))


def _draw_hold_panel(
    frame: np.ndarray,
    title: str,
    candidates: list[dict[str, Any]],
    hold_by_id: dict[int, Any],
    frame_index: int,
    score_key: str,
) -> np.ndarray:
    panel = frame.copy()
    overlay = panel.copy()
    cv2.rectangle(overlay, (0, 0), (panel.shape[1], 170), (20, 20, 20), -1)
    panel = cv2.addWeighted(overlay, 0.35, panel, 0.65, 0.0)

    lines = [title]
    for rank, candidate in enumerate(candidates, start=1):
        hold_id = int(candidate["hold_id"])
        score = float(candidate.get(score_key, 0.0))
        duration_s = float(candidate.get("best_segment", {}).get("duration_s", 0.0))
        active = _candidate_activity(candidate, frame_index)
        marker = "*" if active else "-"
        lines.append(f"{marker} #{rank} hold {hold_id}  score={score:.3f}  seg={duration_s:.2f}s")
    draw_text_block(panel, lines, (16, 28), color=(255, 255, 255))

    for rank, candidate in enumerate(candidates):
        hold_id = int(candidate["hold_id"])
        hold = hold_by_id.get(hold_id)
        if hold is None:
            continue
        polygon = np.asarray(hold.polygon_px, dtype=np.int32).reshape((-1, 1, 2))
        color = RANK_COLORS[rank % len(RANK_COLORS)]
        active = _candidate_activity(candidate, frame_index)
        thickness = 5 if active else 2
        cv2.polylines(panel, [polygon], True, color, thickness, cv2.LINE_AA)
        centroid = np.array([float(hold.cx_px), float(hold.cy_px)], dtype=np.int32)
        label = f"{rank+1}:{hold_id}"
        cv2.putText(panel, label, (int(centroid[0]) + 4, int(centroid[1]) - 4), cv2.FONT_HERSHEY_SIMPLEX, 0.7, color, 2, cv2.LINE_AA)
    return panel


def main() -> None:
    parser = argparse.ArgumentParser(description="Visualize fast vs physics crux candidates side by side.")
    parser.add_argument("--fast-report", type=Path, default=DEFAULT_FAST_REPORT)
    parser.add_argument("--physics-report", type=Path, default=DEFAULT_PHYSICS_REPORT)
    parser.add_argument("--holds-json", type=Path, default=DEFAULT_HOLDS_JSON)
    parser.add_argument("--input-video", type=Path, default=DEFAULT_VIDEO)
    parser.add_argument("--output-video", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--max-frames", type=int, default=0)
    args = parser.parse_args()

    fast_report = load_json(args.fast_report)
    physics_report = load_json(args.physics_report)
    holds_payload = load_polygon_service_holds(args.holds_json)
    hold_by_id = {int(hold.hold_id): hold for hold in holds_payload["holds"]}

    fast_candidates = list(fast_report["crux_result"]["top_candidates"])
    physics_candidates = list(physics_report["crux_result"]["top_candidates"])

    cap = cv2.VideoCapture(str(args.input_video))
    if not cap.isOpened():
        raise FileNotFoundError(f"Unable to open video: {args.input_video}")

    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH) or 0)
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT) or 0)
    fps = float(cap.get(cv2.CAP_PROP_FPS) or 30.0)
    total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT) or 0)

    max_frames = total_frames if args.max_frames <= 0 else min(total_frames, int(args.max_frames))
    output_size = (width * 2, height)
    args.output_video.parent.mkdir(parents=True, exist_ok=True)
    writer = cv2.VideoWriter(
        str(args.output_video),
        cv2.VideoWriter_fourcc(*"mp4v"),
        fps,
        output_size,
    )

    try:
        frame_index = 0
        while frame_index < max_frames:
            ok, frame = cap.read()
            if not ok:
                break
            fast_panel = _draw_hold_panel(
                frame,
                "FAST CRUX: dwell only",
                fast_candidates,
                hold_by_id,
                frame_index,
                "fast_crux_score",
            )
            physics_panel = _draw_hold_panel(
                frame,
                "PHYSICS CRUX: dwell + load + stability + shift",
                physics_candidates,
                hold_by_id,
                frame_index,
                "physics_crux_score",
            )
            canvas = np.zeros((height, width * 2, 3), dtype=np.uint8)
            canvas[:, :width] = fast_panel
            canvas[:, width:] = physics_panel
            cv2.line(canvas, (width, 0), (width, height), (255, 255, 255), 2)
            writer.write(canvas)
            frame_index += 1
    finally:
        writer.release()
        cap.release()


if __name__ == "__main__":
    main()

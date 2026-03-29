from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any

import cv2
import numpy as np

ROOT = Path(__file__).resolve().parent
PROJECT_ROOT = ROOT.parent
ARTIC_ROOT = PROJECT_ROOT / "custom_articulated_human"

sys.path.insert(0, str(ARTIC_ROOT))

from hold_contact_state import load_hold_detections  # noqa: E402

DEFAULT_REPORT = ROOT / "hand_grip_constraint_report.json"

MODE_COLORS = {
    "none": (110, 110, 110),
    "left_only": (255, 200, 80),
    "right_only": (80, 180, 255),
    "both": (120, 255, 120),
}

STATE_COLORS = {
    "FREE": (120, 120, 120),
    "REACH": (0, 220, 255),
    "GRIP": (0, 255, 100),
    "RELEASE": (80, 80, 255),
}


def fit_series(values: list[float], height: int, max_value: float | None = None) -> tuple[np.ndarray, float]:
    arr = np.asarray(values, dtype=np.float64)
    if arr.size == 0:
        return np.zeros((0,), dtype=np.int32), 1.0
    peak = float(np.max(arr)) if max_value is None else float(max_value)
    peak = max(peak, 1e-6)
    scaled = np.clip(arr / peak, 0.0, 1.0)
    ys = (height - 1 - scaled * (height - 1)).astype(np.int32)
    return ys, peak


def draw_series_panel(
    canvas: np.ndarray,
    x: int,
    y: int,
    width: int,
    height: int,
    values: list[float],
    current_idx: int,
    label: str,
    color: tuple[int, int, int],
) -> None:
    cv2.rectangle(canvas, (x, y), (x + width, y + height), (60, 60, 60), 1)
    cv2.putText(canvas, label, (x + 8, y + 18), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (230, 230, 230), 1, cv2.LINE_AA)
    if not values:
        return
    plot_top = y + 28
    plot_bottom = y + height - 8
    plot_height = max(8, plot_bottom - plot_top)
    plot_width = max(1, width - 16)
    ys, peak = fit_series(values, plot_height)
    xs = np.linspace(x + 8, x + 8 + plot_width, num=len(values), dtype=np.int32)
    pts = np.stack([xs, plot_top + ys], axis=1)
    if len(pts) >= 2:
        cv2.polylines(canvas, [pts], False, color, 2, cv2.LINE_AA)
    current_idx = int(np.clip(current_idx, 0, len(values) - 1))
    cx = int(xs[current_idx])
    cy = int(plot_top + ys[current_idx])
    cv2.circle(canvas, (cx, cy), 4, color, -1, cv2.LINE_AA)
    current_value = values[current_idx]
    cv2.putText(
        canvas,
        f"cur {current_value:.3f} / max {peak:.3f}",
        (x + 8, y + height - 8),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.42,
        (190, 190, 190),
        1,
        cv2.LINE_AA,
    )


def draw_hold_boxes(
    frame_bgr: np.ndarray,
    hold_lookup: dict[int, Any],
    left_payload: dict[str, Any],
    right_payload: dict[str, Any],
) -> None:
    highlight_ids = set()
    for payload in (left_payload, right_payload):
        for key in ("candidate_hold_id", "active_hold_id"):
            value = payload.get(key)
            if value is not None:
                highlight_ids.add(int(value))

    for hold_id in highlight_ids:
        hold = hold_lookup.get(int(hold_id))
        if hold is None:
            continue
        x1, y1, x2, y2 = int(hold.x1), int(hold.y1), int(hold.x2), int(hold.y2)
        color = (0, 220, 255)
        if int(left_payload.get("active_hold_id") or -1) == hold_id or int(right_payload.get("active_hold_id") or -1) == hold_id:
            color = (0, 255, 90)
        cv2.rectangle(frame_bgr, (x1, y1), (x2, y2), color, 2, cv2.LINE_AA)
        cv2.putText(
            frame_bgr,
            f"H{hold_id}",
            (x1, max(18, y1 - 6)),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.55,
            color,
            2,
            cv2.LINE_AA,
        )


def draw_hand_state_box(
    canvas: np.ndarray,
    x: int,
    y: int,
    title: str,
    state_payload: dict[str, Any],
    hand_report_payload: dict[str, Any],
    side_color: tuple[int, int, int],
) -> None:
    cv2.rectangle(canvas, (x, y), (x + 300, y + 116), (55, 55, 55), 1)
    cv2.putText(canvas, title, (x + 8, y + 18), cv2.FONT_HERSHEY_SIMPLEX, 0.6, side_color, 2, cv2.LINE_AA)
    state = str(hand_report_payload.get("state", "FREE"))
    state_color = STATE_COLORS.get(state, (220, 220, 220))
    lines = [
        f"state: {state}",
        f"active_hold: {hand_report_payload.get('active_hold_id')}",
        f"candidate_hold: {state_payload.get('candidate_hold_id')}",
        f"weld_active: {bool(hand_report_payload.get('weld_active'))}",
        f"anchor_gap: {float(hand_report_payload.get('anchor_gap_translation_m', 0.0)):.3f} m",
        f"speed: {float(state_payload.get('speed_px_s') or 0.0):.1f} px/s",
    ]
    for idx, line in enumerate(lines):
        color = state_color if idx == 0 else (220, 220, 220)
        cv2.putText(
            canvas,
            line,
            (x + 10, y + 40 + idx * 14),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.45,
            color,
            1,
            cv2.LINE_AA,
        )


def load_reports(report_path: Path) -> tuple[dict[str, Any], dict[str, Any]]:
    report = json.loads(report_path.read_text(encoding="utf-8"))
    dynamic_path = Path(report["dynamic_report"])
    dynamic_report = json.loads(dynamic_path.read_text(encoding="utf-8"))
    return report, dynamic_report


def evaluate_video(
    *,
    report_path: Path,
    output_path: Path,
    show_window: bool,
    preview_max_width: int,
    preview_max_height: int,
) -> Path:
    report, dynamic_report = load_reports(report_path)
    video_path = Path(dynamic_report["video"])
    detections_json = dynamic_report.get("detections_json")
    hold_lookup: dict[int, Any] = {}
    if detections_json:
        payload = load_hold_detections(Path(detections_json))
        hold_lookup = {int(hold.hold_id): hold for hold in payload["holds"]}

    cap = cv2.VideoCapture(str(video_path))
    if not cap.isOpened():
        raise RuntimeError(f"Could not open video: {video_path}")

    frame_width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    frame_height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    fps = float(cap.get(cv2.CAP_PROP_FPS) or 30.0)

    constraint_frames = report["frames"]
    dynamic_frames_by_idx = {int(frame["frame_index"]): frame for frame in dynamic_report["frames"]}
    frame_indices = [int(frame["frame_index"]) for frame in constraint_frames]

    panel_width = 760
    output_size = (frame_width + panel_width, frame_height)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    writer = cv2.VideoWriter(
        str(output_path),
        cv2.VideoWriter_fourcc(*"mp4v"),
        fps,
        output_size,
    )
    if not writer.isOpened():
        raise RuntimeError(f"Could not open output writer: {output_path}")

    qfrc_series = [float(frame["constrained"]["qfrc_constraint_norm"]) for frame in constraint_frames]
    delta_root_series = [float(frame["delta"]["root_inverse_force_norm"]) for frame in constraint_frames]
    left_gap_series = [float(frame["hands"]["left_hand"]["anchor_gap_translation_m"]) for frame in constraint_frames]
    right_gap_series = [float(frame["hands"]["right_hand"]["anchor_gap_translation_m"]) for frame in constraint_frames]
    both_series = [1.0 if frame["constraint_mode"] == "both" else 0.0 for frame in constraint_frames]

    window_name = "hand_grip_constraint_overlay"
    if show_window:
        cv2.namedWindow(window_name, cv2.WINDOW_NORMAL)

    current_capture_idx = 0
    for seq_idx, frame_idx in enumerate(frame_indices):
        while current_capture_idx <= frame_idx:
            ok, frame_bgr = cap.read()
            if not ok:
                break
            if current_capture_idx == frame_idx:
                break
            current_capture_idx += 1
        if frame_bgr is None:
            break
        current_capture_idx = frame_idx + 1

        constraint_frame = constraint_frames[seq_idx]
        dynamic_frame = dynamic_frames_by_idx.get(frame_idx, {})
        left_state = (dynamic_frame.get("limb_states") or {}).get("left_hand", {})
        right_state = (dynamic_frame.get("limb_states") or {}).get("right_hand", {})

        draw_hold_boxes(frame_bgr, hold_lookup, left_state, right_state)

        panel = np.full((frame_height, panel_width, 3), 24, dtype=np.uint8)
        mode = str(constraint_frame["constraint_mode"])
        mode_color = MODE_COLORS.get(mode, (200, 200, 200))
        cv2.putText(panel, "MuJoCo Hand Grip Constraint", (20, 30), cv2.FONT_HERSHEY_SIMPLEX, 0.82, (240, 240, 240), 2, cv2.LINE_AA)
        cv2.putText(panel, f"frame {frame_idx}  time {constraint_frame['timestamp_ms'] / 1000.0:.2f}s", (20, 58), cv2.FONT_HERSHEY_SIMPLEX, 0.56, (215, 215, 215), 1, cv2.LINE_AA)
        cv2.putText(panel, f"constraint_mode: {mode}", (20, 84), cv2.FONT_HERSHEY_SIMPLEX, 0.64, mode_color, 2, cv2.LINE_AA)
        cv2.putText(panel, f"phase: {constraint_frame.get('phase')}  conf: {constraint_frame.get('analysis_confidence')}", (20, 108), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (200, 200, 200), 1, cv2.LINE_AA)
        cv2.putText(panel, f"qfrc_constraint_norm: {float(constraint_frame['constrained']['qfrc_constraint_norm']):.1f}", (20, 132), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (255, 225, 160), 1, cv2.LINE_AA)
        cv2.putText(panel, f"efc_force_norm: {float(constraint_frame['constrained']['efc_force_norm']):.1f}", (20, 154), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (255, 225, 160), 1, cv2.LINE_AA)
        cv2.putText(panel, f"delta_root_inverse_norm: {float(constraint_frame['delta']['root_inverse_force_norm']):.3f}", (20, 176), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (255, 225, 160), 1, cv2.LINE_AA)

        draw_hand_state_box(panel, 20, 210, "LEFT HAND", left_state, constraint_frame["hands"]["left_hand"], (255, 200, 80))
        draw_hand_state_box(panel, 340, 210, "RIGHT HAND", right_state, constraint_frame["hands"]["right_hand"], (80, 180, 255))

        draw_series_panel(panel, 20, 360, 350, 150, qfrc_series, seq_idx, "constraint norm", (120, 255, 120))
        draw_series_panel(panel, 390, 360, 350, 150, left_gap_series, seq_idx, "left anchor gap (m)", (255, 200, 80))
        draw_series_panel(panel, 20, 530, 350, 150, right_gap_series, seq_idx, "right anchor gap (m)", (80, 180, 255))
        draw_series_panel(panel, 390, 530, 350, 150, both_series, seq_idx, "both hands welded (0/1)", (255, 140, 140))
        draw_series_panel(panel, 20, 700, 720, 130, delta_root_series, seq_idx, "delta root inverse norm", (255, 180, 120))

        top_deltas = constraint_frame["delta"]["top_joint_deltas"][:4]
        cv2.putText(panel, "Top Joint Deltas", (20, 858), cv2.FONT_HERSHEY_SIMPLEX, 0.58, (240, 240, 240), 1, cv2.LINE_AA)
        for idx, payload in enumerate(top_deltas):
            cv2.putText(
                panel,
                f"{payload['joint']}: {float(payload['delta_qfrc_inverse']):.1f}",
                (20, 884 + idx * 18),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.48,
                (205, 205, 205),
                1,
                cv2.LINE_AA,
            )

        composed = np.hstack([frame_bgr, panel])
        writer.write(composed)

        if show_window:
            preview = composed
            scale = min(preview_max_width / max(1, composed.shape[1]), preview_max_height / max(1, composed.shape[0]), 1.0)
            if scale < 1.0:
                preview = cv2.resize(composed, None, fx=scale, fy=scale, interpolation=cv2.INTER_AREA)
            cv2.imshow(window_name, preview)
            key = cv2.waitKey(1) & 0xFF
            if key in (27, ord("q"), ord("Q")):
                break

    cap.release()
    writer.release()
    if show_window:
        cv2.destroyWindow(window_name)
    return output_path


def main() -> None:
    parser = argparse.ArgumentParser(description="Visualize hand grip weld constraint report on top of the original video.")
    parser.add_argument("--report", type=Path, default=DEFAULT_REPORT)
    parser.add_argument("--output", type=Path, default=ROOT / "hand_grip_constraint_overlay.mp4")
    parser.add_argument("--no-window", action="store_true")
    parser.add_argument("--preview-max-width", type=int, default=1600)
    parser.add_argument("--preview-max-height", type=int, default=900)
    args = parser.parse_args()

    output = evaluate_video(
        report_path=args.report,
        output_path=args.output,
        show_window=not args.no_window,
        preview_max_width=max(320, int(args.preview_max_width)),
        preview_max_height=max(240, int(args.preview_max_height)),
    )
    print(f"[OK] Wrote {output.resolve()}")


if __name__ == "__main__":
    main()

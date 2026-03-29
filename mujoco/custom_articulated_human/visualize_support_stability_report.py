from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

import cv2
import numpy as np

from hold_contact_state import load_hold_detections

ROOT = Path(__file__).resolve().parent

LIMB_COLORS = {
    "left_hand": (60, 220, 60),
    "right_hand": (60, 160, 255),
    "left_foot": (255, 200, 60),
    "right_foot": (255, 120, 120),
}


def fetch_frame_at(video_path: Path, frame_index: int) -> np.ndarray | None:
    cap = cv2.VideoCapture(str(video_path))
    if not cap.isOpened():
        raise FileNotFoundError(f"Unable to open video: {video_path}")
    cap.set(cv2.CAP_PROP_POS_FRAMES, int(frame_index))
    ok, frame_bgr = cap.read()
    cap.release()
    if not ok:
        return None
    return frame_bgr


def resize_to_width(frame_bgr: np.ndarray, width: int) -> np.ndarray:
    h, w = frame_bgr.shape[:2]
    if w <= 0 or h <= 0 or w == width:
        return frame_bgr
    scale = float(width) / float(w)
    new_h = max(1, int(round(h * scale)))
    return cv2.resize(frame_bgr, (width, new_h), interpolation=cv2.INTER_AREA)


def draw_active_holds(
    frame_bgr: np.ndarray,
    detections: list,
    active_hold_ids: dict[str, int],
) -> None:
    active_ids = set(active_hold_ids.values())
    by_id = {hold.hold_id: hold for hold in detections}
    for limb_name, hold_id in active_hold_ids.items():
        hold = by_id.get(int(hold_id))
        if hold is None:
            continue
        color = LIMB_COLORS.get(limb_name, (255, 255, 255))
        pt1 = (int(round(hold.x1)), int(round(hold.y1)))
        pt2 = (int(round(hold.x2)), int(round(hold.y2)))
        cv2.rectangle(frame_bgr, pt1, pt2, color, 3, cv2.LINE_AA)
        cv2.putText(
            frame_bgr,
            f"{limb_name} -> #{hold.hold_id}",
            (pt1[0], max(20, pt1[1] - 8)),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.5,
            color,
            1,
            cv2.LINE_AA,
        )

    for hold in detections:
        if hold.hold_id in active_ids:
            continue
        pt1 = (int(round(hold.x1)), int(round(hold.y1)))
        pt2 = (int(round(hold.x2)), int(round(hold.y2)))
        cv2.rectangle(frame_bgr, pt1, pt2, (70, 70, 70), 1, cv2.LINE_AA)


def draw_text_block(
    frame_bgr: np.ndarray,
    report: dict[str, Any],
) -> None:
    stability = report.get("support_stability", {})
    lines = [
        f"frame={report['frame_index']}  fit={float(report['fit_mean_error_m']):.3f}m",
        f"mode={report.get('support_mode')}  type={stability.get('support_type')}",
        f"inside={stability.get('inside_support')}  margin={stability.get('stability_margin_m')}",
        f"contacts={','.join(report.get('active_contact_limbs', [])) or 'none'}",
    ]
    y = 28
    for line in lines:
        cv2.putText(
            frame_bgr,
            line,
            (18, y),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.6,
            (255, 255, 255),
            2,
            cv2.LINE_AA,
        )
        y += 26


def draw_support_plot(
    panel: np.ndarray,
    report: dict[str, Any],
    plane_name: str,
    first_axis_label: str,
    first_axis_key: str,
    second_axis_label: str,
    second_axis_key: str,
) -> None:
    stability = report.get("support_stability", {})
    support_points_2d = stability.get(first_axis_key, {})
    hull_vertices_2d = stability.get("hull_vertices_yz", []) if first_axis_key == "support_points_yz" else []
    com_proj_2d = np.asarray(stability.get(second_axis_key, [0.0, 0.0]), dtype=np.float64)

    all_points = [com_proj_2d]
    for point in support_points_2d.values():
        all_points.append(np.asarray(point, dtype=np.float64))
    for point in hull_vertices_2d:
        all_points.append(np.asarray(point, dtype=np.float64))
    points = np.asarray(all_points, dtype=np.float64)

    pad = 0.12
    min_vals = np.min(points, axis=0) - pad
    max_vals = np.max(points, axis=0) + pad
    if float(max_vals[0] - min_vals[0]) < 1e-6:
        max_vals[0] += 0.1
        min_vals[0] -= 0.1
    if float(max_vals[1] - min_vals[1]) < 1e-6:
        max_vals[1] += 0.1
        min_vals[1] -= 0.1

    height, width = panel.shape[:2]
    margin = 60

    def map_point(point_2d: np.ndarray) -> tuple[int, int]:
        first_val = float(point_2d[0])
        second_val = float(point_2d[1])
        x = margin + int(round((first_val - min_vals[0]) / (max_vals[0] - min_vals[0]) * (width - 2 * margin)))
        y = height - margin - int(round((second_val - min_vals[1]) / (max_vals[1] - min_vals[1]) * (height - 2 * margin)))
        return x, y

    panel[:] = (28, 28, 28)
    cv2.rectangle(panel, (margin, margin), (width - margin, height - margin), (90, 90, 90), 1, cv2.LINE_AA)
    cv2.putText(panel, plane_name, (margin, 30), cv2.FONT_HERSHEY_SIMPLEX, 0.8, (255, 255, 255), 2, cv2.LINE_AA)
    cv2.putText(panel, first_axis_label, (width - margin + 10, height - margin + 5), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (180, 180, 180), 1, cv2.LINE_AA)
    cv2.putText(panel, second_axis_label, (margin - 20, margin - 10), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (180, 180, 180), 1, cv2.LINE_AA)

    if hull_vertices_2d:
        hull_pts = np.asarray([map_point(np.asarray(p, dtype=np.float64)) for p in hull_vertices_2d], dtype=np.int32)
        if len(hull_pts) >= 3:
            cv2.polylines(panel, [hull_pts], True, (180, 180, 80), 2, cv2.LINE_AA)
            fill_color = (40, 90, 40) if stability.get("inside_support") else (70, 40, 40)
            cv2.fillPoly(panel, [hull_pts], fill_color)
            cv2.polylines(panel, [hull_pts], True, (220, 220, 120), 2, cv2.LINE_AA)
        elif len(hull_pts) == 2:
            cv2.line(panel, tuple(hull_pts[0]), tuple(hull_pts[1]), (220, 220, 120), 3, cv2.LINE_AA)
    elif len(support_points_2d) == 2:
        segment_pts = np.asarray([map_point(np.asarray(point, dtype=np.float64)) for point in support_points_2d.values()], dtype=np.int32)
        cv2.line(panel, tuple(segment_pts[0]), tuple(segment_pts[1]), (220, 220, 120), 3, cv2.LINE_AA)

    for limb_name, point in support_points_2d.items():
        point_px = map_point(np.asarray(point, dtype=np.float64))
        color = LIMB_COLORS.get(limb_name, (255, 255, 255))
        cv2.circle(panel, point_px, 8, color, -1, cv2.LINE_AA)
        cv2.putText(panel, limb_name, (point_px[0] + 10, point_px[1] - 8), cv2.FONT_HERSHEY_SIMPLEX, 0.45, color, 1, cv2.LINE_AA)

    com_px = map_point(com_proj_2d)
    com_color = (60, 220, 60) if stability.get("inside_support") else (0, 0, 255)
    cv2.circle(panel, com_px, 10, com_color, -1, cv2.LINE_AA)
    cv2.circle(panel, com_px, 15, com_color, 2, cv2.LINE_AA)
    cv2.putText(panel, "CoM", (com_px[0] + 12, com_px[1] + 4), cv2.FONT_HERSHEY_SIMPLEX, 0.6, com_color, 2, cv2.LINE_AA)

    summary_lines = [
        f"type: {stability.get('support_type')}",
        f"inside: {stability.get('inside_support')}",
        f"margin: {stability.get('stability_margin_m'):.3f} m" if stability.get("stability_margin_m") is not None else "margin: n/a",
        f"points: {stability.get('support_point_count')}",
    ]
    y0 = height - 110
    for idx, line in enumerate(summary_lines):
        cv2.putText(panel, line, (margin, y0 + idx * 22), cv2.FONT_HERSHEY_SIMPLEX, 0.58, (230, 230, 230), 1, cv2.LINE_AA)


def build_frame_visualization(
    report: dict[str, Any],
    video_path: Path,
    detections: list,
) -> np.ndarray:
    frame_bgr = fetch_frame_at(video_path, int(report["frame_index"]))
    if frame_bgr is None:
        raise RuntimeError(f"Failed to fetch frame {report['frame_index']} from {video_path}")
    draw_active_holds(frame_bgr, detections, report.get("active_hold_ids", {}))
    draw_text_block(frame_bgr, report)

    left = resize_to_width(frame_bgr, 760)
    right_top = np.zeros((left.shape[0] // 2, 760, 3), dtype=np.uint8)
    right_bottom = np.zeros((left.shape[0] - right_top.shape[0], 760, 3), dtype=np.uint8)
    draw_support_plot(
        right_top,
        report,
        plane_name="Support Y-Z Plane",
        first_axis_label="Y",
        first_axis_key="support_points_yz",
        second_axis_label="Z",
        second_axis_key="com_proj_yz",
    )
    draw_support_plot(
        right_bottom,
        report,
        plane_name="Support X-Z Plane",
        first_axis_label="X",
        first_axis_key="support_points_xz",
        second_axis_label="Z",
        second_axis_key="com_proj_xz",
    )
    right = np.vstack([right_top, right_bottom])
    if right.shape[0] != left.shape[0]:
        right = cv2.resize(right, (right.shape[1], left.shape[0]), interpolation=cv2.INTER_AREA)
    return np.hstack([left, right])


def main() -> None:
    parser = argparse.ArgumentParser(description="Visualize support stability from gate2 static inverse dynamics report.")
    parser.add_argument("--report-json", type=Path, default=ROOT / "gate2_static_inverse_dynamics_report.json")
    parser.add_argument("--detections-json", type=Path, default=ROOT.parent / "detections.json")
    parser.add_argument("--output-dir", type=Path, default=ROOT / "support_stability_frames")
    args = parser.parse_args()

    report = json.loads(args.report_json.read_text(encoding="utf-8"))
    detections = load_hold_detections(args.detections_json)["holds"]
    video_path = Path(report["video"])

    args.output_dir.mkdir(parents=True, exist_ok=True)
    written = []
    for sample_report in report.get("sample_reports", []):
        if not sample_report.get("detected"):
            continue
        frame_vis = build_frame_visualization(sample_report, video_path, detections)
        out_path = args.output_dir / f"support_stability_frame_{int(sample_report['frame_index']):04d}.png"
        cv2.imwrite(str(out_path), frame_vis)
        written.append(str(out_path))

    print(json.dumps({"written_frames": written}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()

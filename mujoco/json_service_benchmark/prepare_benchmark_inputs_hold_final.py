from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

import cv2

from prepare_benchmark_inputs import (
    DEFAULT_TASK_MODEL,
    build_pose_sequence_payload,
    build_user_body_payload_from_profile,
)


ROOT = Path(__file__).resolve().parent
PROJECT_ROOT = ROOT.parent
DEFAULT_VIDEO = PROJECT_ROOT / "video" / "audit.mp4"
DEFAULT_HOLD_FINAL = PROJECT_ROOT / "hold_final.json"
DEFAULT_OUTPUT_DIR = ROOT / "benchmark_inputs" / "audit_final"


def _load_wrapped_json(path: Path) -> dict[str, Any]:
    text = path.read_text(encoding="utf-8")
    start = text.find("{")
    if start < 0:
        raise ValueError(f"JSON body not found in {path}")
    return json.loads(text[start:])


def _normalized_to_px(value: float, size: int) -> float:
    numeric = float(value)
    if 0.0 <= numeric <= 1.5:
        return numeric * float(size)
    return numeric


def _coerce_id_list(value: Any) -> set[int]:
    if value is None:
        return set()
    if isinstance(value, (int, float, str)):
        try:
            return {int(value)}
        except (TypeError, ValueError):
            return set()
    ids: set[int] = set()
    for item in value:
        try:
            ids.add(int(item))
        except (TypeError, ValueError):
            continue
    return ids


def _extract_route_sets(payload: dict[str, Any]) -> tuple[set[int], set[int]]:
    start_keys = (
        "startHoldNos",
        "start_hold_nos",
        "start_hold_ids",
        "startHolds",
        "start_holds",
    )
    end_keys = (
        "endHoldNos",
        "finishHoldNos",
        "end_hold_nos",
        "finish_hold_nos",
        "end_hold_ids",
        "finish_hold_ids",
        "endHolds",
        "finishHolds",
    )
    start_ids: set[int] = set()
    end_ids: set[int] = set()
    for key in start_keys:
        start_ids |= _coerce_id_list(payload.get(key))
    for key in end_keys:
        end_ids |= _coerce_id_list(payload.get(key))
    return start_ids, end_ids


def build_holds_payload_from_hold_final(hold_final_json: Path, video_path: Path) -> dict[str, Any]:
    payload = _load_wrapped_json(hold_final_json)
    holds_payload = payload.get("holds")
    if not isinstance(holds_payload, list):
        raise ValueError(f"'holds' array not found in {hold_final_json}")

    cap = cv2.VideoCapture(str(video_path))
    if not cap.isOpened():
        raise FileNotFoundError(f"Unable to open video: {video_path}")
    frame_width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH) or 0)
    frame_height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT) or 0)
    cap.release()

    start_ids, end_ids = _extract_route_sets(payload)
    hold_nos = [int(item.get("holdNo", index)) for index, item in enumerate(holds_payload, start=1)]
    if not start_ids and hold_nos:
        start_ids = {min(hold_nos)}
    if not end_ids and hold_nos:
        end_ids = {max(hold_nos)}
    holds: list[dict[str, Any]] = []
    for index, item in enumerate(holds_payload, start=1):
        hold_no = int(item.get("holdNo", index))
        bbox = item.get("boundingBox") or {}
        polygon_items = item.get("polygon") or []
        x1 = _normalized_to_px(float(bbox["x1"]), frame_width)
        x2 = _normalized_to_px(float(bbox["x2"]), frame_width)
        y1 = _normalized_to_px(float(bbox["y1"]), frame_height)
        y2 = _normalized_to_px(float(bbox["y2"]), frame_height)
        polygon_px = [
            {
                "x": _normalized_to_px(float(point["x"]), frame_width),
                "y": _normalized_to_px(float(point["y"]), frame_height),
            }
            for point in polygon_items
        ]
        width = max(1.0, x2 - x1)
        height = max(1.0, y2 - y1)
        explicit_start = bool(item.get("isStart") or item.get("start") or item.get("is_start"))
        explicit_end = bool(item.get("isEnd") or item.get("end") or item.get("is_end") or item.get("finish"))
        is_start = explicit_start or hold_no in start_ids
        is_end = explicit_end or hold_no in end_ids
        route_role = "start" if is_start else "end" if is_end else None
        holds.append(
            {
                "hold_id": hold_no,
                "original_hold_no": hold_no,
                "bbox_px": {
                    "x1": float(min(x1, x2)),
                    "y1": float(min(y1, y2)),
                    "x2": float(max(x1, x2)),
                    "y2": float(max(y1, y2)),
                },
                "center_px": {
                    "x": 0.5 * float(x1 + x2),
                    "y": 0.5 * float(y1 + y2),
                },
                "radius_px": 0.45 * min(width, height),
                "polygon_px": polygon_px,
                "confidence": float(item.get("confidence", 1.0)),
                "is_start": bool(is_start),
                "is_end": bool(is_end),
                "route_role": route_role,
            }
        )

    return {
        "schema_version": "1.2.0",
        "source": {
            "type": "challenge_hold_subset_json",
            "path": str(hold_final_json.resolve()),
            "request_wrapper": True,
        },
        "video_metadata": {
            "video_path": str(video_path.resolve()),
            "frame_width": frame_width,
            "frame_height": frame_height,
        },
        "route_metadata": {
            "subset_hold_count": len(holds),
            "start_hold_ids": sorted(int(hold_id) for hold_id in start_ids),
            "end_hold_ids": sorted(int(hold_id) for hold_id in end_ids),
            "has_explicit_start_end": bool(start_ids or end_ids),
        },
        "holds": holds,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Prepare benchmark inputs from hold_final subset JSON and profile-only body data.")
    parser.add_argument("--input-video", type=Path, default=DEFAULT_VIDEO)
    parser.add_argument("--hold-final-json", type=Path, default=DEFAULT_HOLD_FINAL)
    parser.add_argument("--task-model", type=Path, default=DEFAULT_TASK_MODEL)
    parser.add_argument("--height-cm", type=float, default=167.0)
    parser.add_argument("--weight-kg", type=float, default=75.0)
    parser.add_argument("--wingspan-cm", type=float, default=168.0)
    parser.add_argument("--frame-step", type=int, default=1)
    parser.add_argument("--max-frames", type=int, default=None)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    args = parser.parse_args()

    args.output_dir.mkdir(parents=True, exist_ok=True)
    height_m = float(args.height_cm) / 100.0
    wingspan_m = float(args.wingspan_cm) / 100.0

    holds_payload = build_holds_payload_from_hold_final(args.hold_final_json, args.input_video)
    pose_payload = build_pose_sequence_payload(
        video_path=args.input_video,
        task_model=args.task_model,
        frame_step=max(1, int(args.frame_step)),
        max_frames=args.max_frames,
    )
    user_body_payload = build_user_body_payload_from_profile(
        height_m=height_m,
        weight_kg=float(args.weight_kg),
        wingspan_m=wingspan_m,
    )

    holds_path = args.output_dir / "holds_polygon.json"
    pose_path = args.output_dir / "pose3d_sequence.json"
    user_path = args.output_dir / "user_body.json"
    manifest_path = args.output_dir / "benchmark_input_manifest.json"

    holds_path.write_text(json.dumps(holds_payload, ensure_ascii=False, indent=2), encoding="utf-8")
    pose_path.write_text(json.dumps(pose_payload, ensure_ascii=False, indent=2), encoding="utf-8")
    user_path.write_text(json.dumps(user_body_payload, ensure_ascii=False, indent=2), encoding="utf-8")

    manifest = {
        "schema_version": "1.0.0",
        "input_video": str(args.input_video.resolve()),
        "hold_final_json": str(args.hold_final_json.resolve()),
        "task_model": str(args.task_model.resolve()),
        "height_cm": float(args.height_cm),
        "weight_kg": float(args.weight_kg),
        "wingspan_cm": float(args.wingspan_cm),
        "frame_step": int(args.frame_step),
        "max_frames": args.max_frames,
        "outputs": {
            "holds_json": str(holds_path.resolve()),
            "pose_json": str(pose_path.resolve()),
            "user_body_json": str(user_path.resolve()),
        },
        "route_metadata": holds_payload.get("route_metadata", {}),
    }
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"[OK] Wrote {holds_path.resolve()}")
    print(f"[OK] Wrote {pose_path.resolve()}")
    print(f"[OK] Wrote {user_path.resolve()}")
    print(f"[OK] Wrote {manifest_path.resolve()}")


if __name__ == "__main__":
    main()

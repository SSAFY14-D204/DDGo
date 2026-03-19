from __future__ import annotations

import argparse
import json
import time
from pathlib import Path
from typing import Any

import run_json_service_benchmark as bench
from crux_detection import build_hold_segments, enrich_frames_for_crux, score_fast_crux_candidates, summarize_hold_candidates
from hold_contact_state import HoldContactTracker, compute_contact_points_px, load_hold_detections
from polygon_hold_contact_state import PolygonHoldContactTracker, load_polygon_service_holds
from pose_sequence_correction import correct_pose_sequence_payload


DEFAULT_HOLDS_JSON = Path(__file__).resolve().parent / "benchmark_inputs" / "polygon" / "holds_polygon.json"
DEFAULT_POSE_JSON = Path(__file__).resolve().parent / "benchmark_inputs" / "polygon" / "pose3d_sequence.json"
DEFAULT_USER_BODY_JSON = Path(__file__).resolve().parent / "benchmark_inputs" / "polygon" / "user_body.json"
DEFAULT_OUTPUT = Path(__file__).resolve().parent / "crux_detection_fast_report.json"


def _tracker_bundle(hold_mode: str) -> tuple[Any, Any]:
    if hold_mode == "polygon":
        return load_polygon_service_holds, PolygonHoldContactTracker
    return load_hold_detections, HoldContactTracker


def _hold_state_summary(frames: list[dict[str, Any]]) -> dict[str, dict[str, int]]:
    summary: dict[str, dict[str, int]] = {}
    for frame in frames:
        for limb_name, payload in (frame.get("limb_states") or {}).items():
            bucket = summary.setdefault(limb_name, {})
            state = str(payload.get("state", "FREE"))
            bucket[state] = bucket.get(state, 0) + 1
    return summary


def main() -> None:
    parser = argparse.ArgumentParser(description="Fast crux detection using hold dwell time only.")
    parser.add_argument("--holds-json", type=Path, default=DEFAULT_HOLDS_JSON)
    parser.add_argument("--pose-json", type=Path, default=DEFAULT_POSE_JSON)
    parser.add_argument("--user-body-json", type=Path, default=DEFAULT_USER_BODY_JSON)
    parser.add_argument("--hold-mode", choices=("polygon", "bbox"), default="polygon")
    parser.add_argument("--frame-step", type=int, default=1)
    parser.add_argument("--top-k", type=int, default=3)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()

    started = time.perf_counter()

    load_started = time.perf_counter()
    holds_loader, tracker_cls = _tracker_bundle(args.hold_mode)
    holds_payload = holds_loader(args.holds_json)
    pose_payload = json.loads(args.pose_json.read_text(encoding="utf-8"))
    user_body_payload = json.loads(args.user_body_json.read_text(encoding="utf-8"))
    load_inputs_s = float(time.perf_counter() - load_started)

    correction_started = time.perf_counter()
    corrected_payload = correct_pose_sequence_payload(
        pose_payload=pose_payload,
        user_body_payload=user_body_payload,
        preserve_raw_copy=False,
    )
    correction_s = float(time.perf_counter() - correction_started)

    tracking_started = time.perf_counter()
    video_metadata = corrected_payload.get("video_metadata", {})
    frame_width = int(video_metadata.get("frame_width", 0))
    frame_height = int(video_metadata.get("frame_height", 0))
    fps = float(video_metadata.get("fps", 30.0))
    tracker = tracker_cls(holds_payload["holds"])
    crux_frames: list[dict[str, Any]] = []
    for raw_frame in corrected_payload.get("frames", []):
        frame_index = int(raw_frame["frame_index"])
        if args.frame_step > 1 and frame_index % args.frame_step != 0:
            continue
        pose_landmarks_2d = bench.landmark_payload_to_objects(raw_frame.get("pose_landmarks"))
        contact_points = compute_contact_points_px(pose_landmarks_2d, frame_width, frame_height)
        limb_states = tracker.update_frame(contact_points, int(raw_frame["timestamp_ms"]))
        active_hold_ids = {
            limb_name: int(payload["active_hold_id"])
            for limb_name, payload in limb_states.items()
            if str(payload.get("state")) in ("GRIP", "STEP") and payload.get("active_hold_id") is not None
        }
        crux_frames.append(
            {
                "frame_index": frame_index,
                "timestamp_ms": int(raw_frame["timestamp_ms"]),
                "limb_states": limb_states,
                "active_hold_ids": active_hold_ids,
            }
        )
    hold_tracking_s = float(time.perf_counter() - tracking_started)

    crux_started = time.perf_counter()
    enriched_frames = enrich_frames_for_crux(crux_frames)
    segments_by_hold = build_hold_segments(enriched_frames, fps=fps)
    candidates = summarize_hold_candidates(segments_by_hold)
    crux_result = score_fast_crux_candidates(candidates, top_k=max(1, int(args.top_k)))
    crux_scoring_s = float(time.perf_counter() - crux_started)

    report = {
        "schema_version": "1.0.0",
        "mode": "fast_crux_detection",
        "inputs": {
            "holds_json": str(args.holds_json.resolve()),
            "pose3d_sequence_json": str(args.pose_json.resolve()),
            "user_body_json": str(args.user_body_json.resolve()),
        },
        "tracker_mode": args.hold_mode,
        "video_metadata": {
            "frame_width": frame_width,
            "frame_height": frame_height,
            "fps": fps,
            "total_frames": int(video_metadata.get("total_frames", len(corrected_payload.get("frames", [])))),
            "processed_frames": len(crux_frames),
            "frame_step": int(args.frame_step),
        },
        "timings_s": {
            "load_inputs_s": load_inputs_s,
            "correction_s": correction_s,
            "hold_tracking_s": hold_tracking_s,
            "crux_scoring_s": crux_scoring_s,
            "serialize_s": None,
            "total_s": None,
        },
        "correction_summary": corrected_payload.get("correction_summary", {}),
        "hold_state_summary": _hold_state_summary(crux_frames),
        "crux_result": crux_result,
    }

    serialize_started = time.perf_counter()
    _ = json.dumps(report, ensure_ascii=False, indent=2)
    serialize_s = float(time.perf_counter() - serialize_started)
    report["timings_s"]["serialize_s"] = serialize_s
    report["timings_s"]["total_s"] = float(time.perf_counter() - started)

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(
        json.dumps(
            {
                "output": str(args.output.resolve()),
                "timings_s": report["timings_s"],
                "top_candidates": report["crux_result"]["top_candidates"],
            },
            ensure_ascii=False,
            indent=2,
        )
    )


if __name__ == "__main__":
    main()

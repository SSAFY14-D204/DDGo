from __future__ import annotations

import argparse
import json
import time
from pathlib import Path

import run_json_service_benchmark as bench
from crux_detection import (
    build_hold_segments,
    enrich_frames_for_crux,
    score_physics_crux_candidates,
    summarize_hold_candidates,
)
from polygon_hold_contact_state import PolygonHoldContactTracker, compute_contact_points_px, load_polygon_service_holds
from pose_sequence_correction import correct_pose_sequence_payload


DEFAULT_HOLDS_JSON = Path(__file__).resolve().parent / "benchmark_inputs" / "polygon" / "holds_polygon.json"
DEFAULT_POSE_JSON = Path(__file__).resolve().parent / "benchmark_inputs" / "polygon" / "pose3d_sequence.json"
DEFAULT_USER_BODY_JSON = Path(__file__).resolve().parent / "benchmark_inputs" / "polygon" / "user_body.json"
DEFAULT_OUTPUT = Path(__file__).resolve().parent / "crux_detection_physics_report.json"


def _summary_from_physics_report(report: dict[str, object]) -> dict[str, object]:
    frames = list(report.get("frames", []))
    high_conf = sum(1 for frame in frames if str(frame.get("analysis_confidence")) == "high")
    ok_frames = sum(1 for frame in frames if str(frame.get("contact_force_status")) == "ok")
    return {
        "fit_mean_error_m": float(report.get("dynamic_sequence_gate", {}).get("fit_mean_error_m", 0.0)),
        "recovery_ratio": float(report.get("dynamic_sequence_gate", {}).get("recovery_ratio", 0.0)),
        "processed_frames": int(report.get("video_metadata", {}).get("processed_frames", len(frames))),
        "high_confidence_frame_count": high_conf,
        "ok_contact_force_frame_count": ok_frames,
        "point_support_frame_count": int(
            report.get("support_stability_summary", {}).get("support_type_counts", {}).get("point_support", 0)
        ),
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Physics-based explanatory crux detection.")
    parser.add_argument("--holds-json", type=Path, default=DEFAULT_HOLDS_JSON)
    parser.add_argument("--pose-json", type=Path, default=DEFAULT_POSE_JSON)
    parser.add_argument("--user-body-json", type=Path, default=DEFAULT_USER_BODY_JSON)
    parser.add_argument("--xml", type=Path, default=bench.DEFAULT_XML)
    parser.add_argument("--cache-dir", type=Path, default=bench.DEFAULT_CACHE_DIR / "crux_physics")
    parser.add_argument("--frame-step", type=int, default=1)
    parser.add_argument("--sample-count", type=int, default=24)
    parser.add_argument("--ik-iterations", type=int, default=25)
    parser.add_argument("--damping", type=float, default=1e-2)
    parser.add_argument("--smoothing-window", type=int, default=5)
    parser.add_argument("--top-k-joints", type=int, default=8)
    parser.add_argument("--fit-frame-step", type=int, default=2)
    parser.add_argument("--top-k", type=int, default=3)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()

    started = time.perf_counter()

    load_started = time.perf_counter()
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

    bench.load_service_holds = load_polygon_service_holds
    bench.HoldContactTracker = PolygonHoldContactTracker
    bench.compute_contact_points_px = compute_contact_points_px

    benchmark_started = time.perf_counter()
    physics_report = bench.evaluate_from_json_inputs(
        xml_path=args.xml,
        holds_json=args.holds_json,
        pose_json=args.pose_json,
        user_body_json=args.user_body_json,
        frame_step=max(1, int(args.frame_step)),
        sample_count=max(4, int(args.sample_count)),
        ik_iterations=max(1, int(args.ik_iterations)),
        damping=float(args.damping),
        smoothing_window=max(1, int(args.smoothing_window)),
        top_k_joints=max(1, int(args.top_k_joints)),
        cache_dir=args.cache_dir,
        fit_frame_step=max(1, int(args.fit_frame_step)),
        retry_high_confidence_only=True,
        pose_payload_override=corrected_payload,
        user_body_payload_override=user_body_payload,
    )
    benchmark_s = float(time.perf_counter() - benchmark_started)

    crux_started = time.perf_counter()
    enriched_frames = enrich_frames_for_crux(list(physics_report.get("frames", [])))
    segments_by_hold = build_hold_segments(enriched_frames, fps=float(physics_report["video_metadata"]["fps"]))
    candidates = summarize_hold_candidates(segments_by_hold)
    crux_result = score_physics_crux_candidates(candidates, top_k=max(1, int(args.top_k)))
    crux_scoring_s = float(time.perf_counter() - crux_started)

    report = {
        "schema_version": "1.0.0",
        "mode": "physics_crux_detection",
        "inputs": {
            "holds_json": str(args.holds_json.resolve()),
            "pose3d_sequence_json": str(args.pose_json.resolve()),
            "user_body_json": str(args.user_body_json.resolve()),
            "base_xml": str(args.xml.resolve()),
        },
        "timings_s": {
            "load_inputs_s": load_inputs_s,
            "correction_s": correction_s,
            "physics_pipeline_s": benchmark_s,
            "crux_scoring_s": crux_scoring_s,
            "serialize_s": None,
            "total_s": None,
        },
        "correction_summary": corrected_payload.get("correction_summary", {}),
        "physics_summary": _summary_from_physics_report(physics_report),
        "physics_pipeline_benchmark_timings_s": physics_report.get("benchmark_timings_s", {}),
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
                "physics_summary": report["physics_summary"],
                "top_candidates": report["crux_result"]["top_candidates"],
            },
            ensure_ascii=False,
            indent=2,
        )
    )


if __name__ == "__main__":
    main()

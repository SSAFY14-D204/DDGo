from __future__ import annotations

import argparse
import json
from pathlib import Path
import time

import run_json_service_benchmark as bench
from polygon_hold_contact_state import PolygonHoldContactTracker, compute_contact_points_px, load_polygon_service_holds
from pose_sequence_correction import correct_pose_sequence_payload


DEFAULT_HOLDS_JSON = Path(__file__).resolve().parent / "benchmark_inputs" / "polygon" / "holds_polygon.json"
DEFAULT_POSE_JSON = Path(__file__).resolve().parent / "benchmark_inputs" / "polygon" / "pose3d_sequence.json"
DEFAULT_USER_BODY_JSON = Path(__file__).resolve().parent / "benchmark_inputs" / "polygon" / "user_body.json"
DEFAULT_OUTPUT = Path(__file__).resolve().parent / "json_service_benchmark_report_corrected.json"


def main() -> None:
    parser = argparse.ArgumentParser(description="Run JSON-only MuJoCo benchmark using corrected pose sequence input.")
    parser.add_argument("--xml", type=Path, default=bench.DEFAULT_XML)
    parser.add_argument("--holds-json", type=Path, default=DEFAULT_HOLDS_JSON)
    parser.add_argument("--pose-json", type=Path, default=DEFAULT_POSE_JSON)
    parser.add_argument("--user-body-json", type=Path, default=DEFAULT_USER_BODY_JSON)
    parser.add_argument("--corrected-pose-json", type=Path)
    parser.add_argument("--hold-mode", choices=("bbox", "polygon"), default="polygon")
    parser.add_argument("--frame-step", type=int, default=1)
    parser.add_argument("--sample-count", type=int, default=24)
    parser.add_argument("--ik-iterations", type=int, default=25)
    parser.add_argument("--damping", type=float, default=1e-2)
    parser.add_argument("--smoothing-window", type=int, default=5)
    parser.add_argument("--top-k-joints", type=int, default=8)
    parser.add_argument("--cache-dir", type=Path, default=bench.DEFAULT_CACHE_DIR / "corrected")
    parser.add_argument("--fit-frame-step", type=int, default=2)
    parser.add_argument("--retry-high-confidence-only", action="store_true", default=True)
    parser.add_argument("--retry-all-frames", dest="retry_high_confidence_only", action="store_false")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()

    pipeline_started = time.perf_counter()
    pose_payload = json.loads(args.pose_json.read_text(encoding="utf-8"))
    user_body_payload = json.loads(args.user_body_json.read_text(encoding="utf-8"))
    correction_started = time.perf_counter()
    corrected_payload = correct_pose_sequence_payload(
        pose_payload=pose_payload,
        user_body_payload=user_body_payload,
        preserve_raw_copy=True,
    )
    correction_s = float(time.perf_counter() - correction_started)
    corrected_pose_json_path = None
    if args.corrected_pose_json is not None:
        args.corrected_pose_json.parent.mkdir(parents=True, exist_ok=True)
        args.corrected_pose_json.write_text(json.dumps(corrected_payload, ensure_ascii=False, indent=2), encoding="utf-8")
        corrected_pose_json_path = str(args.corrected_pose_json.resolve())

    if args.hold_mode == "polygon":
        bench.load_service_holds = load_polygon_service_holds
        bench.HoldContactTracker = PolygonHoldContactTracker
        bench.compute_contact_points_px = compute_contact_points_px

    report = bench.evaluate_from_json_inputs(
        xml_path=args.xml,
        holds_json=args.holds_json,
        pose_json=args.pose_json,
        user_body_json=args.user_body_json,
        frame_step=args.frame_step,
        sample_count=args.sample_count,
        ik_iterations=args.ik_iterations,
        damping=args.damping,
        smoothing_window=args.smoothing_window,
        top_k_joints=args.top_k_joints,
        cache_dir=args.cache_dir,
        fit_frame_step=args.fit_frame_step,
        retry_high_confidence_only=bool(args.retry_high_confidence_only),
        pose_payload_override=corrected_payload,
        user_body_payload_override=user_body_payload,
    )
    report["mode"] = "json_only_service_benchmark_corrected"
    report["hold_tracker_mode"] = args.hold_mode
    report["correction_summary"] = corrected_payload.get("correction_summary", {})
    report["service_pipeline_timings_s"] = {
        "correction_s": correction_s,
        "end_to_end_total_s": float(time.perf_counter() - pipeline_started),
    }
    report["inputs"]["raw_pose3d_sequence_json"] = str(args.pose_json.resolve())
    report["inputs"]["corrected_pose3d_sequence_json"] = corrected_pose_json_path

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(
        json.dumps(
            {
                "output": str(args.output.resolve()),
                "corrected_pose_json": corrected_pose_json_path,
                "benchmark_timings_s": report["benchmark_timings_s"],
                "service_pipeline_timings_s": report["service_pipeline_timings_s"],
                "correction_summary": report["correction_summary"],
                "dynamic_sequence_gate": report["dynamic_sequence_gate"],
            },
            ensure_ascii=False,
            indent=2,
        )
    )


if __name__ == "__main__":
    main()

from __future__ import annotations

import argparse
import json
from pathlib import Path

import run_json_service_benchmark as bench
from polygon_hold_contact_state import PolygonHoldContactTracker, compute_contact_points_px, load_polygon_service_holds


DEFAULT_HOLDS_JSON = Path(__file__).resolve().parent / "benchmark_inputs" / "polygon" / "holds_polygon.json"
DEFAULT_POSE_JSON = Path(__file__).resolve().parent / "benchmark_inputs" / "polygon" / "pose3d_sequence.json"
DEFAULT_USER_BODY_JSON = Path(__file__).resolve().parent / "benchmark_inputs" / "polygon" / "user_body.json"
DEFAULT_OUTPUT = Path(__file__).resolve().parent / "json_service_benchmark_report_polygon.json"


def main() -> None:
    parser = argparse.ArgumentParser(description="Run JSON-only MuJoCo physics benchmark with polygon-aware hold tracking.")
    parser.add_argument("--xml", type=Path, default=bench.DEFAULT_XML)
    parser.add_argument("--holds-json", type=Path, default=DEFAULT_HOLDS_JSON)
    parser.add_argument("--pose-json", type=Path, default=DEFAULT_POSE_JSON)
    parser.add_argument("--user-body-json", type=Path, default=DEFAULT_USER_BODY_JSON)
    parser.add_argument("--frame-step", type=int, default=1)
    parser.add_argument("--sample-count", type=int, default=24)
    parser.add_argument("--ik-iterations", type=int, default=25)
    parser.add_argument("--damping", type=float, default=1e-2)
    parser.add_argument("--smoothing-window", type=int, default=5)
    parser.add_argument("--top-k-joints", type=int, default=8)
    parser.add_argument("--cache-dir", type=Path, default=bench.DEFAULT_CACHE_DIR / "polygon")
    parser.add_argument("--fit-frame-step", type=int, default=2)
    parser.add_argument("--retry-high-confidence-only", action="store_true", default=True)
    parser.add_argument("--retry-all-frames", dest="retry_high_confidence_only", action="store_false")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()

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
        fit_frame_step=max(1, int(args.fit_frame_step)),
        retry_high_confidence_only=bool(args.retry_high_confidence_only),
    )
    report["mode"] = "json_only_service_benchmark_polygon"
    report["hold_tracker_mode"] = "polygon_aware"
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    timing_summary = report.get("benchmark_timings_s")
    if timing_summary is None:
        timing_summary = {
            key: report[key]
            for key in (
                "load_inputs_s",
                "prepare_model_s",
                "fit_sequence_s",
                "inverse_dynamics_s",
                "serialize_s",
                "total_s",
            )
            if key in report
        }
    print(json.dumps(timing_summary, ensure_ascii=False, indent=2))
    print(f"[OK] Wrote {args.output.resolve()}")


if __name__ == "__main__":
    main()

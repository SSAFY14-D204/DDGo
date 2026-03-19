from __future__ import annotations

import argparse
import json
import sys
from collections import Counter
from pathlib import Path
from typing import Any

import numpy as np

ROOT = Path(__file__).resolve().parent
PROJECT_ROOT = ROOT.parent
DYNAMIC_ROOT = PROJECT_ROOT / "dynamic_sequence_pipeline"
sys.path.insert(0, str(DYNAMIC_ROOT))

from contact_force_distribution import estimate_contact_forces  # noqa: E402


def frame_is_both_feet_step(frame: dict[str, Any]) -> bool:
    limb_states = frame.get("limb_states") or {}
    return (
        limb_states.get("left_foot", {}).get("state") == "STEP"
        and limb_states.get("right_foot", {}).get("state") == "STEP"
    )


def build_foot_positions(frame: dict[str, Any]) -> dict[str, np.ndarray]:
    support_stability = frame.get("support_stability") or {}
    support_points_xyz = support_stability.get("support_points_xyz") or {}
    out: dict[str, np.ndarray] = {}
    for limb_name in ("left_foot", "right_foot"):
        pos = support_points_xyz.get(limb_name)
        if pos is not None:
            out[limb_name] = np.asarray(pos, dtype=np.float64)
    return out


def summarize_results(frame_reports: list[dict[str, Any]]) -> dict[str, Any]:
    status_counts: Counter[str] = Counter()
    residuals: list[float] = []
    plausible_count = 0
    no_hand_grip_count = 0
    for report in frame_reports:
        status_counts[str(report["force_distribution"]["status"])] += 1
        rel = report["force_distribution"].get("relative_residual")
        if rel is not None and np.isfinite(float(rel)):
            residuals.append(float(rel))
        if bool(report["is_plausible_foot_support"]):
            plausible_count += 1
        if bool(report["no_hand_grip"]):
            no_hand_grip_count += 1
    residual_summary = None
    if residuals:
        arr = np.asarray(residuals, dtype=np.float64)
        residual_summary = {
            "mean": float(np.mean(arr)),
            "median": float(np.median(arr)),
            "max": float(np.max(arr)),
        }
    return {
        "candidate_frame_count": len(frame_reports),
        "no_hand_grip_frame_count": no_hand_grip_count,
        "plausible_foot_support_count": plausible_count,
        "status_counts": dict(status_counts),
        "relative_residual_summary": residual_summary,
    }


def evaluate_report(dynamic_report: dict[str, Any]) -> dict[str, Any]:
    frame_reports: list[dict[str, Any]] = []
    for frame in dynamic_report.get("frames", []):
        if not frame_is_both_feet_step(frame):
            continue

        foot_positions = build_foot_positions(frame)
        if len(foot_positions) < 2:
            continue

        root_position = frame.get("root_position_m")
        reference_mode = "root_position"
        if root_position is None:
            root_position = frame.get("com_position_m")
            reference_mode = "com_fallback"
        if root_position is None:
            continue

        force_distribution = estimate_contact_forces(
            root_position_xyz=np.asarray(root_position, dtype=np.float64),
            required_wrench=np.asarray(frame["root_inverse_force"], dtype=np.float64),
            contact_positions_xyz=foot_positions,
            contact_modes={"left_foot": "STEP", "right_foot": "STEP"},
        )

        limb_states = frame.get("limb_states") or {}
        no_hand_grip = (
            limb_states.get("left_hand", {}).get("state") != "GRIP"
            and limb_states.get("right_hand", {}).get("state") != "GRIP"
        )
        rel = force_distribution.get("relative_residual")
        is_plausible = bool(
            force_distribution.get("status") == "ok"
            and rel is not None
            and float(rel) <= 0.25
        )
        frame_reports.append(
            {
                "frame_index": frame["frame_index"],
                "timestamp_ms": frame["timestamp_ms"],
                "reference_mode": reference_mode,
                "analysis_confidence": frame.get("analysis_confidence"),
                "phase": frame.get("phase"),
                "no_hand_grip": no_hand_grip,
                "is_plausible_foot_support": is_plausible,
                "root_inverse_force": frame["root_inverse_force"],
                "force_distribution": force_distribution,
            }
        )

    return {
        "source_dynamic_report": dynamic_report.get("video"),
        "frame_count": dynamic_report.get("frame_count"),
        "fps": dynamic_report.get("fps"),
        "summary": summarize_results(frame_reports),
        "frames": frame_reports,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Evaluate two-foot STEP frames with foot-contact-only force distribution.")
    parser.add_argument(
        "--dynamic-report",
        type=Path,
        default=DYNAMIC_ROOT / "dynamic_sequence_report.json",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=ROOT / "foot_contact_only_report.json",
    )
    args = parser.parse_args()

    dynamic_report = json.loads(args.dynamic_report.read_text(encoding="utf-8"))
    report = evaluate_report(dynamic_report)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(report["summary"], ensure_ascii=False, indent=2))
    print(f"[OK] Wrote {args.output.resolve()}")


if __name__ == "__main__":
    main()

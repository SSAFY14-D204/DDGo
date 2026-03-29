from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

import matplotlib.pyplot as plt
import numpy as np


ROOT = Path(__file__).resolve().parent
DEFAULT_BASELINE = ROOT / "json_service_benchmark_report_polygon_broadphase.json"
DEFAULT_CORRECTED = ROOT / "json_service_benchmark_report_corrected.json"
DEFAULT_OUTPUT = ROOT / "corrected_benchmark_comparison.png"


def load_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def frame_series(report: dict[str, Any], key_path: tuple[str, ...], default: float = np.nan) -> np.ndarray:
    values: list[float] = []
    for frame in report.get("frames", []):
        current: Any = frame
        for key in key_path:
            if not isinstance(current, dict) or key not in current:
                current = default
                break
            current = current[key]
        if current is None:
            current = default
        values.append(float(current))
    return np.array(values, dtype=float)


def boolean_series(report: dict[str, Any], key_path: tuple[str, ...]) -> np.ndarray:
    values: list[float] = []
    for frame in report.get("frames", []):
        current: Any = frame
        for key in key_path:
            if not isinstance(current, dict) or key not in current:
                current = False
                break
            current = current[key]
        values.append(1.0 if bool(current) else 0.0)
    return np.array(values, dtype=float)


def confidence_series(report: dict[str, Any]) -> np.ndarray:
    values: list[float] = []
    for frame in report.get("frames", []):
        confidence = str(frame.get("analysis_confidence", "low")).lower()
        values.append(1.0 if confidence == "high" else 0.0)
    return np.array(values, dtype=float)


def active_contact_count_series(report: dict[str, Any]) -> np.ndarray:
    values: list[float] = []
    for frame in report.get("frames", []):
        values.append(float(len(frame.get("active_contact_limbs", []))))
    return np.array(values, dtype=float)


def build_summary(report: dict[str, Any]) -> dict[str, float]:
    return {
        "total_s": float(report["benchmark_timings_s"]["total_s"]),
        "fit_sequence_s": float(report["benchmark_timings_s"]["fit_sequence_s"]),
        "fit_mean_error_cm": float(report["dynamic_sequence_gate"]["fit_mean_error_m"]) * 100.0,
        "recovery_ratio_pct": float(report["dynamic_sequence_gate"]["recovery_ratio"]) * 100.0,
        "ok_frames": float(report["contact_force_distribution_summary"]["status_counts"].get("ok", 0)),
        "high_residual_frames": float(report["contact_force_distribution_summary"]["status_counts"].get("high_residual", 0)),
        "no_active_contact_frames": float(report["contact_force_distribution_summary"]["status_counts"].get("no_active_contacts", 0)),
        "inside_support_frames": float(report["support_stability_summary"]["inside_support_count"]),
        "point_support_frames": float(report["support_stability_summary"]["support_type_counts"].get("point_support", 0)),
        "residual_mean": float(report["contact_force_distribution_summary"]["relative_residual_summary"]["mean"]),
        "stability_margin_mean_cm": float(report["support_stability_summary"]["stability_margin_summary_m"]["mean_m"]) * 100.0,
    }


def plot_text_summary(ax: plt.Axes, baseline: dict[str, float], corrected: dict[str, float]) -> None:
    ax.axis("off")
    lines = [
        "Baseline vs Corrected",
        "",
        f"Total time: {baseline['total_s']:.2f}s -> {corrected['total_s']:.2f}s",
        f"Fit sequence: {baseline['fit_sequence_s']:.2f}s -> {corrected['fit_sequence_s']:.2f}s",
        f"Fit error: {baseline['fit_mean_error_cm']:.2f}cm -> {corrected['fit_mean_error_cm']:.2f}cm",
        f"Recovery ratio: {baseline['recovery_ratio_pct']:.2f}% -> {corrected['recovery_ratio_pct']:.2f}%",
        f"OK frames: {baseline['ok_frames']:.0f} -> {corrected['ok_frames']:.0f}",
        f"High residual: {baseline['high_residual_frames']:.0f} -> {corrected['high_residual_frames']:.0f}",
        f"No active contacts: {baseline['no_active_contact_frames']:.0f} -> {corrected['no_active_contact_frames']:.0f}",
        f"Inside support: {baseline['inside_support_frames']:.0f} -> {corrected['inside_support_frames']:.0f}",
        f"Point support: {baseline['point_support_frames']:.0f} -> {corrected['point_support_frames']:.0f}",
        f"Residual mean: {baseline['residual_mean']:.3f} -> {corrected['residual_mean']:.3f}",
        f"Mean stability margin: {baseline['stability_margin_mean_cm']:.2f}cm -> {corrected['stability_margin_mean_cm']:.2f}cm",
    ]
    ax.text(0.0, 1.0, "\n".join(lines), va="top", ha="left", fontsize=10, family="monospace")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--baseline", type=Path, default=DEFAULT_BASELINE)
    parser.add_argument("--corrected", type=Path, default=DEFAULT_CORRECTED)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()

    baseline = load_json(args.baseline)
    corrected = load_json(args.corrected)

    frame_count = min(len(baseline.get("frames", [])), len(corrected.get("frames", [])))
    x = np.arange(frame_count)

    base_fit_error = frame_series(baseline, ("fit_error_m",))[:frame_count] * 100.0
    corr_fit_error = frame_series(corrected, ("fit_error_m",))[:frame_count] * 100.0

    base_residual = frame_series(baseline, ("contact_force_distribution", "relative_residual"), default=1.0)[:frame_count]
    corr_residual = frame_series(corrected, ("contact_force_distribution", "relative_residual"), default=1.0)[:frame_count]

    base_margin = frame_series(baseline, ("support_stability", "stability_margin_m"))[:frame_count] * 100.0
    corr_margin = frame_series(corrected, ("support_stability", "stability_margin_m"))[:frame_count] * 100.0

    base_high = confidence_series(baseline)[:frame_count]
    corr_high = confidence_series(corrected)[:frame_count]

    base_ok = boolean_series(baseline, ("contact_force_distribution", "status"))[:0]
    # Explicit status series for ok
    base_ok = np.array(
        [1.0 if frame.get("contact_force_distribution", {}).get("status") == "ok" else 0.0 for frame in baseline.get("frames", [])[:frame_count]],
        dtype=float,
    )
    corr_ok = np.array(
        [1.0 if frame.get("contact_force_distribution", {}).get("status") == "ok" else 0.0 for frame in corrected.get("frames", [])[:frame_count]],
        dtype=float,
    )

    base_contacts = active_contact_count_series(baseline)[:frame_count]
    corr_contacts = active_contact_count_series(corrected)[:frame_count]

    fig, axes = plt.subplots(3, 2, figsize=(16, 12))
    fig.suptitle("JSON Service Benchmark Comparison: Polygon Broad-Phase vs Corrected", fontsize=14)

    axes[0, 0].plot(x, base_fit_error, label="baseline", alpha=0.75)
    axes[0, 0].plot(x, corr_fit_error, label="corrected", alpha=0.75)
    axes[0, 0].set_title("Fit Error per Frame")
    axes[0, 0].set_ylabel("cm")
    axes[0, 0].legend()
    axes[0, 0].grid(alpha=0.25)

    axes[0, 1].plot(x, base_residual, label="baseline", alpha=0.75)
    axes[0, 1].plot(x, corr_residual, label="corrected", alpha=0.75)
    axes[0, 1].set_title("Contact Force Relative Residual")
    axes[0, 1].set_ylabel("ratio")
    axes[0, 1].legend()
    axes[0, 1].grid(alpha=0.25)

    axes[1, 0].plot(x, base_margin, label="baseline", alpha=0.75)
    axes[1, 0].plot(x, corr_margin, label="corrected", alpha=0.75)
    axes[1, 0].axhline(0.0, color="black", linewidth=1.0, alpha=0.5)
    axes[1, 0].set_title("Support Stability Margin")
    axes[1, 0].set_ylabel("cm")
    axes[1, 0].legend()
    axes[1, 0].grid(alpha=0.25)

    axes[1, 1].step(x, base_contacts, where="mid", label="baseline", alpha=0.75)
    axes[1, 1].step(x, corr_contacts, where="mid", label="corrected", alpha=0.75)
    axes[1, 1].set_title("Active Contact Limb Count")
    axes[1, 1].set_ylabel("count")
    axes[1, 1].legend()
    axes[1, 1].grid(alpha=0.25)

    axes[2, 0].step(x, base_high, where="mid", label="baseline high_conf", alpha=0.75)
    axes[2, 0].step(x, corr_high, where="mid", label="corrected high_conf", alpha=0.75)
    axes[2, 0].step(x, base_ok, where="mid", label="baseline ok", alpha=0.55)
    axes[2, 0].step(x, corr_ok, where="mid", label="corrected ok", alpha=0.55)
    axes[2, 0].set_title("High-Confidence / OK Frame Flags")
    axes[2, 0].set_ylabel("flag")
    axes[2, 0].legend(loc="upper right")
    axes[2, 0].grid(alpha=0.25)

    plot_text_summary(axes[2, 1], build_summary(baseline), build_summary(corrected))

    for ax in axes.flat[:-1]:
        ax.set_xlabel("frame")

    fig.tight_layout()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(args.output, dpi=160)
    plt.close(fig)


if __name__ == "__main__":
    main()

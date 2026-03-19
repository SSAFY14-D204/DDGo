from __future__ import annotations

import logging
import sys
import time
from pathlib import Path
from typing import Any

import numpy as np

ROOT = Path(__file__).resolve().parent
APP_ROOT = ROOT.parents[1]
SERVER_ROOT = ROOT.parents[2]
RUNTIME_ROOT = ROOT / "runtime"
JSON_BENCH_ROOT = RUNTIME_ROOT / "json_service_benchmark"
ARTIC_ROOT = RUNTIME_ROOT / "custom_articulated_human"
CUSTOM_SKELETON_ROOT = RUNTIME_ROOT / "custom_skeleton_verify"
DYNAMIC_ROOT = RUNTIME_ROOT / "dynamic_sequence_pipeline"
DYNAMIC_HOLD_ROOT = RUNTIME_ROOT / "dynamic_hold_verify"

for path in (
    str(SERVER_ROOT),
    str(JSON_BENCH_ROOT),
    str(ARTIC_ROOT),
    str(CUSTOM_SKELETON_ROOT),
    str(DYNAMIC_ROOT),
    str(DYNAMIC_HOLD_ROOT),
):
    if path not in sys.path:
        sys.path.insert(0, path)

import run_json_service_benchmark as bench  # noqa: E402
from crux_detection import (  # noqa: E402
    build_hold_segments,
    enrich_frames_for_crux,
    score_fast_crux_candidates,
    score_physics_crux_candidates,
    summarize_hold_candidates,
)
from polygon_hold_contact_state import (  # noqa: E402
    PolygonHoldContactTracker,
    PolygonHoldDetection,
    compute_contact_points_px,
    load_polygon_service_holds,
    polygon_area,
    polygon_centroid,
)
from pose_sequence_correction import correct_pose_sequence_payload  # noqa: E402

logger = logging.getLogger(__name__)


class MujocoCompleteService:
    """MuJoCo 전체 분석 진입점 서비스."""

    def __init__(self) -> None:
        self.default_xml = ARTIC_ROOT / "custom_articulated_human.xml"
        self.cache_dir = SERVER_ROOT / "cache" / "mujoco_complete"
        self.physics_cache_dir = self.cache_dir / "physics"

    @staticmethod
    def _hold_state_summary(frames: list[dict[str, Any]]) -> dict[str, dict[str, int]]:
        summary: dict[str, dict[str, int]] = {}
        for frame in frames:
            for limb_name, payload in (frame.get("limb_states") or {}).items():
                bucket = summary.setdefault(limb_name, {})
                state = str(payload.get("state", "FREE"))
                bucket[state] = bucket.get(state, 0) + 1
        return summary

    def analyze_fast(
        self,
        holds_payload: dict[str, Any],
        pose_payload: dict[str, Any],
        user_body_payload: dict[str, Any],
        top_k_crux: int = 3,
        frame_step: int = 1,
    ) -> dict[str, Any]:
        started = time.perf_counter()

        correction_started = time.perf_counter()
        corrected_payload = correct_pose_sequence_payload(
            pose_payload=pose_payload,
            user_body_payload=user_body_payload,
            preserve_raw_copy=False,
        )
        correction_s = float(time.perf_counter() - correction_started)

        tracking_started = time.perf_counter()
        parsed_holds = load_polygon_service_holds_from_payload(holds_payload)
        tracker = PolygonHoldContactTracker(parsed_holds["holds"])
        video_metadata = corrected_payload.get("video_metadata", {})
        frame_width = int(video_metadata.get("frame_width", 0))
        frame_height = int(video_metadata.get("frame_height", 0))
        fps = float(video_metadata.get("fps", 30.0))

        crux_frames: list[dict[str, Any]] = []
        for raw_frame in corrected_payload.get("frames", []):
            frame_index = int(raw_frame["frame_index"])
            if frame_step > 1 and frame_index % frame_step != 0:
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

        scoring_started = time.perf_counter()
        enriched_frames = enrich_frames_for_crux(crux_frames)
        segments_by_hold = build_hold_segments(enriched_frames, fps=fps)
        candidates = summarize_hold_candidates(segments_by_hold)
        crux_result = score_fast_crux_candidates(candidates, top_k=max(1, int(top_k_crux)))
        crux_scoring_s = float(time.perf_counter() - scoring_started)

        report = {
            "schema_version": "1.0.0",
            "mode": "fast_crux_detection",
            "video_metadata": {
                "frame_width": frame_width,
                "frame_height": frame_height,
                "fps": fps,
                "total_frames": int(video_metadata.get("total_frames", len(corrected_payload.get("frames", [])))),
                "processed_frames": len(crux_frames),
                "frame_step": int(frame_step),
            },
            "timings_s": {
                "correction_s": correction_s,
                "hold_tracking_s": hold_tracking_s,
                "crux_scoring_s": crux_scoring_s,
                "total_s": float(time.perf_counter() - started),
            },
            "correction_summary": corrected_payload.get("correction_summary", {}),
            "hold_state_summary": self._hold_state_summary(crux_frames),
            "crux_result": crux_result,
        }
        return report

    def analyze_physics(
        self,
        holds_payload: dict[str, Any],
        pose_payload: dict[str, Any],
        user_body_payload: dict[str, Any],
        top_k_crux: int = 3,
        frame_step: int = 1,
    ) -> dict[str, Any]:
        started = time.perf_counter()

        correction_started = time.perf_counter()
        corrected_payload = correct_pose_sequence_payload(
            pose_payload=pose_payload,
            user_body_payload=user_body_payload,
            preserve_raw_copy=False,
        )
        correction_s = float(time.perf_counter() - correction_started)

        parsed_holds = load_polygon_service_holds_from_payload(holds_payload)
        bench.load_service_holds = load_polygon_service_holds
        bench.HoldContactTracker = PolygonHoldContactTracker
        bench.compute_contact_points_px = compute_contact_points_px

        physics_started = time.perf_counter()
        physics_report = bench.evaluate_from_json_inputs(
            xml_path=self.default_xml,
            holds_json=SERVER_ROOT / "app" / "dummy_holds.json",
            pose_json=SERVER_ROOT / "app" / "dummy_pose.json",
            user_body_json=SERVER_ROOT / "app" / "dummy_user_body.json",
            frame_step=max(1, int(frame_step)),
            sample_count=24,
            ik_iterations=25,
            damping=1e-2,
            smoothing_window=5,
            top_k_joints=8,
            cache_dir=self.physics_cache_dir,
            fit_frame_step=2,
            retry_high_confidence_only=True,
            holds_payload_override=parsed_holds,
            pose_payload_override=corrected_payload,
            user_body_payload_override=user_body_payload,
        )
        physics_pipeline_s = float(time.perf_counter() - physics_started)

        scoring_started = time.perf_counter()
        enriched_frames = enrich_frames_for_crux(list(physics_report.get("frames", [])))
        segments_by_hold = build_hold_segments(enriched_frames, fps=float(physics_report["video_metadata"]["fps"]))
        candidates = summarize_hold_candidates(segments_by_hold)
        crux_result = score_physics_crux_candidates(candidates, top_k=max(1, int(top_k_crux)))
        crux_scoring_s = float(time.perf_counter() - scoring_started)

        frames = list(physics_report.get("frames", []))
        high_conf = sum(1 for frame in frames if str(frame.get("analysis_confidence")) == "high")
        ok_frames = sum(1 for frame in frames if str(frame.get("contact_force_status")) == "ok")

        report = {
            "schema_version": "1.0.0",
            "mode": "physics_crux_detection",
            "timings_s": {
                "correction_s": correction_s,
                "physics_pipeline_s": physics_pipeline_s,
                "crux_scoring_s": crux_scoring_s,
                "total_s": float(time.perf_counter() - started),
            },
            "correction_summary": corrected_payload.get("correction_summary", {}),
            "physics_summary": {
                "fit_mean_error_m": float(physics_report.get("dynamic_sequence_gate", {}).get("fit_mean_error_m", 0.0)),
                "recovery_ratio": float(physics_report.get("dynamic_sequence_gate", {}).get("recovery_ratio", 0.0)),
                "processed_frames": int(physics_report.get("video_metadata", {}).get("processed_frames", len(frames))),
                "high_confidence_frame_count": high_conf,
                "ok_contact_force_frame_count": ok_frames,
                "point_support_frame_count": int(
                    physics_report.get("support_stability_summary", {}).get("support_type_counts", {}).get("point_support", 0)
                ),
            },
            "physics_pipeline_benchmark_timings_s": physics_report.get("benchmark_timings_s", {}),
            "crux_result": crux_result,
            "physics_result": physics_report,
        }
        return report


def load_polygon_service_holds_from_payload(payload: dict[str, Any]) -> dict[str, Any]:
    if "predictions" in payload:
        holds: list[PolygonHoldDetection] = []
        max_x = 0.0
        max_y = 0.0
        for index, pred in enumerate(payload.get("predictions", []), start=1):
            if pred.get("class") not in (None, "hold"):
                continue
            polygon = np.asarray(
                [[float(point["x"]), float(point["y"])] for point in pred["points"]],
                dtype=np.float64,
            )
            if polygon.shape[0] < 3:
                continue
            x1 = float(np.min(polygon[:, 0]))
            y1 = float(np.min(polygon[:, 1]))
            x2 = float(np.max(polygon[:, 0]))
            y2 = float(np.max(polygon[:, 1]))
            centroid = polygon_centroid(polygon)
            radius = float(np.sqrt(max(polygon_area(polygon), 1.0) / np.pi))
            if not np.isfinite(radius) or radius <= 1.0:
                radius = 0.45 * min(max(1.0, x2 - x1), max(1.0, y2 - y1))
            hold = PolygonHoldDetection(
                hold_id=index,
                cx_px=float(centroid[0]),
                cy_px=float(centroid[1]),
                radius_px=radius,
                x1=x1,
                y1=y1,
                x2=x2,
                y2=y2,
                confidence=float(pred.get("confidence", 0.0)),
                polygon_px=polygon,
            )
            holds.append(hold)
            max_x = max(max_x, x2)
            max_y = max(max_y, y2)
        return {
            "source_file": payload.get("image", {}).get("source_file"),
            "detection_count": len(holds),
            "bbox_extent_px": [max_x, max_y],
            "holds": holds,
        }

    if "holds" in payload:
        holds = []
        max_x = 0.0
        max_y = 0.0
        for item in payload.get("holds", []):
            bbox = item["bbox_px"]
            polygon_items = item.get("polygon_px")
            if polygon_items:
                polygon = np.asarray(
                    [[float(point["x"]), float(point["y"])] for point in polygon_items],
                    dtype=np.float64,
                )
            else:
                x1 = float(bbox["x1"])
                y1 = float(bbox["y1"])
                x2 = float(bbox["x2"])
                y2 = float(bbox["y2"])
                polygon = np.asarray([[x1, y1], [x2, y1], [x2, y2], [x1, y2]], dtype=np.float64)
            if polygon.shape[0] < 3:
                continue
            x1 = float(np.min(polygon[:, 0]))
            y1 = float(np.min(polygon[:, 1]))
            x2 = float(np.max(polygon[:, 0]))
            y2 = float(np.max(polygon[:, 1]))
            centroid = polygon_centroid(polygon)
            radius = float(item.get("radius_px", np.sqrt(max(polygon_area(polygon), 1.0) / np.pi)))
            hold = PolygonHoldDetection(
                hold_id=int(item["hold_id"]),
                cx_px=float(centroid[0]),
                cy_px=float(centroid[1]),
                radius_px=radius,
                x1=x1,
                y1=y1,
                x2=x2,
                y2=y2,
                confidence=float(item.get("confidence", 0.0)),
                polygon_px=polygon,
            )
            holds.append(hold)
            max_x = max(max_x, x2)
            max_y = max(max_y, y2)
        return {
            "source_file": payload.get("source", {}).get("legacy_source_file") or payload.get("source", {}).get("path"),
            "detection_count": len(holds),
            "bbox_extent_px": [max_x, max_y],
            "holds": holds,
        }

    if isinstance(payload, dict) and payload:
        logger.warning("Unknown holds_json payload keys: %s", sorted(payload.keys()))
    raise ValueError("Unsupported holds_json payload")


mujoco_complete_service = MujocoCompleteService()

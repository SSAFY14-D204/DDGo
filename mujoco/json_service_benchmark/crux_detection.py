from __future__ import annotations

from collections import Counter, defaultdict
from dataclasses import dataclass
from typing import Any

import numpy as np


CONFIDENCE_WEIGHT = {
    "high": 1.0,
    "medium": 0.7,
    "low": 0.4,
}

PHYSICS_START_HOLD_SCORE_SCALE = 0.45


@dataclass(slots=True)
class HoldSegment:
    hold_id: int
    start_frame: int
    end_frame: int
    start_time_ms: int
    end_time_ms: int
    duration_s: float
    active_frame_count: int
    limb_counts: dict[str, int]
    mode_counts: dict[str, int]
    mean_total_body_load: float
    mean_core_load: float
    mean_negative_margin_cm: float
    mean_load_shift_proxy: float
    mean_confidence_weight: float
    ok_fraction: float
    is_start_hold: bool
    is_end_hold: bool
    route_roles: list[str]
    score: float | None = None
    reason_tags: list[str] | None = None


def _nominal_frame_duration_ms(fps: float) -> float:
    return 1000.0 / max(float(fps), 1e-6)


def _confidence_to_weight(value: str | None) -> float:
    if value is None:
        return CONFIDENCE_WEIGHT["low"]
    return CONFIDENCE_WEIGHT.get(str(value).lower(), CONFIDENCE_WEIGHT["low"])


def _frame_total_body_load(frame: dict[str, Any]) -> float:
    body_loads = frame.get("body_loads") or {}
    return float(sum(abs(float(value)) for value in body_loads.values()))


def _frame_core_load(frame: dict[str, Any]) -> float:
    body_loads = frame.get("body_loads") or {}
    return float(abs(float(body_loads.get("core", 0.0))))


def _frame_negative_margin_cm(frame: dict[str, Any]) -> float:
    stability = frame.get("support_stability") or {}
    margin_m = stability.get("stability_margin_m")
    if margin_m is None:
        return 0.0
    return float(max(0.0, -float(margin_m) * 100.0))


def _frame_contact_force_sum(frame: dict[str, Any]) -> float:
    total = 0.0
    for value in (frame.get("estimated_contact_forces_n") or {}).values():
        if isinstance(value, dict):
            if value.get("force_norm_n") is not None:
                total += abs(float(value["force_norm_n"]))
                continue
            if value.get("force_xyz") is not None:
                arr = np.asarray(value["force_xyz"], dtype=np.float64)
                total += float(np.linalg.norm(arr))
                continue
        arr = np.asarray(value, dtype=np.float64)
        total += float(np.linalg.norm(arr))
    return total


def enrich_frames_for_crux(frames: list[dict[str, Any]]) -> list[dict[str, Any]]:
    enriched: list[dict[str, Any]] = []
    prev_total_body_load = 0.0
    prev_contact_force_sum = 0.0
    for frame in frames:
        total_body_load = _frame_total_body_load(frame)
        contact_force_sum = _frame_contact_force_sum(frame)
        load_shift_proxy = abs(total_body_load - prev_total_body_load) + 0.25 * abs(
            contact_force_sum - prev_contact_force_sum
        )
        prev_total_body_load = total_body_load
        prev_contact_force_sum = contact_force_sum
        enriched.append(
            {
                **frame,
                "_crux_total_body_load": total_body_load,
                "_crux_core_load": _frame_core_load(frame),
                "_crux_negative_margin_cm": _frame_negative_margin_cm(frame),
                "_crux_load_shift_proxy": float(load_shift_proxy),
                "_crux_confidence_weight": _confidence_to_weight(frame.get("analysis_confidence")),
                "_crux_ok": 1.0 if str(frame.get("contact_force_status")) == "ok" else 0.0,
            }
        )
    return enriched


def _frame_hold_membership(frame: dict[str, Any]) -> dict[int, dict[str, Any]]:
    membership: dict[int, dict[str, Any]] = {}
    active_hold_ids = frame.get("active_hold_ids") or {}
    limb_states = frame.get("limb_states") or {}
    for limb_name, hold_id in active_hold_ids.items():
        try:
            hold_id_int = int(hold_id)
        except (TypeError, ValueError):
            continue
        payload = membership.setdefault(
            hold_id_int,
            {
                "limbs": set(),
                "modes": set(),
                "route_roles": set(),
                "is_start": False,
                "is_end": False,
            },
        )
        payload["limbs"].add(str(limb_name))
        limb_payload = limb_states.get(limb_name, {}) or {}
        state = limb_payload.get("state")
        if state is None:
            state = "GRIP" if "hand" in str(limb_name) else "STEP"
        payload["modes"].add(str(state))
        route_role = limb_payload.get("route_role")
        if route_role is not None:
            payload["route_roles"].add(str(route_role))
        payload["is_start"] = bool(payload["is_start"] or limb_payload.get("is_start"))
        payload["is_end"] = bool(payload["is_end"] or limb_payload.get("is_end"))
    return membership


def build_hold_segments(frames: list[dict[str, Any]], fps: float) -> dict[int, list[HoldSegment]]:
    nominal_dt_ms = _nominal_frame_duration_ms(fps)
    open_segments: dict[int, dict[str, Any]] = {}
    closed_segments: dict[int, list[HoldSegment]] = defaultdict(list)

    def close_segment(hold_id: int) -> None:
        payload = open_segments.pop(hold_id, None)
        if payload is None:
            return
        segment_frames = payload["frames"]
        first = segment_frames[0]
        last = segment_frames[-1]
        duration_s = max(
            nominal_dt_ms,
            float(last["timestamp_ms"]) - float(first["timestamp_ms"]) + nominal_dt_ms,
        ) / 1000.0
        limb_counts: Counter[str] = Counter()
        mode_counts: Counter[str] = Counter()
        route_roles: set[str] = set()
        for item in segment_frames:
            limb_counts.update(item["limbs"])
            mode_counts.update(item["modes"])
            route_roles.update(item["route_roles"])
        closed_segments[hold_id].append(
            HoldSegment(
                hold_id=hold_id,
                start_frame=int(first["frame_index"]),
                end_frame=int(last["frame_index"]),
                start_time_ms=int(first["timestamp_ms"]),
                end_time_ms=int(last["timestamp_ms"]),
                duration_s=float(duration_s),
                active_frame_count=len(segment_frames),
                limb_counts=dict(limb_counts),
                mode_counts=dict(mode_counts),
                mean_total_body_load=float(np.mean([item["total_body_load"] for item in segment_frames])),
                mean_core_load=float(np.mean([item["core_load"] for item in segment_frames])),
                mean_negative_margin_cm=float(np.mean([item["negative_margin_cm"] for item in segment_frames])),
                mean_load_shift_proxy=float(np.mean([item["load_shift_proxy"] for item in segment_frames])),
                mean_confidence_weight=float(np.mean([item["confidence_weight"] for item in segment_frames])),
                ok_fraction=float(np.mean([item["ok"] for item in segment_frames])),
                is_start_hold=bool(any(bool(item["is_start"]) for item in segment_frames)),
                is_end_hold=bool(any(bool(item["is_end"]) for item in segment_frames)),
                route_roles=sorted(route_roles),
            )
        )

    for frame in frames:
        timestamp_ms = int(frame["timestamp_ms"])
        membership = _frame_hold_membership(frame)
        active_hold_ids = set(membership.keys())

        for hold_id in list(open_segments.keys()):
            if hold_id not in active_hold_ids:
                close_segment(hold_id)

        for hold_id, payload in membership.items():
            segment = open_segments.get(hold_id)
            if segment is None:
                segment = {"frames": []}
                open_segments[hold_id] = segment
            segment["frames"].append(
                {
                    "frame_index": int(frame["frame_index"]),
                    "timestamp_ms": timestamp_ms,
                    "limbs": sorted(payload["limbs"]),
                    "modes": sorted(payload["modes"]),
                    "total_body_load": float(frame.get("_crux_total_body_load", 0.0)),
                    "core_load": float(frame.get("_crux_core_load", 0.0)),
                    "negative_margin_cm": float(frame.get("_crux_negative_margin_cm", 0.0)),
                    "load_shift_proxy": float(frame.get("_crux_load_shift_proxy", 0.0)),
                    "confidence_weight": float(frame.get("_crux_confidence_weight", 0.4)),
                    "ok": float(frame.get("_crux_ok", 0.0)),
                    "is_start": bool(payload["is_start"]),
                    "is_end": bool(payload["is_end"]),
                    "route_roles": sorted(payload["route_roles"]),
                }
            )

    for hold_id in list(open_segments.keys()):
        close_segment(hold_id)
    return dict(closed_segments)


def summarize_hold_candidates(
    segments_by_hold: dict[int, list[HoldSegment]],
) -> list[dict[str, Any]]:
    candidates: list[dict[str, Any]] = []
    for hold_id, segments in segments_by_hold.items():
        total_active_time_s = float(sum(segment.duration_s for segment in segments))
        best_segment = max(segments, key=lambda item: item.duration_s)
        limb_counts: Counter[str] = Counter()
        mode_counts: Counter[str] = Counter()
        route_roles: set[str] = set()
        for segment in segments:
            limb_counts.update(segment.limb_counts)
            mode_counts.update(segment.mode_counts)
            route_roles.update(segment.route_roles)
        candidates.append(
            {
                "hold_id": int(hold_id),
                "segment_count": len(segments),
                "engagement_count": len(segments),
                "total_active_time_s": total_active_time_s,
                "longest_continuous_dwell_s": float(best_segment.duration_s),
                "best_segment": {
                    "start_frame": best_segment.start_frame,
                    "end_frame": best_segment.end_frame,
                    "start_time_ms": best_segment.start_time_ms,
                    "end_time_ms": best_segment.end_time_ms,
                    "duration_s": float(best_segment.duration_s),
                    "dominant_limbs": [limb for limb, _ in Counter(best_segment.limb_counts).most_common()],
                    "dominant_modes": [mode for mode, _ in Counter(best_segment.mode_counts).most_common()],
                },
                "limb_counts": dict(limb_counts),
                "mode_counts": dict(mode_counts),
                "is_start_hold": bool(any(segment.is_start_hold for segment in segments)),
                "is_end_hold": bool(any(segment.is_end_hold for segment in segments)),
                "route_roles": sorted(route_roles),
                "_segments": segments,
            }
        )
    return candidates


def _robust_normalize_lookup(values: dict[Any, float]) -> dict[Any, float]:
    if not values:
        return {}
    arr = np.asarray(list(values.values()), dtype=np.float64)
    p50 = float(np.percentile(arr, 50))
    p90 = float(np.percentile(arr, 90))
    if p90 <= p50 + 1e-8:
        max_value = float(np.max(arr))
        if max_value <= 1e-8:
            return {key: 0.0 for key in values}
        return {key: float(np.clip(val / max_value, 0.0, 1.0)) for key, val in values.items()}
    return {key: float(np.clip((val - p50) / (p90 - p50), 0.0, 1.0)) for key, val in values.items()}


def _max_normalize_lookup(values: dict[Any, float]) -> dict[Any, float]:
    if not values:
        return {}
    max_value = max(float(value) for value in values.values())
    if max_value <= 1e-8:
        return {key: 0.0 for key in values}
    return {key: float(np.clip(float(val) / max_value, 0.0, 1.0)) for key, val in values.items()}


def score_fast_crux_candidates(
    candidates: list[dict[str, Any]],
    top_k: int = 3,
) -> dict[str, Any]:
    eligible_candidates = [candidate for candidate in candidates if not bool(candidate.get("is_start_hold"))]
    if not eligible_candidates:
        eligible_candidates = list(candidates)

    longest_lookup = {
        candidate["hold_id"]: float(candidate["longest_continuous_dwell_s"])
        for candidate in eligible_candidates
    }
    total_lookup = {
        candidate["hold_id"]: float(candidate["total_active_time_s"])
        for candidate in eligible_candidates
    }
    longest_norm = _max_normalize_lookup(longest_lookup)
    total_norm = _max_normalize_lookup(total_lookup)

    scored: list[dict[str, Any]] = []
    excluded_start_candidates: list[dict[str, Any]] = []
    for candidate in candidates:
        hold_id = int(candidate["hold_id"])
        reason_tags: list[str] = []
        if bool(candidate.get("is_start_hold")) and len(eligible_candidates) != len(candidates):
            excluded_start_candidates.append(
                {
                    **{key: value for key, value in candidate.items() if key != "_segments"},
                    "fast_crux_score": 0.0,
                    "reason_tags": ["start_hold_excluded"],
                }
            )
            continue

        score = 0.7 * longest_norm.get(hold_id, 0.0) + 0.3 * total_norm.get(hold_id, 0.0)
        if longest_norm.get(hold_id, 0.0) >= 0.6:
            reason_tags.append("longest_dwell")
        if total_norm.get(hold_id, 0.0) >= 0.6:
            reason_tags.append("high_total_dwell")
        scored.append(
            {
                **{key: value for key, value in candidate.items() if key != "_segments"},
                "fast_crux_score": float(score),
                "reason_tags": reason_tags,
            }
        )

    ranked = sorted(
        scored,
        key=lambda item: (
            float(item["fast_crux_score"]),
            float(item["longest_continuous_dwell_s"]),
            float(item["total_active_time_s"]),
        ),
        reverse=True,
    )
    return {
        "logic": {
            "score_formula": "0.7 * longest_continuous_dwell_norm + 0.3 * total_active_time_norm",
            "why": "Fast crux ranks holds by dwell time, favoring the longest continuous stay and then total active time.",
            "start_hold_policy": "start_hold_excluded_from_fast_ranking",
        },
        "candidate_count": len(ranked),
        "top_candidates": ranked[:top_k],
        "all_candidates": ranked,
        "excluded_start_hold_candidates": excluded_start_candidates,
    }


def score_physics_crux_candidates(
    candidates: list[dict[str, Any]],
    top_k: int = 3,
) -> dict[str, Any]:
    all_segments: list[HoldSegment] = []
    for candidate in candidates:
        all_segments.extend(candidate.get("_segments", []))

    dwell_lookup = {idx: float(segment.duration_s) for idx, segment in enumerate(all_segments)}
    load_lookup = {idx: float(segment.mean_total_body_load) for idx, segment in enumerate(all_segments)}
    instability_lookup = {idx: float(segment.mean_negative_margin_cm) for idx, segment in enumerate(all_segments)}
    shift_lookup = {idx: float(segment.mean_load_shift_proxy) for idx, segment in enumerate(all_segments)}
    dwell_norm = _robust_normalize_lookup(dwell_lookup)
    load_norm = _robust_normalize_lookup(load_lookup)
    instability_norm = _robust_normalize_lookup(instability_lookup)
    shift_norm = _robust_normalize_lookup(shift_lookup)

    for idx, segment in enumerate(all_segments):
        base_score = (
            0.35 * dwell_norm.get(idx, 0.0)
            + 0.35 * load_norm.get(idx, 0.0)
            + 0.15 * instability_norm.get(idx, 0.0)
            + 0.15 * shift_norm.get(idx, 0.0)
        )
        quality_weight = 0.7 * float(segment.mean_confidence_weight) + 0.3 * float(segment.ok_fraction)
        rest_penalty = 0.0
        if (
            dwell_norm.get(idx, 0.0) >= 0.6
            and load_norm.get(idx, 0.0) < 0.3
            and instability_norm.get(idx, 0.0) < 0.25
        ):
            rest_penalty = 0.15
        score = max(0.0, quality_weight * base_score - rest_penalty)
        reason_tags: list[str] = []
        if dwell_norm.get(idx, 0.0) >= 0.6:
            reason_tags.append("long_dwell")
        if load_norm.get(idx, 0.0) >= 0.6:
            reason_tags.append("high_load")
        if instability_norm.get(idx, 0.0) >= 0.6:
            reason_tags.append("unstable_support")
        if shift_norm.get(idx, 0.0) >= 0.6:
            reason_tags.append("load_shift")
        segment.score = float(score)
        segment.reason_tags = reason_tags

    total_dwell_lookup = {
        candidate["hold_id"]: float(candidate["total_active_time_s"])
        for candidate in candidates
    }
    total_dwell_norm = _robust_normalize_lookup(total_dwell_lookup)

    scored: list[dict[str, Any]] = []
    for candidate in candidates:
        hold_id = int(candidate["hold_id"])
        segments = list(candidate.get("_segments", []))
        if not segments:
            continue
        best_segment = max(segments, key=lambda item: float(item.score or 0.0))
        hold_score = 0.85 * float(best_segment.score or 0.0) + 0.15 * total_dwell_norm.get(hold_id, 0.0)
        reason_tags = list(best_segment.reason_tags or [])
        if bool(candidate.get("is_start_hold")):
            hold_score *= PHYSICS_START_HOLD_SCORE_SCALE
            reason_tags.append("start_hold_penalty")
        scored.append(
            {
                **{key: value for key, value in candidate.items() if key != "_segments"},
                "physics_crux_score": float(hold_score),
                "best_segment": {
                    **candidate["best_segment"],
                    "mean_total_body_load": float(best_segment.mean_total_body_load),
                    "mean_core_load": float(best_segment.mean_core_load),
                    "mean_negative_margin_cm": float(best_segment.mean_negative_margin_cm),
                    "mean_load_shift_proxy": float(best_segment.mean_load_shift_proxy),
                    "mean_confidence_weight": float(best_segment.mean_confidence_weight),
                    "ok_fraction": float(best_segment.ok_fraction),
                    "segment_crux_score": float(best_segment.score or 0.0),
                    "reason_tags": list(best_segment.reason_tags or []),
                },
                "reason_tags": reason_tags,
            }
        )

    ranked = sorted(
        scored,
        key=lambda item: (
            float(item["physics_crux_score"]),
            float(item["best_segment"]["segment_crux_score"]),
            float(item["longest_continuous_dwell_s"]),
        ),
        reverse=True,
    )
    return {
        "logic": {
            "score_formula": "quality_weight * (0.35*dwell + 0.35*load + 0.15*instability + 0.15*load_shift) - rest_penalty",
            "why": "Physics crux combines dwell, load, instability, and load shift, then discounts low-load resting segments.",
            "start_hold_policy": f"start_hold_score_scaled_by_{PHYSICS_START_HOLD_SCORE_SCALE:.2f}",
        },
        "candidate_count": len(ranked),
        "top_candidates": ranked[:top_k],
        "all_candidates": ranked,
    }

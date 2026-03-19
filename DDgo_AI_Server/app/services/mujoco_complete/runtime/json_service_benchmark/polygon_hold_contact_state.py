from __future__ import annotations

import json
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

import numpy as np

ROOT = Path(__file__).resolve().parent
PROJECT_ROOT = ROOT.parent
ARTIC_ROOT = PROJECT_ROOT / "custom_articulated_human"
sys.path.insert(0, str(ARTIC_ROOT))

from hold_contact_state import compute_contact_points_px  # noqa: E402


HAND_DWELL_MS = 120
FOOT_DWELL_MS = 120
HAND_SPEED_THRESHOLD_PX_S = 220.0
FOOT_SPEED_THRESHOLD_PX_S = 260.0
REACH_RADIUS_SCALE = 1.20
ENTER_MARGIN_SCALE = 0.20
EXIT_MARGIN_SCALE = 0.34
MIN_ENTER_MARGIN_PX = 10.0
MIN_EXIT_MARGIN_PX = 18.0

LIMB_LABELS = {
    "left_hand": "GRIP",
    "right_hand": "GRIP",
    "left_foot": "STEP",
    "right_foot": "STEP",
}


def normalize(v: np.ndarray, eps: float = 1e-8) -> np.ndarray:
    arr = np.asarray(v, dtype=np.float64)
    norm = float(np.linalg.norm(arr))
    if norm < eps:
        return np.zeros_like(arr)
    return arr / norm


def polygon_area(points: np.ndarray) -> float:
    if points.shape[0] < 3:
        return 0.0
    x = points[:, 0]
    y = points[:, 1]
    return 0.5 * abs(float(np.dot(x, np.roll(y, -1)) - np.dot(y, np.roll(x, -1))))


def polygon_centroid(points: np.ndarray) -> np.ndarray:
    if points.shape[0] < 3:
        return np.mean(points, axis=0)
    x = points[:, 0]
    y = points[:, 1]
    factor = x * np.roll(y, -1) - np.roll(x, -1) * y
    signed_area = 0.5 * float(np.sum(factor))
    if abs(signed_area) < 1e-6:
        return np.mean(points, axis=0)
    cx = np.sum((x + np.roll(x, -1)) * factor) / (6.0 * signed_area)
    cy = np.sum((y + np.roll(y, -1)) * factor) / (6.0 * signed_area)
    return np.array([cx, cy], dtype=np.float64)


def point_in_polygon(point: np.ndarray, polygon: np.ndarray) -> bool:
    x = float(point[0])
    y = float(point[1])
    inside = False
    j = polygon.shape[0] - 1
    for i in range(polygon.shape[0]):
        xi = float(polygon[i, 0])
        yi = float(polygon[i, 1])
        xj = float(polygon[j, 0])
        yj = float(polygon[j, 1])
        intersects = ((yi > y) != (yj > y)) and (
            x < (xj - xi) * (y - yi) / max(yj - yi, 1e-8) + xi
        )
        if intersects:
            inside = not inside
        j = i
    return inside


def distance_point_to_segment(point: np.ndarray, a: np.ndarray, b: np.ndarray) -> float:
    ab = b - a
    denom = float(np.dot(ab, ab))
    if denom <= 1e-8:
        return float(np.linalg.norm(point - a))
    t = float(np.dot(point - a, ab) / denom)
    t = float(np.clip(t, 0.0, 1.0))
    proj = a + t * ab
    return float(np.linalg.norm(point - proj))


def polygon_proximity(point: np.ndarray, polygon: np.ndarray) -> tuple[bool, float]:
    inside = point_in_polygon(point, polygon)
    min_distance = float("inf")
    for idx in range(polygon.shape[0]):
        a = polygon[idx]
        b = polygon[(idx + 1) % polygon.shape[0]]
        min_distance = min(min_distance, distance_point_to_segment(point, a, b))
    if not np.isfinite(min_distance):
        min_distance = 0.0
    return inside, 0.0 if inside else float(min_distance)


def point_to_expanded_bbox_distance(
    point: np.ndarray,
    x1: float,
    y1: float,
    x2: float,
    y2: float,
    margin_px: float,
) -> float:
    px = float(point[0])
    py = float(point[1])
    ex1 = x1 - margin_px
    ey1 = y1 - margin_px
    ex2 = x2 + margin_px
    ey2 = y2 + margin_px
    dx = max(ex1 - px, 0.0, px - ex2)
    dy = max(ey1 - py, 0.0, py - ey2)
    if dx <= 0.0 and dy <= 0.0:
        return 0.0
    return float(np.hypot(dx, dy))


@dataclass
class PolygonHoldDetection:
    hold_id: int
    cx_px: float
    cy_px: float
    radius_px: float
    x1: float
    y1: float
    x2: float
    y2: float
    confidence: float
    polygon_px: np.ndarray


@dataclass
class LimbTrackerState:
    state: str = "FREE"
    active_hold_id: int | None = None
    candidate_hold_id: int | None = None
    candidate_since_ms: int | None = None
    last_point_px: np.ndarray | None = None
    last_timestamp_ms: int | None = None
    last_transition: str | None = None
    last_distance_px: float | None = None
    last_speed_px_s: float = 0.0


def _polygon_array(points: list[dict[str, Any]]) -> np.ndarray:
    arr = np.asarray([[float(item["x"]), float(item["y"])] for item in points], dtype=np.float64)
    if arr.shape[0] < 3:
        raise ValueError("polygon requires at least 3 points")
    return arr


def _hold_from_prediction(index: int, pred: dict[str, Any]) -> PolygonHoldDetection:
    polygon = _polygon_array(list(pred["points"]))
    x_coords = polygon[:, 0]
    y_coords = polygon[:, 1]
    x1 = float(np.min(x_coords))
    y1 = float(np.min(y_coords))
    x2 = float(np.max(x_coords))
    y2 = float(np.max(y_coords))
    width = max(1.0, x2 - x1)
    height = max(1.0, y2 - y1)
    centroid = polygon_centroid(polygon)
    area = polygon_area(polygon)
    radius = float(np.sqrt(max(area, 1.0) / np.pi))
    if not np.isfinite(radius) or radius <= 1.0:
        radius = 0.45 * min(width, height)
    return PolygonHoldDetection(
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


def _hold_from_prepared(payload: dict[str, Any]) -> PolygonHoldDetection:
    bbox = payload["bbox_px"]
    polygon_items = payload.get("polygon_px")
    if polygon_items:
        polygon = _polygon_array(list(polygon_items))
        centroid = polygon_centroid(polygon)
    else:
        x1 = float(bbox["x1"])
        y1 = float(bbox["y1"])
        x2 = float(bbox["x2"])
        y2 = float(bbox["y2"])
        polygon = np.asarray(
            [[x1, y1], [x2, y1], [x2, y2], [x1, y2]],
            dtype=np.float64,
        )
        centroid = np.asarray([0.5 * (x1 + x2), 0.5 * (y1 + y2)], dtype=np.float64)
    x_coords = polygon[:, 0]
    y_coords = polygon[:, 1]
    x1 = float(np.min(x_coords))
    y1 = float(np.min(y_coords))
    x2 = float(np.max(x_coords))
    y2 = float(np.max(y_coords))
    radius = float(payload.get("radius_px", np.sqrt(max(polygon_area(polygon), 1.0) / np.pi)))
    return PolygonHoldDetection(
        hold_id=int(payload["hold_id"]),
        cx_px=float(centroid[0]),
        cy_px=float(centroid[1]),
        radius_px=radius,
        x1=x1,
        y1=y1,
        x2=x2,
        y2=y2,
        confidence=float(payload.get("confidence", 0.0)),
        polygon_px=polygon,
    )


def load_polygon_service_holds(detections_json: Path) -> dict[str, Any]:
    payload = json.loads(detections_json.read_text(encoding="utf-8"))
    holds: list[PolygonHoldDetection] = []
    max_x = 0.0
    max_y = 0.0

    if "predictions" in payload:
        for index, pred in enumerate(payload.get("predictions", []), start=1):
            if pred.get("class") not in (None, "hold"):
                continue
            hold = _hold_from_prediction(index, pred)
            holds.append(hold)
            max_x = max(max_x, hold.x2)
            max_y = max(max_y, hold.y2)
        source_file = payload.get("image", {}).get("source_file")
    else:
        for item in payload.get("holds", []):
            hold = _hold_from_prepared(item)
            holds.append(hold)
            max_x = max(max_x, hold.x2)
            max_y = max(max_y, hold.y2)
        source_file = payload.get("source", {}).get("legacy_source_file") or payload.get("source", {}).get("path")

    return {
        "source_file": source_file,
        "detection_count": len(holds),
        "bbox_extent_px": [max_x, max_y],
        "holds": holds,
    }


@dataclass
class PolygonHoldContactTracker:
    holds: list[PolygonHoldDetection]
    limb_states: dict[str, LimbTrackerState] = field(
        default_factory=lambda: {
            "left_hand": LimbTrackerState(),
            "right_hand": LimbTrackerState(),
            "left_foot": LimbTrackerState(),
            "right_foot": LimbTrackerState(),
        }
    )
    _holds_by_id: dict[int, PolygonHoldDetection] = field(init=False, repr=False)

    def __post_init__(self) -> None:
        self._holds_by_id = {hold.hold_id: hold for hold in self.holds}

    def update_frame(
        self,
        limb_points_px: dict[str, np.ndarray | None],
        timestamp_ms: int,
    ) -> dict[str, dict[str, Any]]:
        frame_result: dict[str, dict[str, Any]] = {}
        for limb_name, point_px in limb_points_px.items():
            state = self.limb_states[limb_name]
            frame_result[limb_name] = self._update_single_limb(limb_name, state, point_px, timestamp_ms)
        return frame_result

    def _update_single_limb(
        self,
        limb_name: str,
        state: LimbTrackerState,
        point_px: np.ndarray | None,
        timestamp_ms: int,
    ) -> dict[str, Any]:
        engaged_label = LIMB_LABELS[limb_name]
        dwell_ms = HAND_DWELL_MS if "hand" in limb_name else FOOT_DWELL_MS
        speed_threshold = HAND_SPEED_THRESHOLD_PX_S if "hand" in limb_name else FOOT_SPEED_THRESHOLD_PX_S

        if point_px is None or not self.holds:
            state.state = "FREE"
            state.active_hold_id = None
            state.candidate_hold_id = None
            state.candidate_since_ms = None
            state.last_transition = "missing"
            state.last_distance_px = None
            state.last_speed_px_s = 0.0
            state.last_timestamp_ms = timestamp_ms
            state.last_point_px = None if point_px is None else np.asarray(point_px, dtype=np.float64)
            return {
                "state": state.state,
                "active_hold_id": state.active_hold_id,
                "candidate_hold_id": state.candidate_hold_id,
                "distance_px": None,
                "speed_px_s": state.last_speed_px_s,
                "transition": state.last_transition,
                "hold_center_px": None,
                "hold_radius_px": None,
                "inside_polygon": None,
            }

        point_px = np.asarray(point_px, dtype=np.float64)
        speed_px_s = 0.0
        if state.last_point_px is not None and state.last_timestamp_ms is not None:
            dt = max((timestamp_ms - state.last_timestamp_ms) / 1000.0, 1e-6)
            speed_px_s = float(np.linalg.norm(point_px - state.last_point_px) / dt)
        state.last_point_px = point_px.copy()
        state.last_timestamp_ms = timestamp_ms
        state.last_speed_px_s = speed_px_s

        hold, inside_polygon, distance_px = self._nearest_hold(point_px)
        state.last_distance_px = distance_px
        enter_margin = max(MIN_ENTER_MARGIN_PX, hold.radius_px * ENTER_MARGIN_SCALE)
        exit_margin = max(MIN_EXIT_MARGIN_PX, hold.radius_px * EXIT_MARGIN_SCALE)
        reach_radius = max(enter_margin * 1.5, hold.radius_px * REACH_RADIUS_SCALE)

        transition = None

        if state.active_hold_id is not None:
            active_hold = self._hold_by_id(state.active_hold_id)
            if active_hold is not None:
                active_inside, active_distance = polygon_proximity(point_px, active_hold.polygon_px)
                if active_inside or active_distance <= exit_margin:
                    hold = active_hold
                    inside_polygon = active_inside
                    distance_px = active_distance
                else:
                    transition = "release"
                    state.state = "RELEASE"
                    state.active_hold_id = None
                    state.candidate_hold_id = None
                    state.candidate_since_ms = None

        if state.active_hold_id is None:
            if inside_polygon or distance_px <= enter_margin:
                if state.candidate_hold_id != hold.hold_id:
                    state.candidate_hold_id = hold.hold_id
                    state.candidate_since_ms = timestamp_ms
                elapsed_ms = 0 if state.candidate_since_ms is None else timestamp_ms - state.candidate_since_ms
                if elapsed_ms >= dwell_ms and speed_px_s <= speed_threshold:
                    state.active_hold_id = hold.hold_id
                    state.state = engaged_label
                    transition = "engage"
                else:
                    state.state = "REACH"
            elif distance_px <= reach_radius:
                state.state = "REACH"
                state.candidate_hold_id = None
                state.candidate_since_ms = None
            else:
                state.state = "FREE"
                state.candidate_hold_id = None
                state.candidate_since_ms = None
        else:
            state.state = engaged_label

        state.last_transition = transition
        return {
            "state": state.state,
            "active_hold_id": state.active_hold_id,
            "candidate_hold_id": state.candidate_hold_id,
            "distance_px": float(distance_px),
            "speed_px_s": float(speed_px_s),
            "transition": transition,
            "hold_center_px": [float(hold.cx_px), float(hold.cy_px)],
            "hold_radius_px": float(hold.radius_px),
            "inside_polygon": bool(inside_polygon),
        }

    def _nearest_hold(self, point_px: np.ndarray) -> tuple[PolygonHoldDetection, bool, float]:
        candidate_holds = self._candidate_holds(point_px)
        if not candidate_holds:
            candidate_holds = self.holds

        best_hold: PolygonHoldDetection | None = None
        best_inside = False
        best_distance = float("inf")
        for hold in candidate_holds:
            inside, distance_px = polygon_proximity(point_px, hold.polygon_px)
            if best_hold is None:
                best_hold = hold
                best_inside = inside
                best_distance = distance_px
                continue
            if inside and not best_inside:
                best_hold = hold
                best_inside = True
                best_distance = distance_px
                continue
            if inside == best_inside and distance_px < best_distance:
                best_hold = hold
                best_inside = inside
                best_distance = distance_px
        assert best_hold is not None
        return best_hold, best_inside, float(best_distance)

    def _hold_by_id(self, hold_id: int) -> PolygonHoldDetection | None:
        return self._holds_by_id.get(int(hold_id))

    def _candidate_holds(self, point_px: np.ndarray) -> list[PolygonHoldDetection]:
        candidates: list[tuple[float, PolygonHoldDetection]] = []
        for hold in self.holds:
            margin_px = max(MIN_EXIT_MARGIN_PX, hold.radius_px * REACH_RADIUS_SCALE)
            bbox_distance = point_to_expanded_bbox_distance(
                point=point_px,
                x1=hold.x1,
                y1=hold.y1,
                x2=hold.x2,
                y2=hold.y2,
                margin_px=margin_px,
            )
            if bbox_distance == 0.0:
                candidates.append((0.0, hold))
        if candidates:
            return [hold for _, hold in candidates]

        # If no expanded bbox contains the point, keep only a few closest bbox candidates.
        fallback: list[tuple[float, PolygonHoldDetection]] = []
        for hold in self.holds:
            margin_px = max(MIN_EXIT_MARGIN_PX, hold.radius_px * REACH_RADIUS_SCALE)
            bbox_distance = point_to_expanded_bbox_distance(
                point=point_px,
                x1=hold.x1,
                y1=hold.y1,
                x2=hold.x2,
                y2=hold.y2,
                margin_px=margin_px,
            )
            fallback.append((bbox_distance, hold))
        fallback.sort(key=lambda item: item[0])
        return [hold for _, hold in fallback[:6]]

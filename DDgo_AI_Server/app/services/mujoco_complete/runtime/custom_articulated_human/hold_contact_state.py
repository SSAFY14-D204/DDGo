from __future__ import annotations

import json
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

import numpy as np

from evaluate_static_fit import ROOT

import sys

CUSTOM_SKELETON_ROOT = ROOT.parent / "custom_skeleton_verify"
DYNAMIC_HOLD_ROOT = ROOT.parent / "dynamic_hold_verify"
sys.path.insert(0, str(DYNAMIC_HOLD_ROOT))

from physics_worker import (  # noqa: E402
    LEFT_ELBOW,
    LEFT_FOOT_INDEX,
    LEFT_HEEL,
    LEFT_INDEX,
    LEFT_PINKY,
    LEFT_THUMB,
    LEFT_WRIST,
    RIGHT_ELBOW,
    RIGHT_FOOT_INDEX,
    RIGHT_HEEL,
    RIGHT_INDEX,
    RIGHT_PINKY,
    RIGHT_THUMB,
    RIGHT_WRIST,
)


HAND_DWELL_MS = 120
FOOT_DWELL_MS = 120
HAND_SPEED_THRESHOLD_PX_S = 220.0
FOOT_SPEED_THRESHOLD_PX_S = 260.0
REACH_RADIUS_SCALE = 1.85
ENTER_RADIUS_SCALE = 1.00
EXIT_RADIUS_SCALE = 1.35

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


@dataclass
class HoldDetection:
    hold_id: int
    cx_px: float
    cy_px: float
    radius_px: float
    x1: float
    y1: float
    x2: float
    y2: float
    confidence: float


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


@dataclass
class HoldContactTracker:
    holds: list[HoldDetection]
    limb_states: dict[str, LimbTrackerState] = field(
        default_factory=lambda: {
            "left_hand": LimbTrackerState(),
            "right_hand": LimbTrackerState(),
            "left_foot": LimbTrackerState(),
            "right_foot": LimbTrackerState(),
        }
    )

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
            }

        point_px = np.asarray(point_px, dtype=np.float64)
        speed_px_s = 0.0
        if state.last_point_px is not None and state.last_timestamp_ms is not None:
            dt = max((timestamp_ms - state.last_timestamp_ms) / 1000.0, 1e-6)
            speed_px_s = float(np.linalg.norm(point_px - state.last_point_px) / dt)
        state.last_point_px = point_px.copy()
        state.last_timestamp_ms = timestamp_ms
        state.last_speed_px_s = speed_px_s

        nearest = self._nearest_hold(point_px)
        assert nearest is not None
        hold, distance_px = nearest
        state.last_distance_px = distance_px
        enter_radius = hold.radius_px * ENTER_RADIUS_SCALE
        exit_radius = hold.radius_px * EXIT_RADIUS_SCALE
        reach_radius = hold.radius_px * REACH_RADIUS_SCALE

        transition = None

        if state.active_hold_id is not None:
            active_hold = self._hold_by_id(state.active_hold_id)
            if active_hold is not None:
                active_distance = float(np.linalg.norm(point_px - np.array([active_hold.cx_px, active_hold.cy_px], dtype=np.float64)))
                if active_distance <= exit_radius:
                    hold = active_hold
                    distance_px = active_distance
                else:
                    transition = "release"
                    state.state = "RELEASE"
                    state.active_hold_id = None
                    state.candidate_hold_id = None
                    state.candidate_since_ms = None

        if state.active_hold_id is None:
            if distance_px <= enter_radius:
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
        }

    def _nearest_hold(self, point_px: np.ndarray) -> tuple[HoldDetection, float] | None:
        best: tuple[HoldDetection, float] | None = None
        for hold in self.holds:
            center = np.array([hold.cx_px, hold.cy_px], dtype=np.float64)
            distance_px = float(np.linalg.norm(point_px - center))
            if best is None or distance_px < best[1]:
                best = (hold, distance_px)
        return best

    def _hold_by_id(self, hold_id: int) -> HoldDetection | None:
        for hold in self.holds:
            if hold.hold_id == hold_id:
                return hold
        return None


def load_hold_detections(detections_json: Path) -> dict[str, Any]:
    payload = json.loads(detections_json.read_text(encoding="utf-8"))
    detections = payload.get("detections", [])
    holds: list[HoldDetection] = []
    max_x = 0.0
    max_y = 0.0
    for det in detections:
        x1 = float(det["x1"])
        y1 = float(det["y1"])
        x2 = float(det["x2"])
        y2 = float(det["y2"])
        width = max(1.0, x2 - x1)
        height = max(1.0, y2 - y1)
        radius_px = 0.45 * min(width, height)
        holds.append(
            HoldDetection(
                hold_id=int(det["hold_id"]),
                cx_px=0.5 * (x1 + x2),
                cy_px=0.5 * (y1 + y2),
                radius_px=radius_px,
                x1=x1,
                y1=y1,
                x2=x2,
                y2=y2,
                confidence=float(det.get("confidence", 0.0)),
            )
        )
        max_x = max(max_x, x2)
        max_y = max(max_y, y2)

    return {
        "source_file": payload.get("image", {}).get("source_file"),
        "detection_count": len(holds),
        "bbox_extent_px": [max_x, max_y],
        "holds": holds,
    }


def landmarks_to_pixels(pose_landmarks: list, frame_width: int, frame_height: int) -> dict[int, np.ndarray]:
    points: dict[int, np.ndarray] = {}
    for idx, lm in enumerate(pose_landmarks):
        points[idx] = np.array(
            [
                float(lm.x) * float(frame_width),
                float(lm.y) * float(frame_height),
            ],
            dtype=np.float64,
        )
    return points


def infer_palm_contact_px(
    wrist: np.ndarray,
    elbow: np.ndarray,
    index_tip: np.ndarray,
    pinky_tip: np.ndarray,
    thumb_tip: np.ndarray,
) -> np.ndarray:
    fingertip_centroid = (index_tip + pinky_tip + thumb_tip) / 3.0
    fingertip_dir = normalize(fingertip_centroid - wrist)
    forearm_dir = normalize(wrist - elbow)
    blended_dir = normalize(0.55 * fingertip_dir + 0.45 * forearm_dir)
    if float(np.linalg.norm(blended_dir)) < 1e-6:
        blended_dir = forearm_dir if float(np.linalg.norm(forearm_dir)) >= 1e-6 else fingertip_dir
    forearm_len = float(np.linalg.norm(wrist - elbow))
    fingertip_span = float(np.linalg.norm(fingertip_centroid - wrist))
    offset = max(0.28 * forearm_len, 0.65 * fingertip_span)
    offset = float(np.clip(offset, 8.0, 60.0))
    return wrist + blended_dir * offset


def infer_forefoot_contact_px(heel: np.ndarray, toe: np.ndarray) -> np.ndarray:
    return heel + 0.80 * (toe - heel)


def compute_contact_points_px(
    pose_landmarks: list | None,
    frame_width: int,
    frame_height: int,
) -> dict[str, np.ndarray | None]:
    result = {
        "left_hand": None,
        "right_hand": None,
        "left_foot": None,
        "right_foot": None,
    }
    if not pose_landmarks:
        return result

    px = landmarks_to_pixels(pose_landmarks, frame_width, frame_height)
    result["left_hand"] = infer_palm_contact_px(
        px[LEFT_WRIST],
        px[LEFT_ELBOW],
        px[LEFT_INDEX],
        px[LEFT_PINKY],
        px[LEFT_THUMB],
    )
    result["right_hand"] = infer_palm_contact_px(
        px[RIGHT_WRIST],
        px[RIGHT_ELBOW],
        px[RIGHT_INDEX],
        px[RIGHT_PINKY],
        px[RIGHT_THUMB],
    )
    result["left_foot"] = infer_forefoot_contact_px(px[LEFT_HEEL], px[LEFT_FOOT_INDEX])
    result["right_foot"] = infer_forefoot_contact_px(px[RIGHT_HEEL], px[RIGHT_FOOT_INDEX])
    return result

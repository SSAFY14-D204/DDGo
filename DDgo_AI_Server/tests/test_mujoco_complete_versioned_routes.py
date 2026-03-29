from __future__ import annotations

import gzip
import json
import sys
import types
import unittest
from unittest.mock import patch

from fastapi.testclient import TestClient


def _install_import_stubs() -> None:
    if "numpy" not in sys.modules:
        sys.modules["numpy"] = types.ModuleType("numpy")

    if "run_json_service_benchmark" not in sys.modules:
        bench_module = types.ModuleType("run_json_service_benchmark")
        bench_module.landmark_payload_to_objects = lambda payload: payload  # type: ignore[attr-defined]
        bench_module.evaluate_from_json_inputs = lambda *args, **kwargs: {}  # type: ignore[attr-defined]
        sys.modules["run_json_service_benchmark"] = bench_module

    if "crux_detection" not in sys.modules:
        crux_module = types.ModuleType("crux_detection")
        crux_module.build_hold_segments = lambda *args, **kwargs: {}  # type: ignore[attr-defined]
        crux_module.enrich_frames_for_crux = lambda frames: frames  # type: ignore[attr-defined]
        crux_module.score_fast_crux_candidates = lambda candidates, top_k=3: {}  # type: ignore[attr-defined]
        crux_module.score_physics_crux_candidates = lambda candidates, top_k=3: {}  # type: ignore[attr-defined]
        crux_module.summarize_hold_candidates = lambda segments: []  # type: ignore[attr-defined]
        sys.modules["crux_detection"] = crux_module

    if "polygon_hold_contact_state" not in sys.modules:
        polygon_module = types.ModuleType("polygon_hold_contact_state")

        class _PolygonHoldContactTracker:  # pragma: no cover - import stub
            def __init__(self, *args, **kwargs) -> None:
                pass

            def update_frame(self, *args, **kwargs):
                return {}

        class _PolygonHoldDetection:  # pragma: no cover - import stub
            def __init__(self, *args, **kwargs) -> None:
                pass

        polygon_module.PolygonHoldContactTracker = _PolygonHoldContactTracker  # type: ignore[attr-defined]
        polygon_module.PolygonHoldDetection = _PolygonHoldDetection  # type: ignore[attr-defined]
        polygon_module.compute_contact_points_px = lambda *args, **kwargs: []  # type: ignore[attr-defined]
        polygon_module.load_polygon_service_holds = lambda *args, **kwargs: {}  # type: ignore[attr-defined]
        polygon_module.polygon_area = lambda *args, **kwargs: 0.0  # type: ignore[attr-defined]
        polygon_module.polygon_centroid = lambda *args, **kwargs: (0.0, 0.0)  # type: ignore[attr-defined]
        sys.modules["polygon_hold_contact_state"] = polygon_module

    if "pose_sequence_correction" not in sys.modules:
        pose_module = types.ModuleType("pose_sequence_correction")
        pose_module.correct_pose_sequence_payload = lambda *args, **kwargs: {  # type: ignore[attr-defined]
            "video_metadata": {},
            "frames": [],
            "correction_summary": {},
        }
        sys.modules["pose_sequence_correction"] = pose_module


_install_import_stubs()

from app.main import app


def _batch_request_payload() -> dict[str, object]:
    return {
        "holds_json": {"holds": []},
        "pose3d_sequence_json": {
            "video_metadata": {
                "fps": 30,
                "frame_width": 1280,
                "frame_height": 720,
                "total_frames": 12,
            },
            "frames": [],
        },
        "user_body_json": {"user_profile": {"weight_kg": 60}},
        "top_k_crux": 4,
        "frame_step": 2,
    }


class MujocoCompleteVersionedRouteTest(unittest.TestCase):
    def setUp(self) -> None:
        self.client = TestClient(app)

    def test_v1_plain_batch_analyze_still_works(self) -> None:
        payload = _batch_request_payload()
        expected = {"version": "v1", "mode": "fast"}

        with patch("app.api.mujoco_complete.mujoco_complete_service.analyze_fast", return_value=expected) as mock_analyze:
            response = self.client.post("/api/v1/mujoco-complete/analyze/fast", json=payload)

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json(), expected)
        mock_analyze.assert_called_once_with(
            holds_payload=payload["holds_json"],
            pose_payload=payload["pose3d_sequence_json"],
            user_body_payload=payload["user_body_json"],
            top_k_crux=4,
            frame_step=2,
        )

    def test_v2_plain_batch_analyze_works(self) -> None:
        payload = _batch_request_payload()
        expected = {"version": "v2", "mode": "physics"}

        with patch(
            "app.api.mujoco_complete.mujoco_complete_service.analyze_physics",
            return_value=expected,
        ) as mock_analyze:
            response = self.client.post("/api/v2/mujoco-complete/analyze/physics", json=payload)

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json(), expected)
        mock_analyze.assert_called_once_with(
            holds_payload=payload["holds_json"],
            pose_payload=payload["pose3d_sequence_json"],
            user_body_payload=payload["user_body_json"],
            top_k_crux=4,
            frame_step=2,
        )

    def test_v2_gzip_batch_analyze_is_decompressed_and_routed(self) -> None:
        payload = _batch_request_payload()
        expected = {"version": "v2", "mode": "fast", "encoding": "gzip"}
        body = gzip.compress(json.dumps(payload).encode("utf-8"))

        with patch("app.api.mujoco_complete.mujoco_complete_service.analyze_fast", return_value=expected) as mock_analyze:
            response = self.client.post(
                "/api/v2/mujoco-complete/analyze/fast",
                content=body,
                headers={
                    "Content-Encoding": "gzip",
                    "Content-Type": "application/json",
                },
            )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json(), expected)
        mock_analyze.assert_called_once_with(
            holds_payload=payload["holds_json"],
            pose_payload=payload["pose3d_sequence_json"],
            user_body_payload=payload["user_body_json"],
            top_k_crux=4,
            frame_step=2,
        )

    def test_v2_realtime_session_is_not_versioned(self) -> None:
        response = self.client.post("/api/v2/mujoco-complete/session/start", json={})

        self.assertEqual(response.status_code, 404)


if __name__ == "__main__":
    unittest.main()

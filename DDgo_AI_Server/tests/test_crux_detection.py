from __future__ import annotations

import unittest

from app.services.mujoco_complete.runtime.json_service_benchmark.crux_detection import (
    build_hold_segments,
)


def _frame(frame_index: int, timestamp_ms: int, hold_id: int = 1) -> dict[str, object]:
    return {
        "frame_index": frame_index,
        "timestamp_ms": timestamp_ms,
        "active_hold_ids": {"left_hand": hold_id},
        "limb_states": {
            "left_hand": {
                "state": "GRIP",
            }
        },
    }


class CruxDetectionTimingTest(unittest.TestCase):
    def test_hold_segment_duration_prefers_timestamp_spacing(self) -> None:
        segments = build_hold_segments(
            frames=[
                _frame(frame_index=0, timestamp_ms=0),
                _frame(frame_index=1, timestamp_ms=200),
            ],
            fps=10,
        )

        segment = segments[1][0]

        self.assertAlmostEqual(segment.duration_s, 0.4, places=6)

    def test_hold_segment_duration_falls_back_to_fps_when_only_one_frame_exists(self) -> None:
        segments = build_hold_segments(
            frames=[_frame(frame_index=0, timestamp_ms=0)],
            fps=10,
        )

        segment = segments[1][0]

        self.assertAlmostEqual(segment.duration_s, 0.1, places=6)


if __name__ == "__main__":
    unittest.main()
